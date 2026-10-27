# 운영 구성과 검증

## 검증 범위

실제 클라우드 생성은 사용자 요청으로 보류했다. 공개 도메인, 공인 인증서 발급, 클라우드 운영 DB, 원격 백업 저장소 및 운영 트래픽 실적은 아직 없다. 현재 증거는 **로컬 Docker Desktop에서 prod profile로 실행한 결과**다. 최신 수치는 [운영 검증 JSON](../reports/verification/operations.json)에 기록한다.

이전 V5 기준 기록은 [2026-10-26 이력](history/PRODUCTION_2026-10-26.md)이다. 현재 검증과 혼동하지 않는다.

## 구성

`infra/compose.prod.yml`에 `infra/compose.edge.yml`을 병합한다.

```text
HTTPS client → Caddy → Nginx → Spring Boot → PostgreSQL
               TLS     제한/접근로그  JWT/도메인/JSON 로그
```

- Caddy만 외부 80/443을 연다. Nginx는 호스트 포트를 열지 않는다.
- backend의 직접 포트는 기본 127.0.0.1, PostgreSQL은 호스트 포트를 열지 않는다. 직접 backend 포트는 프록시의 Rate Limit을 거치지 않으므로 외부 공개하지 않는다.
- Caddy는 X-Study-Client-IP를 실제 접속 주소로 덮어쓴다. Nginx는 이 값으로 요청을 제한한다. 이 신뢰 모델은 단독 Caddy 진입점 기준이다. 앞단에 CDN/LB를 추가할 때는 신뢰할 프록시를 별도로 설계해야 한다.
- 현재는 단일 앱·단일 PostgreSQL 인스턴스다. HA, 멀티 리전, 외부 로그 수집기는 포함하지 않는다.

## 환경과 실행

```bash
cp -n .env.prod.example .env.prod
chmod 600 .env.prod
openssl rand -base64 32
```

.env.prod에 POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD, JWT_SECRET, CORS_ALLOWED_ORIGINS를 설정한다. PUBLIC 배포 시 SITE_ADDRESS에 보유 도메인을 넣고 TLS_MODE는 비운다. DNS와 방화벽이 80/443을 해당 서버로 연결해야 공인 ACME 인증서 발급을 시도할 수 있다. 이 외부 발급은 아직 검증하지 않았다.

```bash
docker compose --env-file .env.prod -f infra/compose.prod.yml -f infra/compose.edge.yml config -q
docker compose --env-file .env.prod -f infra/compose.prod.yml -f infra/compose.edge.yml up -d --build --wait --wait-timeout 180
```

`config -q`는 비밀값을 출력하지 않는다. 전체 config/inspect 출력은 환경변수를 포함하므로 공유하지 않는다. 비밀값은 실행 시 주입하며 .env 파일은 Git에서 제외된다.

## 로컬 증거 재현

Docker Desktop, Java 21(테스트), Python 3, Bash가 필요하다. macOS/Linux 경로를 사용하는 보조 스크립트다. 스크립트는 `/private/tmp/study-proof.env`에 임시 자격 증명을 권한 600으로 생성하고, 고정된 `study-proof` 프로젝트만 사용한다.

```bash
python3 scripts/prepare-proof.py
python3 scripts/measure-performance.py --seed-only
python3 scripts/verify-operations.py
```

- HTTPS localhost:18443, HTTP 리다이렉트 18080, 직접 backend 18081은 모두 loopback에만 바인딩된다.
- Caddy의 로컬 CA를 `/private/tmp/study-proof-ca.crt`로 추출한다. 시스템 신뢰 저장소에는 설치하지 않으며 스크립트가 명시적으로 신뢰한다. TLS 검증을 끄지 않는다.
- `--seed-only`는 검증용 스터디 12만 건과 요일 24만 행을 생성한다. 이미 있는 `bench-fixture-*` 행만 교체한다. 운영 DB에 실행하는 도구가 아니다.
- 운영 검증은 HTTPS 로그인·인증 API, 헤더 위조를 동반한 인증 Rate Limit, 새 DB 복원 및 내용 해시, 앱 재시작 후 데이터 유지, DB 중단 시 503과 복구 후 200, 로그 상관관계와 비밀값 미노출을 확인한다.
- 검증 과정에서 `study-proof`의 backend와 PostgreSQL을 재시작/중단한다. 개발용 `infra` 프로젝트와 운영 프로젝트에는 사용하지 않는다.

