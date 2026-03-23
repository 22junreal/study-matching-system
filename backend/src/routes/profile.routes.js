const express = require("express");
const db = require("../config/db");
const authMiddleware = require("../middlewares/auth.middleware");

const router = express.Router();

// 프로필 저장 (없으면 생성, 있으면 수정)
router.post("/", authMiddleware, (req, res) => {
  const userId = req.user.id;

  const {
    age,
    gender,
    department,
    student_id,
    preferred_category,
    preferred_day,
    preferred_time,
    mbti
  } = req.body;

  // 최소 필수값 검증
  if (!department || !student_id) {
    return res.status(400).json({
      message: "department and student_id are required"
    });
  }

  // 1. 기존 프로필 존재 여부 확인
  const checkQuery = `
    SELECT * FROM profiles
    WHERE user_id = ?
  `;

  db.get(checkQuery, [userId], (checkErr, existingProfile) => {
    if (checkErr) {
      return res.status(500).json({ message: "Database error" });
    }

    // 2. 기존 프로필 있으면 UPDATE
    if (existingProfile) {
      const updateQuery = `
        UPDATE profiles
        SET age = ?,
            gender = ?,
            department = ?,
            student_id = ?,
            preferred_category = ?,
            preferred_day = ?,
            preferred_time = ?,
            mbti = ?
        WHERE user_id = ?
      `;

      db.run(
        updateQuery,
        [
          age,
          gender,
          department,
          student_id,
          preferred_category,
          preferred_day,
          preferred_time,
          mbti,
          userId
        ],
        function (updateErr) {
          if (updateErr) {
            return res.status(500).json({ message: "Database error" });
          }

          return res.status(200).json({
            message: "Profile updated"
          });
        }
      );
    } else {
      // 3. 기존 프로필 없으면 INSERT
      const insertQuery = `
        INSERT INTO profiles (
          user_id,
          age,
          gender,
          department,
          student_id,
          preferred_category,
          preferred_day,
          preferred_time,
          mbti
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      `;

      db.run(
        insertQuery,
        [
          userId,
          age,
          gender,
          department,
          student_id,
          preferred_category,
          preferred_day,
          preferred_time,
          mbti
        ],
        function (insertErr) {
          if (insertErr) {
            return res.status(500).json({ message: "Database error" });
          }

          return res.status(201).json({
            message: "Profile created"
          });
        }
      );
    }
  });
});

module.exports = router;