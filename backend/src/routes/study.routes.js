const express = require("express");
const db = require("../config/db");
const authMiddleware = require("../middlewares/auth.middleware");

const router = express.Router();

router.get("/test", (req, res) => {
  res.json({ message: "STUDY_TEST_ROUTE_7719" });
});

// 스터디 생성
router.post("/", authMiddleware, (req, res) => {
  const ownerId = req.user.id;

  const {
    title,
    category,
    description,
    study_day,
    study_time,
    level,
    max_members
  } = req.body;

  if (!title || !category || !study_day || !study_time || !level || max_members === undefined || max_members === null) {
  return res.status(400).json({
    message: "title, category, study_day, study_time, level, max_members are required"
  });
}

  if (Number(max_members) < 1) {
    return res.status(400).json({
      message: "max_members must be at least 1"
    });
  }

  const insertQuery = `
    INSERT INTO studies (
      owner_id,
      title,
      category,
      description,
      study_day,
      study_time,
      level,
      max_members
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `;

  db.run(
    insertQuery,
    [
      ownerId,
      title,
      category,
      description,
      study_day,
      study_time,
      level,
      max_members
    ],
    function (err) {
      if (err) {
        return res.status(500).json({ message: "Database error" });
      }

      return res.status(201).json({
        message: "Study created",
        studyId: this.lastID
      });
    }
  );
});

// 스터디 목록 조회
router.get("/", (req, res) => {
  const selectQuery = `
    SELECT * FROM studies
    ORDER BY id DESC
  `;

  db.all(selectQuery, [], (err, rows) => {
    if (err) {
      return res.status(500).json({ message: "Database error" });
    }

    return res.status(200).json(rows);
  });
});

console.log("Loaded study routes from:", __filename);
console.log(
  router.stack
    .filter((layer) => layer.route)
    .map((layer) => ({
      path: layer.route.path,
      methods: Object.keys(layer.route.methods),
    }))
);

// 스터디 참여 신청
router.post("/:studyId/join", authMiddleware, (req, res) => {
  const studyId = req.params.studyId;
  const userId = req.user.id;

  // 1. 스터디 존재 여부 확인
  const studyQuery = `
    SELECT * FROM studies
    WHERE id = ?
  `;

  db.get(studyQuery, [studyId], (studyErr, study) => {
    if (studyErr) {
      return res.status(500).json({ message: "Database error" });
    }

    if (!study) {
      return res.status(404).json({ message: "Study not found" });
    }

    // 2. 모집 상태 확인
    if (study.status !== "recruiting") {
      return res.status(400).json({ message: "Study is not recruiting" });
    }

    // 3. 본인 스터디 신청 금지
    if (study.owner_id === userId) {
      return res.status(400).json({ message: "You cannot join your own study" });
    }

    // 4. 중복 신청 확인
    const checkMemberQuery = `
      SELECT * FROM study_members
      WHERE study_id = ? AND user_id = ?
    `;

    db.get(checkMemberQuery, [studyId, userId], (memberErr, existingMember) => {
      if (memberErr) {
        return res.status(500).json({ message: "Database error" });
      }

      if (existingMember) {
        return res.status(409).json({ message: "Already applied to this study" });
      }

      // 5. 참여 신청 저장
      const insertMemberQuery = `
        INSERT INTO study_members (study_id, user_id, status)
        VALUES (?, ?, 'pending')
      `;

      db.run(insertMemberQuery, [studyId, userId], function (insertErr) {
        if (insertErr) {
          return res.status(500).json({ message: "Database error" });
        }

        return res.status(201).json({
          message: "Join request submitted"
        });
      });
    });
  });
});

// 신청자 조회 (스터디 owner만 가능)
router.get("/:studyId/members", authMiddleware, (req, res) => {
  const studyId = req.params.studyId;
  const userId = req.user.id;

  // 1. 스터디 존재 여부 확인
  const studyQuery = `
    SELECT * FROM studies
    WHERE id = ?
  `;

  db.get(studyQuery, [studyId], (studyErr, study) => {
    if (studyErr) {
      return res.status(500).json({ message: "Database error" });
    }

    if (!study) {
      return res.status(404).json({ message: "Study not found" });
    }

    // 2. owner 권한 확인
    if (study.owner_id !== userId) {
      return res.status(403).json({
        message: "Forbidden: only the study owner can view members"
      });
    }

    // 3. 신청자 목록 조회
    const membersQuery = `
      SELECT
        study_members.user_id,
        users.username,
        study_members.status,
        study_members.created_at
      FROM study_members
      JOIN users ON study_members.user_id = users.id
      WHERE study_members.study_id = ?
      ORDER BY study_members.created_at ASC
    `;

    db.all(membersQuery, [studyId], (membersErr, rows) => {
      if (membersErr) {
        return res.status(500).json({ message: "Database error" });
      }

      return res.status(200).json(rows);
    });
  });
});

