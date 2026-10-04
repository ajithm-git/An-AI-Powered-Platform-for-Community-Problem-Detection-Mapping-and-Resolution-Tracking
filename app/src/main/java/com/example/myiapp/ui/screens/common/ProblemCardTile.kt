package com.example.myiapp.ui.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myiapp.data.model.Problem
import com.example.myiapp.ui.theme.PrimaryGreen
import com.example.myiapp.ui.theme.SurfaceWhite
import com.example.myiapp.ui.theme.TextPrimary
import com.example.myiapp.ui.theme.TextSecondary
import com.example.myiapp.ui.theme.TextTertiary

/**
 * Shared small card used in Resident feed & my-reports lists.
 * Maintainer and Admin use their own richer cards.
 */
@Composable
fun ProblemCardTile(
    problem: Problem,
    onClick: () -> Unit,
    highlightMyReport: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(problem.priority, priorityColor(problem.priority))
                Spacer(Modifier.width(6.dp))
                Chip(problem.status, statusColor(problem.status))
                Spacer(Modifier.weight(1f))
                if (highlightMyReport) {
                    Chip("My Report", PrimaryGreen)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                problem.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    problem.location,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private fun priorityColor(p: String) = when (p) {
    "Urgent" -> Color(0xFFDC2626)
    "High" -> Color(0xFFEF4444)
    "Medium" -> Color(0xFFF59E0B)
    "Low" -> Color(0xFF10B981)
    else -> TextTertiary
}

private fun statusColor(s: String) = when (s) {
    "Open" -> Color(0xFFF59E0B)
    "Assigned" -> Color(0xFF3B82F6)
    "In Progress" -> Color(0xFF8B5CF6)
    "Resolved" -> Color(0xFF10B981)
    "Rejected" -> Color(0xFFEF4444)
    else -> TextTertiary
}