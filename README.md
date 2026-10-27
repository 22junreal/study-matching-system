# Study Matching System

스터디 모집·신청·참여자 관리를 제공하는 Spring Boot REST API다. 현재 실행 대상은 `spring-backend/`이며 `backend/`의 Node.js 코드는 초기 구현 참고 자료다. AI 추천·학습계획 생성·매칭 점수 계산은 아직 구현하지 않았다.

## 현재 상태와 증거

2026-10-27 기준 로컬 검증 결과다. **클라우드 생성은 보류했으며 공개 서비스 운영 실적은 없다.**

| 항목 | 확인 결과 | 근거 |
|---|---|---|
| 전체 테스트 | 179개, 실패/오류/건너뜀 0 | [테스트 요약](reports/verification/tests.json) |
| 입력 정규화 | 공통 enum·요일 배열·HH:mm, V8 변환 및 오류 시 롤백 | [입력 계약](docs/INPUT_CONTRACT.md) |
| 참여자 관리 | 탈퇴·강퇴·위임·재가입 정책 및 경쟁 조건 검증 | [Lifecycle](docs/STUDY_MEMBER_LIFECYCLE.md) |
| HTTPS | 로컬 CA를 명시적으로 신뢰해 인증서 검증 및 인증 API 호출 성공 | [운영 원자료](reports/verification/operations.json) |
| Rate Limit | 인증 요청 12개 중 7개 429, 위조 IP 헤더 우회 차단 | [운영 원자료](reports/verification/operations.json) |
| Backup/Restore | 12만 건 스터디 포함, 새 DB의 테이블별 행 수·내용 해시 일치 | [운영 원자료](reports/verification/operations.json) |
| 장애·로그 | DB 중단 503 / 복구 200, requestId 상관관계, 비밀값 미노출 확인 | [운영 가이드](docs/PRODUCTION.md) |
| 성능 | 필터 검색 p95 28.38 → 7.09ms, p99 41.84 → 21.85ms | [측정 조건·한계·실행계획](docs/PERFORMANCE.md) |

테스트와 운영 검증은 임시 PostgreSQL 컨테이너에서 실행했다. GitHub Actions workflow는 저장소에 있으나 위 표는 로컬 실행 결과이며 원격 CI 성공을 주장하지 않는다. 성능 수치는 로컬 40 requests/s 단기 비교로, 운영 SLA나 최대 처리량이 아니다.

## 기술과 구조

- Java 21, Spring Boot 4.1.0, Spring Security/JWT, Spring Data JPA
- PostgreSQL 17, Flyway V1~V9, Hibernate schema validate
- JUnit 5, Testcontainers, 실제 HTTP E2E 테스트
- Docker Compose, Caddy 2.10.2, Nginx 1.28.0, k6 1.3.0

```text
spring-backend/  현재 API·migration·테스트
infra/          개발/운영 Compose, HTTPS·Rate Limit 프록시
scripts/        로컬 검증, 백업·복원, 성능 측정
perf/           k6 workload와 측정 대상 인덱스
reports/        실행한 테스트·운영·성능 원자료
backend/        초기 Node.js 구현 (현재 Spring API와 별도)
```

기존 [설계](docs/DESIGN.md), [요구사항](docs/SRS.md)는 초기 설계/진행 이력을 포함한다. 현재 동작의 기준은 이 README, 아래 API 표와 연결된 계약 문서, `spring-backend/src/main`이다.

## 개발 실행

Docker Desktop/Engine과 Compose v2가 필요하다. 이미지 빌드가 Java/Gradle을 포함한다.

```bash
cp -n .env.example .env
# .env의 DB 자격 증명과 JWT_SECRET 등을 설정한다.
docker compose up -d --build
curl -fsS http://localhost:8080/actuator/health
```

