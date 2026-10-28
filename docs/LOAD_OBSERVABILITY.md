# k6 · Grafana 부하와 DB 연결 풀 관측

목표는 같은 시간축에서 **동시 사용자 증가 → 응답시간과 DB 풀 사용/대기 변화 → 고부하 종료 후 회복**을 관측하는 것이다. 관측 결과를 만들기 위해 지연을 주입하거나 그래프 수치를 수정하지 않는다. CPU·GC·DB 쿼리 실행도 응답시간에 영향을 주므로 두 곡선의 동시 상승만으로 연결 풀 크기가 유일한 원인이라고 단정하지 않는다.

## 구성

```text
k6 ──HTTP GET /api/studies──> Spring Boot ──> HikariCP ──> PostgreSQL
 │                              │
 └─Prometheus remote write───────┤ Actuator/Micrometer scrape
                                ↓
                            Prometheus → Grafana
```

`infra/compose.monitoring.yml`의 Prometheus에 remote-write 수신을 활성화했다. k6는 Docker 내부의 `http://prometheus:9090/api/v1/write`로 5초마다 전송한다. 앱 지표 수집은 기존 15초 주기를 유지한다. Prometheus UI/수신 포트는 호스트 `127.0.0.1`에만 바인딩하며 Docker 내부 네트워크를 신뢰한다. 인증 없는 remote-write 엔드포인트를 공개 인터넷에 노출하는 구성은 아니다.

Grafana 파일 프로비저닝으로 `Study Matching · k6 and database pool` 대시보드(UID `study-load-correlation`)를 등록한다. k6 run ID와 시간 범위를 선택하면 다음을 같은 시간축으로 볼 수 있다.

- 실제 k6 VU 수와 요청률.
- 서버 검색 API p95/p99(1분 histogram 구간).
- Hikari active/max, pending, 평균 연결 획득 대기시간과 평균 연결 보유 시간.
- 앱 CPU·heap 사용률과 GC pause 시간.
- 검색 API 5xx 오류율.

k6의 단계별 p95/p99는 원시 `summary.json`에서 별도로 비교한다. k6 remote-write의 기본 Trend 집계는 누적값이므로 이를 회복 구간의 순간 p95처럼 해석하지 않는다. 서버 그래프의 백분위는 histogram 추정치이며 k6 클라이언트 측정값과 동일할 필요는 없다.

## 재현

```bash
# 최초 실행/모니터링 구성 갱신 시
python3 scripts/prepare-monitoring.py

# 5분 단계별 부하 + 준비/검증 시간
python3 scripts/verify-load-observability.py
```

기존 `study-monitoring` 프로젝트에만 실행한다. PostgreSQL DB 이름과 컨테이너 라벨을 확인한다. 기존 개발 스택 `infra`에는 부하를 주지 않는다.

| 구간 | 동시 VU | 지속시간 |
|---|---:|---:|
| baseline | 1 | 45초 |
| medium | 10 | 45초 |
| high | 30 | 60초 |
| peak | 60 | 60초 |
| recovery | 1 | 90초 |

각 VU는 페이지당 100개를 조회하고 50ms 쉬는 closed-loop 방식이다. 응답이 느려지면 요청률도 제한된다. 단계 종료 후 진행 중인 요청에는 최대 10초의 완료 시간을 주므로 전환 직후 일부 VU가 겹칠 수 있다. 실제 VU 그래프와 각 단계 후반 표본을 기준으로 비교한다.

회복 단계는 **고부하 종료 후 1 VU의 확인 트래픽**이다. 요청이 완전히 없으면 새 응답시간을 측정할 수 없으므로 이 트래픽을 유지한다. 마지막에는 k6도 종료하고 Hikari active가 0~1(주기적 DB 검사 포함)로 내려오는지 확인한다. idle 연결은 정상적으로 남을 수 있다.

읽기 비용을 확보하기 위해 `load-observe-fixture-` 접두사의 스터디 12만 건과 요일 24만 행을 생성한다. 기존 데이터를 삭제하지 않으며 같은 fixture 12만 건이 있으면 재사용한다. 수량이 예상과 다르면 자동 변경하지 않고 중단한다. 데이터는 검증 DB에 남는다. 앱/DB가 사용하는 CPU·메모리는 같은 로컬 Docker 호스트를 공유하므로 운영 서버 최대 처리량 검증은 아니다.

TLS와 Nginx rate limit을 우회한 Docker 내부 API를 사용해 앱/DB 경로를 측정한다. 운영 외부 요청 경로 전체의 성능과 구분한다. 실제 비밀번호·토큰은 보고서에 저장하지 않고 임시 env 파일은 종료 시 삭제한다. 검증 회원의 Refresh 세션은 측정 성공 후 로그아웃한다.

