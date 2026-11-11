package com.pleos.consumables

import ai.pleos.playground.vehicle.api.Safety
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 차량이 직접 알려 주는 액량 신호(창의성 아이디어 ③ "차량 신호와 내 기록 나란히").
 * 내 교체 기록·거리 계산과 섞지 않고 S01에 출처를 붙여 따로 보여 줍니다. 경고 해석·조치 권고는 하지 않습니다.
 *
 * | 신호 | API(Vehicle SDK 2.3.2 Safety) | 퍼미션 |
 * |---|---|---|
 * | 엔진오일 양 | `checkEngineOilLevelStateCapabilities` → `getEngineOilLevelState` | CAR_SAFETY |
 * | 브레이크액 양 | `checkBrakeFluidLevelStateCapabilities` → `getBrakeFluidLevelState` | CAR_SAFETY |
 * | 변속기 오일 양 | `checkTransmissionOilLevelStateCapabilities` → `getTransmissionOilLevelState` | CAR_SAFETY |
 *
 * - Tire는 저압 '기준값'만 CAR_SAFETY로 읽을 수 있고 실제 공기압은 PRIVATE_CAR_SAFETY라 쓰지 않습니다.
 * - 조회 시점은 누적 주행거리와 같습니다(화면 진입·복귀·"다시 확인"). 상시 리스너는 쓰지 않습니다.
 * - 미지원·권한 없음·실패면 그 신호는 [SignalReading.Hidden]이고 화면에서 숨깁니다.
 */
class VehicleSignalSource(
    private val gateway: VehicleGateway,
    private val onUpdate: (VehicleSignal, SignalReading) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var active = false

    fun start() {
        if (active) return
        active = true
        refresh()
    }

    fun stop() {
        if (!active) return
        active = false
        generation++
    }

    fun refresh() {
        if (!active) return
        val gen = ++generation
        val safety = try {
            gateway.vehicle().getSafety()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Vehicle initialize failed", e)
            VehicleSignal.entries.forEach { onUpdate(it, SignalReading.Hidden) }
            return
        }
        VehicleSignal.entries.forEach { signal ->
            onUpdate(signal, SignalReading.Checking)
            read(safety, signal, gen)
        }
    }

    private fun read(safety: Safety, signal: VehicleSignal, gen: Int) {
        val failed: (Exception) -> Unit = { e ->
            Log.w(TAG, "${signal.name} read failed: ${e.message}")
            post(gen) { onUpdate(signal, SignalReading.Hidden) }
        }
        val capability: (CapabilityConfig, List<OilLevelState>) -> Unit = { config, _ ->
            post(gen) {
                if (!config.readable()) {
                    onUpdate(signal, SignalReading.Hidden)
                    return@post
                }
                val got: (OilLevelState?) -> Unit = { level ->
                    post(gen) {
                        // QA-025: 값 없음·센서 오류(ERROR)는 실패와 같게 숨깁니다.
                        onUpdate(
                            signal,
                            if (level == null || level == OilLevelState.ERROR) SignalReading.Hidden
                            else SignalReading.Received(level, System.currentTimeMillis()),
                        )
                    }
                }
                try {
                    when (signal) {
                        VehicleSignal.ENGINE_OIL -> safety.getEngineOilLevelState(got, failed)
                        VehicleSignal.BRAKE_FLUID -> safety.getBrakeFluidLevelState(got, failed)
                        VehicleSignal.TRANSMISSION_OIL -> safety.getTransmissionOilLevelState(got, failed)
                    }
                } catch (e: RuntimeException) {
                    failed(e)
                }
            }
        }
        try {
            when (signal) {
                VehicleSignal.ENGINE_OIL -> safety.checkEngineOilLevelStateCapabilities(capability, failed)
                VehicleSignal.BRAKE_FLUID -> safety.checkBrakeFluidLevelStateCapabilities(capability, failed)
                VehicleSignal.TRANSMISSION_OIL -> safety.checkTransmissionOilLevelStateCapabilities(capability, failed)
            }
        } catch (e: RuntimeException) {
            // SDK 자체 퍼미션 검사(SecurityException) 등
            failed(e)
        }
    }

    private fun post(gen: Int, block: () -> Unit) {
        main.post { if (active && gen == generation) block() }
    }

    private companion object {
        const val TAG = "VehicleSignalSource"

        fun CapabilityConfig?.readable() =
            this?.accessType == AccessType.READ || this?.accessType == AccessType.READ_WRITE
    }
}

/** 차량 액량 신호와, 그 신호를 나란히 보여 줄 소모품. */
enum class VehicleSignal(val item: Consumable, val labelRes: Int) {
    ENGINE_OIL(Consumable.OIL, R.string.signal_engine_oil),
    BRAKE_FLUID(Consumable.BRAKE_FLUID, R.string.signal_brake_fluid),
    TRANSMISSION_OIL(Consumable.TRANSMISSION_OIL, R.string.signal_transmission_oil),
    ;

    companion object {
        fun of(item: Consumable): VehicleSignal? = entries.firstOrNull { it.item == item }
    }
}

/** 차량 액량 신호 조회 결과. 값을 추측해서 채우지 않습니다. */
sealed interface SignalReading {
    data object Checking : SignalReading

    /** 미지원·권한 없음·실패: 화면에서 숨깁니다. */
    data object Hidden : SignalReading

    data class Received(val level: SignalLevel, val checkedAtMillis: Long) : SignalReading {
        constructor(level: OilLevelState, checkedAtMillis: Long) : this(SignalLevel.from(level), checkedAtMillis)
    }
}

/** SDK OilLevelState를 앱 안에서 쓰는 값으로 옮긴 것(단위 테스트에서 SDK 없이 쓰기 위함). */
enum class SignalLevel(val labelRes: Int) {
    NORMAL(R.string.signal_level_normal),
    LOW(R.string.signal_level_low),
    CRITICALLY_LOW(R.string.signal_level_critically_low),
    HIGH(R.string.signal_level_high),
    ERROR(R.string.signal_level_error),
    ;

    companion object {
        fun from(level: OilLevelState): SignalLevel = when (level) {
            OilLevelState.NORMAL -> NORMAL
            OilLevelState.LOW -> LOW
            OilLevelState.CRITICALLY_LOW -> CRITICALLY_LOW
            OilLevelState.HIGH -> HIGH
            OilLevelState.ERROR -> ERROR
        }
    }
}
