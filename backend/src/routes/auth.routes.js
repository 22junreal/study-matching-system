const express = require("express");
const bcrypt = require("bcrypt");
const jwt = require("jsonwebtoken");
const db = require("../config/db");
const authMiddleware = require("../middlewares/auth.middleware");

const router = express.Router();

// 회원가입
router.post("/register", async (req, res) => {
  const { username, password } = req.body;

  // 1. 입력값 체크
  if (!username || !password) {
    return res.status(400).json({ message: "Username and password required" });
  }

  try {
    // 2. 비밀번호 해싱
    const hashedPassword = await bcrypt.hash(password, 10);

    // 3. DB 저장
    const query = `
      INSERT INTO users (username, password_hash)
      VALUES (?, ?)
    `;

    db.run(query, [username, hashedPassword], function (err) {
      if (err) {
        if (err.message.includes("UNIQUE")) {
          return res.status(409).json({ message: "Username already exists" });
        }
        return res.status(500).json({ message: "Database error" });
      }

      return res.status(201).json({
        message: "User registered",
        userId: this.lastID,
      });
    });
  } catch (error) {
    return res.status(500).json({ message: "Server error" });
  }
});

// 로그인 테스트
router.get("/login-test", (req, res) => {
  res.json({ message: "login route test ok" });
});


// 로그인
router.post("/login", (req, res) => {
  const { username, password } = req.body;

  // 1. 입력값 체크
  if (!username || !password) {
    return res.status(400).json({ message: "Username and password required" });
  }

  // 2. 사용자 조회
  const query = `
    SELECT * FROM users
    WHERE username = ?
  `;

  db.get(query, [username], async (err, user) => {
    if (err) {
      return res.status(500).json({ message: "Database error" });
    }

   // 3. 사용자 없음
    if (!user) {
      return res.status(401).json({ message: "Invalid username or password" });
    }

    try {
      // 4. 비밀번호 비교
      const isMatch = await bcrypt.compare(password, user.password_hash);

      if (!isMatch) {
        return res.status(401).json({ message: "Invalid username or password" });
      }

      // 5. JWT 발급
      const token = jwt.sign(
        { id: user.id, username: user.username },
        process.env.JWT_SECRET,
        { expiresIn: "1h" }
      );

      return res.status(200).json({
        message: "Login successful",
        token,
      });
    } catch (error) {
      return res.status(500).json({ message: "Server error" });
    }
  });
});

// 현재 로그인 사용자 정보 확인
router.get("/me", authMiddleware, (req, res) => {
  return res.status(200).json({
    message: "Authenticated user",
    user: req.user,
  });
});

console.log("Loaded auth routes from:", __filename);
console.log(
  router.stack
    .filter((layer) => layer.route)
    .map((layer) => ({
      path: layer.route.path,
      methods: Object.keys(layer.route.methods),
    }))
);

module.exports = router;  
