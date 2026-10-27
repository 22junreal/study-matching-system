# Spring Boot → Actuator → Micrometer → Prometheus → Grafana

이 구성은 HTTP/JVM/HikariCP 지표 수집과 대시보드, Prometheus 경보 평가를 제공한다. 기존 requestId 구조화 로그는 계속 사용한다. 로그 중앙 수집(Loki 등), 분산 추적(OpenTelemetry/Tempo), 외부 알림 전송은 포함하지 않는다. 클라우드는 생성하지 않는다.

## 로컬 실행

Java 21 테스트 환경과 Docker Desktop/Engine이 필요하다. 저장소 루트에서:

```bash
python3 scripts/prepare-monitoring.py
```

전용 `study-monitoring` 프로젝트에 새 PostgreSQL, backend, Prometheus, Grafana, 검증용 Caddy/Nginx를 실행한다. 기존 `infra` 개발 컨테이너와 DB에는 접근하지 않는다.

- Grafana: http://localhost:13000/d/study-backend — 계정 `admin`.
- Prometheus: http://localhost:19090 — Status/Targets에서 `study-backend`의 UP 확인.
- 검증용 backend: http://localhost:18082.
- 검증용 HTTPS: https://localhost:18444 — 로컬 CA이며 브라우저가 자동 신뢰하지 않는다.
- macOS의 Grafana 비밀번호 파일: `/private/tmp/study-monitoring/secrets/grafana.password`.
- 환경 파일: `/private/tmp/study-monitoring/environment`. Linux에서는 `/tmp/study-monitoring/`에 생성한다.

비밀번호는 터미널에서 해당 파일을 열어 확인하고 Grafana 로그인에 사용한다. 생성된 값은 Git이나 채팅에 붙이지 않는다. 스크립트 재실행은 기존 자격 증명을 재사용한다. Grafana의 관리자 암호 파일은 최초 DB 초기화에 사용하므로 기존 볼륨의 관리자 암호를 바꾸려면 Grafana 자체 변경 절차가 필요하다.

중지(데이터 볼륨 유지):

```bash
docker compose --env-file /private/tmp/study-monitoring/environment -p study-monitoring \
  -f infra/compose.prod.yml -f infra/compose.monitoring.yml -f infra/compose.edge.yml down
```

별도 prod 환경에 추가하려면 `infra/compose.prod.yml`에 `infra/compose.monitoring.yml`을 합친다. `.env.prod`에는 `MONITORING_SECRETS_DIR`를 전용 비밀 디렉터리 절대 경로로 지정하고 `monitoring.password`, `grafana.password`를 각각 독립적인 랜덤 값으로 준비한다(최소 32자 권장). 부모 디렉터리는 0700으로 보호하고, Docker secret 파일은 비루트 컨테이너가 읽을 수 있게 한다. 로컬 스크립트는 부모 0700/파일 0444로 준비한다. 실제 공개 서버에서 이 단일 호스트 구성을 사용하기 전 접근 제어와 암호화 전송을 별도 점검해야 한다.

## 연결·보안

- backend: 기존 prod 뒤에 `monitoring` 프로필을 적용한다. `micrometer-registry-prometheus` 버전은 Spring Boot BOM을 따른다.
- 지표: 기존 backend 포트의 `/actuator/prometheus`. 별도 management 포트로 옮기지 않아 기존 `/actuator/health`와 Docker healthcheck를 유지한다.
- 전용 Basic 계정 `prometheus`와 `/run/secrets/monitoring.password`로 인증한다. 비밀번호가 없거나 32자 미만이면 모니터링 프로필이 시작되지 않는다.
- 일반 JWT/회원 계정으로 지표 API를 읽을 수 없으며, 모니터링 계정으로 업무 API에 접근할 수 없다. 업무 로그인용 AuthenticationManager와 별도로 구성했다.
- Nginx 외부 경로에서 `/actuator/prometheus`와 하위 경로는 인증 여부와 무관하게 404다.
- Prometheus는 Docker 내부 주소 `backend:<PORT>`를 15초마다 수집한다. Basic 인증은 이 로컬 Docker 내부 HTTP 경로에서 사용한다. 여러 호스트 간 전송을 추가하려면 TLS 등 별도 보호가 필요하다.
- Prometheus와 Grafana의 호스트 포트는 항상 `127.0.0.1`에 바인딩한다. Prometheus UI 자체의 사용자 인증은 구성하지 않았으며 로컬 접근을 신뢰한다. 공개 바인딩으로 바꾸지 않는다.
- Grafana는 익명 로그인과 가입을 끄고 Prometheus를 서버 측 데이터 소스로 연결한다. `prod`의 SQL 로그 비활성화와 기존 requestId 로깅을 유지한다.
- API 경로 태그는 Spring의 경로 템플릿을 사용하고 URI 태그 개수를 100으로 제한한다. 토큰·사용자명·이메일을 지표 라벨로 추가하지 않는다.

