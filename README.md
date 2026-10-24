# ThisIsNotOverlayer
TINO

Overlayer처럼 동작하는 마인크래프트 HUD 편집 모드입니다 (Fabric, Minecraft 26.3).
화면에 텍스트·아이템·블록·엔티티를 올리고, 값은 Overlayer 방식의 **태그**와 **Fx**(JavaScript 식)로 채웁니다.

*An Overlayer-style HUD editor for Minecraft (Fabric 26.3): put texts, items, blocks and entities on the screen and drive them with Overlayer-style tags and Fx expressions.*

## 사용법

**Right Shift**로 편집 창을 엽니다. 창은 끌어서 옮기고, 오른쪽 아래 모서리로 크기를 바꾸고, 제목을 두 번 누르면 최대화됩니다. `Tab`은 창 숨기기, `Esc`는 닫기입니다.

| 탭 | 하는 일 |
|---|---|
| **프리셋** | 프리셋(Overlayer의 캔버스)을 미리보기 타일로 봅니다. 체크박스로 켜고 끄기, 복제, 클립보드로 공유, 삭제. 새 프리셋은 빈 것 / 템플릿 / 클립보드 붙여넣기로 만듭니다. |
| **편집** | 프리셋의 오브젝트 트리와 선택한 오브젝트의 설정. 화면에서 드래그로 이동(스냅), 빈 곳 드래그로 범위 선택, Ctrl/Shift+클릭으로 여러 개 선택. |
| **태그** | 모든 태그를 검색하고, 클릭하면 복사됩니다. |

편집 단축키: `Ctrl+G` 그룹 · `Ctrl+Shift+G` 그룹 해제 · `Ctrl+D` 복제 · `Del` 삭제 · `Ctrl+A` 전체 선택 · 방향키 1px 이동 (`Shift` 10px).

## 오브젝트

- **텍스트**: `{Fps:0}` 같은 태그와 `<color=#FF5555>`, `<b>`, `<i>`, `<u>`, `<s>`, `<br>` 같은 Overlayer 태그, `&` 색 코드.
- **아이템 / 블록 / 엔티티**: id로 표시(Tab 자동완성). 블록과 엔티티는 X/Y/Z 3D 회전.
- **그룹**: 오브젝트를 묶어 함께 이동·회전·크기 조절.

모든 오브젝트 공통: 정렬(기준점), 위치, 크기, 회전, 배경, 외곽선. 설정마다 **ƒ**를 누르면 고정값 대신 Fx 식을 쓸 수 있습니다 (예: `SlotItem("head")`, `Math.sin(Time()) * 10`).

## 템플릿

- **완성된 HUD 6종**: 야생, PvP, 스피드런, 건축, 미니멀, 방송용
- **부품 32종**: FPS, 핑, 성능, 시계, 날짜, 플레이 시간, 서버, 좌표, 네더 좌표, 나침반, 속도, 체력, 배고픔, 경험치, 상태 패널, 상태 효과, 산소, 내 캐릭터, 키 입력, CPS, 방어구, 든 아이템, 왼손 아이템, 토템/화살 개수, 조준 대상, 바이옴, 게임 시간, 밝기, 보고 있는 블록, 청크, 회전 블록

템플릿도 일반 오브젝트 + 태그 + Fx로만 만들어져 있어서, 열어서 그대로 고칠 수 있습니다.

## 스크립트

`config/customhud/Script/`의 `.js` 파일로 자신만의 태그를 추가할 수 있습니다 (Rhino). 태그 탭의 **↻ 스크립트**로 다시 불러옵니다.

## 저장

프리셋은 `config/customhud/presets/presetN.json`에 하나씩 저장됩니다. 예전 버전의 `hud.json`과 Overlayer식 `canvases/`는 처음 실행할 때 프리셋으로 옮겨집니다.

## 빌드

Java 25가 필요합니다.

```bash
./gradlew build            # build/libs/custom-hud-<version>.jar
./gradlew runClient        # 개발용 클라이언트 실행
./gradlew test             # 단위 테스트
./gradlew runClientGameTest  # 실제 게임에서 편집기를 조작하는 테스트
```

## 라이선스

[GNU AGPL-3.0](LICENSE)
