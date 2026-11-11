# Gleo 음성 기능 — 내 차량 소모품

> 기준: 커밋 f503dbf(2026-11-10). 창의성 아이디어 ⑦ "말로 묻고, 화면은 정차 때".
> 근거: Pleos [Gleo Interface](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts) · [App Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability) · [Callback Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback) · [Code Examples](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples) · [Delivering the Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema)

## 한눈에 보기

Gleo(차량 음성비서)가 사용자의 말을 해석해 앱 기능을 부릅니다. 앱은 `assets/schema.json`에 **음성으로 부를 수 있는 기능 3개**(최대 3개)를 등록했습니다.

| 기능(function) | 이렇게 말하면(예) | 앱이 하는 일 | 방식 |
|---|---|---|---|
| `GET_CONSUMABLE_STATUS` | "타이어 얼마나 탔어?" | 한 항목의 교체 후 달린 거리와, 내가 정한 교체 거리 대비 남은 비율을 음성으로 답함 | service(화면 없음) |
| `LIST_CONSUMABLES_BY_REMAINING` | "교체 거리 얼마 안 남은 거 뭐야?" | 내가 정한 거리 대비 남은 비율이 적은 항목 최대 3개를 답함 | service(화면 없음) |
| `OPEN_CONSUMABLE_RECORD` | "와이퍼 기록 열어 줘" | 앱을 열고 그 항목의 상세 화면(S01)을 보여 줌 | activity |

말 예시는 설명용입니다. 스키마의 description에는 문서 규칙대로 예시를 넣지 않았고, 실제 발화 해석은 Gleo가 합니다.

## 공통 규칙

- **정차 중 전용 유지**(DECISIONS 2026-11-10): 주행 규제 중에는 기록 내용을 말하지 않고 `fail` + "Consumable records can be checked only while the vehicle is parked."로 답합니다. 규제 상태를 확인하지 못해도 주행 중으로 봅니다.
- **권고 없음**: 남은 비율은 사용자가 정한 교체 거리 기준이며 "교체하세요" 같은 말은 하지 않습니다.
- **판정은 화면과 같음**: 기록·누적 주행거리·교체 거리로 `ConsumableCalculator.status`를 계산합니다(S01·S05와 같은 결과).
- **누적 주행거리는 호출마다 한 번 읽음**: Vehicle Odometer(`CAR_DRIVE`). Gleo는 7초 뒤 취소하므로 5초 안에 못 받으면 실패로 답합니다.
- **응답 언어**: Callback `message`는 문서 규칙대로 짧은 영어 완결 문장이고 항목 이름만 한국어입니다. 사용자에게 들리는 말은 Gleo가 이를 바탕으로 만듭니다.
- **항목 이름**: 스키마 enum은 음성 해석이 쉬운 영어 이름을 씁니다. 앱 저장 키와 다르므로 `GleoActions.voiceKeys`로 바꿉니다.

| 스키마 값 | 항목 | 스키마 값 | 항목 |
|---|---|---|---|
| engine_oil | 엔진오일 | brake_fluid | 브레이크액 |
| engine_air_filter | 엔진 에어필터 | coolant | 냉각수 |
| wiper_blade | 와이퍼 | 12v_battery | 12V 배터리 |
| cabin_air_filter | 에어컨 필터 | spark_plug | 점화 플러그 |
| tire | 타이어 | drive_belt | 구동 벨트 |
| brake_pad | 브레이크 패드 | transmission_oil | 변속기 오일 |
| brake_disc | 브레이크 디스크 | fuel_filter | 연료 필터 |
| tire_rotation | 타이어 위치 교환 | reduction_gear_oil | 감속기 오일 |
| wheel_alignment | 휠 얼라인먼트 | battery_coolant | 배터리 냉각수 |

항목 표시 이름은 앱 `strings.xml` 기준이며 위 표는 대응 관계만 보여 줍니다.

## 기능별 상세

### 1. GET_CONSUMABLE_STATUS — 한 항목 상태 묻기
- 파라미터: `item`(필수, 위 표의 값)
- 처리: `GleoActionReceiver`(broadcast) → 정차 확인 → 누적 주행거리 1회 조회 → 상태 판정 → Callback

| 상태 | status | message(요지) |
|---|---|---|
| 기록 + 교체 거리 있음 | success | "타이어 has been driven 3,000 km since the last replacement, with 70% of your 10,000 km distance left." |
| 교체 거리에 도달 | success | "… reaching your 10,000 km distance." |
| 기록만 있고 교체 거리 없음 | success | "… and no replacement distance is set." |
| 기록 없음 | success | "There is no replacement record for 타이어." |
| 현재 값 < 마지막 기록(비교 불가) | success | "… so the distance cannot be compared." |
| 이 차량 동력 방식에 맞지 않음 | fail | "엔진오일 does not apply to this vehicle's powertrain." |
| 누적 주행거리 못 읽음 | fail | "The vehicle odometer could not be read." |
| `item` 누락·알 수 없음 | asking | "Which consumable item should be checked?" |
| 주행 규제 중 | fail | "Consumable records can be checked only while the vehicle is parked." |

`data`: `item`, `drivenSinceReplacementKm`, `yourDistanceKm`, `leftPercent` 중 해당 값.

