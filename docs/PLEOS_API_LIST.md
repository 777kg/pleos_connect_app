# Pleos Connect 제공 API 목록

**출처**: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/ 의 API Reference·Programming Guide (2026-10-27 조회)
**주의**
- 공식 문서는 로그인 없이 웹으로 볼 수 있고, 다운로드용 PDF·zip·javadoc은 제공하지 않는다.
- API Reference 목록 페이지에는 **함수 이름만** 있다. 아래 한국어 설명은 이름에서 추정한 것이다. 정확한 시그니처·`@RequiresPermission`·값 범위는 함수별 상세 페이지에서 확인해야 한다 [확인 필요].
- 개수는 목록 페이지 기준이다. 상세 페이지를 열면 오버로드가 더 있을 수 있다.

## 1. 한눈에 보기

| SDK | Gradle 좌표 | 퍼미션 | 공개 함수 수 |
|---|---|---|---|
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` | 도메인별 `CAR_*` / `CONTROL_CAR_*` | `Vehicle` 클래스 22 + 도메인 19개 458 = **약 480** |
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` | `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP` | 함수 21 + 리스너 콜백 25 |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` | `TTS_SERVICE` | 생성자 + 함수 7 |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` | `STT_SERVICE` | 생성자 + 함수 7 |
| FusedLocation | `ai.pleos.playground:FusedLocation:1.1.3` | `FUSED_LOCATION` | 함수 9 |
| Gleo Interface | SDK 없음(JSON 스키마 + Intent/Broadcast) | 문서에 없음 [확인 필요] | 앱이 정의한 함수 |

- 저장소는 모두 `https://nexus-playground.pleos.ai/repository/maven-releases/`이다.
- 퍼미션 접두사는 모두 `pleos.car.permission.`이다.
- 현재 `PleosApp`에는 Vehicle 2.3.2만 들어 있다.

## 2. Vehicle SDK

### 2.1 Vehicle 클래스 (22)
생성자, `initialize`, `release`, 그리고 도메인 객체를 얻는 함수 19개: `getBrake`, `getCarInfo`, `getDisplay`, `getDoor`, `getDrivingMode`, `getEvBattery`, `getFuel`, `getHVAC`, `getLight`, `getOdometer`, `getSafety`, `getSeat`, `getSideMirror`, `getSteeringWheel`, `getTire`, `getToll`, `getTurnSignal`, `getWindow`, `getWiper`

- 문서 목록에는 `getGear()`가 없다. AAR에는 있다는 기존 기록이 있다(README). 기어는 `CarPropertyManager`를 우선한다.

### 2.2 도메인별 개수

C = capability 확인, G = 조회, S = 제어(set·move·select), L = 리스너 등록+해제

| 도메인 | 조회 퍼미션 | C | G | S | L | 합계 |
|---|---|---|---|---|---|---|
| Brake | CAR_DRIVE | 1 | 1 | 0 | 2 | 4 |
| CarInfo | CAR_INFO | 2 | 2 | 0 | 0 | 4 |
| Display | CAR_INFO | 1 | 1 | 0 | 2 | 4 |
| Door | CAR_DOORS | 3 | 3 | 3 | 6 | 15 |
| DrivingMode | CAR_DRIVE | 3 | 3 | 0 | 6 | 12 |
| EvBattery | CAR_ENERGY | 9 | 7 | 4 | 10 | 30 |
| Fuel | CAR_ENERGY | 5 | 5 | 0 | 10 | 20 |
| HVAC | CAR_CLIMATE | 14 | 14 | 13 | 26 | 67 |
| Light | CAR_LIGHTS | 19 | 19 | 11 | 22 | 71 |
| Odometer | CAR_DRIVE | 10 | 8 | 1 | 16 | 35 |
| Safety | CAR_SAFETY | 10 | 9 | 0 | 18 | 37 |
| Seat | CAR_SEATS | 38 | 15 | 14 | 30 | 97 |
| SideMirror | CAR_MIRRORS | 1 | 1 | 1 | 2 | 5 |
| SteeringWheel | 문서 표에 없음 [확인 필요] | 2 | 2 | 1 | 4 | 9 |
| Tire | CAR_SAFETY | 2 | 1 | 0 | 0 | 3 |
| Toll | CAR_TOLL | 1 | 1 | 1 | 2 | 5 |
| TurnSignal | CAR_LIGHTS | 1 | 1 | 0 | 2 | 4 |
| Window | CAR_WINDOWS | 7 | 6 | 6 | 10 | 29 |
| Wiper | CAR_WINDOWS | 2 | 2 | 1 | 2 | 7 |
| **합계** | | **131** | **101** | **56** | **170** | **458** |

