# Chapter 5: 오디오 포커스 · Suspend

## Core Idea
소리를 내는 앱은 **요청, 4가지 이벤트 처리, 반납**을 모두 구현해야 합니다. Suspend에 들어갈 때는 **멈추고 반납하고 저장하고 해제**합니다. Resume에서는 **자동으로 되살리지 않습니다.**

## Frameworks Introduced
- **E / C / R 상호작용**: 새 요청이 기존 보유자와 만났을 때의 결과입니다.
  - **R(Reject)**: 새 요청이 실패합니다. **E(Exclusive)**: 새 요청이 획득하고 기존 보유자는 상실합니다. **C(Concurrent)**: 함께 유지합니다(보통 duck).
  - 보유자가 여럿이면 각 셀을 조회해 **가장 보수적인 결과(R > E > C)** 를 채택합니다.
- **이벤트 4종 처리 (Pleos 정책상 필수)**
  - `AUDIOFOCUS_GAIN`: 재생/복귀 · `LOSS`: 즉시 정지 · `LOSS_TRANSIENT`: 일시정지 · `LOSS_TRANSIENT_CAN_DUCK`: 볼륨 감쇄, 해제되면 원래 볼륨으로 복원
- **Suspend 4단계**: ① 재생 즉시 중단 ② 포커스 abandon ③ 재생 여부와 위치 저장 ④ Player와 track 해제. 이 과정에서 새 재생이나 새 포커스 요청은 금지입니다.
  - Resume: 상태만 복원합니다. 포커스와 재생은 앱이 재평가한 뒤 필요하면 다시 요청합니다. `shouldAutoResume`의 기본값은 `false`입니다.

## Key Concepts
- **오디오 컨텍스트**: MUSIC(MEDIA·GAME·UNKNOWN), NAVIGATION, VOICE_COMMAND(ASSISTANT), CALL_RING, CALL, ALARM, NOTIFICATION, SYSTEM_SOUND, EMERGENCY, SAFETY, VEHICLE_STATUS, ANNOUNCEMENT
- **MAY_DUCK**: 동시 재생을 기대하는 요청(예: 내비 안내)은 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`을 씁니다.
- **Gleo(음성비서)**: `LOSS_TRANSIENT`를 받으면 즉시 멈추고, 필요하면 반납합니다.
- **선행 묵음**: TTS나 음성 안내의 첫 음절이 잘리지 않도록 앞에 무음을 넣습니다.

## Reference Tables

| 상황(현재 → 새 요청) | 결과 |
|---|---|
| MUSIC → NAVIGATION | C (음악 duck, 내비 재생) |
| CALL → MUSIC | R (실패) |
| MUSIC → CALL | E (통화가 선점) |
| CALL → NAVIGATION | C (옵션으로 R 가능) |
| EMERGENCY → 대부분 | R |
| MUSIC+NAVIGATION → VOICE_COMMAND | 보유자별 결과 중 보수적인 것 |

## Code Examples
```kotlin
val attrs = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(attrs)
    .setOnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> { player.volume = 1f; if (pausedByFocus) player.play() }
            AudioManager.AUDIOFOCUS_LOSS -> { player.stop(); audioManager.abandonAudioFocusRequest(focusRequest) }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { pausedByFocus = true; player.pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.volume = 0.3f
        }
    }.build()

if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) player.play()

fun onSuspend() {                       // 순서 중요
    player.pause()
    audioManager.abandonAudioFocusRequest(focusRequest)
    saveState(isPlaying = wasPlaying, position = player.currentPosition)
    player.release()
}
fun onResume() { restoreState(); /* shouldAutoResume=false: 자동 재생/포커스 요청 안 함 */ }
```

## Worked Example
음악 재생 중 → 내비 안내 → 전화 → Suspend
1. 내비가 MAY_DUCK으로 요청합니다(C). 음악 앱은 `CAN_DUCK`을 받아 볼륨을 낮춥니다. 안내가 끝나 `GAIN`을 받으면 볼륨을 복원합니다.
2. 전화가 옵니다(E). 음악 앱은 `LOSS_TRANSIENT`를 받아 일시정지하고, 통화가 끝나 `GAIN`을 받으면 재개합니다.
3. 통화 중에 사용자가 재생 버튼을 누르면 요청이 R로 실패합니다. 재생하지 않고 UI를 그대로 둡니다.
4. 시동을 끄면 Suspend입니다. 재생을 중단하고, 포커스를 반납하고, 위치를 저장하고, 플레이어를 해제합니다.
5. 다시 시동을 걸면(Resume) 곡과 위치만 복원합니다. 사용자가 재생을 눌렀을 때 포커스를 요청합니다.

## Anti-patterns
- **포커스 없이 재생**하거나 **변경 이벤트를 무시**: 필수 위반입니다.
- **Suspend 중 포커스 보유, 백그라운드 재생 지속**: 금지입니다.
- **Resume을 위해 포커스를 미리 선점하거나 자동 복원**: 금지입니다.
- **포커스 매트릭스 하드코딩**(시스템 측 구현 시): JSON이나 CSV로 로드합니다.
- **duck 중 pause**: 동시 재생(C) 의도가 깨집니다. 정책에 맞게 설정합니다.

## Key Takeaways
1. 재생 전에 요청하고, 4개 이벤트를 모두 처리하고, 끝나면 반납합니다.
2. 동시 재생을 원하면 MAY_DUCK을 쓰고, duck이 풀리면 볼륨을 복원합니다.
3. Suspend는 중단, 반납, 저장, 해제 순서입니다. Resume은 수동입니다.
4. 필수 검증 시나리오 5개(위 표)를 테스트합니다.

## Connects To
- **ch03**: 운전 관련 안내가 나오면 미디어를 멈추거나 줄임
- **ch04**: Back으로 숨긴 뒤에도 미디어 재생은 유지
- **ch06**: 오디오 포커스는 성능 심사 항목이기도 함
