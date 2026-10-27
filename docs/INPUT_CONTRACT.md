# 입력 계약: Spring Boot API

스터디와 프로필은 같은 `StudyCategory`, `StudyLevel`, `StudyDay`, `LocalTime` 타입을 사용한다. 문자열 요일 목록과 시간 범위 문자열은 새 API에서 받지 않는다.

| 필드 | 입력 | 응답 / 저장 |
|---|---|---|
| category | PROGRAMMING, AI, DATA, LANGUAGE, CERTIFICATE, ETC. 영문 대소문자·앞뒤 공백 정규화 | 대문자 enum, DB CHECK 제약 |
| level | BEGINNER, INTERMEDIATE, ADVANCED. 영문 대소문자·앞뒤 공백 정규화 | 대문자 enum, DB CHECK 제약 |
| days | 요일 문자열 배열. MONDAY~SUNDAY 또는 MON~SUN, 대소문자·앞뒤 공백 허용 | 월→일 순서의 전체 이름 배열, 별도 요일 테이블 |
| startTime / endTime | 정확한 HH:mm, 00:00~23:59 | HH:mm 응답, SQL TIME |

- 같은 요일을 별칭과 함께 중복 제출해도 400이다. `MON`과 `MONDAY`는 같은 요일이다.
- 스터디의 days 생략/null은 빈 배열로 처리한다. 시간은 둘 다 생략하거나 둘 다 지정해야 한다. 요일만 지정한 시간 미정 스터디도 허용한다.
- 프로필의 department, category, level, 비어 있지 않은 days, startTime, endTime은 필수다.
- 시간은 시작 < 종료여야 한다. 동일 시각·역전·자정을 넘는 범위·24:00·초/소수초 입력은 허용하지 않는다. 시간은 주간 일정의 현지 시각이며 UTC timestamp가 아니다. 서로 다른 시간대 매칭 기능은 구현하지 않았다.
- 검색 query의 category와 level도 JSON과 동일하게 대소문자·공백을 정규화한다. status는 RECRUITING/CLOSED 기존 enum이다.
- 알 수 없는 enum·잘못된 JSON 타입은 400 INVALID_REQUEST, 중복 요일은 400 STUDY_INVALID_DAYS, 시간 쌍/범위 오류는 400 STUDY_INVALID_TIME_RANGE다.

## 스터디 생성/수정 예시

```json
{
  "title": "Java 스터디",
  "description": "주 2회",
  "category": "programming",
  "level": "beginner",
  "days": ["MON", "WEDNESDAY"],
  "startTime": "19:00",
  "endTime": "21:00",
  "maxMembers": 4
}
```

프로필 PUT `/api/profiles/me`도 category, level, days, startTime, endTime을 사용하고 department를 추가한다. 이전 preferredCategory/availableDays/availableTime 필드는 제거되었다. 클라이언트는 새 요청 형식으로 함께 변경해야 한다.

## V8 이전

- studies.days → study_days, profiles.available_days → profile_days. 영문 약어·전체 이름을 정규화하고 과거 문자열의 중복 요일은 하나로 합친다.
- profiles.preferred_category → category. available_time은 start_time/end_time으로 분리한다.
- 기존 category/level은 공백 제거와 대문자 변환 후 CHECK 제약을 적용한다.
- 잘못된 요일·시간 문자열·알 수 없는 enum·부분 시간·초 단위 값은 migration을 실패시킨다. 오류를 무시하거나 자동으로 지우지 않는다. 원본을 확인해 정상 데이터로 정리한 뒤 다시 적용해야 한다.
- 빈 요일은 빈 컬렉션, 기존 null 시간은 양쪽 null로 보존한다. 기존 불완전 프로필은 보존하지만 다음 API 수정 때 필수 필드를 채워야 한다.
- Flyway PostgreSQL 트랜잭션이 실패 시 기존 컬럼과 데이터를 보존하는지 테스트한다. 변경 전 백업이 필요하며 구버전 앱과 혼합 운영하지 않는다.

## 조회 비용

요일 컬렉션은 페이지 조회 시 50개 단위로 batch fetch한다. 목록 + count + 요일 조회가 항목마다 증가하지 않는지 테스트한다. 목록 정렬은 createdAt DESC, id DESC로 동일 생성시각에도 순서를 고정한다.
