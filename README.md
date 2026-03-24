# Study Matching System (Backend)

대학생 스터디 모집 과정에서 발생하는 “목표/수준/시간대 불일치” 및 “모집 관리 비효율” 문제를 줄이기 

위한 **스터디 매칭 시스템 MVP 백엔드**입니다.
사용자는 회원가입/로그인 후 프로필을 저장하고, 스터디를 생성하거나 스터디에 참여 신청할 수 있습니다. 
스터디 생성자(Owner)는 신청자 목록을 조회해 승인/거절할 수 있으며, 승인 인원이 정원에 도달하면 스
터디는 자동으로 `closed` 처리됩니다.

---

## 문제 정의 (SRS 요약)

기존 스터디 모집은 커뮤니티/단체채팅/지인 추천에 의존하여, 원하는 목표·수준·요일·시간대가 맞는 사람을 
찾기 어렵고 모집/관리 과정이 비효율적입니다. 본 시스템은 사용자 프로필 기반의 스터디 생성/조회/신청 
및 생성자 승인/거절 흐름을 제공하여 이를 개선합니다.

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

## 폴더 구조 (핵심)

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
