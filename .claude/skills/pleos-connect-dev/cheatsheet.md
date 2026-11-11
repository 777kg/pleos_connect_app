# Cheatsheet — Pleos Connect 개발 판단 규칙

## 결정 규칙
- 차량 값이 필요하면 → 표준 프로퍼티인가? 그렇다면 `CarPropertyManager`, 아니면 Pleos SDK, SDK에도 없으면 기술 지원팀에 문의합니다.
- 도메인 API를 부르기 전에 → capability가 READ 계열이 아니면 기능을 숨기거나 비활성화합니다. 모델명으로 분기하지 않습니다.
- 퍼미션을 추가하려면 → 정말 쓰는 도메인인지, 승인을 받았는지 확인합니다. 하나라도 아니면 넣지 않습니다.
- 주행 중에도 쓸 앱인가 → Music/Audio, Navigation, Mobility라면 DO를 선언하고 비트별로 차단합니다. 그 외에는 DO를 선언하지 않습니다.
- 동영상, 차량 설정, QR, 긴 텍스트 → 어느 지역이든 주행 중에는 차단합니다.
- 텍스트 입력 UI를 만들려면 → 먼저 프리셋, 최근 항목, 음성으로 대체할 수 있는지 봅니다. 북미 주행 중에는 키보드가 불가합니다.
- 음성을 쓰려면 → 앱 안에서 받아쓰기만 하면 STT(`STT_SERVICE`), "OO 해줘" 같은 음성비서 명령으로 앱 기능을 부르게 하려면 Gleo Interface(`assets/schema.json`)입니다.
- NaviHelper·TTS·STT·FusedLocation을 쓰려면 → 그 SDK의 퍼미션만 추가합니다. NaviHelper는 내비가 없는 차량을, FusedLocation은 `isSupported`가 false인 환경을 대비해 기능을 숨기는 경로를 둡니다.
- Back 처리 → 팝업이면 닫기, 하위 화면이면 pop, 최상위면 `moveTaskToBack(true)`. `finish()`는 쓰지 않습니다.
- 소리를 내려면 → 포커스를 요청하고, 이벤트 4종을 처리하고, abandon합니다. 동시 재생은 MAY_DUCK.
- Suspend가 오면 → 중단, 반납, 저장, 해제. Resume은 자동으로 하지 않습니다.
- 값을 주기적으로 갱신하려면 → 리스너나 Callback을 씁니다. 폴링과 타이머는 쓰지 않습니다(CPU 5%).
- 비밀값이 생기면 → Keystore에 둡니다. 코드, 리소스, URL에는 넣지 않습니다.
- 다시 제출하려면 → 버전을 반드시 올립니다.

## 숫자
| 항목 | 값 |
|---|---|
| minSdk / API / Gradle | 28 / 34 / 8.0+ |
| AVD | Cold boot, RAM 4GB |
| RAM 한도 | 400MB |
| CPU 주행 중 | 5% 미만(내비 15%) |
| CPU 정차 중 | 25% |
| 실행, 로드 | 10초 |
| 버튼 반응 | 2초(넘으면 로딩 UI) |
| 터치 반응 | 0.25초 |
| TLS | 1.2 이상 |
| APK | 최대 600MB |
| 아이콘 | 512×512, 1MB |
| 스크린샷 | 최대 8장, 장당 8MB |
| SDK 호환성 보장 | 최소 2개 메이저 버전 |

## 지역 × 주행 중
| | 북미 | 유럽 | 그 외 |
|---|---|---|---|
| 키보드, 다이얼패드, 내비 검색 | ⛔ | ✅ | ✅ |
| 통합 검색 | 음성만 | ✅ | ✅ |
| 미디어 로그인·설정·검색 | ⛔ | ⛔ | ✅ |
| 차량 설정, 동영상, QR, 매뉴얼 | ⛔ | ⛔ | ⛔ |

## 오디오 포커스 빠른 표
| 현재 → 새 요청 | 결과 |
|---|---|
| MUSIC → NAV | C(duck) |
| MUSIC → CALL | E |
| CALL → MUSIC | R |
| CALL → NAV | C |
| EMERGENCY → 대부분 | R |
| 보유자가 여럿 | R > E > C 중 가장 보수적인 것 |

## 위험 신호 (보이면 반려)
- `finish()`가 최상위 Back에 있음
- `while`/`Handler.postDelayed`로 차량 값 폴링
- Manifest에 쓰지 않는 `pleos.car.permission.*`
- `screenOrientation` 고정 + 고정 dp 레이아웃
- 주행 중 재생 버튼이 활성화된 동영상 화면
- `Build.MODEL`이나 차종명으로 기능 분기
- SharedPreferences 평문 토큰, URL `?token=`
- `debuggable=true` 또는 디버그 키로 서명된 APK
- 같은 versionCode로 재제출