### 2. LIST_CONSUMABLES_BY_REMAINING — 남은 비율 적은 항목
- 파라미터: 없음
- 대상: 홈에 켠 항목 중 이 차량에 맞는 것, 그중 기록과 교체 거리가 모두 있는 항목
- 정렬: 홈 '목록' 보기와 같은 순서(실제 남은 비율이 낮은 순), 최대 3개
- 답: "By the distances you set: 타이어 10% left, 와이퍼 20% left, …" / 대상이 없으면 "No item has both a replacement record and a replacement distance set."
- 주행 규제·조회 실패는 1과 같음

### 3. OPEN_CONSUMABLE_RECORD — 항목 화면 열기
- 파라미터: `item`(필수)
- 처리: `MainActivity`(launchMode `singleTask`, 이미 떠 있으면 `onNewIntent`) → 해당 항목 S01로 이동
- 정차 중: success "Opened the 타이어 record."
- 주행 중: 화면은 기존 규제 안내가 가리고, success "The 타이어 record will be shown once the vehicle is parked." 정차하면 S01이 보입니다.
- 이 차량에 맞지 않는 항목·`item` 누락은 1과 같은 fail/asking

## 하지 않는 것(결정)
- 음성으로 교체 기록 저장·수정·삭제: 확인 단계 없이 기록이 바뀌면 안 되므로 화면에서만 합니다.
- 주행 중 기록 내용 답변: 정차 중 전용 결정과 충돌. 허용 여부는 확인 필요(아래).
- 앱 자체 TTS·STT: 준비 중 SDK라 쓰지 않습니다. 말은 Gleo가 합니다.

## 구현 위치
| 파일 | 내용 |
|---|---|
| `PleosApp/app/src/main/assets/schema.json` | App Schema(3개 기능, 영어 description) |
| `GleoActions.kt` | 파라미터 읽기, 항목 매핑, 답 문장, Callback 전송, 정차 확인 |
| `GleoActionReceiver.kt` | service 기능 2개(broadcast) 처리 |
| `MainActivity.handleGleoIntent`, `MainViewModel.openFromVoice` | activity 기능 처리 |
| `AndroidManifest.xml` | activity·receiver intent-filter |
| `GleoActionsTest.kt` | 파라미터·매핑·답 문장·Callback JSON 단위 테스트 |

Callback은 action `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`, extra `packageName`·`response`(JSON)로 보내며, **Gleo 음성비서 패키지 `ai.umos.ambientai`로만** 보냅니다(`setPackage`, QA-023). 다른 앱은 기록 요약을 받을 수 없습니다. `ACTION_PARAM`은 문서 예제처럼 바깥 따옴표로 감싼 형태와 일반 JSON, 파라미터별 extra를 모두 받습니다.

## 검증
- 단위 테스트: `GleoActionsTest` 7개 통과.
- 에뮬레이터(2026-11-10, adb로 Gleo 호출 흉내): 정차 중 GET(기록 있음·없음·item 누락), LIST, OPEN(일반 JSON·따옴표 감싼 JSON) 모두 의도대로 응답·이동.
  ```bash
  adb shell "am broadcast -a GET_CONSUMABLE_STATUS -p com.pleos.consumables --es ACTION_PARAM '{\"item\":\"tire\"}'"
  ```
  ```bash
  adb shell "am start -a OPEN_CONSUMABLE_RECORD -p com.pleos.consumables --es ACTION_PARAM '{\"item\":\"brake_fluid\"}'"
  ```
  응답은 `adb logcat -s GleoActions`에서 확인.
- 주행 규제 중(2026-11-11, Virtual Console 기어 D): GET·LIST → fail "Consumable records can be checked only while the vehicle is parked.", OPEN → success "… will be shown once the vehicle is parked." + 화면은 시스템 주행 안내가 가림. 모두 의도대로.
- **미검증**: 실제 Gleo 호출(승인 전).

## 확인 필요
- Gleo Interface에 필요한 퍼미션, 지원 언어, 승인 기준(문서에 없음)
- 주행 중 음성 답변 허용 범위(현재는 답하지 않음)
- Gleo 패키지명(QA-023):
  - **응답 받는 쪽**: 에뮬레이터 v2.1.0에서 `APP_ACTION_RESPONSE`를 등록한 앱은 `ai.umos.ambientai`(시스템 앱 AmbientAiApp 4.2.0.15)뿐이라 이 패키지로 제한했다. 확인 방법: `adb shell dumpsys activity broadcasts`에서 해당 action 위의 `ReceiverList{… 패키지명}`. 실차 패키지명이 같은지는 확인 필요.
  - **요청 보내는 쪽**: 확인 전이라 막지 않는다(receiver exported 유지). `OPEN_CONSUMABLE_RECORD`는 보낸 앱(referrer)을 `adb logcat -s GleoActions`에 남기므로 승인 뒤 실제 Gleo 호출로 확인한다. 화면 없는 두 기능은 보낸 쪽이 신원 공개를 켜야 알 수 있다(`getSentFromPackage`, Android 14).
  - 퍼미션: `ai.umos.ambientai`가 선언한 퍼미션이 없어 퍼미션으로 제한할 수 없다.
  - 공식 확인: Pleos 개발자 문의로 Gleo Action 발신 패키지, Callback `setPackage` 허용 여부, 권장 호출자 확인 방법을 묻는다.
- 실제 Gleo의 `ACTION_PARAM` 형식(문서 설명과 예제가 다름)
- 스키마 `version`(현재 `0.1.0`)을 App Market 등록 버전과 맞추는 시점
