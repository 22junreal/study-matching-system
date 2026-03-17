## 시스템 아키텍처

본 시스템은 웹 기반 클라이언트-서버 구조로 설계된다.

사용자는 웹 브라우저를 통해 서비스에 접속하며
프론트엔드는 REST API를 통해 백엔드 서버와 통신한다.

Backend 서버는 사용자 정보와 스터디 데이터를
데이터베이스에 저장하고 관리한다.

Architecture

User Browser
↓
Frontend (Web)
↓
Backend API Server
↓
Database

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

## 데이터베이스 설계(ERD)

users
- id
- username
- password
- created_at
 
profiles
- id
- user_id
- interest
- goal
- study_time

studies
- id
- user_id
- title
- description
- max_members
- status
- created_at

## API 설계

POST /api/register
회원가입

POST /api/login
로그인

POST /api/profile
프로필 저장

POST /api/studies
스터디 생성

GET /api/studies
스터디 목록 조회

## 사용자 흐름

- 사용자 흐름

회원가입
↓
로그인
↓
프로필 입력
↓
스터디 생성
↓
스터디 목록 조회
↓
스터디 참여
