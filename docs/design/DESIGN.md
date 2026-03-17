## 시스템 아키텍처

본 시스템은 웹 기반 Client–Server 구조로 설계된다.

### Client (Frontend)
- HTML, CSS, JavaScript 기반 웹 페이지
- 사용자 입력 처리
- API 요청 전송 및 응답 데이터 표시

### Server (Backend)
- Node.js + Express 기반 REST API 서버
- 사용자 인증 처리
- 스터디 생성 및 조회 기능 제공
- 데이터베이스와 통신

### Database
- SQLite 기반 데이터 저장
- 사용자 정보, 프로필 정보, 스터디 정보 관리

### Architecture Diagram

User Browser  
↓  
Frontend (HTML / CSS / JS)  
↓ REST API  
Backend Server (Node.js / Express)  
↓  
SQLite Database

## 기술 스택

Frontend
- HTML
- CSS
- JavaScript

Backend
- Node.js
- Express

Database
- SQLite

Deployment
- GitHub

## 데이터베이스 설계 (ERD)

### users

| column | type | description |
|------|------|-------------|
| id | INTEGER | 사용자 고유 ID |
| username | TEXT | 사용자 이름 |
| password | TEXT | 사용자 비밀번호 |
| created_at | DATETIME | 가입 시간 |

---

### profiles

| column | type | description |
|------|------|-------------|
| id | INTEGER | 프로필 ID |
| user_id | INTEGER | 사용자 ID (users.id 참조) |
| interest | TEXT | 관심 분야 |
| goal | TEXT | 학습 목표 |
| level | TEXT | 학습 수준 |
| study_day | TEXT | 공부 요일 |
| study_time | TEXT | 공부 시간 |

---

### studies
| column | type | description |
|------|------|-------------|
| id | INTEGER | 스터디 ID |
| user_id | INTEGER | 생성한 사용자 ID |
| title | TEXT | 스터디 제목 |
| category | TEXT | 스터디 분야 |
| level | TEXT | 모집 대상 수준 |
| description | TEXT | 스터디 설명 |
| max_members | INTEGER | 모집 인원 |
| study_day | TEXT | 모집 요일 |
| study_time | TEXT | 모집 시간 |
| status | TEXT | 모집 상태 |
| created_at | DATETIME | 생성 시간 |

---

### 테이블 관계

users (1) —— (1) profiles  
users (1) —— (N) studies

## API 설계

### 회원가입

POST /api/register

Request

{
  "username": "user1",
  "password": "1234"
}

Response

{
  "message": "User created successfully"
}

---

### 로그인

POST /api/login

Request

{
  "username": "user1",
  "password": "1234"
}

Response

{
  "message": "Login success"
}

---

### 프로필 저장

POST /api/profile

Request

{
  "interest": "TOEIC",
  "goal": "800점 목표",
  "level": "Intermediate",
  "study_day": "Mon, Wed",
  "study_time": "Evening"
}

Response

{
  "message": "Profile saved"
}

---

### 스터디 생성

POST /api/studies

Request

{
  "title": "TOEIC 800 목표 스터디",
  "category": "TOEIC",
  "level": "Intermediate",
  "description": "토익 800점 목표 스터디",
  "max_members": 4,
  "study_day": "Mon, Wed",
  "study_time": "Evening"
}

Response

{
  "message": "Study created"
}
---

### 스터디 목록 조회

GET /api/studies

Response

[
  {
    "id": 1,
    "title": "TOEIC 800 목표 스터디",
    "category": "TOEIC",
    "level": "Intermediate",
    "max_members": 4,
    "study_day": "Mon, Wed",
    "study_time": "Evening",
    "status": "recruiting"
  }
]

## 사용자 흐름

1. 사용자는 회원가입을 한다.
2. 사용자는 로그인한다.
3. 로그인 후 사용자 프로필을 입력한다.
4. 사용자는 스터디 제목, 설명, 모집 인원, 모집 요일, 모집 시간대를 입력하여 스터디를 생성한다.
5. 생성된 스터디는 스터디 목록에 표시된다.
6. 다른 사용자는 스터디 목록을 조회하고 조건에 맞는 스터디를 확인할 수 있다.
7. 사용자는 모집 상태를 확인하며 스터디 참여를 진행할 수 있다.
