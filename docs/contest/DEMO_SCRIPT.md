# 시연 영상 시나리오 — 내 차량 소모품

> 목적: 공모전 참고자료(선택·가산점). 기획서 4번 시나리오(엔진오일 교체 운전자)를 실제 앱 화면으로 보여 준다.
> 길이 목표 2~3분. 화면은 Pleos Connect 에뮬레이터 v2.1.0, 차량 상태는 Virtual Console로 바꾼다.

## 원칙
- 실제 동작만 찍는다. 에뮬레이터가 지원하지 않는 **차량 신호 카드**(엔진오일·브레이크액·변속기 오일 양)는 시연값으로 꾸미지 않고 자막으로만 "지원 차량에서 표시"라고 알린다.
- Gleo는 승인 전이라 음성으로 부를 수 없다. **adb로 Gleo와 같은 호출**을 보내고, 앱이 Gleo에 돌려주는 답(로그)을 화면에 함께 보여 준다. 자막에 "Gleo 호출을 adb로 재현"이라고 밝힌다.
- 개인정보가 나오는 화면(PC 바탕화면, 계정, 터미널 사용자 경로)은 잘라 낸다.

## 준비(녹화 전)
1. 앱 기록 초기화: release APK를 지우고 다시 설치한다.
   ```bash
   adb uninstall com.pleos.consumables
   ```
   ```bash
   adb install apk/777kg_내차량소모품_release.apk
   ```
2. Virtual Console 열기: `adb forward tcp:8088 tcp:8088` → 브라우저 `http://127.0.0.1:8088/`
3. 기어 **P**, Odometer를 지금 값보다 큰 값으로 설정(에뮬레이터는 값을 올리기만 받음). 2026-11-12 녹화는 55,800 km에서 시작해 63,400 km로 올림(7,600 km, 24%)
4. 앱 설정 → 차량 동력 → **내연기관**(에뮬레이터는 전기차로 알려 주므로 엔진오일을 보이게 함). 이 단계는 녹화 전에 해 둔다.

## 장면
| # | 화면 | 하는 일 | 자막(예) | 길이 |
|---|---|---|---|---|
| 1 | 런처 → 앱 홈(탑뷰) | 앱 아이콘을 눌러 실행 | 소모품을 교체할 때 차가 아는 주행거리로 기록합니다 | 10초 |
| 2 | 홈 → 엔진오일 상세 | 차 그림에서 엔진오일 선택 | 정비를 마치고 운전석에서 | 10초 |
| 3 | 교체 기록 | '교체 기록' → 지금 누적 주행거리·조회 시각 확인 → 저장 | 주행거리를 직접 입력하지 않습니다(Pleos Vehicle SDK) | 20초 |
| 4 | 교체 거리 | 10,000 km 입력(또는 빠른 선택) | 교체 주기는 내가 정합니다 | 10초 |
| 5 | Virtual Console | Odometer를 7,600 km 올림 | 몇 달 뒤, 7,600 km를 달렸습니다 | 10초 |
| 6 | 엔진오일 상세 → 홈 | '다시 확인' → 교체 후 7,600 km, 24% 남음 → 홈 아이콘 비율 | 교체 후 달린 거리와 남은 비율 | 20초 |
| 7 | Virtual Console 기어 D | 앱이 가려지고 시스템 주행 안내 | 주행 중에는 값과 조작을 모두 숨깁니다 | 10초 |
| 8 | (주행 중) Gleo 호출 재현 | `GET_CONSUMABLE_STATUS engine_oil` → 로그 fail "…only while the vehicle is parked." | 주행 중 음성 질문엔 정차 후 확인하라고만 답합니다 | 15초 |
| 9 | 기어 P → Gleo 호출 재현 | GET → success "…7,600 km… 24% …", OPEN → 엔진오일 화면이 열림 | 정차하면 음성으로 묻고 화면을 바로 엽니다 | 25초 |
| 10 | 홈 목록 보기 | 목록 전환, 남은 비율 적은 순 | 한눈에 보는 교체 시점 | 10초 |
| 11 | 마무리 | 탑뷰 홈 정지 화면 | 내 차량 소모품 · 팀 777kg | 5초 |

선택 장면(시간이 남으면): 타이어도 기록해 목록 순서가 바뀌는 모습, 같은 항목을 한 번 더 기록해 이력과 평균 교체 간격이 보이는 모습.

## Gleo 호출 재현 명령
```bash
adb shell "am broadcast -a GET_CONSUMABLE_STATUS -p com.pleos.consumables --es ACTION_PARAM '{\"item\":\"engine_oil\"}'"
```
```bash
adb shell "am start -a OPEN_CONSUMABLE_RECORD -p com.pleos.consumables --es ACTION_PARAM '{\"item\":\"engine_oil\"}'"
```
```bash
adb logcat -s GleoActions
```

## 녹화 방법
- **권장: 에뮬레이터 바깥에서 녹화** `adb emu screenrecord start --size 1920x1080 --fps 30 --time-limit 600 <PC 경로>.webm` → 끝나면 `adb emu screenrecord stop`. 2026-11-12에 기기 안 `screenrecord`(1920x1080)로 찍다가 부하로 시스템이 재시작됨(watchdog). 앱 문제 아님.
- Virtual Console은 HTTP로도 바꿀 수 있다(콘솔 페이지를 브라우저에 열어 둔 상태에서): `curl -X PUT "http://127.0.0.1:8088/vehicle/odometer?traveled_distance_km=63400"`, `curl -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Drive"`
- 원본은 `docs/contest/시연영상_원본.webm`(로컬 전용, `.gitignore`)
- 이 PC에는 ffmpeg가 없어 클립 합치기·자막은 영상 편집기(Clipchamp 등)에서 한다. 확인 필요.
