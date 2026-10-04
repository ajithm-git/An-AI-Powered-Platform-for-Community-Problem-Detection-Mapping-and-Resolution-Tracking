"""
YOLO Inference Service
─────────────────────────────────────────────
Standalone script invoked by Node.js via child_process.spawn.

USAGE
  # Standard inference (JSON on stdin, JSON on stdout)
  echo '{ "imageBase64": "data:image/jpeg;base64,..." }' | python detect.py

  # Print model info and exit (no inference)
  python detect.py --info

  # Verbose mode — logs detections to stderr for real-time debugging
  python detect.py --verbose

  # Save annotated image (bounding boxes drawn on the photo)
  python detect.py --save-annotated ./output/annotated.jpg

INPUT (stdin, JSON)
  {
      "imageBase64": "data:image/jpeg;base64,..." | "<raw_base64>",
      "confThreshold": 0.35,     // optional
      "iouThreshold": 0.45       // optional
  }

OUTPUT (stdout, JSON)
  {
      "success": true,
      "modelLoaded": "best.pt",
      "detections": [
          {
              "class": "garbage",
              "classId": 0,
              "confidence": 0.87,
              "bbox": { "x1": 100, "y1": 200, "x2": 350, "y2": 450 }
          }
      ],
      "inferenceMs": 142,
      "imageSize": { "width": 1280, "height": 720 }
  }

ERROR (stdout, JSON + non-zero exit)
  { "success": false, "error": "...", "traceback": "..." }
"""

import sys
import os
import json
import time
import base64
import io
import traceback
import argparse
from pathlib import Path

# ─────────────────────────────────────────────
# Resolve paths
# ─────────────────────────────────────────────
SCRIPT_DIR = Path(__file__).parent.resolve()
BACKEND_DIR = SCRIPT_DIR.parent
ANNOTATED_DIR = SCRIPT_DIR / "annotated_outputs"


# ─────────────────────────────────────────────
# Logging helper (goes to stderr, never stdout)
# ─────────────────────────────────────────────
VERBOSE = False
def log(msg: str):
    """Log to stderr so it doesn't interfere with JSON stdout."""
    print(f"[YOLO-PY] {msg}", file=sys.stderr, flush=True)


# ─────────────────────────────────────────────
# Model discovery
# ─────────────────────────────────────────────
def find_model():
    """
    Locate the YOLO .pt file.
    Priority:
      1. YOLO_MODEL_PATH env var
      2. weights_yolo11n/best.pt
      3. best.pt
      4. model.pt
      5. yolo11n.pt
      6. yolo11n.pt downloaded by ultralytics cache
    """
    env_path = os.environ.get("YOLO_MODEL_PATH")
    if env_path:
        p = Path(env_path)
        if not p.is_absolute():
            p = BACKEND_DIR / env_path
        if p.exists():
            return p

    candidates = [
        SCRIPT_DIR / "weights_yolo11n" / "best.pt",
        SCRIPT_DIR / "best.pt",
        SCRIPT_DIR / "model.pt",
        SCRIPT_DIR / "yolo11n.pt",
    ]
    for c in candidates:
        if c.exists():
            return c
    return None


# ─────────────────────────────────────────────
# Load model once at startup
# ─────────────────────────────────────────────
try:
    from ultralytics import YOLO
    from PIL import Image, ImageDraw, ImageFont
    import numpy as np

    model_path = find_model()
    if model_path is None:
        raise FileNotFoundError(
            "No YOLO model found. Place a model at backend/yolo/model.pt "
            "or backend/yolo/weights_yolo11n/best.pt, "
            "or set YOLO_MODEL_PATH in .env"
        )

    log(f"Loading model: {model_path}")
    MODEL = YOLO(str(model_path))
    MODEL_NAME = model_path.name
    MODEL_CLASSES = list(MODEL.names.values()) if isinstance(MODEL.names, dict) else list(MODEL.names)
    log(f"✅ Model loaded: {MODEL_NAME} ({len(MODEL_CLASSES)} classes)")

except Exception as e:
    error_payload = {
        "success": False,
        "error": f"Model load failed: {type(e).__name__}: {e}",
        "traceback": traceback.format_exc(),
    }
    print(json.dumps(error_payload))
    sys.exit(1)


# ─────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────
def decode_base64_image(image_base64: str):
    """Accept data URI or raw base64. Return PIL Image."""
    if not image_base64:
        raise ValueError("imageBase64 is empty")

    if image_base64.startswith("data:"):
        try:
            _, b64data = image_base64.split(",", 1)
        except ValueError:
            raise ValueError("Malformed data URI (missing comma)")
    else:
        b64data = image_base64

    b64data = b64data.strip()

    try:
        raw_bytes = base64.b64decode(b64data, validate=False)
    except Exception as e:
        raise ValueError(f"Invalid base64: {e}")

    try:
        image = Image.open(io.BytesIO(raw_bytes))
        if image.mode != "RGB":
            image = image.convert("RGB")
        return image
    except Exception as e:
        raise ValueError(f"Cannot decode image: {e}")


