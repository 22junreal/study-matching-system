# CI 테스트 · Docker 빌드 · GHCR 업로드

워크플로: [.github/workflows/backend-test.yml](../.github/workflows/backend-test.yml)

## 실행 조건

| 이벤트 | 테스트 | 이미지 빌드 | GHCR 업로드 |
|---|---|---|---|
| main 대상 PR, backend/workflow 변경 | 실행 | 테스트 성공 후 실행 | 하지 않음 |
| main push, backend/workflow 변경 | 실행 | 테스트 성공 후 실행 | 실행 |
| Actions에서 수동 실행, main | 실행 | 테스트 성공 후 실행 | 실행 |
| Actions에서 수동 실행, 다른 브랜치 | 실행 | 테스트 성공 후 실행 | 하지 않음 |

기존 `Backend Test` 워크플로 이름과 `test` job ID를 유지한다. `image-check`는 PR/다른 브랜치 검증용이고 `publish`는 main 업로드용이다. 테스트 실패 시 둘 다 실행하지 않는다. 같은 브랜치의 새 실행이 시작되면 이전 실행을 취소한다. 문서만 바꾸는 커밋은 자동 실행하지 않는다.

## 이미지 주소와 태그

```text
ghcr.io/22junreal/study-matching-system:sha-<40자리 커밋 SHA>
```

Linux amd64 이미지를 빌드한다. Apple Silicon에서 직접 실행하려면 `--platform linux/amd64` 에뮬레이션이 필요하다. ARM64 멀티 아키텍처 이미지는 현재 범위에 포함하지 않는다.

커밋 전체 SHA를 태그에 사용하고 `latest`나 `main` 같은 이동 태그는 발급하지 않는다. 같은 커밋을 재실행하면 기반 이미지나 외부 의존성 변경으로 digest가 달라질 수 있으므로 동일 바이너리를 지정할 때에는 실행 요약의 `@sha256:...` 주소를 사용한다.

빌드 컨텍스트는 `spring-backend/`이며 기존 Dockerfile을 사용한다. `.dockerignore`가 .env, 로컬 빌드 결과, 테스트 소스를 제외한다. DB 비밀번호/JWT_SECRET은 빌드에 전달하지 않는다. 런타임 설정은 향후 실행 환경에서 제공해야 한다. 최종 이미지의 기존 비루트 사용자와 healthcheck는 유지한다.

## 인증과 권한

업로드 job에만 `packages: write`를 부여한다. 테스트와 PR 빌드는 `contents: read`만 가진다. GHCR 로그인은 GitHub가 실행마다 발급하는 `GITHUB_TOKEN`을 사용하므로 PAT를 저장소 Secret에 추가할 필요가 없다. 각 Action은 확인한 커밋 SHA에 고정했다. 패키지는 OCI source 라벨로 저장소와 연결한다.

GHCR 패키지 공개 여부는 자동으로 바꾸지 않는다. 저장소가 공개여도 패키지는 별도로 확인해야 한다. 비공개 패키지를 개발 PC에서 받으려면 해당 계정의 접근 권한과 `read:packages` 권한이 있는 자격 증명으로 `docker login ghcr.io`해야 한다. 토큰을 명령행 인수나 Git 파일에 넣지 않는다.

## 확인 방법

1. [GitHub Actions](https://github.com/22junreal/study-matching-system/actions/workflows/backend-test.yml)에서 해당 커밋 실행을 연다.
2. `test`, `publish`가 성공했는지 확인한다. main 실행의 `image-check` skipped는 정상이다.
3. `backend-test-report` 아티팩트에서 테스트 보고서를 확인한다(보관 7일).
4. 실행 Summary에서 이미지 주소, digest, 정확한 `docker pull` 명령을 확인한다. 마지막 단계는 업로드된 digest가 GHCR에서 조회되는지도 검증한다.
5. 저장소/계정 Packages에서 `study-matching-system` 이미지 버전을 확인한다.

권한 오류가 나면 해당 패키지의 Actions 접근 권한에 이 저장소가 포함되는지 확인한다. 이전에 같은 이름으로 만든 패키지가 저장소와 연결되지 않았다면 `GITHUB_TOKEN`만으로 쓰기가 허용되지 않을 수 있다. 테스트 보고서에 실패가 있으면 먼저 실패를 수정한다.

## 범위

이 작업은 CI에서 테스트·이미지 빌드·레지스트리 업로드까지다. 클라우드 생성, 운영 서버 배포, DB 마이그레이션 실행, 서비스 재시작은 수행하지 않는다. CI 이미지 빌드는 컨테이너의 실제 운영 기동 검증을 대신하지 않는다.

구성 참고: [GitHub 공식 GHCR 이미지 게시 가이드](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images).