- 조회 전용(제어 없음) 도메인: Brake, CarInfo, Display, DrivingMode, Fuel, Safety, Tire, TurnSignal
- 제어(S)는 `CONTROL_CAR_*` 퍼미션이 필요하다. 정확한 상수 이름과 App Market 승인 가능 여부는 문서에 없다 [확인 필요].
- 주행 중에는 차량 설정(도어·시트 등)을 금지한다(ch03). 제어 기능은 정차 중에만 쓰는 것을 전제로 한다.

### 2.3 도메인별 항목

함수 이름 규칙: `check<X>Capabilities` → `get<X>` 또는 `getCurrent<X>` → `set<X>` → `register<X>` / `unregister<X>`
표에는 항목(X)만 적는다. ✓는 해당 유형의 함수가 있다는 뜻이다.

#### Brake (4)
| 항목 | G | S | L |
|---|---|---|---|
| BrakePedalStatus (브레이크 페달 상태) | ✓ | | ✓ |

#### CarInfo (4)
| 항목 | G | S | L |
|---|---|---|---|
| VINInfo (차대번호) | ✓ | | |
| CRNInfo (의미 [확인 필요]) | ✓ | | |

#### Display (4)
| 항목 | G | S | L |
|---|---|---|---|
| TemperatureDisplayUnit (온도 표시 단위) | ✓ | | ✓ |

#### Door (15)
| 항목 | G | S | L |
|---|---|---|---|
| DoorPosition (도어 열림 위치) | ✓ | ✓ | ✓ |
| DoorLock (도어 잠금) | ✓ | ✓ | ✓ |
| GloveBoxLock (글로브박스 잠금) | ✓ | ✓ | ✓ |

#### DrivingMode (12)
| 항목 | G | S | L |
|---|---|---|---|
| DrivingModeState (주행 모드) | ✓ | | ✓ |
| CruiseControlState (크루즈 상태) | ✓ | | ✓ |
| CruiseControlTargetSpeed (크루즈 목표 속도) | ✓ | | ✓ |

#### EvBattery (30)
| 항목 | G | S | L |
|---|---|---|---|
| EvBatteryStateOfCharge (배터리 잔량 SOC) | ✓ | | ✓ |
| CurrentEvEfficiency (현재 전비) | ✓ | | ✓ |
| CumulativeEvEfficiency (누적 전비) | ✓ | | ✓ |
| InstantaneousEvEfficiency (순간 전비) | ✓ | | ✓ |
| SinceChargeEvEfficiency (충전 후 전비) | ✓ | | ✓ |
| EvChargeCurrentDrawLimit (충전 전류 제한) | ✓ | ✓ | |
| EvChargePercentLimit (충전 목표 %) | | ✓ | |
| EvChargePortOpen (충전구 열기) | | ✓ | |
| EvChargeSwitch (충전 on/off) | ✓ | ✓ | |

#### Fuel (20)
| 항목 | G | S | L |
|---|---|---|---|
| FuelPercent (연료 잔량 %) | ✓ | | ✓ |
| CurrentFuelEconomy (현재 연비) | ✓ | | ✓ |
| CumulativeFuelEconomy (누적 연비) | ✓ | | ✓ |
| InstantaneousFuelEconomy (순간 연비) | ✓ | | ✓ |
| SinceChargeFuelEconomy (주유 후 연비) | ✓ | | ✓ |

