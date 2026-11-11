# Chapter 2: Vehicle SDK · 퍼미션 · 차량 정보 조회

## Core Idea
차량 데이터는 **표준이면 AAOS Car API, 특화면 Pleos SDK**로 다룹니다. 어떤 도메인이든 **Capability 확인 → 비동기 Get/Set → 콜백 완료 후 release** 순서를 지킵니다.

## Frameworks Introduced
- **AAOS Alignment (표준 우선)**
  - When to use: 어떤 차량 값을 읽을지 고를 때 씁니다.
  - How: 속도, 기어, 외기온, 연료 같은 표준 데이터는 `CarPropertyManager`(AAOS)로 읽습니다. 시트 제어나 특화 라이팅처럼 제조사 고유 기능만 Pleos SDK로 다룹니다. SDK 기능이 AAOS로 편입되어도 **최소 2개 메이저 버전**까지 하위 호환됩니다.
- **Capability-first 호출**
  - When to use: 모든 Vehicle 도메인을 처음 쓸 때입니다.
  - How: ① `Vehicle(context)` 후 `initialize()`를 합니다. ② `getXxx()`로 도메인 객체를 얻습니다(직접 생성 금지). ③ `checkXxxCapabilities`를 호출합니다. ④ `AccessType`이 `READ`나 `READ_WRITE`일 때만 조회하고, 그 외에는 UI를 비활성화하거나 대체 흐름으로 갑니다. ⑤ `AreaBounds`(min/max)의 null을 처리합니다. ⑥ Get/Set 결과는 `onComplete`, 오류는 `onFailed`로 받습니다. ⑦ 생명주기가 끝나면 `release()`를 호출합니다.
- **퍼미션 쌍**: 조회는 `CAR_*`, 제어는 `CONTROL_CAR_*`
  - How: 쓰는 도메인 것만 `pleos.car.permission.<상수>`로 선언합니다. 함수마다 `@RequiresPermission`을 확인합니다. **App Market이 승인한 퍼미션만** 선언할 수 있습니다.

## Key Concepts
- **Vehicle Service**: AAOS 위에서 도는 차량 서비스입니다. SDK는 IPC로 붙습니다. 그래서 모든 결과가 비동기입니다.
- **도메인 19개**: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer, Safety, Seat, SideMirror, Steeringwheel, Tire, Toll, TurnSignal, Window, Wiper
- **API 4유형**: Capability / Get / Set / Callback(리스너 구독)
- **AccessType**: `NONE`, `READ`, `WRITE`, `READ_WRITE`
- **VehicleException**: `onFailed`로 전달됩니다. 퍼미션 누락은 `ERROR_SECURITY_PERMISSION`입니다.

## Reference Tables

| 도메인 | 조회 퍼미션(`pleos.car.permission.`) |
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

| SDK | 좌표(`ai.pleos.playground:`) | 퍼미션(`pleos.car.permission.`) | 차량 호환성 확인 |
|---|---|---|---|
| Vehicle | `Vehicle:2.3.2` | 위 도메인 표 | 도메인별 `checkXxxCapabilities` |
| NaviHelper | `NaviHelper:3.0.5` | `NAVI_ROUTE`(`get*Info`, `requestTrip`) · `NAVI_ROUTE_SEARCH`(경로·경유지) · `NAVI_CUSTOM_MAP`(`requestNavigationScreen`) | 내비가 탑재된 차량에서만 동작(탑재 여부 API는 비공개) |
| TextToSpeech | `TextToSpeech:3.3.1` | `TTS_SERVICE` | 전 차량 |
| SpeechToText | `SpeechToText:3.3.1` | `STT_SERVICE` | 전 차량 |
| FusedLocation | `FusedLocation:1.1.3` | `FUSED_LOCATION` | `isSupported` |

- 전체 함수 목록과 개수: 저장소 `docs/PLEOS_API_LIST.md`. Vehicle은 도메인 19개에 함수 458개입니다.
- **TTS/STT `Mode`**: `HYBRID`(기본, 서버 → 실패 시 On-device), `SERVER`, `ON_DEVICE`. HYBRID·SERVER는 My Project 자격 증명으로 앱 등록이 필요합니다. TTS 서버 모드는 `onConnected()` 이후에 `speak()`를 부릅니다.
- **STT는 인식만** 합니다. 결과 텍스트로 앱 기능을 실행하는 것은 앱의 몫이고, 음성비서 명령으로 앱을 부르게 하려면 Gleo Interface를 씁니다.
- **NaviHelper**: `initialize` → `addListener(NaviHelperEventListener)` → 조회·경로 요청 → `removeListener`(같은 인스턴스) → `release`. 이벤트는 내비 앱 구현에 따라 다를 수 있습니다.
- **FusedLocation**: `isSupported`를 먼저 부르고, 지원하지 않으면 위치 기능을 숨깁니다. `getLastKnownLocation()`은 null일 수 있습니다.