## 결과 판정과 원자료

각 실행은 `reports/load-observability/<run-id>/`에 별도로 저장한다.

- `summary.json`: k6 전체/단계별 원시 집계.
- `console.txt`: k6 실행 로그와 threshold 결과.
- `prometheus-series.json`: 실제 query_range 응답, 쿼리, 시간 범위.
- `report.json`: 환경·fixture·단계별 비교·고정 Grafana 링크.

`requestedPatternObserved`는 peak p95가 baseline의 1.5배 이상, pool active 최대가 max의 90% 이상, pending 관측, recovery p95가 baseline의 2배 또는 50ms 이내로 회복됐는지를 확인한다. 이는 **이 실험에서 원하는 변화가 관측됐는지**를 나타내며 통계적 인과성 검정이나 운영 SLA가 아니다. 조건이 충족되지 않으면 false로 남긴다.

연결 획득 대기와 pending이 같이 증가하면 풀 대기가 응답시간에 기여했다는 근거가 된다. 하지만 그 앞단 원인은 느린 쿼리, CPU 경합, 긴 트랜잭션 등일 수 있다. 풀 확대가 해결책인지 확정하려면 같은 부하에서 풀 크기만 바꾼 추가 대조 실험이 필요하다.

이전 보고서의 SQL 인덱스 전후 성능 측정(`reports/performance/`)과는 별도 실험이다. 동시 사용자는 k6 VU 수이며 서로 다른 회원 계정 수를 뜻하지 않는다. 하나의 검증 계정을 공유하고 사용자별 쓰기 경쟁은 측정하지 않는다. 다른 검증 스크립트나 수동 부하를 동시에 실행하지 않는다.

참고: [k6 Prometheus remote write](https://grafana.com/docs/k6/latest/results-output/real-time/prometheus-remote-write/).

## 2026-10-28 실제 실행 결과

| 구간 | VU | k6 p95(ms) | k6 p99(ms) | active 관측 최대 / max | pending 관측 최대 | 평균 연결 획득 시간(ms) |
|---|---:|---:|---:|---:|---:|---:|
| baseline | 1 | 24.59 | 42.84 | 0 / 10 | 0 | 0.037 |
| medium | 10 | 15.79 | 25.13 | 0 / 10 | 0 | 0.020 |
| high | 30 | 15.93 | 33.26 | 7 / 10 | 0 | 0.207 |
| peak | 60 | 105.07 | 156.44 | 10 / 10 | 33 | 23.511 |
| recovery | 1 | 23.35 | 61.00 | 1 / 10 | 0 | 0.196 |

총 73,192건, HTTP 실패율 0%, 백엔드 재시작 없음. 12만 건 fixture, Hikari 최대 10개를 유지했고 인위적 지연을 주입하지 않았다. k6 종료 후 active 연결은 0이었다.

k6 백분위는 단계 전체의 클라이언트 측정값이다. 서버/풀 값은 단계 후반 표본을 비교한 것으로, 평균 연결 획득 시간은 1분 평균 시계열의 후반 중앙값이다. 앱 scrape는 15초, query_range는 5초 간격이며 같은 scrape 값이 반복 평가될 수 있다. baseline/medium의 active=0은 관측 순간의 값이며 DB를 전혀 사용하지 않았다는 뜻이 아니다.

1→10→30 구간에서는 p95가 계속 증가하지 않았다. 캐시/JIT 준비 등의 영향도 있어 전체 구간이 단조 증가한다고 주장하지 않는다. **30→60 VU에서 p95 15.93→105.07ms, 연결 획득 시간 0.207→23.511ms, active 10/10과 pending 33이 함께 관측**됐다. 고부하 종료 후 1 VU 확인 구간에서는 p95 23.35ms, pending 0으로 회복됐다.

이는 이 조건에서 부하 증가가 연결 풀 대기와 지연 상승을 동반했다는 실험 근거다. 풀 크기만 늘리면 해결된다는 결론은 검증하지 않았다. DB 쿼리 실행/CPU 경합 등 대기 앞단 원인의 추가 분석이 필요하다.

[해당 시간대 Grafana](http://localhost:13000/d/study-load-correlation?from=1793190796926&to=1793191126926&var-run_id=load-20261028T123309Z-5ab65c) · [실행 보고서](../reports/load-observability/load-20261028T123309Z-5ab65c/report.json) · [k6 원자료](../reports/load-observability/load-20261028T123309Z-5ab65c/summary.json) · [Prometheus 원자료](../reports/load-observability/load-20261028T123309Z-5ab65c/prometheus-series.json)
