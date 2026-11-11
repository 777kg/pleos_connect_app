# Pleos Connect 앱 개발 유의사항

> 공식 문서(https://document.pleos.ai/docs/pleos-only/getting-started 및 하위 문서)를 2026-10-27에 읽고 정리했습니다.
> 사용자와 Claude가 개발할 때 같이 보는 기준 문서입니다. 문서가 바뀌면 이 파일도 갱신합니다.
> 표시: **[필수]** 어기면 심사 반려 · **[권장]** · **[확인 필요]** 공식 문서에 없는 내용

---

## 0. 한눈에 보는 핵심 15개

1. 플랫폼은 **Android Automotive OS(AAOS)**. 즉 일반 Android 앱(Kotlin/Java)입니다. 웹앱이 아닙니다.
2. `minSdk` **28 이상**, compileSdk와 에뮬레이터는 **API 34**, Gradle **8.0 이상**.
3. 차량 데이터는 반드시 **SDK나 AAOS Car API**로만 다룹니다. 센서나 하드웨어에 직접 접근하면 안 됩니다.
4. 표준 데이터(속도·기어·외기온·연료)는 **AAOS Car API**(`CarPropertyManager`)를, 제조사 특화 기능은 **Pleos SDK**를 씁니다.
5. 도메인을 쓰기 전에 **`checkXxxCapabilities`로 지원 여부를 확인**합니다. 차종마다 다르고, 모델명으로 분기하면 안 됩니다.
6. 퍼미션은 **App Market과 사전 협의해 승인받은 것만** 선언할 수 있습니다. 미승인 퍼미션을 선언하면 심사에서 떨어집니다.
7. SDK 호출은 IPC 기반 **비동기(`onComplete`/`onFailed`)** 입니다. `release()`는 콜백이 끝난 뒤에 호출합니다.
8. **주행 규제**: `CarUxRestrictionsManager`로 제한 상태를 받아 기능을 막습니다. 북미가 가장 엄격합니다.
9. 주행 중에는 **동영상, 차량 설정, QR 로그인, 긴 텍스트**가 전 지역에서 금지됩니다.
10. 화면은 **멀티 윈도우**입니다(Driving view 옆 앱 영역 1~2칸). portrait와 landscape를 **모두** 설계하고 반응형으로 만듭니다.
11. **성능 한도**: RAM 400MB 이하, 주행 중 CPU 5% 미만(내비 앱 15%), 정차 중 25% 이하, 실행과 로드는 10초, 버튼 반응 2초, 터치 반응 0.25초.
12. **오디오 포커스**는 요청, 반납, 변경 이벤트 처리를 모두 구현해야 합니다. Suspend 진입 시에는 반드시 반납합니다.
13. CCP(로터리) Back 하드키를 최상위 화면에서 받으면 **`finish()`가 아니라 `moveTaskToBack(true)`** 를 호출합니다.
14. 보안: 토큰은 Keystore에 저장, TLS 1.2 이상, 릴리스 빌드에서 디버깅 비활성화, 로그 마스킹, 정식 서명.
15. 심사를 다시 요청할 때마다 **앱 버전을 올립니다.** 제출은 **APK만, 최대 600MB**.

---

## 1. 계정 · 프로젝트 · SDK 신청 (개발 착수 전)

- Pleos Playground(https://pleos.ai/playground)에서 계정을 만들고 **Workspace > My Project > 프로젝트 생성**을 합니다.
  - 카테고리는 앱 개발에 필수라서 **미리 정하는 것을 권장**합니다(주행 중 허용 범위가 카테고리에 따라 달라짐. §5 참고).
- 프로젝트 정보에서 확인할 것
  - **프로젝트 ID(Key)**: 심사 요청 시 App Market Console에 입력합니다.
  - **클라이언트 ID/시크릿**: 서버 연동 SDK(TTS, STT)에 씁니다. **시크릿은 코드에 하드코딩하지 않습니다.**
  - CRN: Emulator 2.1.0부터는 필요 없습니다(CRN 제공 종료).
- SDK별 신청 상태
  | SDK | 상태(문서 기준) |
  |---|---|
  | Vehicle State, NaviHelper | 신청 없이 바로 사용 |
  | Vehicle Control, FusedLocation, TTS, STT | 준비 중(Coming Soon). 아직 신청 불가 |
  | 그 밖의 일부 | "제휴 계약 필요". 웹에서 신청 불가, partnership@pleos.ai로 문의 |

## 2. 개발 환경

- Android Studio(최신) + Android SDK API 34 + **Pleos Connect Emulator system image**
- SDK Manager > SDK Update Sites에 "Pleos Connect System Image"를 추가합니다.
  - URL은 OS마다 다릅니다. **Windows/Linux는 `crp-sys-img.xml`**, Mac은 `crp-arm64-sys-img.xml`.
  - 이전 버전 에뮬레이터가 있으면 먼저 지웁니다.
- AVD: Form Factor **Automotive**, `Pleos Connect v2.1.0`, **Cold boot**, **RAM 4GB**, API 34.
- 최신 Emulator는 **v2.1.0(2026-10-19)**. Virtual Console과 폼팩터 테스트 기능이 추가되었고, LLM SDK·CRN·블루투스 동글은 제공 종료되었습니다.

### Gradle 설정

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // 필요한 SDK만 추가합니다 (각 SDK의 Programming Guide > Importing 페이지, 2026-10-27 확인)
    // implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    // implementation("ai.pleos.playground:SpeechToText:3.3.1")
    // implementation("ai.pleos.playground:FusedLocation:1.1.3")
}
```

> ⚠ Getting Started 예제에는 `2.3.1`로 적혀 있지만, SDK 릴리스 노트 최신 버전은 **v2.3.2**입니다. 최신 버전을 쓰고, 받아지지 않으면 2.3.1로 확인합니다.
> 모든 SDK가 같은 저장소(`nexus-playground.pleos.ai`)와 그룹(`ai.pleos.playground`)을 씁니다. 다만 FusedLocation·TTS·STT는 §1에서 "준비 중"이므로 좌표가 있어도 실제로 받을 수 있는지는 **[확인 필요]**.

### SDK 최신 버전과 Breaking change (2026-10-19 릴리스)

| SDK | 버전 | 주의할 변경 |
|---|---|---|
| Vehicle | 2.3.2 | `CruiseControl` 상수 제거, `checkHvacFanSpeedCapabilities`가 `AreaBounds<Int>` 반환, `FanDirectionType` 순서 변경(**ordinal을 저장하지 말 것**), Area 타입에 equals/hashCode 추가 |
| NaviHelper | 3.0.5 | 좌표 인자 순서가 **(lat, lng)** 로 변경, `totalDistance/Time`이 `remainDistance/Time`으로 변경, 콜백 타입이 `NavigationStatus`로 변경 |
| TTS / STT | 3.3.1 | 오류 콜백 인자 타입이 `ErrorType`으로 변경 |
| FusedLocation | 1.1.3 | `onChanged(LocationData?)`가 nullable로 변경, `onError` 구현 필수 |

## 3. SDK 사용 규칙

### 사용 가능한 SDK와 차량 호환성
| SDK | 용도 | 호환성 확인 방법 |
|---|---|---|
| Vehicle | 차량 상태 조회와 제어(19개 도메인) | 도메인별 `checkXxxCapabilities` |
| NaviHelper | 길안내 제어와 정보 조회 | **내비가 탑재된 차량에서만** 동작 |
| TextToSpeech / SpeechToText | 음성 합성과 인식 | 전 차량 |
| FusedLocation | 차량 위치 | `isSupported` |

Vehicle 도메인: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer, Safety, Seat, SideMirror, Steeringwheel, Tire, Toll, TurnSignal, Window, Wiper

### Vehicle SDK 호출 흐름
```kotlin
val vehicle = Vehicle(context)
vehicle.initialize()
val door = vehicle.getDoor()          // 도메인 객체는 getXxx()로만 얻는다 (직접 생성 X)
door.checkXxxCapabilities(...)        // 먼저 지원 여부와 범위 확인
door.getCurrentDoorPosition(DoorArea(DoorValue.ROW_1_LEFT),
    onComplete = { /* 결과 */ },
    onFailed   = { e -> /* VehicleException이면 errorCode 확인 */ })
// ...
vehicle.release()                     // onDestroy 등 SDK가 더는 필요 없는 시점. 콜백 직후에 바로 부르지 않는다
```

- API 종류: **Capability**(지원 여부와 범위) / **Get** / **Set** / **Callback**(리스너로 변화 구독)
- Capability 결과의 `AccessType`(`NONE`/`READ`/`WRITE`/`READ_WRITE`)을 보고 **READ 계열일 때만 조회**합니다. 아니면 UI를 비활성화하거나 대체 흐름으로 갑니다.
- `AreaBounds`(min/max)는 **null일 수 있으니** 반드시 처리합니다.
- Pleos SDK에 없는 특수 프로퍼티가 필요하면 직접 구현하기 전에 **기술 지원팀에 검토를 요청**합니다.

### 퍼미션
- 형식: `<uses-permission android:name="pleos.car.permission.CAR_DOORS"/>`
- `CAR_*`는 조회용, `CONTROL_CAR_*`는 제어용입니다. **호출하는 함수의 `@RequiresPermission`을 확인**합니다.

| 도메인 | 조회 퍼미션 |
|---|---|
| Door | CAR_DOORS |
| Window, Wiper | CAR_WINDOWS |
| HVAC | CAR_CLIMATE |
| DrivingMode, Brake, Odometer | CAR_DRIVE |
| EvBattery, Fuel | CAR_ENERGY |
| CarInfo, Display | CAR_INFO |
| Light, TurnSignal | CAR_LIGHTS |
| SideMirror | CAR_MIRRORS |
| Safety, Tire | CAR_SAFETY |
| Seat | CAR_SEATS |
| Toll | CAR_TOLL |

- 퍼미션이 없으면 `onFailed`로 `VehicleException(ERROR_SECURITY_PERMISSION)`이 옵니다.
- **[필수] 쓰는 도메인의 퍼미션만, 그리고 승인받은 퍼미션만** 선언합니다.

### 자주 쓰는 정보 조회 (FAQ)
- 차량 속도(클러스터와 같은 값): `CarPropertyManager`의 `PERF_VEHICLE_SPEED`. GPS 속도는 클러스터 속도와 다를 수 있습니다.
- EV 배터리 잔량: Vehicle SDK `getEvBatteryStateOfCharge`
- 플랫폼 구분: `Build.DEVICE`가 `CONNECT_S` / `CONNECT_L` / `CONNECT_W` (**실차에서만** 유효, 에뮬레이터에서는 쓸 수 없음)
- 지역 구분: `product.sw.target_region`이 `KOR` / `EUR` / `USA`·`CAN`
- 내비 탑재 여부: 공개되지 않음(partnership@pleos.ai 문의)
- 코덱 지원은 실차마다 다르고 **에뮬레이터 결과는 실차 기준으로 쓰면 안 됩니다.**

### 모바일 API 주의
- 전화(Telephony), SMS, 일부 센서(자이로, 근접), 캘린더, 주소록은 **없거나 제한될 수 있습니다.** 이런 기능은 지원 여부를 확인한 뒤 동작하는 **Optional 구조**로 만듭니다.

### Vehicle 외 SDK 요약 (API Reference 2026-10-27 확인)
전체 함수 목록은 `docs/PLEOS_API_LIST.md`에 있습니다. 퍼미션 접두사는 모두 `pleos.car.permission.`입니다.

| SDK | 좌표 | 퍼미션 | 주요 API |
|---|---|---|---|
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` | `NAVI_ROUTE`: `get*Info()`, `requestTrip()` · `NAVI_ROUTE_SEARCH`: `requestRoute*`, `requestReRoute`, `cancelRoute`, `changeRouteOption`, `add/removeWaypoint` · `NAVI_CUSTOM_MAP`: `requestNavigationScreen` | `initialize` → `addListener(NaviHelperEventListener)` → 조회·경로 요청 → `removeListener` → `release` |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` | `TTS_SERVICE` | `TextToSpeech(Context, Mode)`, `initialize`, `addEventListener`, `setLocale`, `speak`, `stop`, `release` |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` | `STT_SERVICE` | `SpeechToText(Context, Mode)`, `initialize`, `addListener(ResultListener)`, `setLocale`, `request`, `stop`, `release` |
| FusedLocation | `ai.pleos.playground:FusedLocation:1.1.3` | `FUSED_LOCATION` | `isSupported`(먼저) → `initialize` → `getLastKnownLocation` / `register·unregisterFusedLocationCallback` / `add·removeProximityAlert` → `release` |

- **NaviHelper**: 이벤트는 Navigation 앱 구현에 따라 다를 수 있습니다. 리스너는 초기화 후 등록하고, 같은 인스턴스로 `release()` 전에 제거합니다. 내비 탑재 여부 API는 공개되지 않았습니다.
- **TTS/STT 모드**(`Mode`): `HYBRID`(기본, 서버 우선 → 실패하면 On-device), `SERVER`(대체 없음), `ON_DEVICE`(네트워크·등록 불필요). HYBRID·SERVER는 My Project 자격 증명으로 **앱 등록**이 필요합니다. 클라이언트 시크릿은 하드코딩하지 않습니다(§1).
- **TTS**: 서버 모드에서는 `OnServerConnectionListener.onConnected()`가 온 뒤에 `speak()`를 호출합니다. 퍼미션이 없으면 오류 콜백이 `PERMISSION_DENIED`를 줄 수 있습니다. 지원 언어와 오디오 포커스 처리 방식은 **[확인 필요]**.
- **STT는 인식만** 합니다. "음악 틀어줘"를 텍스트로 돌려줄 뿐 실행하지 않습니다. 음성 명령으로 앱 기능을 실행하려면 Gleo Interface를 씁니다.
- **FusedLocation**: `getLastKnownLocation()`은 null일 수 있습니다. 지원하지 않는 환경에서는 위치 기능을 숨깁니다. 위치를 쓰면 제출 시 위치정보 약관이 필요합니다(§13).

### Gleo Interface (음성비서 연동, SDK 아님)
차량 음성비서 Gleo AI가 사용자의 음성 명령을 해석해 앱 기능을 호출하는 규약입니다. function calling과 비슷합니다.
1. **App Schema**: 호출을 허용할 기능을 `assets/schema.json`(이름 고정)에 JSON으로 정의합니다. 최상위 필드는 `packageName`, `appDescription`, `version`, `actionCalls`이고, **`actionCalls`(음성으로 부를 기능)는 최대 3개**입니다. 나머지 기능은 화면 조작으로 씁니다. Gleo 측이 가이드 준수를 검토해 승인하면 자동으로 활성화됩니다. Gleo는 부팅 시와 앱 설치·삭제 시 스키마를 스캔합니다.
2. **Gleo Action 수신**: 기능마다 Manifest `intent-filter`에 action(예: `ADD_REMINDER`)과 `android.intent.category.DEFAULT`를 선언합니다. 스키마의 `component`가 `activity`면 Gleo가 `startActivity`, `service`면 `sendBroadcast`를 호출합니다(BroadcastReceiver로 수신). 파라미터는 extra `"ACTION_PARAM"`에 JSON 문자열로 옵니다.
3. **Callback**: 앱이 action `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`로 broadcast를 보냅니다. extra `packageName`(자기 패키지)과 `response`(JSON)를 담습니다. `response` 필드: `functionName`(필수, 호출된 action 이름), `status`(필수, `success`/`asking`/`fail`), `message`(필수, 짧은 완결 영어 문장), `mode`(선택, `voice` 기본/`beep`/`silent`), `data`(선택이지만 권장, **key-value 객체 배열을 담은 JSON 문자열**).
- 필요한 퍼미션, 지원 언어, 승인 기준은 문서에 없습니다 **[확인 필요]**. 예제 코드의 JSON 이스케이프가 잘못되었을 수 있으니 실제 동작으로 확인합니다.
- Gleo가 말하는 동안 앱은 `LOSS_TRANSIENT`를 받으므로 즉시 멈춥니다(§8).

## 4. 주행 안전 · 주행 규제 (가장 중요)

### 판정
- 플랫폼이 속도, 기어, 브레이크와 지역 규정으로 **Parking / Idling / Driving**을 판정해 `CarUxRestrictions`로 내려줍니다. 앱은 **받은 값을 그대로 반영**하면 됩니다.
- Distraction Optimization(DO)을 **선언하지 않은 앱은 주행 중에 아예 사용할 수 없습니다.**
- DO를 선언한 앱도 `ActiveRestrictions` 비트를 직접 확인해 기능별로 막아야 합니다.

```java
if (r.isRequiresDistractionOptimization()
        && (r.getActiveRestrictions() & CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) != 0) {
    stopPlayingVideo();
}
```
- `CarUxRestrictionsManager.registerListener()`로 등록하고 `onUxRestrictionsChanged()`에서 처리합니다.
- DO를 선언하는 manifest meta-data 형식은 이 문서에 없습니다 **[확인 필요]**. AAOS 표준은 `<meta-data android:name="distractionOptimized" android:value="true"/>`입니다.

### 지역별 주행 중 허용 여부 (Parking에서는 전부 허용)

| 기능 (플래그) | 북미 | 유럽 | 그 외(한국 등) |
|---|---|---|---|
| 키보드 (NO_KEYBOARD) | ⛔ | ✅ | ✅ |
| 다이얼패드 (NO_DIALPAD) | ⛔ | ✅ | ✅ |
| 통합 검색 | 음성만 | ✅ | ✅ |
| 미디어 앱 로그인·설정·검색 (NO_SETUP) | ⛔ | ⛔ | ✅ |
| 내비 검색 | ⛔ | ✅ | ✅ |
| 디지털키·도어·시트·차량 설정 | ⛔ | ⛔ | ⛔ |
| 동영상 (NO_VIDEO) | ⛔ | ⛔ | ⛔ |
| QR 로그인 | ⛔ | ⛔ | ⛔ |
| 매뉴얼·대용량 텍스트 (LIMIT_CONTENT, LIMIT_STRING_LENGTH) | ⛔ | ⛔ | ⛔ |

### 설계 규칙
- **[필수]** 주행 규제 중에는 영상 재생을 시작하거나 재개하지 않습니다. 게임, 웹서핑, 피트니스 같은 활동을 유도하지 않습니다.
- **[필수]** 미디어 앱(동영상·게임·브라우저)은 주행이 시작되면 **재생을 일시정지하고 UI를 숨기며**, 주행 중에는 일시정지를 풀 수 없게 합니다.
- **[필수]** 미디어 앱은 헤드업 알림을 쓸 수 없고, 운전과 무관한 알림도 금지입니다.
- **[필수]** 동영상 앱은 MediaSession과 통합하고, 재생·일시정지·중지를 지원하며, 미디어를 동시에 재생하지 않습니다.
- **[권장]** 큰 터치 영역, 높은 대비, 적은 텍스트, 단순한 메뉴(드롭다운과 다단 메뉴 자제), 자주 쓰는 기능은 첫 화면에 둡니다.
- **[권장]** 텍스트 입력 대신 프리셋, 최근 항목, 음성 입력을 씁니다. 장식용 애니메이션은 넣지 않습니다.
- **[권장]** 터치스크린 조작을 줄이고 음성 명령·물리 버튼 등 차량 입력과 연계합니다. 이미지는 정보 전달에 필요한 만큼만 쓰고, HUD·클러스터·OSD 영역을 쓰면 그 영역 정책을 따릅니다(주행 안전 설계 문서).
- 버튼 크기는 **Pleos Connect UI Design Guide** Basic button의 Sizes 그림을 따릅니다: **Large 100 · Medium 80 · Small 64**(그림 좌표를 1배율로 잰 값, 2026-11-10 확인). 버튼 사이 간격 수치는 웹 가이드에 없습니다 **[확인 필요]**.
- 가이드를 지켜도 심사에서 위험하다고 판단하면 거절될 수 있습니다.

### 테스트
- **에뮬레이터 v2.1.0**: `hmg_car_service` 명령이 없습니다(2026-11-11 확인, `cmd: Can't find service`). VHAL 주입(`cmd car_service inject-vhal-event`)도 user 빌드라 막혀 있습니다.
- 주행 규제는 **Virtual Console**에서 기어를 D로 바꿔 확인합니다(P로 돌리면 해제). 접속: `adb forward tcp:8088 tcp:8088` 후 PC 브라우저에서 `http://127.0.0.1:8088/`.
- Virtual Console → Emulation Controls → Motion → Odometer로 누적 주행거리를 0~1,000,000 km로 바꿀 수 있습니다. 값을 줄이는 것은 반영되지 않았습니다(2026-11-11 실측) [확인 필요].
- 이전 이미지에서 쓰던 명령(참고):
```bash
adb shell cmd hmg_car_service --service drivingstate driving_regulation on
```
- 문서: [Virtual Console 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator), [사용 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)

## 5. 앱 카테고리

Maps/Navigation, Mobility Services, Lifestyle, Entertainment, Business, Music/Audio, Kids, Social/Communication

- 주행 중 일부 기능을 쓸 수 있는 카테고리는 **Music/Audio**(단순 재생 조작, 앨범아트), **Maps/Navigation**(TBT, 간결한 지도), **Mobility Services**(충전소·주유소·주차장 찾기)입니다.
- 나머지 카테고리는 기본적으로 **정차 중 사용**을 전제로 설계합니다.
- 카테고리와 맞지 않는 기능을 넣으면 심사에서 거절될 수 있습니다.

## 6. 화면 구조 · 생명주기

- 화면은 Driving view(고정, 가리거나 종료할 수 없음)와 앱 영역(window 최대 2개)으로 나뉩니다.
- `screenOrientation`을 지정하지 않으면 **portrait(1칸)**, `landscape`로 지정하면 **2칸**을 씁니다. **두 방향을 모두 설계**합니다(portrait에는 핵심 기능, landscape에는 부가 정보).
- 사용자가 window 크기를 바꿀 수 있으므로 **어떤 크기에서도 핵심 기능이 동작**해야 합니다. 레이아웃이 깨지면 안 됩니다.
- 심사 기준(디스플레이 호환성)
  - 레터박스 없이 화면을 채웁니다(한 방향 미세 보정만 허용). **호환성 모드로 실행되면 안 됩니다.**
  - 해상도가 바뀌어도 작업 상태를 유지하고, 화면 상태가 달라도 **같은 기능을 노출**합니다(기능 패리티).
  - 노치, 인셋, 시스템 바, 키보드 같은 안전 영역을 지킵니다.
- 화면에서 사라진 앱은 종료되지 않고 백그라운드에 남습니다. 다시 포그라운드로 올 때를 고려합니다.
  - 포그라운드 진입: 상태를 복원하고 현재 크기에 맞게 UI를 다시 구성합니다.
  - 백그라운드 전환: 화면 갱신, 애니메이션, 센서 수집, 타이머, 폴링을 멈춥니다.
  - **[필수]** 백그라운드에서 돌아오면 **마지막 상태를 복원**합니다(수 시간 이상 미사용이면 초기 상태도 허용). 프로세스가 재생성돼도 복원되게 합니다.
- IME(키보드): EditText가 없는 Dialog는 키보드가 떠도 밀려 올라가면 안 됩니다. `setFitInsetsTypes(statusBars() or navigationBars())`로 IME insets를 제외합니다.
- 화면 사양 수치(px/dp)는 웹 문서에는 없고, 공식 display guide(Figma 파일)의 해상도 표에 있습니다(2026-11-10 확인). 폼팩터 테스트 예시로 12.9" Portrait와 14.6" Landscape가 나옵니다.
  - 해상도 표(행 순서대로 옮김). 화면 크기(inch) 열은 15.6·14.6·12.9·24.6·24.6·18.5 여섯 값만 읽혀 행과 짝짓기는 **[확인 필요]**.

    | DPI | 전체 해상도 | 앱 영역 Landscape(dp) | 앱 영역 Portrait(dp) |
    |---|---|---|---|
    | 160 | 2560 x 1440 | 1700 x 1184 | 840 x 1184 |
    | 160 | 2172 x 1222 | 1408 x 1018 | 692 x 1018 |
    | 200 | 2048 x 1152 | 1360 x 948 | 672 x 948 |
    | 160 | 1920 x 1080 | 1274 x 855 | 628 x 855 |
    | 160 | 3840 x 850 | 1640 x 810 | 800 x 810 |
    | 160 | 3840 x 1440 | 1640 x 1400 | 800 x 1400 |
    | 200 | 3072 x 816 | 1256 x 800 | 648 x 800 |

  - 가장 좁은 앱 영역은 628dp 폭, 가장 낮은 높이는 800dp입니다. 앱 레이아웃은 이 크기에서 잘리지 않아야 합니다.
  - 버튼 높이는 §4(Basic button Sizes). 간격 수치는 Figma 파일에서 글자로 뽑히지 않아 **[확인 필요]**.

## 7. CCP(로터리 컨트롤러) · Back 키

- 사용자가 설치하는 앱은 CCP 지원이 **선택**입니다. 단, **필수 조작은 터치만으로 끝낼 수 있어야** 합니다. 기본 탑재 앱은 필수입니다.
- 지원하지 않으면 `<application>`에 다음을 넣습니다.
  ```xml
  <meta-data android:name="com.hmg.rotary.ROTARY_SUPPORTED" android:value="false"/>
  ```
- **[필수] Back 하드키**: 최상위 화면에서는 `moveTaskToBack(true)`로 숨깁니다. `finish()`나 `finishAffinity()`는 **쓰지 않습니다.** 하위 화면이나 팝업이 있으면 일반 Back 동작을 합니다. 숨긴 뒤에도 미디어 재생 등은 계속됩니다. 이를 어기면 반려됩니다.

## 8. 오디오

- **[필수]** 재생 전에 `AudioFocusRequest`로 포커스를 요청하고, 다음 이벤트를 모두 처리합니다.
  - `GAIN`: 재생 / `LOSS`: 즉시 정지 / `LOSS_TRANSIENT`: 일시정지 / `LOSS_TRANSIENT_CAN_DUCK`: 볼륨 낮추기
- 내비 안내처럼 동시 재생을 기대하는 요청은 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`을 씁니다.
- 음성비서(Gleo) 때문에 `LOSS_TRANSIENT`를 받으면 **즉시 멈춥니다.**
- duck이 풀리면 원래 볼륨으로 되돌립니다.
- **[필수] Suspend 진입 시** 재생 중단, **포커스 반납(abandon)**, 상태 저장, 플레이어 해제 순서로 처리합니다. **Resume 후 자동으로 포커스를 다시 잡거나 자동 재생하지 않습니다**(기본 `shouldAutoResume=false`).
- TTS나 음성 안내는 앞부분 음절이 잘리지 않도록 **앞에 묵음(pre-roll silence)** 을 넣습니다(별도 가이드 참고).
- 주요 컨텍스트 관계: 통화 중 음악 요청은 거부, 통화 중 내비는 공존, 음악 재생 중 통화가 오면 통화가 선점, EMERGENCY 재생 중에는 대부분 거부.

## 9. 성능 기준 (심사 항목)

| 항목 | 기준 |
|---|---|
| RAM | 400MB 이하 |
| CPU (주행 중) | 내비 앱 15% 이하, 그 외 5% 미만 |
| CPU (정차 중) | 25% 이하 |
| 앱 실행 / 콘텐츠 로드 | 10초 이내 |
| 버튼 반응 | 2초 이내(넘으면 로딩 UI 표시) |
| 터치 후 반응 애니메이션 | 0.25초 이내 |

- Cold start 때 초기화를 최소화하고 lazy loading을 씁니다. 리스트는 RecyclerView와 캐시를 씁니다.
- 스터터링이나 프레임 드랍이 있으면 반려됩니다. 기기 재시작이나 무관한 시스템 설정 변경을 요구해서도 안 됩니다.

## 10. 네트워크

- 주행 중에는 연결이 끊기거나 대역폭이 떨어진다는 것을 전제로 합니다. **로컬 캐시, 재시도, 타임아웃, 오프라인 안내 UI**를 갖춥니다.
- HTTPS와 **TLS 1.2 이상**(1.3 권장)을 씁니다. 서버 IP를 하드코딩하지 않고 도메인으로 관리합니다.
- 토큰과 세션은 URL 쿼리에 넣지 않고 **Authorization 헤더**로 보냅니다.

## 11. 보안 · 개인정보

- 차량은 **여러 사람이 함께 쓰는 기기**입니다. 로그아웃과 계정 전환이 쉬워야 합니다.
- 토큰과 키는 **Android Keystore**에 둡니다. 민감정보는 앱 수준에서 암호화하고 내부 저장소에 둡니다. 캐시와 백업에서는 제외합니다.
- 암호화는 AES-GCM과 SHA-256을 씁니다(ECB, MD5, SHA-1 금지). 키를 하드코딩하지 않습니다.
- 로그에 개인정보가 들어가면 마스킹합니다. 릴리스 빌드에서는 디버그 로그를 끕니다.
- 외부에서 호출할 일이 없는 컴포넌트는 `exported=false`로 둡니다. 암시적 브로드캐스트로 민감정보를 보내지 않습니다.
- **[필수] 릴리스 빌드**: `debuggable=false`, WebView 원격 디버깅 끄기, **정식 배포 키로 서명**.
- 개인정보
  - 위치, 음성, 계정 정보를 요청할 때는 이유를 밝히고 최소한만 수집합니다.
  - 동의 버튼 외의 행동(뒤로가기, 빈 곳 터치)을 **동의로 간주하면 안 됩니다.**
  - 사용자가 동의를 철회하면 즉시 수집을 멈추고 저장된 데이터를 삭제합니다.
  - 차량 ID와 사용자 ID는 익명화하고, 운전 행동 데이터는 마케팅에 쓰지 않습니다.
  - 아동 정보는 법정대리인 동의가 필요합니다. 위치정보를 쓰면 위치정보 약관이 필수입니다(국가별 신고 확인).

## 12. 테스트

- **Virtual Console**: 도메인별 차량 상태와 이벤트(기어 등)를 설정해 앱 반응을 검증합니다.
- **폼팩터 테스트**: 가상 디스플레이(Portrait/Landscape)로 레이아웃을 확인합니다.
  - 에뮬레이터는 **별도 창**으로 실행합니다(Running Devices 내장이나 Headless 모드는 안 됨).
  - Go 1.22 이상, 포트 8095와 8096이 필요합니다. 끝나면 `adb forward --remove tcp:8095`를 실행합니다.
  - 가상 디스플레이를 제거하면 그 위의 앱이 강제 종료됩니다.
- 체크: 잘림이나 겹침, 정렬, 가로/세로 배치, 터치와 전환.
- 주행 규제 on/off(§4), 오디오 포커스 필수 시나리오 5개(§8), Back 키 숨김(§7), 백그라운드 복귀 시 상태 복원(§6)을 확인합니다.
- 자주 쓰는 adb 명령
  ```bash
  adb install -d -r app-debug.apk
  ```
  ```bash
  adb shell monkey -p <패키지> -c android.intent.category.LAUNCHER 1
  ```
  ```bash
  adb exec-out screencap -p > screen.png
  ```

## 13. 등록 · 심사 제출

- **Pleos App Market Console**에서 제출합니다.
- 필수 자료
  - **APK**(최대 600MB). AAB는 언급이 없습니다.
  - 프로젝트 ID(권한 신청이 필요한 SDK를 쓸 때)
  - 아이콘 512×512 PNG/JPG(1MB 이하)
  - IVI 스크린샷 최대 8장(장당 8MB 이하, 권장 2560×1380, 1700×1212 등)
  - IARC 연령 등급, 국가·언어별 개인정보처리방침 URL, (위치를 쓰면) 위치정보 약관, 고객지원 연락처 1개 이상
- 모든 기능을 확인할 수 있는 **테스트 계정을 제공**하고, 필요하면 심사 노트와 사용 설명서를 첨부합니다.
- **[필수] 제출할 때마다 버전을 올립니다**(버전이 같으면 심사가 진행되지 않거나 반려).
- 테스트용 더미 데이터나 임시 콘텐츠가 있으면 안 됩니다. 실제 서비스와 같아야 합니다.
- 앱 이름이 외국어라면 **원문 영문 앱명을 태그로 반드시 추가**합니다(축약이나 변형 금지).
- 게임은 국가별 심의가 필요합니다(한국은 게임물관리위원회).
- 등록 후에도 모니터링과 재심사가 있고, 품질이 미달하면 마켓에서 내려갈 수 있습니다.
- targetSdk, 서명 방식, 심사 소요 기간은 문서에 없습니다 **[확인 필요]**.
- 서명(2026-11-10 확인): 제출 파일은 **APK**(최대 600 MB, 앱 심사 제출 문서). 앱 보안 가이드는 "테스트·디버그용 인증서 대신 정식 배포용 서명 인증서"를 요구합니다. 키 발급 주체·키 길이·v2/v3 요건은 문서에 없어 개발자가 직접 만든 키를 쓰는 것으로 봅니다 **[확인 필요]**. 같은 키로만 업데이트할 수 있으므로 키 파일과 비밀번호를 잃지 않게 저장소 밖에 백업합니다.

## 14. 공식 문서에 없어서 직접 확인해야 하는 것

- [x] 화면 해상도와 dp — §6 해상도 표(display guide). 플랫폼(Connect-S/L/W)과 행의 짝은 확인 필요
- [x] UI Design Guide의 버튼 높이(§4) — 간격 수치는 아직 확인 필요
- [ ] DO 선언 meta-data의 정확한 형식(AAOS 표준과 같은지)
- [ ] Google Play Services(지도, FCM, 로그인) 사용 가능 여부
- [ ] targetSdk 요건, 서명(v2/v3) 요건, 심사 기간
- [x] NaviHelper, TTS, STT, FusedLocation의 Maven 좌표 → §2, §3 (2026-10-27 확인). "준비 중" SDK를 실제로 받을 수 있는지는 미확인
- [ ] TTS/STT 지원 언어, Gleo Interface 퍼미션과 승인 기준
- [ ] 함수별 `@RequiresPermission` (API Reference 상세 페이지)
- [ ] `CONTROL_CAR_*` 제어 퍼미션 전체 목록과 승인 절차
- [ ] Vehicle SDK 오류 코드 목록(`/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code`)

## 15. 참고 링크 (모두 https://document.pleos.ai 기준)

| 주제 | 경로 |
|---|---|
| 시작하기 | /docs/pleos-only/getting-started |
| 프로젝트·SDK 신청 | /docs/pleos-only/create-my-project |
| 에뮬레이터 설치 | /docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator |
| 기획·설계 가이드 | /docs/pleos-only/vehicle-app-planning-guide/intro |
| 주행 안전 설계(주의 분산 방지 UX) | /docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline |
| 주행 규제 정책(지역별) | /docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy |
| UI 디자인 가이드 Basic button(크기) | /docs/connect/guide/docs-design/components/actions/basic-button |
| 개발·연동 가이드 | /docs/pleos-only/vehicle-app-developer-guide/intro |
| 주행 규제 개발 | /docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide |
| 오디오 포커스 | /docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development |
| CCP | /docs/pleos-only/vehicle-app-developer-guide/ccp-rotary-controller-development |
| ADB 명령 | /docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide |
| SDK 개요 | /docs/pleos-only/connect-sdk-pleos/ |
| Vehicle SDK | /docs/pleos-only/connect-sdk-pleos/Vehicle/intro |
| Vehicle 퍼미션 | /docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission |
| Capability API | /docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability |
| Vehicle API Reference | /docs/pleos-only/connect-sdk-pleos/Vehicle/entry |
| NaviHelper (퍼미션) | /docs/pleos-only/connect-sdk-pleos/NaviHelper/intro (/declaring-permissions) |
| TextToSpeech | /docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro |
| SpeechToText | /docs/pleos-only/connect-sdk-pleos/SpeechToText/intro |
| FusedLocation | /docs/pleos-only/connect-sdk-pleos/FusedLocation/intro |
| Gleo Interface | /docs/pleos-only/app-integration-guide/guide/core-concepts |
| 샘플 앱 | /docs/pleos-only/tutorial/vehicle-sdk-sample-app |
| Virtual Console | /docs/pleos-only/app-test-guide/virtual-console-guide |
| 폼팩터 테스트 | /docs/pleos-only/app-test-guide/form-factor-test-guide |
| 앱 보안 가이드 | /docs/pleos-only/app-registration-guide/app-security-guide |
| 심사 가이드(기능·성능) | /docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability |
| 심사 가이드(주의 분산) | /docs/pleos-only/distribute/app-review-guide/review-driver-distraction-elements |
| 심사 가이드(개인정보) | /docs/pleos-only/distribute/app-review-guide/review-user-privacy-security |
| 제출 가이드 | /docs/pleos-only/distribute/app-submission-guide |
| 릴리스 노트(SDK) | /docs/pleos-only/release-note/sdk-261015 |
| UI 디자인 가이드 | /docs/connect/guide/docs-design/Introduction/intro |
| Workspace | https://pleos.ai/playground/console/projects |
| 문의 | partnership@pleos.ai |