수동 HTTPS 확인:

```bash
curl --cacert /private/tmp/study-proof-ca.crt https://localhost:18443/actuator/health
```

검증 스택 종료(데이터 볼륨 유지):

```bash
docker compose --env-file /private/tmp/study-proof.env -p study-proof -f infra/compose.prod.yml -f infra/compose.edge.yml down
```

## Backup / Restore

```bash
bash scripts/backup-db.sh .env.prod study-matching-prod backups/snapshot.dump
bash scripts/restore-db.sh .env.prod study-matching-prod backups/snapshot.dump restore_check
```

- pg_dump custom format, 소유자/ACL 제외. 기존 백업 파일은 덮어쓰지 않고 완료 후 partial 파일을 최종 경로로 이동한다.
- 복원 대상은 **존재하지 않는 새 DB**여야 한다. 원본 DB 및 이미 존재하는 DB에 대한 덮어쓰기를 차단한다.
- 검증 스크립트는 모든 애플리케이션 테이블과 Flyway 이력의 행 수·내용 해시를 비교한다. 측정한 복원 시간은 이 로컬 장비에서의 실행 시간이며 운영 RTO 보장이 아니다.
- custom format은 암호화가 아니다. 운영 시작 전 백업 암호화·외부 보관·보존 기간·정기 실행·복구 훈련 정책을 결정해야 한다. 현재는 수동 스크립트와 로컬 복원 증거만 있다.

## Rate Limit

- 로그인/회원가입: IP 기준 5 requests/minute, burst 5, 초과 429.
- 일반 경로: IP 기준 30 requests/second, burst 60, 초과 429.
- 응답은 JSON `RATE_LIMITED`, Retry-After: 12, X-Request-ID다. 일반 경로에도 보수적으로 12초 재시도 대기를 반환한다.
- limit 상태는 Nginx 메모리이므로 재시작 시 초기화되고 여러 Nginx 인스턴스 사이에서 공유되지 않는다. NAT를 공유하는 사용자에게 같은 IP 제한이 적용된다.
- rate-limit 응답은 Nginx가 생성한다. 별도 origin의 브라우저에서 429 본문을 읽게 하려면 프록시 CORS 정책을 추가 검증해야 한다. 현재 API의 일반 CORS 처리는 Spring 설정을 따른다.

## Logging / Health

- prod 앱 로그는 ECS JSON이다. 요청마다 서버가 생성한 X-Request-ID를 반환하고 MDC에 포함한다.
- 접근 로그는 method, path, status, elapsed time만 기록한다. 인증 헤더·본문·query string은 기록하지 않는다. Nginx JSON 로그에는 앱 requestId와 edgeRequestId가 있다.
- Nginx 오류 로그는 raw request line에 query가 섞일 수 있어 crit 레벨로 제한했다. 4xx/5xx 추적은 접근 로그와 앱 로그로 수행한다.
- Docker 로그는 서비스당 10MB × 5파일로 회전한다. 중앙 수집·장기 보존·알림은 미구현이다.
- health에는 DB 상태를 포함하고 세부 연결 정보는 숨긴다. prod에서는 Swagger/OpenAPI가 비활성화된다.
- `restart: unless-stopped`는 프로세스 종료 시 재시작하며 unhealthy 상태만으로 재시작하지 않는다. shutdown phase 대기 30초, Compose 종료 유예 60초다.

## Migration 배포 조건

V8은 API/DB 구조 변경이며 V9는 검색 인덱스 추가다. 기존 DB의 비표준 데이터를 V8이 발견하면 적용을 중단한다. [입력 계약](INPUT_CONTRACT.md)과 백업을 확인하고 앱·클라이언트를 함께 변경해야 한다. 구버전 앱과 혼합 운영하지 않는다. V9의 일반 CREATE INDEX는 쓰기를 막을 수 있으므로 실제 데이터 규모를 확인하고 점검 시간을 잡는다. 앱 이미지만 되돌려 DB 구조까지 복구할 수는 없다.

## 공식 참고

- [Caddy Automatic HTTPS](https://caddyserver.com/docs/automatic-https)
- [Caddy reverse_proxy와 헤더](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy)
- [Nginx limit_req](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html)