// 신청 승인 / 거절 (스터디 owner만 가능)
router.patch("/:studyId/members/:userId", authMiddleware, (req, res) => {
  const studyId = req.params.studyId;
  const targetUserId = req.params.userId;
  const requesterId = req.user.id;
  const { status } = req.body;

  // 1. 요청 상태값 검증
  if (status !== "approved" && status !== "rejected") {
    return res.status(400).json({
      message: "status must be approved or rejected"
    });
  }

  // 2. 스터디 존재 여부 확인
  const studyQuery = `
    SELECT * FROM studies
    WHERE id = ?
  `;

  db.get(studyQuery, [studyId], (studyErr, study) => {
    if (studyErr) {
      return res.status(500).json({ message: "Database error" });
    }

    if (!study) {
      return res.status(404).json({ message: "Study not found" });
    }

    // 3. owner 권한 확인
    if (study.owner_id !== requesterId) {
      return res.status(403).json({
        message: "Forbidden: only the study owner can update member status"
      });
    }

    // 4. 신청 내역 존재 확인
    const memberQuery = `
      SELECT * FROM study_members
      WHERE study_id = ? AND user_id = ?
    `;

    db.get(memberQuery, [studyId, targetUserId], (memberErr, member) => {
      if (memberErr) {
        return res.status(500).json({ message: "Database error" });
      }

      if (!member) {
        return res.status(404).json({ message: "Join request not found" });
      }

      // 5. pending 상태만 변경 가능
      if (member.status !== "pending") {
        return res.status(400).json({
          message: "Only pending requests can be updated"
        });
      }

      // 6. 승인일 경우 정원 체크
      if (status === "approved") {
        const countApprovedQuery = `
          SELECT COUNT(*) AS approvedCount
          FROM study_members
          WHERE study_id = ? AND status = 'approved'
        `;

        db.get(countApprovedQuery, [studyId], (countErr, countRow) => {
          if (countErr) {
            return res.status(500).json({ message: "Database error" });
          }

          if (countRow.approvedCount >= study.max_members) {
            return res.status(400).json({ message: "Study is already full" });
          }

          // 승인 처리
          updateMemberStatusAndCloseIfNeeded(studyId, targetUserId, status, study.max_members, res);
        });
      } else {
        // 거절 처리
        updateMemberStatus(targetUserId, studyId, status, res);
      }
    });
  });
});

function updateMemberStatus(targetUserId, studyId, status, res) {
  const updateQuery = `
    UPDATE study_members
    SET status = ?
    WHERE study_id = ? AND user_id = ?
  `;

  db.run(updateQuery, [status, studyId, targetUserId], function (updateErr) {
    if (updateErr) {
      return res.status(500).json({ message: "Database error" });
    }

    return res.status(200).json({
      message: `Member status updated to ${status}`
    });
  });
}

function updateMemberStatusAndCloseIfNeeded(studyId, targetUserId, status, maxMembers, res) {
  const updateQuery = `
    UPDATE study_members
    SET status = ?
    WHERE study_id = ? AND user_id = ?
  `;

  db.run(updateQuery, [status, studyId, targetUserId], function (updateErr) {
    if (updateErr) {
      return res.status(500).json({ message: "Database error" });
    }

    const countApprovedQuery = `
      SELECT COUNT(*) AS approvedCount
      FROM study_members
      WHERE study_id = ? AND status = 'approved'
    `;

    db.get(countApprovedQuery, [studyId], (countErr, countRow) => {
      if (countErr) {
        return res.status(500).json({ message: "Database error" });
      }

      if (countRow.approvedCount >= maxMembers) {
        const closeStudyQuery = `
          UPDATE studies
          SET status = 'closed'
          WHERE id = ?
        `;

        db.run(closeStudyQuery, [studyId], (closeErr) => {
          if (closeErr) {
            return res.status(500).json({ message: "Database error" });
          }

          return res.status(200).json({
            message: `Member status updated to ${status}`
          });
        });
      } else {
        return res.status(200).json({
          message: `Member status updated to ${status}`
        });
      }
    });
  });
}

module.exports = router;