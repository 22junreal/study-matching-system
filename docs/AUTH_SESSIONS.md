# 로그인 세션 · Refresh Token Rotation · Logout

## API 계약

이 API는 기존 JSON/Bearer 방식을 확장한다. 쿠키는 발급하거나 읽지 않는다.

- `POST /api/auth/login`: 기존 `{ "username": "alice", "password": "..." }` 요청.
- `POST /api/auth/refresh`: `{ "refreshToken": "<발급받은 토큰>" }` 요청.
- `POST /api/auth/logout`: 같은 요청 본문. 성공은 본문 없는 `204`.

로그인과 갱신 성공은 `200`, 다음 형태다. 토큰 값은 예시이며 실제 토큰이 아니다.

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "refreshToken": "<43-character-base64url-token>",
  "expiresIn": 900,
  "refreshExpiresIn": 1209600
}
```

`expiresIn`과 `refreshExpiresIn`은 초 단위다. 갱신 시 refreshExpiresIn은 해당 로그인 세션의 남은 수명이며 줄어든다. 로그인/갱신 응답은 `Cache-Control: no-store`, `Pragma: no-cache`다. Access Token에는 고유 `jti`가 있어 같은 초에 발급해도 달라진다.

Refresh/Logout에는 Access Token을 보내지 않는다. 만료된 Authorization 헤더를 넣으면 Spring Security 필터가 먼저 401을 반환할 수 있다. 보호 API에는 기존처럼 `Authorization: Bearer <accessToken>`만 사용한다. Refresh Token으로 보호 API에 접근할 수 없다.

## 수명과 폐기 정책

- Access Token 기본 900초(15분), `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS`로 변경.
- Refresh 세션 기본 1209600초(14일), `JWT_REFRESH_TOKEN_EXPIRATION_SECONDS`로 변경.
- 기존 `.env`의 3600 등 명시적인 설정은 그대로 우선한다. 예제 파일을 바꿔도 기존 설정이 자동으로 변경되지는 않는다.
- 로그인마다 새 세션을 만든다. 세션은 로그인 시 정한 절대 만료일까지 유지되며 갱신으로 연장하지 않는다.
- 갱신 성공마다 이전 Refresh Token을 사용 완료로 표시하고 새 Access/Refresh 쌍을 발급한다.
- 사용 완료 토큰을 다시 제출하면 해당 세션 전체를 폐기한다. 이미 발급된 후속 Refresh Token도 모두 401이다. 다른 로그인 세션에는 영향이 없다.
- 로그아웃은 제출한 Refresh Token이 속한 세션 전체를 폐기한다. 그 세션의 이전 토큰으로도 로그아웃할 수 있다. 형식이 맞는 알 수 없는 토큰/이미 로그아웃한 토큰은 204로 처리한다.
- **로그아웃·재사용 탐지 후에도 이미 발급된 Access Token은 자체 만료까지 유효하다.** 서버가 요청마다 세션을 조회하거나 Access Token 차단 목록을 사용하지 않는다. 클라이언트는 로그아웃 시 두 토큰을 함께 지운다.
- 여러 기기에서 로그인할 수 있으며 이 API는 현재 토큰의 세션만 종료한다. 전체 기기 로그아웃·세션 목록·Access Token 즉시 폐기는 구현하지 않았다.

## 클라이언트 처리

1. 로그인 성공 시 Access/Refresh 쌍을 보관한다. 비밀번호나 토큰을 로그·URL·분석 이벤트에 넣지 않는다.
2. 보호 API에서 Access 만료로 401이 오면 갱신을 **한 번만 공동 실행**하고, 같은 시점의 다른 요청은 그 결과를 기다린다.
3. 갱신 성공 시 두 토큰을 함께 교체한 뒤 원래 요청을 한 번 재시도한다.
4. 갱신에서 401이 오면 로컬 토큰을 지우고 다시 로그인한다. 무한 재시도하지 않는다.
5. 갱신 응답 유실 시 이전 토큰을 자동으로 재전송하지 않는다. 이미 서버에서 회전됐을 수 있어 재사용 탐지로 세션이 폐기된다. 다시 로그인하는 흐름을 사용한다.
6. 로그아웃에는 마지막으로 보관한 Refresh Token을 보내고 로컬 토큰을 지운다. 서버 요청이 실패하면 서버 세션 폐기가 완료됐다고 표시하지 않는다.

브라우저 localStorage 영구 저장은 XSS에 노출된다. 현재 구현은 JSON API이며 브라우저의 안전한 장기 저장을 제공하지 않는다. 웹 프론트의 지속 로그인에는 BFF/HttpOnly Secure 쿠키 등 별도 저장 설계를 적용해야 한다. 쿠키 방식으로 전환할 때에는 CSRF·SameSite·CORS 정책도 함께 바꿔야 한다. 현재 CSRF 비활성화는 자동 전송되는 인증 쿠키를 사용하지 않는 구조를 전제로 한다.

## 서버 정합성 · 저장

V10은 `refresh_sessions`, `refresh_tokens` 테이블과 FK/인덱스를 추가한다. 기존 회원·스터디 데이터는 바꾸지 않는다. 기존 Access Token은 계속 사용할 수 있지만 Refresh Token을 얻으려면 새로 로그인해야 한다.

Refresh Token은 SecureRandom 32바이트(256비트)를 Base64 URL-safe로 인코딩한 불투명 토큰이다. DB에는 SHA-256 해시만 저장한다. 만료일·폐기 여부는 세션 테이블에 있고, 사용한 토큰 해시도 세션 만료까지 남겨 재사용을 탐지한다. DTO의 toString도 토큰을 가린다.

갱신과 로그아웃은 같은 세션 행에 PostgreSQL 비관적 잠금을 건다. 잠금을 얻은 뒤 토큰 사용 여부를 읽는다. 동시에 동일한 토큰을 갱신하면 하나만 성공하며 다른 요청은 재사용으로 판정되어 해당 세션을 폐기한다. 따라서 먼저 성공한 응답의 새 Refresh Token도 사용 불가가 된다. 이는 클라이언트의 병렬 갱신을 허용하는 유예 정책이 아니다.

재사용에 대한 401을 반환하더라도 세션 폐기는 커밋된다. 서명/저장 중 예상치 못한 오류는 트랜잭션을 롤백하여 기존 토큰 소비와 새 토큰 삽입을 함께 취소한다. DB 장애는 401로 숨기지 않고 500으로 처리한다.

만료 세션과 연결된 토큰 기록은 시간당 정리한다(`jwt.refresh-cleanup-interval-ms`, 기본 3600000). 폐기되었지만 아직 만료되지 않은 기록은 만료일까지 유지한다. 회원 삭제 시 FK cascade로 세션과 토큰이 삭제된다. 백업에는 두 테이블을 포함해야 하며, 이전 시점의 DB 복원은 그 이후 로그아웃/회전 상태를 되돌릴 수 있다. 운영 복원 후 기존 Refresh 세션 전체 폐기 여부를 판단해야 한다.

Nginx 인증 요청 제한은 login/register/refresh/logout에 공통 적용된다(기본 IP당 5회/분 + burst 5). 프록시를 통하지 않는 직접 백엔드 접근에는 이 제한이 없다.

재사용 탐지 정책 참고: [RFC 9700 §4.14.2](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14.2). 이 서비스가 OAuth Authorization Server 전체를 구현한다는 의미는 아니다.

## 검증

`RefreshTokenE2ETest`는 실제 HTTP 서버와 PostgreSQL에서 로그인 쌍 발급/해시 저장, 회전, 절대 만료 유지, 이전 토큰 재사용 후 후속 토큰 폐기, 기기별 격리, 로그아웃 반복, 만료 Access 갱신, Refresh의 Access 사용 거부, 잘못된 요청, 동시 갱신, 갱신/로그아웃 경쟁, 서명 실패 롤백, DB 오류 경계를 확인한다. `RefreshTokenMigrationTest`는 V9→V10 적용과 FK cascade를 검증한다.

기존 `reports/verification/operations.json`과 성능 보고서는 V9 시점의 로컬 운영/성능 자료다. V10 Refresh/Logout을 해당 프록시 환경에서 재측정한 자료가 아니다. 현재 인증 변경은 위 통합 테스트 결과를 기준으로 한다.

## Backend 재시작 검증

[Backend 재시작 시나리오](BACKEND_RESTART.md)는 실제 Docker stop/start 전후 DB 유지와 기존 Refresh Token의 갱신을 검증한다. 실행 결과는 [원자료](../reports/verification/backend-restart.json)에 별도로 기록한다.
