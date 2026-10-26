# Study Member Lifecycle

## 데이터와 정책

- `study_applications`는 신청 상태를, `study_members`는 참여 상태를 관리한다. 실제 인원은 `ACTIVE` 참여자 수이며 방장도 포함한다.
- 스터디 생성 시 방장 참여 레코드가 생성된다. 신청 승인 시 참여 레코드를 생성하거나 `LEFT` 레코드를 다시 활성화한다. 신청 상태 변경과 참여·정원 변경은 같은 트랜잭션에서 커밋한다.
- 참여 상태는 `ACTIVE`, `LEFT`(자진 탈퇴), `REMOVED`(강퇴)다. `(study_id, member_id)`는 유일하며 복귀 시 기존 레코드를 재사용한다.
- 탈퇴·강퇴 자체는 기존 `APPROVED` 신청을 변경하지 않는다. 두 동작 모두 인원을 줄이고 빈자리가 생기면 `RECRUITING`으로 전환한다. 현재 `CLOSED`는 기존 코드와 같이 정원 마감 상태로 취급한다.
- 탈퇴자는 기존 신청의 `/reapply` API로 재신청하고 방장 승인을 다시 받아야 한다. 이때 `APPROVED → PENDING`으로 변경되고 승인 전까지 참여 상태는 `LEFT`다. 기존의 `CANCELED → PENDING` 재신청도 유지한다. 취소·재신청을 거듭해도 멤버 수는 늘지 않는다.
- 신청 이력이 없는 이전 방장은 위임·탈퇴 후 일반 신청 API를 사용한다. 거절된 신청을 다시 제출하는 기능은 기존 정책과 같이 제공하지 않는다.
- 강퇴자는 일반 신청과 재신청 모두 차단된다. 차단 해제 API는 이번 범위에 포함하지 않는다.
- 방장은 위임 전 탈퇴하거나 자신을 강퇴할 수 없다. 위임 대상은 같은 스터디의 다른 `ACTIVE` 멤버여야 한다. 이전 방장은 일반 참여자로 남는다.
- 방장 권한의 기준은 `studies.owner_id` 한 곳이다. 응답의 `OWNER`/`MEMBER` 역할은 이 값으로 계산한다. 위임 시 두 참여자의 활성 상태와 인원은 유지된다.

## API

모든 요청은 로그인으로 발급받은 Bearer JWT가 필요하다. `{memberId}`는 사용자 `members.id`이며 참여 레코드 ID가 아니다.

| 메서드 | 경로 | 권한 / 성공 응답 |
|---|---|---|
| GET | `/api/studies/{studyId}/members` | 현재 참여자만 조회, 200 + ACTIVE 멤버 배열 |
| DELETE | `/api/studies/{studyId}/members/me` | 방장 이외 현재 참여자의 자진 탈퇴, 204 |
| DELETE | `/api/studies/{studyId}/members/{memberId}` | 방장의 현재 참여자 강퇴, 204 |
| PATCH | `/api/studies/{studyId}/owner` | 방장 위임, 200 + 새 방장 정보 |

위임 요청 본문:

```json
{"memberId": 2}
```

참여자 응답 예시:

```json
{"memberId": 2, "username": "applicant", "role": "OWNER", "status": "ACTIVE"}
```

재신청은 기존 `PATCH /api/studies/{studyId}/applications/{applicationId}/reapply`를 사용한다. 모집 중이며 빈자리가 있어야 한다. 재신청 성공 자체가 자리를 예약하지는 않는다.

## 오류

기존 `code`, `status`, `message`, `timestamp` 공통 응답을 사용한다.

| HTTP | code | 의미 |
|---|---|---|
| 401 | UNAUTHORIZED | 인증 없음 / 잘못된 토큰 |
| 403 | STUDY_ACCESS_DENIED | 목록 조회·강퇴·위임 권한 없음 |
| 403 | STUDY_MEMBER_REMOVED | 강퇴된 사용자의 신청·재신청 |
| 404 | STUDY_NOT_FOUND | 스터디 없음 |
| 404 | STUDY_MEMBER_NOT_FOUND | 해당 스터디의 참여 레코드 없음 |
| 409 | STUDY_MEMBER_NOT_ACTIVE | 이미 탈퇴·강퇴된 대상 변경 |
| 409 | STUDY_MEMBER_ALREADY_ACTIVE | 이미 참여 중인 사용자의 재가입 |
| 409 | STUDY_OWNER_CANNOT_LEAVE | 방장의 탈퇴·자기 강퇴 |
| 409 | STUDY_OWNER_TRANSFER_TO_SELF | 자기 자신에게 위임 |
| 400 | VALIDATION_FAILED | 위임 대상 ID 누락 / 양수 아님 |

반복 탈퇴·강퇴는 409로 응답한다. 대상이 다른 스터디에만 속해 있으면 404로 응답한다. 탈퇴와 강퇴가 동시에 요청되면 먼저 성공한 상태를 유지하고 나중 요청은 409가 된다.

## 동시성

승인·거절·신청·재신청·정원 수정·삭제·탈퇴·강퇴·위임은 먼저 동일한 Study 행의 비관적 쓰기 잠금을 획득한다. 그 다음 최신 방장 권한, 참여 상태, 정원을 확인한다. 위임을 기다리던 이전 방장의 승인·거절 요청은 잠금을 얻은 뒤 새 방장을 확인하고 403을 반환한다.

신청 취소는 참여 관계를 바꾸지 않으며 기존 `StudyApplication.@Version`으로 승인·거절과의 오래된 상태 덮어쓰기를 막는다. 낙관적 잠금 충돌은 409 `CONCURRENT_MODIFICATION`이다. 승인/거절처럼 스터디 잠금으로 직렬화되는 요청은 후속 요청이 이미 처리된 상태에 대한 비즈니스 409를 받는다.

## Migration과 적용

`V7__create_study_members.sql`은 별도 참여 테이블과 인덱스를 추가하고 기존 방장 및 `APPROVED` 신청자를 `ACTIVE`로 이전한다. 중복되는 방장은 한 번만 삽입한다. 기존 신청 상태와 version은 변경하지 않으며 PENDING/CANCELED/REJECTED 신청자는 참여자로 만들지 않는다. 스터디 삭제 시 참여 레코드는 함께 삭제된다.

운영 DB에는 아직 적용하지 않았다. 실제 배포 시 백업 후 V7과 새 앱을 함께 적용해야 한다. 구버전 앱은 참여 테이블을 갱신하지 않으므로 신구 버전을 동시에 운영하지 않는다. 앱 이미지만 구버전으로 되돌리면 탈퇴·강퇴를 반영하지 않는 이전 인원 계산으로 돌아가므로 DB와 앱을 함께 고려한 복구 절차가 필요하다.

## 검증

`StudyMemberE2ETest`는 실제 HTTP 서버, JWT, 임시 PostgreSQL에서 탈퇴·재승인, 강퇴 차단, 위임 후 권한, 다른 스터디 접근, 정원 계산, 삭제, 동시 승인/탈퇴/강퇴/위임을 검증한다. 동시 요청이 실제 스터디 잠금에 도달하도록 테스트용 동기화를 사용한다. 이전 방장 요청이 위임 잠금을 기다리는 순서도 제어해 검증한다.

`StudyMemberMigrationTest`는 데이터가 있는 V6 DB에 V7을 적용해 이전 대상, 기존 신청 상태·version 보존, 중복 방장 처리, 유일성 제약, 재실행 및 삭제 연쇄를 검증한다. 기존 정원·신청 Service/E2E 테스트도 새 참여 인원 기준으로 계속 실행한다.
