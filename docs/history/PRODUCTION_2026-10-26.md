> 과거 V5 기준 검증 기록입니다. 현재 운영 구성과 결과는 ../PRODUCTION.md를 참고하세요.

# Spring Boot 운영 실행 준비

## 구성

- 개발: 루트 `compose.yaml` → `infra/compose.local.yml`, `local` profile, 기존 `infra` 프로젝트/볼륨 유지.
- 운영: `infra/compose.prod.yml` 단독 사용, `prod` profile 고정, 별도 `study-matching-prod` 프로젝트/볼륨.
- 운영 PostgreSQL은 호스트 포트를 공개하지 않는다. backend는 기본적으로 `127.0.0.1`에 바인딩한다.
- 실제 외부 공개 시 배포 환경에 맞는 HTTPS 진입점, 방화벽, DB 백업/복원 절차를 설정한다. 이번 구성에는 서버 생성이나 프록시 설치를 포함하지 않는다.

## 환경변수와 실행

저장소 루트에서 실행한다. `.env.prod`에는 개발용과 다른 자격 증명을 사용한다.

```bash
cd /Users/euijun/IdeaProjects/study-matching-system
cp -n .env.prod.example .env.prod
chmod 600 .env.prod
openssl rand -base64 32
```

`.env.prod`의 빈 `POSTGRES_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` 값을 입력한다. JWT는 위 명령으로 생성한 Base64 문자열, CORS는 실제 프런트엔드 origin을 쉼표로 구분해 지정한다(예: `https://study.example.com`, 경로·와일드카드 없이).

- Compose가 `POSTGRES_*`로 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 전달한다.
- `PORT`는 컨테이너와 호스트의 backend 포트다. 개발 서버가 8080을 사용 중이면 검증용으로 `PORT=18080`을 사용한다.
- 호스트/JAR 직접 실행 시 `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`를 환경변수로 전달한다. `.env.prod`는 Java/Gradle이 자동으로 읽지 않는다.
- 공통 설정의 포트 우선순위는 `PORT` → 기존 `SERVER_PORT` → `8080`이다. 개발 Compose의 `SERVER_PORT`는 기존처럼 호스트 포트로 사용한다.
- 같은 호스트의 프록시는 `127.0.0.1:PORT`로 연결한다. 다른 호스트/컨테이너의 프록시를 사용할 때는 네트워크 연결 방식과 `BACKEND_BIND_ADDRESS`를 해당 환경에 맞춰 조정한다.

```bash
docker compose --env-file .env.prod -f infra/compose.prod.yml config -q
docker compose --env-file .env.prod -f infra/compose.prod.yml build backend
docker compose --env-file .env.prod -f infra/compose.prod.yml up -d --wait --wait-timeout 180
docker compose --env-file .env.prod -f infra/compose.prod.yml ps
curl -fsS http://127.0.0.1:8080/actuator/health
```

`PORT`가 다르면 확인 URL도 변경한다. 성공 응답에는 `"status":"UP"`이 포함되며 DB 세부 정보는 표시하지 않는다. `config -q`는 값 출력 없이 구문/필수 변수만 검사한다. `config` 전체 출력이나 `docker inspect` 전체 출력에는 비밀값이 포함될 수 있으므로 공유하지 않는다.

## Health / API / 종료

- 인증 없이 공개하는 Actuator 경로는 `/actuator/health`다. DB health를 포함하고 세부 정보와 구성 요소를 숨긴다.
- 이미지의 healthcheck는 15초마다 실제 앱 포트의 `/actuator/health`를 조회한다. 시작 유예 60초, 3회 실패 시 unhealthy다.
- DB가 중단되면 health는 DOWN/HTTP 503 또는 연결 풀 대기 중 타임아웃이 되며 Docker도 unhealthy가 된다. DB가 복구되면 다시 healthy가 된다.
- `restart: unless-stopped`는 프로세스 종료에 대한 재시작 정책이다. unhealthy 표시만으로 자동 재시작하지 않는다.
- `prod`에서는 Swagger와 OpenAPI 생성을 비활성화한다. API 검증은 회원가입/로그인 후 JWT로 보호된 API를 호출한다. 개발 profile의 Swagger는 유지한다.
- Spring 종료 대기는 phase당 30초, Compose 종료 유예는 60초다. SIGTERM이 Java 프로세스로 직접 전달되도록 exec 형식의 ENTRYPOINT를 유지한다.

