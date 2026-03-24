# Study Matching System (Backend)

대학생 스터디 모집 과정에서 발생하는 목표 / 수준 / 시간대 불일치 문제, 비효율적인 모집 / 관리 과정을 해결하기 위한 스토디 매칭 시스템 MVP 벡엔드 입니다.

본 시스템은 사용자가 로그인 후 자신의 관심분야, 학습 목표, 학습 수준, 원하는 학습 요일과 

---

## 프로젝트 개요

기존 스터디 모집은 커뮤니티 게시글, 단체 채팅, 지인 추천에 의존합니다.
이 방식은 다음과 같은 문제가 있습니다.

- 원하는 목표/수준/시간대가 맞는 사람 찾기 어려움
- 모집 

---

## 주요 기능

- 회원가입 / 로그인 (비밀번호 해싱 + JWT 발급)
- 인증 기반 보호 API (Bearer 토큰)
- 프로필 저장 (없으면 생성, 있으면 수정)
- 스터디 생성 / 스터디 목록 조회(공개)
- 스터디 참여 신청 (중복 신청 방지, 본인 스터디 신청 방지, 모집 상태 체크)
- 신청자(멤버) 목록 조회 (Owner 권한)
- 승인/거절 (Owner 권한, `pending`만 처리 가능, 정원 초과 방지, 정원 도달 시 자동 `closed`)
     
---

## 기술 스택

- Backend: Node.js, Express
- Database: SQLite- Auth: JWT (jsonwebtoken)
- Password Hashing: bcrypt
- Env: dotenv- API 테스트: Postman

---

## 폴더 구조

---

## 실행 방법

### 사전 요구
- Node.js: LTS
- SQLite는 `sqlite3` 라이브러리로 파일 DB를 사용하므로 별도 서버 설치 없이 동작합니다.
- `backend/database/schema.sql`이 존재해야 합니다. (서버 시작 시 schema를 읽어 DB 초기화)

### 설치/실행
```bash
cd backend
npm install
node src/server.js