- 루트 compose.yaml은 기존 `infra` 프로젝트 이름과 개발 DB 볼륨을 사용한다. 기존 DB가 있으면 [V8 계약 변경](docs/INPUT_CONTRACT.md)과 [백업 절차](docs/PRODUCTION.md)를 먼저 확인한다.
- `SERVER_PORT` 기본값은 8080이다. 바꿨다면 확인 URL도 바꾼다.
- local Swagger: http://localhost:8080/swagger-ui/index.html, OpenAPI: `/v3/api-docs`.
- 종료: `docker compose down`. 데이터 보존을 위해 기존 개발/운영 DB에 `down -v`를 사용하지 않는다.
- 호스트에서 실행하려면 Java 21과 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` 환경변수가 필요하다. Gradle은 .env를 자동으로 읽지 않는다.

```bash
cd spring-backend
bash gradlew bootRun --args='--spring.profiles.active=local'
```

## API

회원가입·로그인과 health를 제외한 아래 API에는 `Authorization: Bearer <accessToken>`이 필요하다. local 문서 경로는 공개되어 있고 prod에서는 문서 기능이 비활성화된다.

| 메서드 | 경로 | 동작 / 권한 |
|---|---|---|
| POST | `/api/auth/register` | username/password/email 회원가입, 201 |
| POST | `/api/auth/login` | accessToken/tokenType 반환, 200 |
| GET | `/api/members/me` | 내 계정 |
| GET / PUT | `/api/profiles/me` | 내 프로필 조회/수정 |
| POST | `/api/studies` | 스터디 생성, 생성자가 방장·ACTIVE 참여자 |
| GET | `/api/studies` | category/level/status 검색과 page/size 페이지네이션 |
| GET | `/api/studies/mine` | **내가 방장인** 스터디 목록 |
| GET | `/api/studies/{studyId}` | 스터디 상세 |
| PUT / DELETE | `/api/studies/{studyId}` | 방장만 수정/삭제 |
| POST | `/api/studies/{studyId}/applications` | 참여 신청, 201 |
| GET | `/api/studies/{studyId}/applications` | 방장만 신청 목록 조회 |
| PATCH | `/api/studies/{studyId}/applications/{applicationId}/approve` | 방장 승인 |
| PATCH | `/api/studies/{studyId}/applications/{applicationId}/reject` | 방장 거절 |
| PATCH | `/api/studies/{studyId}/applications/{applicationId}/cancel` | 본인의 PENDING 신청 취소 |
| PATCH | `/api/studies/{studyId}/applications/{applicationId}/reapply` | 취소 후 또는 자진 탈퇴 후 재신청 |
| GET | `/api/studies/{studyId}/members` | 현재 참여자만 ACTIVE 멤버 조회 |
| DELETE | `/api/studies/{studyId}/members/me` | 자진 탈퇴, 204 |
| DELETE | `/api/studies/{studyId}/members/{memberId}` | 방장 강퇴, 204 |
| PATCH | `/api/studies/{studyId}/owner` | 방장 위임, 본문 `{"memberId": 2}` |
| GET | `/actuator/health` | 공개 health |

검색 기본값은 page=0, size=10이며 size는 1~100이다. createdAt DESC, id DESC로 정렬한다. 응답은 content/page/size/totalElements/totalPages/first/last다.

## 입력 계약

스터디 생성/수정 예시:

```json
{
  "title": "Java 스터디",
  "description": "주 2회",
  "category": "PROGRAMMING",
  "level": "BEGINNER",
  "days": ["MONDAY", "WEDNESDAY"],
  "startTime": "19:00",
  "endTime": "21:00",
  "maxMembers": 4
}
```

- category: PROGRAMMING / AI / DATA / LANGUAGE / CERTIFICATE / ETC.
- level: BEGINNER / INTERMEDIATE / ADVANCED. category/level은 JSON과 query 모두 대소문자·앞뒤 공백을 정규화한다.
- days는 배열이며 영문 약어도 받지만 응답은 월→일 순서의 전체 이름이다. 중복 요일은 400이다. 이전 `"MON,WED"` 문자열은 더 이상 받지 않는다.
- 시간은 정확한 HH:mm이며 시작 < 종료다. 스터디는 양쪽 생략을 허용하고, 한쪽만 지정·24:00·초 단위·자정을 넘는 범위는 거절한다.
- 프로필은 department와 같은 category/level/days/startTime/endTime을 사용하며 모두 필수다. 이전 preferredCategory/availableDays/availableTime 입력은 새 필드로 바꿔야 한다.

정확한 null/빈 배열 처리와 V8 이전 규칙은 [입력 계약](docs/INPUT_CONTRACT.md)에 있다.

## 상태·권한·정합성

- 신청 상태: PENDING / APPROVED / REJECTED / CANCELED.
- 참여 상태: ACTIVE / LEFT / REMOVED. 실제 인원은 방장을 포함한 ACTIVE 참여자 수다.
- 자진 탈퇴자는 재신청 후 승인받아 복귀할 수 있다. 강퇴자는 재신청을 차단한다. 방장은 위임 후 탈퇴할 수 있다.
- 스터디의 RECRUITING/CLOSED는 현재 정원에 따라 전환된다. 수동 마감과 정원 마감을 별도로 구분하는 기능은 없다.
- 인원·방장 권한을 바꾸는 작업은 Study 비관적 잠금으로 직렬화한다. 취소와 승인·거절 사이의 stale update는 StudyApplication의 @Version이 차단한다.
- 기본 오류 응답은 code/status/message/timestamp다. validation 응답에는 errors가 추가된다. 인증 실패 401, 권한 실패 403, 충돌 409, 인증 중 DB 장애·예상치 못한 서버 오류 500을 구분한다.

자세한 정책은 [Lifecycle 가이드](docs/STUDY_MEMBER_LIFECYCLE.md)에 있다.

## 테스트

Java 21과 Docker가 실행 중이어야 한다.

```bash
cd spring-backend
bash gradlew clean test
```

macOS에서 Docker를 찾지 못하면:

```bash
export DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
bash gradlew clean test
```

전체 실행에는 Service/Controller/Entity, V6~V8 데이터 이전, DB 제약, 정원·신청·위임 동시성, 실제 JWT/HTTP E2E, 입력 정규화, 조회 쿼리 수 검증이 포함된다. 빈 DB에는 V1~V9를 실제 적용한다. Docker 이미지 빌드는 bootJar만 수행하므로 테스트는 별도 실행해야 한다.

## 운영·성능 재현

```bash
# 저장소 루트. 운영 DB가 아닌 study-proof 로컬 환경 전용.
python3 scripts/prepare-proof.py
python3 scripts/measure-performance.py
python3 scripts/verify-operations.py
```

- [운영 가이드](docs/PRODUCTION.md): prod 환경, Caddy HTTPS, Nginx 요청 제한, JSON 로그, 백업/새 DB 복원, 종료 방법.
- [성능 보고서](docs/PERFORMANCE.md): k6 p95/p99, 5회 실행계획, V9 인덱스 개선과 한계.
- [운영 원자료](reports/verification/operations.json), [성능 원자료](reports/performance/metadata.json), [최종 소스 해시](reports/verification/source-manifest.json).

실제 클라우드·도메인·공인 TLS·운영 DB·외부 암호화 백업·상시 모니터링은 향후 작업이다. 현재 구현 및 로컬 측정 결과와 구분한다.
