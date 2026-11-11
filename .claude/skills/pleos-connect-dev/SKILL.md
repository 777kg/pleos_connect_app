---
name: pleos-connect-dev
description: "Pleos Connect(Android Automotive OS 차량 앱) 개발 규칙 지식베이스. 공식 문서(document.pleos.ai)를 정리한 PLEOS_DEV_NOTES.md에서 추출. Pleos Vehicle·NaviHelper·TTS·STT·FusedLocation SDK와 퍼미션, Gleo Interface(음성비서 연동), 주행 규제(CarUxRestrictions), 멀티 윈도우·CCP Back, 오디오 포커스, 성능 한도, 보안, App Market 심사 제출을 다룰 때 사용."
---

<!-- argument-hint: [주제, 패턴 이름, 또는 챕터 번호] -->

# Pleos Connect 앱 개발 규칙
**출처**: Pleos 공식 문서 → `PLEOS_DEV_NOTES.md`(저장소 루트) (2026-10-27 정리) | **Chapters**: 7 | **Generated**: 2026-10-27

## How to Use This Skill

- **코드를 쓰거나 리뷰할 때**: 아래 Core 규칙을 적용하고, 해당 영역 챕터를 읽은 뒤 작업합니다.
- **주제로 물을 때**: `주행 규제`, `오디오 포커스`, `퍼미션` 같은 주제는 Topic Index로 챕터를 찾아 읽습니다.
- **챕터로 물을 때**: `ch03`처럼 요청하면 그 파일을 로드합니다.
- **판단이 필요할 때**: [cheatsheet.md](cheatsheet.md)의 결정 규칙과 위험 신호를 봅니다.
- 원본 정리 문서와 공식 링크: `PLEOS_DEV_NOTES.md`(저장소 루트) §15. 내용이 오래됐을 수 있으므로 버전과 정책은 릴리스 노트로 재확인합니다.

---

## Core Rules (작업할 때 항상 적용)

### 1. 차량 데이터: 표준 우선 + Capability-first
- 속도, 기어, 외기온, 연료처럼 **표준 데이터는 AAOS `CarPropertyManager`** 로 읽습니다. 제조사 특화 기능만 Pleos SDK로 다룹니다.
- Vehicle SDK 순서: `Vehicle(context).initialize()` → `getXxx()` → **`checkXxxCapabilities`** → AccessType이 READ 계열일 때만 Get/구독 → `AreaBounds` null 처리 → **생명주기 끝에서 `release()`**(콜백 직후 금지).
- 결과는 IPC 비동기(`onComplete`/`onFailed`)입니다. 퍼미션이 없으면 `VehicleException(ERROR_SECURITY_PERMISSION)`이 옵니다.
- **모델명이나 `Build.DEVICE`로 기능을 분기하지 않습니다.** capability로 판단합니다.
- 차량 하드웨어와 센서에 직접 접근하지 않습니다. Telephony, SMS, 일부 센서, 캘린더, 주소록은 Optional 구조로 만듭니다.

### 2. 퍼미션: 최소 + 승인된 것만
- `pleos.car.permission.CAR_*`는 조회, `CONTROL_CAR_*`는 제어입니다. 쓰는 도메인 것만 선언하고, 함수마다 `@RequiresPermission`을 확인합니다.
- **App Market이 승인하지 않은 퍼미션이 있으면 심사에서 반려됩니다.**

### 3. 주행 규제: 판정은 플랫폼, 반영은 앱
- `CarUxRestrictionsManager`에 리스너를 등록하고 **초기값도 반영**합니다. `isRequiresDistractionOptimization()`일 때 `ActiveRestrictions` 비트(NO_VIDEO, NO_KEYBOARD, NO_SETUP, NO_FILTERING, NO_DIALPAD, LIMIT_CONTENT, LIMIT_STRING_LENGTH)별로 기능을 끕니다.
- 속도, 기어, 지역을 앱이 직접 판정하거나 분기하지 않습니다.
- DO 미선언 앱은 주행 중 사용할 수 없습니다. DO는 Music/Audio, Navigation, Mobility처럼 주행 중 가치가 있는 앱만 선언합니다.
- **전 지역 주행 중 금지**: 동영상, 차량 설정(디지털키·도어·시트), QR 로그인, 매뉴얼·긴 텍스트. 북미는 키보드, 다이얼패드, 미디어 로그인도 금지이고 검색은 음성만 가능합니다.
- 미디어 앱(동영상·게임·브라우저)은 주행이 시작되면 **일시정지하고 UI를 숨기며, 재개를 금지**합니다. 헤드업 알림이나 운전과 무관한 알림은 금지입니다.
- UI: 큰 터치 영역, 높은 대비, 적은 텍스트, 단순한 메뉴. 입력 대신 프리셋, 최근 항목, 음성을 씁니다. 장식 애니메이션은 넣지 않습니다.

