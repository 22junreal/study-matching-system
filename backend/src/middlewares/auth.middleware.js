const jwt = require("jsonwebtoken");

const authMiddleware = (req, res, next) => {
  const authHeader = req.headers.authorization;

  // 1. Authorization 헤더 없는 경우
  if (!authHeader) {
    return res.status(401).json({ message: "Authentication required" });
  }

  // 2. Bearer 형식 확인
  const parts = authHeader.split(" ");

  if (parts.length !== 2 || parts[0] !== "Bearer") {
    return res.status(401).json({ message: "Invalid token format" });
  }

  const token = parts[1];

  try {
    // 3. 토큰 검증
    const decoded = jwt.verify(token, process.env.JWT_SECRET);

    // 4. 검증 성공 시 req.user에 저장
    req.user = decoded;

    // 5. 다음 라우트로 이동
    next();
  } catch (error) {
    return res.status(401).json({ message: "Invalid or expired token" });
  }
};

module.exports = authMiddleware;