## 기본 대시보드

Grafana의 `Study Matching` 폴더에 `Study Matching · Backend` 대시보드를 파일로 자동 등록한다. UID는 `study-backend`다. 수정은 저장소 JSON에 반영하고 재실행한다. Grafana UI에서의 영구 편집은 허용하지 않는다.

| 패널 | 해석 |
|---|---|
| Backend scrape availability | `up=1`이면 수집 성공. DB 정상이라는 의미는 아니다. |
| API throughput | Actuator 제외, 초당 요청 수 |
| API 5xx ratio | Actuator 제외, 서버 오류 비율 |
| API latency p95 / p99 | HTTP histogram 기반 백분위 추정 |
| JVM heap usage | 최대 heap 대비 사용량 |
| Process CPU | JVM 프로세스 CPU 사용률 |
| GC pause p95 | GC pause histogram 기반 추정 |
| JVM threads | 전체/daemon 스레드 수 |
| Database connections | HikariCP active/idle/max |
| Database waiting / timeouts | 커넥션 대기 수 / 조회 구간 내 timeout 증가량 |
| Backend uptime | 프로세스 실행 시간 |
| Prometheus alerts | firing 경보. 없으면 No data가 정상 |

처음에는 요청이나 GC 이벤트가 없어 일부 패널이 비어 있을 수 있다. 특히 p95/p99는 트래픽 없이 0으로 해석하면 안 된다. API를 호출하고 최소 2회 이상 수집될 때까지 기다린다. CPU는 전체 호스트나 모든 컨테이너의 사용량이 아니다. DB query latency/slow query를 직접 수집하는 exporter는 포함하지 않는다.

대표 쿼리:

```promql
sum(rate(http_server_requests_seconds_count{job="study-backend",uri!~"/actuator.*"}[5m]))

histogram_quantile(0.95, sum by (le) (
  rate(http_server_requests_seconds_bucket{job="study-backend",uri!~"/actuator.*"}[5m])
))
```

## 경보와 보관

- `StudyBackendDown`: scrape 실패 1분 지속. 프로세스 중단 외에도 잘못된 인증 정보/네트워크 오류로 발생할 수 있다.
- `StudyHigh5xxRate`: 5분 구간 오류율 >5%, 트래픽 ≥1 req/s 상태가 2분 지속.
- `StudyHighLatency`: 5분 구간 p95 >1초, 트래픽 ≥1 req/s 상태가 5분 지속.
- `StudyDatabasePoolWaiting`: DB connection 대기 >0 상태가 2분 지속.

Prometheus Alerts 화면에서 pending/firing을 확인한다. **Alertmanager·Slack·메일 등의 외부 통지는 아직 연결하지 않았다.** 위 임계값은 초기 기준이며 운영 트래픽에 맞춰 조정해야 한다.

Prometheus 데이터는 named volume에 저장하며 7일 또는 512MB 제한 중 먼저 도달한 기준으로 정리한다. Grafana 설정 DB도 named volume에 저장한다. 모니터링 컨테이너 로그는 10MB×3개로 제한한다. 볼륨은 단일 호스트 저장소이며 외부 백업/고가용성을 의미하지 않는다.

## 검증과 재현

```bash
# 실제 수집, 인증 경계, Grafana datasource/대시보드 쿼리,
# 전용 backend 중단 → 경보 firing → 재시작 → 경보 해제 검증
python3 scripts/verify-monitoring.py

# Prometheus 실제 promtool로 경보 조건/지속시간/해제/저트래픽 억제 검증
docker run --rm --entrypoint /bin/promtool \
  -v "$PWD/infra/monitoring/prometheus:/rules:ro" -w /rules \
  prom/prometheus:v3.15.1 test rules /rules/alerts.test.yml
```

`verify-monitoring.py`는 `study-monitoring` 프로젝트만 대상으로 하며 backend를 중단했다가 finally에서 재시작한다. 실행 중 해당 데모 API가 잠시 중단된다. 기존 개발/운영 스택에는 실행하지 않는다. 결과는 [검증 원자료](../reports/verification/monitoring.json)에 기록한다. 보안 통합 테스트는 `MonitoringSecurityTest`다.

참고: [Spring Boot metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [Prometheus configuration](https://prometheus.io/docs/prometheus/latest/configuration/configuration/), [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/).