### Gleo Interface (음성비서 연동, SDK 아님)
| 단계 | 방법 |
|---|---|
| 기능 등록 | `assets/schema.json`(이름 고정)에 App Schema(JSON, function calling 형식: `packageName`, `appDescription`, `version`, `actionCalls`)를 작성합니다. **`actionCalls`는 최대 3개**입니다. Gleo 측 검토 후 승인되면 자동으로 활성화됩니다. |
| 호출 수신 | 기능마다 Manifest `intent-filter`에 action + `android.intent.category.DEFAULT`를 선언합니다. `component`가 `activity`면 `startActivity`, `service`면 `sendBroadcast`로 옵니다. 파라미터는 extra `"ACTION_PARAM"`(JSON 문자열)입니다. |
| 결과 회신 | action `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` broadcast에 extra `packageName`과 `response`(JSON)를 담아 보냅니다. 필수 `functionName`, `status`(`success`/`asking`/`fail`), `message`(짧은 영어 문장) · 선택 `mode`(`voice`/`beep`/`silent`), `data`(key-value 객체 배열을 담은 **JSON 문자열**). |

- 퍼미션, 지원 언어, 승인 기준은 문서에 없습니다(확인 필요).

| 알고 싶은 것 | 방법 |
|---|---|
| 클러스터와 같은 속도 | `CarPropertyManager` · `PERF_VEHICLE_SPEED` (GPS 속도는 다를 수 있음) |
| EV 배터리 잔량 | `getEvBatteryStateOfCharge` |
| 플랫폼 | `Build.DEVICE`가 `CONNECT_S/L/W` (**실차에서만** 유효) |
| 지역 | `product.sw.target_region`이 `KOR`/`EUR`/`USA`·`CAN` |

## Code Examples
```kotlin
// 문서 흐름을 재구성한 스케치입니다. 정확한 시그니처는 SDK API 문서로 확인합니다.
val vehicle = Vehicle(context).also { it.initialize() }
val door = vehicle.getDoor()

door.checkDoorPositionCapabilities(          // 이름은 checkXxxCapabilities 형태
    onComplete = { config, areaMap ->
        if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
            val bounds = areaMap[DoorArea(DoorValue.ROW_1_LEFT)] ?: return@checkDoorPositionCapabilities
            door.getCurrentDoorPosition(DoorArea(DoorValue.ROW_1_LEFT),
                onComplete = { pos -> render(pos, bounds.minValue, bounds.maxValue) },
                onFailed = { e -> handle(e) })
        } else {
            disableDoorUi()                      // NONE·WRITE 단독: 대체 흐름
        }
    },
    onFailed = { e -> handle(e) }
)

fun handle(e: Exception) {
    if (e is VehicleException) log("vehicle error ${e.errorCode}")   // 퍼미션 누락이면 ERROR_SECURITY_PERMISSION
}

override fun onDestroy() { vehicle.release(); super.onDestroy() }   // 콜백 직후가 아니라 생명주기 끝에서
```
```xml
<uses-permission android:name="pleos.car.permission.CAR_DOORS"/>
```

## Worked Example
"EV 충전량 위젯"을 만든다고 해 봅니다.
1. 잔량은 Pleos 특화 API인 `getEvBatteryStateOfCharge`를 씁니다. 속도처럼 표준 프로퍼티가 있는 값은 AAOS를 우선합니다.
2. 퍼미션은 `CAR_ENERGY` 하나만 선언하고, 승인을 받았는지 확인합니다.
3. EvBattery capability를 먼저 확인합니다. 내연기관 차량에서는 지원하지 않을 수 있으므로 위젯을 숨깁니다.
4. 값이 바뀌는 것은 Callback 유형의 리스너로 구독합니다. 폴링하지 않습니다(ch06의 CPU 한도).
5. 차종에 따라 분기할 때 **모델명이나 `Build.DEVICE`로 판단하지 않습니다.** capability 결과만 봅니다.

## Anti-patterns
- **모델명으로 기능 분기**: 문서에서 금지합니다. capability와 `CarPropertyManager`로 실제 지원 여부를 확인합니다.
- **콜백 안에서 바로 `release()`**: 다른 비동기 호출이 끊깁니다.
- **도메인 객체 직접 생성**: `getXxx()`로만 얻습니다.
- **필요 이상의 퍼미션 선언**: 승인되지 않은 퍼미션이 있으면 심사에서 바로 반려됩니다.
- **모바일 API를 그대로 이식**: Telephony, SMS, 자이로나 근접 센서, 캘린더, 주소록은 없거나 제한될 수 있습니다. 지원 여부를 확인한 뒤 동작하는 Optional 구조로 만듭니다.
- **에뮬레이터 코덱 결과를 믿음**: 실차와 다를 수 있습니다.

## Key Takeaways
1. 표준 데이터는 AAOS, 특화 기능은 Pleos SDK입니다.
2. capability를 먼저 확인하고 AccessType과 AreaBounds의 null을 처리합니다.
3. 모든 결과는 비동기입니다. `release()`는 생명주기 끝에서 호출합니다.
4. 퍼미션은 도메인 단위로 최소한만, 그리고 승인받은 것만 선언합니다.
5. SDK에 없는 특수 프로퍼티는 직접 구현하기 전에 기술 지원팀 검토를 받습니다.

## Connects To
- **ch01**: SDK 버전과 Breaking change
- **ch06**: 리스너 대신 폴링을 하면 CPU 한도를 넘음
- **ch07**: 퍼미션 승인 여부가 심사 항목
