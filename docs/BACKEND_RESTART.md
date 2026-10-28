# Backend 재시작 후 Refresh Token 유지 검증

별도 로컬 `study-monitoring` 스택에서 실제 백엔드 컨테이너를 중단·재시작한다. PostgreSQL과 데이터 볼륨은 그대로 유지한다. 기존 개발 스택(`infra`)에는 실행하지 않는다.

## 실행

```bash
# 스택이 없거나 최신 애플리케이션 이미지를 반영해야 할 때 먼저 실행
python3 scripts/prepare-monitoring.py

python3 scripts/verify-backend-restart.py
```

실행 순서는 다음과 같다.

1. 검증용 회원 가입 → 로그인 → Access/Refresh Token 발급.
2. DB에 Refresh Token 해시와 미사용 세션이 저장됐는지 확인.
3. 프로필·스터디를 만들고 기존 데이터와 함께 10개 테이블의 행 수·내용 MD5를 기록.
4. backend 컨테이너만 정상 중단. API가 접속 불가이고 PostgreSQL은 계속 실행되는지 확인.
5. backend 재시작 → `/actuator/health`가 `200 UP`이 될 때까지 대기.
6. 컨테이너 ID는 같고 시작 시각이 달라졌는지, PostgreSQL의 ID·시작 시각·재시작 횟수는 그대로인지 확인.
7. **토큰 갱신 전에** 10개 테이블의 행 수·내용 해시가 재시작 전과 같은지 확인. 기존 회원·프로필·스터디 API 응답과 Refresh 세션도 비교.
8. 재시작 **전에 받은** Refresh Token으로 `/api/auth/refresh` 요청 → `200`과 새 Access/Refresh Token 쌍 확인.
9. 기존 Refresh Token의 사용 완료 상태, 같은 로그인 세션 유지, 절대 만료일 유지, 새 Access Token으로 회원 API 호출 성공 확인.
10. 검증용 세션을 로그아웃하고 백엔드를 정상 실행 상태로 남김.

결과는 [backend-restart.json](../reports/verification/backend-restart.json)에 저장한다. 단계별 시각과 데이터 일치 여부를 기록하지만 실제 비밀번호·Access Token·Refresh Token은 기록하지 않는다. 내용 해시는 재시작 전후 일치 확인용이며 암호 보안 검증 수단이 아니다.

## 범위와 복구

이 검증은 같은 이미지·환경변수·JWT 서명 키·DB를 유지한 **컨테이너 stop/start**다. 컨테이너 삭제 후 재생성, 이미지 교체, 서명 키 교체, Refresh 만료 이후 갱신을 검증하는 것은 아니다. 프로세스가 재시작돼도 DB의 Refresh 세션이 유지되는지 확인하는 데 목적이 있다.

서비스 중단 이후에는 `finally`에서 backend를 다시 시작하고 health UP을 기다린다. 일반 오류·인터럽트·SIGTERM에서도 복구를 시도한다. 검증 프로세스 SIGKILL이나 Docker/호스트 자체 장애로 finally가 실행되지 못하면 `prepare-monitoring.py`로 복구한다.

DB 전체 내용 비교이므로 같은 검증 스택에서 다른 쓰기 작업을 동시에 수행하면 불일치로 실패할 수 있다. 검증 스크립트들을 동시에 실행하지 않는다. 성공/실패 결과는 `status` 필드를 확인한다.

애플리케이션 인증 로직은 수정하지 않는다. Refresh 세션·토큰 해시를 PostgreSQL에 저장하는 기존 V10 구현을 실제 재시작으로 검증한다. 갱신은 정상적으로 DB를 변경하므로 전체 내용 비교는 반드시 갱신 전에 수행한다.

## 2026-10-28 실행 결과

실제 stop/start 검증이 통과했다. 재시작 전후 10개 테이블의 행 수·내용 해시가 일치했고, PostgreSQL은 재시작되지 않았다. 재시작 전에 발급한 Refresh Token으로 새 토큰 쌍을 발급받았으며 새 Access Token의 회원 API 호출도 200이었다. 기존 Refresh Token은 사용 완료로 바뀌었고 세션 ID·절대 만료일은 유지됐다. 검증 후 세션은 로그아웃했으며 backend는 정상 실행 중이다.
