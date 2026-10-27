# 성능 측정 결과

2026-10-27 한국시간에 로컬 Docker Desktop에서 측정했다. 클라우드 지연, 인터넷 TLS 왕복, 최대 처리량을 측정한 결과가 아니다. 원자료는 [reports/performance](../reports/performance)에 있다.

## 조건

- 스터디 120,000건, 스터디 요일 240,000행의 합성 데이터. 모든 스터디에 ACTIVE 방장 참여자를 생성했다.
- macOS ARM64, Docker 8 CPU / 약 3.82 GiB 메모리. PostgreSQL 17, Java 21, k6 1.3.0.
- 실제 로그인 JWT로 HTTP API를 호출한다. Rate Limit/TLS 프록시를 제외한 Docker 내부 backend를 대상으로 DB/API 차이를 분리했다.
- 기본 목록과 3개 필터 검색 각각 20 requests/s, 합계 40 requests/s. 각 구간 10초 예열 후 30초 측정, 페이지 0 / 크기 20.
- 두 시나리오에 각각 20 VU를 미리 할당했다. 측정 전후 각각 1,201 requests, HTTP 실패 0, dropped iterations 0.
- 같은 앱·데이터에서 인덱스만 변경했다. 생성시각이 같은 행도 id DESC로 안정적으로 정렬한다.

## 실제 API 지연

| 경로 | p95 전 → 후 | p99 전 → 후 | p95 감소 |
|---|---|---|---|
| 기본 목록 | 38.05 → 17.64 ms | 51.48 → 29.85 ms | 53.6% |
| category+level+status 검색 | 28.38 → 7.09 ms | 41.84 → 21.85 ms | 75.0% |

이는 한 번의 before/after 비교다. 캐시·JIT·스케줄링·단일 장비의 영향을 포함하므로 운영 SLA나 최대 처리량으로 일반화하지 않는다. p99에 20~30ms 변동이 남으며 평균만으로 개선을 주장하지 않는다. 기준은 p95 < 500ms, p99 < 1000ms, 실패율 < 0.1%, 누락 요청 0이었다.

## EXPLAIN (ANALYZE, BUFFERS)

실제 검색 조건·조인·정렬·LIMIT과 대응하는 SQL 및 count SQL을 각 5회 실행했다. SQL 전문은 metadata.json에, 모든 실행계획은 before-plans.json / after-plans.json에 있다. 아래 값은 5회 Execution Time 중앙값이며 전체 HTTP 시간과 다르다.

| SQL | 적용 전 | 적용 후 |
|---|---|---|
| all_page | 24.269 ms | 0.086 ms |
| filtered_page | 10.739 ms | 0.087 ms |
| filtered_count | 9.313 ms | 0.482 ms |

기본 페이지의 병렬 조회·정렬과 필터 페이지의 bitmap 조회·정렬이 ordered index scan으로 바뀌었다. 필터 count는 index-only scan을 사용했다. 실제 계획의 buffers도 함께 보존했으며 따뜻한 캐시에서의 측정이다.

## 반영한 인덱스

V9__optimize_study_search_order.sql:

```sql
CREATE INDEX idx_studies_filter_newest ON studies(category, level, status, created_at DESC, id DESC);
CREATE INDEX idx_studies_newest ON studies(created_at DESC, id DESC);
```

동등 조건을 앞에, 정렬 키를 뒤에 두어 별도 sort 없이 첫 페이지를 읽는다. 기존 단일 컬럼 인덱스는 다른 필터 조합을 위해 유지했다. 측정 시 두 인덱스는 약 7.53 MiB와 3.63 MiB를 사용했다. 쓰기 비용과 저장 공간이 증가하므로 모든 조합의 인덱스를 추가하지 않았다.

## 재현

```bash
python3 scripts/prepare-proof.py
python3 scripts/measure-performance.py
```

스크립트는 고정된 study-proof / study_proof 로컬 환경만 사용한다. 테스트용 행과 두 인덱스를 제거해 baseline을 만들고 측정 후 후보 인덱스를 적용한다. 마지막에는 실패한 경우에도 두 인덱스를 복원한다. 운영 DB에 실행하지 않는다. 정상 완료 시 원자료를 덮어쓰므로 기존 결과를 보존하려면 reports/performance를 별도 복사한다.

초기 예열에서 VU를 동적으로 늘리며 1건이 누락된 기록은 diagnostics/initial-warmup-before*에 보존했다. 최종 비교는 20 VU를 미리 할당한 동일 설정으로 다시 수행했다. 예열 값은 SLO 판정에 포함하지 않으며 최종 before/after에서 오류·누락을 따로 확인한다.

metadata.json의 baseCommit은 변경 전 기준 커밋이며 측정에는 미커밋 변경이 포함되어 있다. backendImageId는 측정한 실제 이미지다. 최종 source-manifest.json은 이후 migration 임시 함수 정리·추가 회귀 테스트·문서 정리를 포함한 최종 파일 상태를 식별한다. 이후 운영 검증 이미지는 operations.json에 별도로 기록한다.

## 미검증 범위

장시간 soak, 동시 쓰기 부하, 다중 서버, 네트워크 장애, 큰 offset 페이지, 다른 필터 분포 및 PostgreSQL 쓰기 비용은 이번 비교에 포함하지 않았다. 정규화·멤버 lifecycle의 정확성은 별도 통합/E2E 테스트로 검증한다.

[k6 custom summary 공식 문서](https://grafana.com/docs/k6/latest/results-output/end-of-test/custom-summary/)
