const fs = require("fs");
const path = require("path");
const db = require("./db");

const schemaPath = path.join(__dirname, "../../database/schema.sql");
const schema = fs.readFileSync(schemaPath, "utf-8");

db.exec(schema, (err) => {
  if (err) {
    console.error("Failed to initialize database:", err.message);
  } else {
    console.log("Database initialized successfully.");
  }
});