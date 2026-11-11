# Patterns — Pleos Connect 개발

## Capability-first 차량 데이터 접근
**When to use**: Vehicle SDK의 어떤 도메인이든 처음 쓸 때
**How**: `Vehicle(context).initialize()` → `getXxx()` → `checkXxxCapabilities` → AccessType이 READ 계열이면 Get/구독, 아니면 UI 비활성화 → AreaBounds null 처리 → 생명주기 끝에서 `release()`
**Trade-offs**: 호출이 하나 더 늘지만, 차종 차이를 모델명 분기 없이 흡수합니다.

## 표준 우선 API 선택
**When to use**: 차량 값을 읽을 API를 고를 때
**How**: 속도, 기어, 외기온, 연료는 `CarPropertyManager`. 제조사 특화 기능은 Pleos SDK. SDK에 없는 특수 프로퍼티는 기술 지원팀에 검토를 요청합니다.
**Trade-offs**: AAOS 표준은 이식성이 높고, SDK 전용 API는 호환성이 2개 메이저 버전까지만 보장됩니다.

## 규제 비트 반영 (DO 앱)
**When to use**: Distraction Optimization을 선언한 앱
**How**: `CarUxRestrictionsManager`에 리스너를 등록하고, **초기값도 즉시 반영**합니다. `isRequiresDistractionOptimization`이 true일 때 NO_VIDEO, NO_KEYBOARD, NO_SETUP 등 비트별로 기능을 끄고 켭니다. 지역 분기는 하지 않습니다.
**Trade-offs**: 모든 입력 기능에 분기가 생깁니다. DO를 선언하지 않으면 단순하지만 주행 중에는 쓸 수 없습니다.

## 미디어 정차 전용
**When to use**: 동영상, 게임, 브라우저 앱
**How**: 주행이 시작되면 일시정지하고 UI를 숨깁니다. 주행 중에는 재개를 막습니다. 헤드업 알림은 쓰지 않고, MediaSession과 통합합니다.
**Trade-offs**: 없음. 심사 필수 조건입니다.

## 숨기기 Back
**When to use**: 모든 앱의 최상위 화면 Back(CCP 하드키 포함)
**How**: 팝업이면 닫고, 하위 화면이면 pop하고, 최상위면 `moveTaskToBack(true)`. `finish()`는 쓰지 않습니다.
**Trade-offs**: 프로세스가 오래 살기 때문에 백그라운드 정리 패턴과 함께 씁니다.

## 반응형 2-window 레이아웃
**When to use**: 모든 화면
**How**: portrait(1칸)에는 핵심, landscape(2칸)에는 확장을 둡니다. 크기가 바뀌면 재배치하되 기능은 같게 둡니다. 레터박스 없이 안전 영역을 지킵니다.
**Trade-offs**: 레이아웃이 두 벌 필요하지만, 디스플레이 호환성 심사를 통과하려면 해야 합니다.

## 백그라운드 정리 / 포그라운드 복원
**When to use**: onStop/onStart, 프로세스 재생성
**How**: 숨겨지면 갱신, 애니메이션, 센서, 타이머, 폴링을 멈춥니다. 돌아오면 상태를 복원하고 UI를 재구성합니다. 상태는 영속화해서 프로세스가 죽어도 복원되게 합니다.
**Trade-offs**: 저장 로직이 늘지만, 상태 복원은 심사 항목입니다.

## 오디오 포커스 전체 사이클
**When to use**: 소리를 내는 모든 앱
**How**: 재생 전에 요청합니다. GAIN, LOSS, LOSS_TRANSIENT, CAN_DUCK을 모두 처리합니다. 동시 재생은 MAY_DUCK. 끝나면 abandon합니다.
**Trade-offs**: 없음. 필수입니다.

## Suspend 4단계
**When to use**: 차량 절전 진입과 복귀
**How**: 중단 → 반납 → 상태 저장 → 리소스 해제. Resume에서는 상태만 복원하고, 재생은 사용자 행동이나 정책에 따라 다시 요청합니다.
**Trade-offs**: 자동 재생 UX를 포기하는 대신 정책을 준수합니다.

## 오프라인 우선 네트워크
**When to use**: 서버와 통신하는 모든 앱
**How**: 최근 콘텐츠를 캐시하고, 타임아웃과 백오프 재시도를 두고, 오프라인 배너를 띄웁니다. TLS 1.2 이상만 허용하고, 토큰은 헤더로 보냅니다.
**Trade-offs**: 캐시 무효화 로직이 필요합니다.

## 공유 기기 계정
**When to use**: 로그인이 있는 앱
**How**: 로그아웃과 계정 전환을 쉽게 만듭니다. 토큰은 Keystore에 두고, 로그아웃하면 즉시 삭제합니다. 주행 중 로그인은 지역 규제를 따르고, QR 로그인은 정차 중에만 가능합니다.
**Trade-offs**: 운전자가 바뀔 때마다 다시 로그인하는 비용이 생깁니다.