#### HVAC (67)
| 항목 | G | S | L |
|---|---|---|---|
| HvacPower (공조 전원) | ✓ | ✓ | ✓ |
| HvacTemperature (설정 온도) | ✓ | ✓ | ✓ |
| HvacTemperatureCurrent (실내 온도) | ✓ | | ✓ |
| HvacFanSpeed (풍량) | ✓ | ✓ | ✓ |
| HvacFanDirection (풍향) | ✓ | ✓ | |
| HvacAc / HvacMaxAc (에어컨 / 최대 냉방) | ✓ | ✓ | ✓ |
| HvacAuto (자동) | ✓ | ✓ | ✓ |
| HvacDual (좌우 독립) | ✓ | ✓ | ✓ |
| HvacRecirculation / HvacAutoRecirculation (내기순환 / 자동 내기) | ✓ | ✓ | ✓ |
| HvacDefroster / HvacMaxDefrost (성에 제거 / 최대) | ✓ | ✓ | ✓ |
| HvacAirCleaner (공기청정) | ✓ | ✓ | ✓ |

- 2026-10 Breaking change: `FanDirectionType`의 순서가 바뀌었다. ordinal로 저장하지 않는다.

#### Light (71)
| 항목 | G | S | L |
|---|---|---|---|
| LowBeam / HighBeam (하향등 / 상향등) | 상태·스위치 | 스위치 | 상태 |
| FrontFog / RearFog (전·후 안개등) | 상태·스위치 | 스위치 | 상태 |
| TailLamp (후미등) | 상태·스위치 | 스위치 | 상태 |
| Cabin / Reading (실내등 / 독서등) | 상태·스위치 | 스위치 | 상태 |
| AmbientLight (앰비언트 상태·스위치) | ✓ | 스위치 | 상태 |
| AmbientLightBrightness (앰비언트 밝기) | ✓ | ✓ | ✓ |
| AmbientLightBrightnessColor (앰비언트 밝기+색상) | ✓ | ✓ | ✓ |
| AmbientColorAutoByDriveModeEnabled (주행모드 연동 색상) | ✓ | ✓ | ✓ |

#### Odometer (35)
| 항목 | G | S | L |
|---|---|---|---|
| DistanceDriven (총 주행거리) | ✓ | | ✓ |
| TripCurrent / TripCumulative DrivingDistance·Time (트립 거리·시간) | ✓ | | ✓ |
| TripInstantaneousDrivingDistance (순간 트립 거리) | ✓ | | ✓ |
| TripSinceRefill DrivingDistance·Time (주유 후 거리·시간) | ✓ | | ✓ |
| TripDrivingDistance / TripDrivingTime | C만 있음 | | |
| RangeRemainingDistance (주행 가능 거리) | | ✓ | |

- `setRangeRemainingDistance`는 있지만 get이 없다. 의미를 확인해야 한다 [확인 필요].

#### Safety (37)
| 항목 | G | S | L |
|---|---|---|---|
| Telltales (경고등 정보) | ✓ | | ✓ |
| EngineOilLevelState / TransmissionOilLevelState / BrakeFluidLevelState (오일·브레이크액) | ✓ | | ✓ |
| LowWasherFluidWarning (워셔액 부족) | ✓ (`has…`) | | ✓ |
| PowerDownWarningIndicator (출력 저하 경고) | ✓ (`has…`) | | ✓ |
| SeatAirbagEnabled / SeatAirbagDeployed (에어백) | ✓ (`has…`) | | ✓ |
| SeatBeltNotBuckledWarning (벨트 미체결 경고) | ✓ (`has…`) | | ✓ |
| SeatBuckled | C만 있음 | | |

- 리스너 이름은 `registerLowWasherFluidWaringState`이다(문서의 철자 그대로).

#### Seat (97)
| 항목 | G | S | L |
|---|---|---|---|
| ForeAft, Height, Tilt, Depth, BackrestAngle (좌석 위치) | Position ✓ | `moveSeat…` | ✓ |
| HeadrestAngle, HeadrestForeAft, HeadrestHeight (헤드레스트) | Position ✓ | `moveSeat…` | ✓ |
| LumbarForeAft, LumbarSideSupport (요추) | Position ✓ | `moveSeat…` | ✓ |
| BeltHeight (벨트 높이) | Position ✓ | `moveSeat…` | ✓ |
| SeatBeltBuckledState (벨트 체결) | ✓ | | ✓ |
| SeatOccupancy (착석 여부) | ✓ | | ✓ |
| HvacSeatTemperature (열선) | ✓ | ✓ | ✓ |
| HvacSeatVentilation (통풍) | ✓ | ✓ | ✓ |
| SeatMemory (메모리 시트) | | `selectSeatMemory` | |