### 4. 화면·생명주기
- Driving view 옆의 window 1~2칸에 뜹니다. `screenOrientation`을 지정하지 않으면 portrait(1칸), landscape면 2칸입니다. **두 방향 모두 설계하고, 반응형으로 만들고, 기능 패리티를 지키고, 레터박스와 호환성 모드는 금지**입니다.
- 앱은 종료되지 않고 숨겨집니다. 백그라운드에서는 갱신, 애니메이션, 센서, 타이머, 폴링을 멈춥니다. 복귀하면 **마지막 상태를 복원**하고, 프로세스가 재생성돼도 복원되게 합니다.
- **최상위 Back(CCP 하드키 포함)은 `moveTaskToBack(true)`** 입니다. `finish()`는 쓰지 않으며, 어기면 반려됩니다.
- CCP를 지원하지 않으면 `<application>`에 `com.hmg.rotary.ROTARY_SUPPORTED=false`를 넣고, 터치만으로 완결되게 합니다.
- EditText가 없는 Dialog는 `setFitInsetsTypes(statusBars or navigationBars)`로 IME insets를 제외합니다.

### 5. 오디오
- 재생 전에 `AudioFocusRequest`를 요청하고, GAIN, LOSS(정지), LOSS_TRANSIENT(일시정지), CAN_DUCK(감쇄 후 복원)을 **모두 처리**합니다. 끝나면 abandon합니다.
- 동시 재생은 `GAIN_TRANSIENT_MAY_DUCK`. 결과 우선순위는 R > E > C입니다. 통화 중 음악은 거부, 음악 중 통화는 선점, 음악 중 내비는 duck입니다.
- **Suspend**: 중단 → 반납 → 상태 저장 → 해제. **Resume에서는 자동 포커스나 자동 재생을 하지 않습니다.**

### 6. 성능 한도 (심사)
RAM **400MB** · CPU 주행 중 **5% 미만**(내비 15%) · 정차 중 **25%** · 실행·로드 **10초** · 버튼 **2초**(넘으면 로딩 UI) · 터치 반응 **0.25초**. Cold start 초기화는 최소한으로 하고 lazy 로드합니다. 폴링 대신 리스너를 씁니다.

### 7. 네트워크·보안·개인정보
- 끊김을 전제로 캐시, 재시도·타임아웃, 오프라인 UI를 갖춥니다. TLS 1.2 이상, 토큰은 Authorization 헤더로 보내고 IP를 하드코딩하지 않습니다.
- 공유 기기이므로 쉬운 로그아웃과 계정 전환, Keystore 토큰, 민감정보 암호화(AES-GCM), 로그 마스킹, 백업 제외를 기본으로 합니다.
- 릴리스 빌드: `debuggable=false`, WebView 디버깅 off, **정식 키 서명**, 쓰지 않는 컴포넌트는 `exported=false`.
- 동의는 이유를 밝히고 최소한만 받으며, 버튼으로만 받습니다. 철회하면 즉시 삭제합니다. 텔레메트리 ID는 가명화하고, 운전 데이터는 마케팅에 쓰지 않습니다.