def run_inference(image, conf_threshold: float, iou_threshold: float):
    """Run YOLO on PIL image. Return list of detection dicts."""
    np_img = np.array(image)

    results = MODEL.predict(
        source=np_img,
        conf=conf_threshold,
        iou=iou_threshold,
        verbose=False,
        save=False,
    )

    detections = []
    for result in results:
        boxes = getattr(result, "boxes", None)
        if boxes is None:
            continue

        xyxy = boxes.xyxy.cpu().numpy() if hasattr(boxes.xyxy, "cpu") else boxes.xyxy
        confs = boxes.conf.cpu().numpy() if hasattr(boxes.conf, "cpu") else boxes.conf
        clss = boxes.cls.cpu().numpy() if hasattr(boxes.cls, "cpu") else boxes.cls

        for i in range(len(xyxy)):
            x1, y1, x2, y2 = [float(v) for v in xyxy[i][:4]]
            conf = float(confs[i])
            cls_id = int(clss[i])
            cls_name = MODEL_CLASSES[cls_id] if 0 <= cls_id < len(MODEL_CLASSES) else f"class_{cls_id}"

            detections.append({
                "class": cls_name,
                "classId": cls_id,
                "confidence": round(conf, 4),
                "bbox": {
                    "x1": round(x1, 1),
                    "y1": round(y1, 1),
                    "x2": round(x2, 1),
                    "y2": round(y2, 1),
                },
            })
    return detections


def save_annotated_image(image: Image.Image, detections: list, output_path: Path):
    """Draw bounding boxes on the image and save to disk."""
    try:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        annotated = image.copy()
        draw = ImageDraw.Draw(annotated)

        try:
            font = ImageFont.truetype("arial.ttf", 16)
        except Exception:
            font = ImageFont.load_default()

        for det in detections:
            bbox = det["bbox"]
            x1, y1, x2, y2 = bbox["x1"], bbox["y1"], bbox["x2"], bbox["y2"]
            label = f"{det['class']} {det['confidence']*100:.0f}%"

            draw.rectangle([x1, y1, x2, y2], outline=(16, 185, 129), width=3)
            # Label background
            text_bbox = draw.textbbox((x1, y1), label, font=font)
            tw = text_bbox[2] - text_bbox[0]
            th = text_bbox[3] - text_bbox[1]
            draw.rectangle([x1, y1 - th - 6, x1 + tw + 8, y1], fill=(16, 185, 129))
            draw.text((x1 + 4, y1 - th - 4), label, fill=(255, 255, 255), font=font)

        annotated.save(output_path, "JPEG", quality=85)
        log(f"💾 Annotated image saved: {output_path}")
        return True
    except Exception as e:
        log(f"⚠️ Failed to save annotated image: {e}")
        return False


# ─────────────────────────────────────────────
# Model info (--info mode)
# ─────────────────────────────────────────────
def print_model_info():
    info = {
        "success": True,
        "modelLoaded": MODEL_NAME,
        "modelPath": str(model_path),
        "numClasses": len(MODEL_CLASSES),
        "classes": MODEL_CLASSES,
    }
    print(json.dumps(info))
    sys.exit(0)


# ─────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────
def main():
    global VERBOSE

    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--info", action="store_true", help="Print model info and exit")
    parser.add_argument("--verbose", action="store_true", help="Log detections to stderr")
    parser.add_argument("--save-annotated", type=str, default=None,
                        help="Save annotated image to this path")
    args, _ = parser.parse_known_args()

    VERBOSE = args.verbose

    # Info mode — no inference, no stdin
    if args.info:
        print_model_info()
        return

    started = time.time()

    try:
        # 1. Read JSON from stdin
        raw = sys.stdin.read()
        if not raw.strip():
            raise ValueError("No input received on stdin")

        try:
            payload = json.loads(raw)
        except json.JSONDecodeError as e:
            raise ValueError(f"Invalid JSON on stdin: {e}")

        image_base64 = payload.get("imageBase64", "")
        conf_threshold = float(payload.get("confThreshold", 0.35))
        iou_threshold = float(payload.get("iouThreshold", 0.45))

        if VERBOSE:
            log(f"Image payload size: {len(image_base64)} chars")
            log(f"Thresholds: conf={conf_threshold}, iou={iou_threshold}")

        # 2. Decode image
        image = decode_base64_image(image_base64)
        if VERBOSE:
            log(f"Image decoded: {image.width}x{image.height}")

        # 3. Run inference
        detections = run_inference(image, conf_threshold, iou_threshold)

        inference_ms = int((time.time() - started) * 1000)

        if VERBOSE:
            log(f"Inference complete in {inference_ms}ms — {len(detections)} detection(s)")
            for d in detections:
                log(f"   • {d['class']} ({d['confidence']*100:.1f}%)")

        # 4. Optionally save annotated image
        if args.save_annotated:
            save_path = Path(args.save_annotated)
            if not save_path.is_absolute():
                save_path = SCRIPT_DIR / save_path
            save_annotated_image(image, detections, save_path)

        # 5. Emit success
        print(json.dumps({
            "success": True,
            "modelLoaded": MODEL_NAME,
            "detections": detections,
            "inferenceMs": inference_ms,
            "imageSize": {"width": image.width, "height": image.height},
        }))
        sys.exit(0)

    except Exception as e:
        print(json.dumps({
            "success": False,
            "error": f"{type(e).__name__}: {e}",
            "traceback": traceback.format_exc(),
        }))
        sys.exit(2)


if __name__ == "__main__":
    main()