- capability 함수가 많은(38) 이유: 같은 항목에 `check<X>`, `check<X>Position`, `checkMoveSeat<X>` 세 가지가 있다.
- 헤드레스트 높이는 `getCurrentSeatHeadrestHeightAftPosition`이다(문서의 이름 그대로).

#### SideMirror (5)
| 항목 | G | S | L |
|---|---|---|---|
| HvacSideMirrorHeat (미러 열선) | ✓ | ✓ | ✓ |

#### SteeringWheel (9)
| 항목 | G | S | L |
|---|---|---|---|
| SteeringWheelAngle (조향각) | ✓ | | ✓ |
| HvacSteeringWheelHeat (핸들 열선) | ✓ | ✓ | ✓ |

#### Tire (3)
| 항목 | G | S | L |
|---|---|---|---|
| LowTirePressure (저압 경고) | ✓ | | |
| WheelArea | C만 있음 | | |

#### Toll (5)
| 항목 | G | S | L |
|---|---|---|---|
| EtcsVoiceGuidanceVolume (하이패스 안내 음량) | ✓ | ✓ | ✓ |

#### TurnSignal (4)
| 항목 | G | S | L |
|---|---|---|---|
| TurnSignalState (방향지시등) | ✓ | | ✓ |

#### Window (29)
| 항목 | G | S | L |
|---|---|---|---|
| WindowPosition (창문 위치) | ✓ | ✓ | ✓ |
| WindowLock (창문 잠금) | ✓ | ✓ | ✓ |
| WindowBlindPosition (블라인드) | ✓ | `moveWindowBlind` | ✓ |
| SmartGlassState / SmartGlassLevel (스마트 글라스) | ✓ | ✓ | ✓ |
| SmartGlassZoneState (구역별) | | ✓ | |
| SmartGlassConfig | ✓ | | |

#### Wiper (7)
| 항목 | G | S | L |
|---|---|---|---|
| WiperState (와이퍼 동작 상태) | ✓ | | ✓ |
| WiperSwitch (와이퍼 스위치) | ✓ | ✓ | |

## 3. NaviHelper (3.0.5)
- 내비가 탑재된 차량에서만 동작한다. 탑재 여부를 알려 주는 API는 공개되지 않았다.
- 2026-10 Breaking change: 좌표 순서가 (lat, lng)이다.
- 이벤트는 Navigation 앱 구현에 따라 다를 수 있다고 문서에 적혀 있다.

| 분류 | 함수 | 퍼미션 |
|---|---|---|
| 생명주기 | `initialize`, `release`, `addListener`, `removeListener` | |
| 정보 조회 | `getCurrentLocationInfo`, `getDestinationInfo`, `getWaypointInfo`, `getRouteStateInfo`, `getTBTInfo`, `getBookmarkInfo`, `getRecentDestinationInfo`, `getChargerOperatorInfo` | NAVI_ROUTE |
| 트립 | `requestTrip` | NAVI_ROUTE |
| 경로 | `requestRoute`, `requestRouteWithWaypoints`, `requestReRoute`, `cancelRoute`, `changeRouteOption`, `addWaypoint`, `removeWaypoint` | NAVI_ROUTE_SEARCH |
| 화면 | `requestNavigationScreen` | NAVI_CUSTOM_MAP |

**NaviHelperEventListener 콜백 (25)**
- 경로: `onRouteStarted`, `onRoutingCompleted`, `onRouteCancelled`, `onReRouteEnded`, `onRouteOptionChanged`
- 목적지·경유지: `onDestinationChanged`, `onDestinationArrived`, `onWaypointChanged`, `onWayPointArrived`
- 정보: `onBookmarkInfo`, `onBookMarkChanged`, `onRecentDestinationInfo`, `onCurrentLocationInfo`, `onDestinationInfo`, `onWayPointInfo`, `onRouteStateInfo`, `onTBTInfo`, `onChargerOperatorInfo`, `onTripInfo`, `onNavigationStatus`, `onDrivingInfo`, `onCurrentRoadSpeedLimit`, `onCameraAlert`, `onCautionPointInfo`
- 오류: `onError`

