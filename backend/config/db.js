const mongoose = require('mongoose');

/**
 * Connect to MongoDB Database
 * This function uses the MONGO_URI from the .env file
 */
const connectDB = async () => {
    try {
        const conn = await mongoose.connect(process.env.MONGO_URI, {
            // useNewUrlParser and useUnifiedTopology are no longer strictly required in Mongoose 6+, 
            // but keeping them ensures compatibility across versions.
            useNewUrlParser: true,
            useUnifiedTopology: true,
        });

        console.log(`✅ MongoDB Connected successfully to host: ${conn.connection.host}`);
        console.log(`📁 Database Name: ${conn.connection.name}`);
    } catch (error) {
        console.error(`❌ MongoDB Connection Error: ${error.message}`);
        // Exit process with failure
        process.exit(1);
    }
};

module.exports = connectDB;