```bash
docker compose --env-file .env.prod -f infra/compose.prod.yml logs --tail=100 backend
docker compose --env-file .env.prod -f infra/compose.prod.yml down
```

`down`은 DB 볼륨을 유지한다. 운영 DB 보존을 위해 `down -v`는 사용하지 않는다. 기존 볼륨의 DB 비밀번호는 환경변수 변경만으로 갱신되지 않는다.

## Flyway / 비밀값

Flyway가 버전 순서대로 migration을 적용하고, Hibernate는 `ddl-auto=validate`로 결과를 검증한다. prod에서는 clean을 차단하고 checksum 검증을 켜며 자동 baseline과 out-of-order migration을 끈다. 기존 migration SQL은 수정하지 않고 새 버전을 추가한다. 배포 전 백업과 migration 내용을 확인한다. 애플리케이션 이미지를 되돌리는 것만으로 DB migration이 되돌아가지는 않는다.

`.env`, `.env.prod` 등은 Git에서 제외한다. 예제 파일만 추적한다. backend 빌드 context는 `spring-backend`로 제한하고 `.dockerignore`로 중첩 `.env*`도 제외한다. Dockerfile은 빌드 파일과 `src/main`만 복사하며 최종 이미지에는 빌드한 JAR만 복사한다. 비밀값은 실행 시 환경변수로 주입하고 소스/리소스/빌드 인자에 넣지 않는다.

## 기존 테스트

Docker Desktop을 실행한 상태에서:

```bash
cd /Users/euijun/IdeaProjects/study-matching-system/spring-backend
export DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
bash gradlew clean test
```

## 실행 검증 기록 (2026-10-26)

macOS / Java 21 / Docker Desktop에서 별도 `study-readiness-check` 프로젝트와 새 DB 볼륨, 임시 자격 증명, `PORT=18080`으로 검증했다. 기존 개발 컨테이너와 DB는 유지했다.

| 항목 | 결과 |
|---|---|
| `bash gradlew clean test` | 105개 통과, 실패/오류/건너뜀 0 |
| Docker 이미지 빌드 / prod 기동 | 성공, backend와 PostgreSQL healthy |
| Flyway | 빈 DB에 V1~V5 적용 성공, 재기동 시 검증 성공 및 추가 migration 없음 |
| Health | HTTP 200 UP, DB 세부 정보/구성 요소 미노출 |
| DB 장애 / 복구 | 중지 시 HTTP 503 DOWN 및 Docker unhealthy, 복구 후 앱 재시작 없이 healthy |
| API 인증 | 회원가입 201, 로그인 200, 인증된 스터디 조회 200, 미인증 조회 401 |
| CORS | 지정 origin preflight 200, 미허용 origin 403 |
| prod 문서 / 관리 경로 | JWT 인증 상태에서도 Swagger, OpenAPI, actuator/env, actuator/info는 404 |
| 정상 종료 | SIGTERM 후 graceful shutdown 및 연결 풀 종료 완료 로그, 종료 코드 143, OOM 없음 |
| local 호환성 | 새 이미지에서 SERVER_PORT=8080 적용, health/Swagger/OpenAPI 200 |
| 필수값 누락 | DB 비밀번호, JWT, CORS 빈값 각각 Compose에서 차단 |
| 비밀값 | Git env 제외 확인, JAR의 .env 미포함, 알려진 비밀값이 JAR 리소스/이미지 메타데이터에 없는 것 확인 |
| 설정 검사 | 개발/운영 Compose 검증 및 git diff --check 통과 |

장시간 진행 중인 요청을 이용한 종료 부하 테스트와 실제 외부 HTTPS 접속은 이번 검증에 포함하지 않았다. 비밀값 점검은 현재 파일 및 알려진 값 기준이며 Git 전체 이력에 대한 감사는 아니다. 현재 개발용 DB 비밀번호는 공개 예제와 같은 값이므로 운영에 재사용하지 않는다.

## 참고

- [Spring Boot graceful shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html)
- [Spring Boot Actuator health](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Docker Compose 서비스 설정](https://docs.docker.com/reference/compose-file/services/)
