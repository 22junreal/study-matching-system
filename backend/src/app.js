const express = require("express");
const authRoutes = require("./routes/auth.routes");
const profileRoutes = require("./routes/profile.routes");
const studyRoutes = require("./routes/study.routes");

const app = express();

app.use(express.json());

app.get("/", (req, res) => {
  res.json({ message: "Backend server is running" });
});

app.use("/api/auth", authRoutes);
app.use("/api/profile", profileRoutes);
app.use("/api/studies", studyRoutes);

module.exports = app;