## 4. TextToSpeech (3.3.1)
- 클래스: `ai.pleos.playground.tts.TextToSpeech(Context, Mode)`. 모드는 `ai.pleos.playground.tts.constant.Mode`의 `HYBRID`(기본), `SERVER`, `ON_DEVICE`이다.
- 함수: `initialize`, `addEventListener`, `removeEventListener`, `setLocale`, `speak`, `stop`, `release`
- 리스너: `OnServerConnectionListener.onConnected()`. 서버 모드에서는 이 콜백이 온 뒤에 `speak()`를 호출한다.
- 서버 합성(HYBRID·SERVER)을 쓰려면 My Project 자격 증명으로 앱을 등록해야 한다. ON_DEVICE는 등록과 네트워크가 필요 없다.
- 지원 언어 목록과 오디오 포커스 처리 방식은 소개 페이지에 없다 [확인 필요].

## 5. SpeechToText (3.3.1)
- 클래스: `ai.pleos.playground.stt.SpeechToText(Context, Mode)`. 모드는 TTS와 같다.
- 함수: `initialize`, `addListener(ResultListener)`, `removeListener`, `setLocale`, `request`, `stop`, `release`
- 인식만 하고 명령은 실행하지 않는다(예: "음악 틀어줘"라는 텍스트만 돌려준다). 음성 명령으로 앱 기능을 실행하려면 Gleo Interface를 쓴다.
- 앱 등록 조건은 TTS와 같다.

## 6. FusedLocation (1.1.3)
- 클래스: `FusedLocation`. 데이터는 `LocationData`(위도, 경도, 고도, 방향, 신뢰도, 시각)이다.
- 함수: `isSupported`(먼저 호출), `initialize`, `release`, `getLastKnownLocation`(없으면 null), `registerFusedLocationCallback`, `unregisterFusedLocationCallback`, `showLocationAccessBlockedPopup`, `addProximityAlert`, `removeProximityAlert`
- 지원하지 않는 환경에서는 위치 기능을 숨긴다.
- 2026-10 Breaking change: 콜백의 `onError` 구현이 필수다. 소개 페이지에는 나오지 않으므로 Error handling 페이지로 확인한다.

## 7. Gleo Interface (음성 비서 연동)
SDK가 아니라 규약이다. 차량의 Gleo AI가 사용자의 음성 명령을 해석해 앱 기능을 호출한다. function calling과 비슷한 방식이다.
1. **App Schema**: 앱이 호출을 허용할 기능을 `assets/schema.json`에 JSON으로 정의한다. 최상위 필드는 `packageName`, `appDescription`, `version`, `actionCalls`이다. **`actionCalls`는 최대 3개**다. Gleo 측이 검토해 승인하면 자동으로 활성화된다. 부팅 시와 앱 설치·삭제 시 스캔한다.
2. **Gleo Action 수신**: 기능마다 Intent action을 정의한다(예: `ADD_REMINDER`).
   - `component`가 `activity`면 Gleo가 `startActivity`를 호출한다. `service`면 `sendBroadcast`를 호출하므로 BroadcastReceiver로 받는다.
   - 파라미터는 extra `"ACTION_PARAM"`에 JSON 문자열로 들어 있다.
3. **Callback**: 앱이 action `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`로 broadcast를 보낸다.
   - extra `packageName`
   - extra `response`: JSON. 필수는 `functionName`, `status`(`success`/`asking`/`fail`), `message`(짧은 영어 문장)이다. 선택은 `mode`(`voice` 기본/`beep`/`silent`)와 `data`(key-value 객체 배열을 담은 JSON 문자열, 권장)이다.
- 필요한 퍼미션, 지원 언어, 승인 기준은 문서에 없다 [확인 필요]. 샘플의 JSON 이스케이프가 잘못되었을 수 있어 실제로 확인해야 한다.

## 8. 확인 필요 모음
- 함수별 `@RequiresPermission`과 `CONTROL_CAR_*` 상수 전체 목록 (상세 페이지)
- SteeringWheel 조회 퍼미션
- CarInfo `CRNInfo`의 의미, Odometer `setRangeRemainingDistance`의 용도
- TTS·STT 지원 언어, TTS 오디오 포커스 처리
- Gleo Interface의 퍼미션·승인 기준
- 이 목록은 2026-10-27 기준이다. 버전이 바뀌면 릴리스 노트로 다시 확인한다.
