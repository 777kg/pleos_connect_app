package com.pleos.consumables

import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 차량 누적 주행거리를 Vehicle SDK 2.3.2 Odometer에서 읽습니다. 조회만 하고 제어는 하지 않습니다.
 *
 * | 값 | API | 퍼미션 |
 * |---|---|---|
 * | 누적 주행거리(km) | Odometer `checkCurrentDistanceDrivenCapabilities` → `getCurrentDistanceDriven` | CAR_DRIVE |
 *
 * - SDK KDoc: "Current odometer value of the vehicle in kilometers (km)". 값이 없으면 null.
 * - 화면 진입·복귀와 "다시 확인" 때만 1회 조회합니다. 상시 리스너·폴링은 쓰지 않습니다(UI_SPEC S01 동작).
 * - 결과는 메인 스레드로 옮겨 [onUpdate]로 전달하고, [stop] 이후 도착한 응답은 세대 번호로 버립니다.
 */
class OdometerSource(
    private val gateway: VehicleGateway,
    private val onUpdate: (OdometerReading) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var active = false

    /** 화면이 보이고 규제가 없을 때: 조회합니다. */
    fun start() {
        if (active) return
        active = true
        refresh()
    }

    /** 숨김·주행 규제: 진행 중 응답 무효화. Vehicle 자체 release는 하지 않습니다(생명주기 끝에서). */
    fun stop() {
        if (!active) return
        active = false
        generation++
    }

    /** "다시 확인": capability 확인 뒤 다시 조회합니다. */
    fun refresh() {
        if (!active) return
        val gen = ++generation
        onUpdate(OdometerReading.Checking)
        val odometer = try {
            gateway.vehicle().getOdometer()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Vehicle initialize failed", e)
            onUpdate(errorState(e))
            return
        }
        odometer.checkCurrentDistanceDrivenCapabilities(
            { config: CapabilityConfig ->
                post(gen) {
                    if (!config.readable()) {
                        onUpdate(OdometerReading.Unsupported)
                        return@post
                    }
                    odometer.getCurrentDistanceDriven(
                        { km: Float? ->
                            post(gen) {
                                onUpdate(
                                    if (km == null || km.isNaN() || km < 0f) OdometerReading.Failed
                                    else OdometerReading.Received(km, System.currentTimeMillis()),
                                )
                            }
                        },
                        { e: Exception -> post(gen) { onUpdate(errorState(e)) } },
                    )
                }
            },
            { e: Exception -> post(gen) { onUpdate(errorState(e)) } },
        )
    }

    private fun post(gen: Int, block: () -> Unit) {
        main.post { if (active && gen == generation) block() }
    }

    private companion object {
        const val TAG = "OdometerSource"

        fun CapabilityConfig?.readable() =
            this?.accessType == AccessType.READ || this?.accessType == AccessType.READ_WRITE

        fun errorState(e: Exception): OdometerReading {
            Log.w(TAG, "odometer read failed: ${e.message}")
            return when {
                isNotSupported(e) -> OdometerReading.Unsupported
                isPermissionError(e) -> OdometerReading.PermissionDenied
                else -> OdometerReading.Failed
            }
        }

        /*
         * SDK 2.3.2 AAR에서 VehicleException은 난독화되어(callback.exception.a) 앱이 타입·errorCode를 참조할 수 없습니다.
         * 서비스 오류 코드를 받으면 message가 ErrorCode 이름이 되므로 이름으로 구분합니다.
         * (vehicle-data 1.1.11: 60001 NOT_SUPPORTED_PROPERTY, 60003 NOT_SUPPORTED_AREA, 800001 SECURITY_PERMISSION)
         * SDK 자체 퍼미션 검사는 SecurityException("… need Permission")으로 옵니다.
         */
        fun isNotSupported(e: Exception) =
            e.message == "ERROR_NOT_SUPPORTED_PROPERTY" || e.message == "ERROR_NOT_SUPPORTED_AREA"

        fun isPermissionError(e: Exception) =
            e is SecurityException || e.message == "ERROR_SECURITY_PERMISSION" ||
                e.message?.contains("permission", ignoreCase = true) == true
    }
}
