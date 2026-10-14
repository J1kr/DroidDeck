# 한글 입력 작업 노트 (PR 전에 이 파일 삭제)

브랜치: `fix/hangul-ime` · 관련 이슈: Droid-Deck/DroidDeck#567 (3번 항목: Non-ASCII characters never reach the guest)

## 지금까지 한 것 (2026-10-14, 회사)

- `app/src/main/java/com/droiddeck/launcher/input/KeyboardHost.kt`
  - US 키 표로 칠 수 없는 글자(한글 등)는 버리지 않고 `WaylandCompositor.textInputCommit()`(text-input-v3)으로 보냄
  - ASCII는 기존처럼 키 이벤트, 섞인 문자열은 순서 유지하며 나눠서 보냄
  - `setComposingText` → `textInputPreedit()` (조합 중 글자 표시), `finishComposingText` → 조합 중 글자 확정
- `app/src/test/.../input/KeyboardHostTest.kt`에 테스트 5개 추가
- **아직 한 번도 빌드/테스트 안 돌림** (회사 Mac에 Java/Android SDK 없음)

## 집에서 할 것

1. `git pull` 후 단위 테스트:
   `./gradlew -PskipRust=true :app:testDebugUnitTest --tests '*KeyboardHostTest*'`
2. APK 빌드 후 TB520FU에 설치 (`tools/deploy_local.sh`, Gboard 한국어 자판)
3. 확인할 것
   - [ ] Steam Big Picture 검색창에 "한글" 입력되는지
   - [ ] 조합 중 글자(ㅎ→하→한)가 보이는지
   - [ ] 영어/숫자/기호, 엔터, 백스페이스가 예전처럼 동작하는지 (회귀 확인)
   - [ ] 데스크톱(KDE) 앱(예: Kate, 터미널)에서 한글 입력
   - [ ] 게임 내 채팅창 (가능하면)
4. 안 되면 세션 로그(`files/logs/`의 session.log)에서 `text-input` 줄 확인
   - `typed text (...) dropped: no program accepts text input` → 그 앱이 text-input-v3를 안 씀
   - `text input enabled by <프로그램>` / `committed N chars to <프로그램>` → 경로는 정상

## 위험 요소 (확인 필요)

Steam 게임 모드는 `gamescope`(우리 컴포지터의 Wayland 클라이언트) 안의 Xwayland(X11)에서 돈다.
gamescope가 부모 컴포지터의 text-input-v3를 받아 X11 앱에 넘겨주지 않으면, 이 변경만으로는
Steam에 한글이 안 들어간다(로그에 "dropped"가 찍힘). 그 경우 다음 단계 후보:
- gamescope 패치(`tools/gamescope/patches/`): Wayland 백엔드에서 text-input-v3 commit을 받아
  gamescope 자체 IME(Steam 화상 키보드가 쓰는 경로, 임의 유니코드를 X11로 타이핑)로 전달