### 8. 환경·제출
- minSdk 28, API 34, Gradle 8.0 이상. 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`, 그룹 `ai.pleos.playground`: `Vehicle:2.3.2`, `NaviHelper:3.0.5`, `TextToSpeech:3.3.1`, `SpeechToText:3.3.1`, `FusedLocation:1.1.3`. 에뮬레이터 v2.1.0(Cold boot, 4GB).
- SDK 퍼미션: NaviHelper `NAVI_ROUTE`(조회)·`NAVI_ROUTE_SEARCH`(경로)·`NAVI_CUSTOM_MAP`(화면), TTS `TTS_SERVICE`, STT `STT_SERVICE`, FusedLocation `FUSED_LOCATION`. 쓰는 SDK 것만 선언합니다.
- STT는 인식만 합니다. 음성 명령으로 앱 기능을 실행하려면 **Gleo Interface**(`assets/schema.json` + intent-filter + `APP_ACTION_RESPONSE` broadcast)를 씁니다(ch02).
- 2026-10 Breaking change: NaviHelper 좌표 (lat, lng), `FanDirectionType` 순서 변경(ordinal 저장 금지), FusedLocation `onError` 필수.
- 제출은 **APK(최대 600MB)** 이고 **매번 버전을 올립니다.** 테스트 계정, 프로젝트 ID, 아이콘 512, 스크린샷 8장 이하, IARC, 처리방침 URL, 영문 태그가 필요합니다.
- 검증: Virtual Console 기어 D/P(에뮬레이터 v2.1.0, `adb forward tcp:8088 tcp:8088`), 이전 이미지는 `adb shell cmd hmg_car_service --service drivingstate driving_regulation on|off`, 폼팩터 테스트.

### 미확인 (문서에 없음, 추측하지 말고 확인 필요로 표시)
화면 해상도와 dp, UI Design Guide 버튼 수치, DO meta-data 정확한 형식, Google Play Services 사용 가능 여부, targetSdk와 서명 요건, 심사 기간, `CONTROL_CAR_*` 전체 목록, 함수별 `@RequiresPermission`, "준비 중" SDK(TTS·STT·FusedLocation)를 실제로 받을 수 있는지, TTS/STT 지원 언어, Gleo Interface 퍼미션과 승인 기준.

---

## Chapter Index

| # | Title | Key Frameworks |
|---|-------|----------------|
| [ch01](chapters/ch01-setup.md) | 프로젝트 준비 · 개발 환경 | SDK 신청 상태, 3종 키, Gradle 설정, Breaking change |
| [ch02](chapters/ch02-vehicle-sdk.md) | Vehicle SDK · 퍼미션 · 차량 정보 | AAOS Alignment, Capability-first, 퍼미션 표 |
| [ch03](chapters/ch03-driving-restriction.md) | 주행 안전 · 규제 · 카테고리 | Parking/Idling/Driving, DO, 지역 표, 미디어 정차 전용 |
| [ch04](chapters/ch04-screen-lifecycle.md) | 화면 구조 · 생명주기 · CCP/Back | Window 모델, 기능 패리티, 숨기기 Back, IME insets |
| [ch05](chapters/ch05-audio.md) | 오디오 포커스 · Suspend | E/C/R, 이벤트 4종, Suspend 4단계 |
| [ch06](chapters/ch06-performance-security.md) | 성능 · 네트워크 · 보안 · 개인정보 | 성능 한도표, 오프라인 우선, 공유 기기 보안 |
| [ch07](chapters/ch07-test-submission.md) | 테스트 · 등록 · 심사 | 검증 루프, 심사 5영역, 제출 체크리스트 |

## Topic Index

- **adb 명령** → ch03, ch07
- **AreaBounds / AccessType** → ch02
- **Back 키 / moveTaskToBack** → ch04
- **Breaking change / SDK 버전** → ch01
- **CarPropertyManager / 차량 속도** → ch02
- **CarUxRestrictions / 주행 규제** → ch03
- **CCP / 로터리** → ch04
- **CPU / RAM / 응답 시간** → ch06
- **Distraction Optimization (DO)** → ch03
- **Duck / 오디오 포커스** → ch05
- **IME / Dialog insets** → ch04
- **Keystore / 토큰 / TLS** → ch06
- **NaviHelper / TTS / STT / FusedLocation** → ch01, ch02
- **Gleo Interface / 음성 명령 연동** → ch02, ch05
- **전체 API 함수 목록** → `docs/PLEOS_API_LIST.md`(저장소)
- **멀티 윈도우 / 화면 방향** → ch04
- **미디어 앱(동영상·게임·브라우저)** → ch03
- **백그라운드 / 상태 복원** → ch04
- **앱 카테고리** → ch03
- **에뮬레이터 / AVD / Virtual Console** → ch01, ch07
- **개인정보 / 동의** → ch06
- **퍼미션 (CAR_* / CONTROL_CAR_*)** → ch02
- **폼팩터 테스트** → ch07
- **프로젝트 생성 / SDK 신청** → ch01
- **Suspend / Resume** → ch05
- **심사 제출 자료 / 버전 업** → ch07

## Supporting Files

- [glossary.md](glossary.md) — 핵심 용어
- [patterns.md](patterns.md) — 구현 패턴 11개
- [cheatsheet.md](cheatsheet.md) — 결정 규칙, 숫자, 위험 신호

---

## Scope & Limits

이 스킬은 2026-10-27 시점의 공식 문서 정리본만 다룹니다. SDK 시그니처의 세부는 공식 API 문서(document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)로 확인합니다. 챕터의 코드는 문서 흐름을 재구성한 **스케치**입니다. 정책이 바뀌면 `PLEOS_DEV_NOTES.md`를 먼저 갱신하고, 이 스킬을 Update/Fold-in 합니다.
