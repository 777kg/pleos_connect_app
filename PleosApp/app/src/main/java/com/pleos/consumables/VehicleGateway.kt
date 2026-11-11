package com.pleos.consumables

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle

/**
 * Pleos Vehicle SDK 진입점. 도메인 호출은 [OdometerSource]가 합니다.
 *
 * - Vehicle은 처음 필요할 때 만듭니다(cold start 최소화, ch06).
 * - release()는 화면/서비스 생명주기 끝(ViewModel.onCleared)에서만 부릅니다. 콜백 직후 금지(ch02).
 *
 * 속도·기어·외기온·연료 같은 표준 값은 여기가 아니라 CarPropertyManager로 읽습니다.
 */
class VehicleGateway(private val context: Context) {

    private var vehicle: Vehicle? = null

    fun vehicle(): Vehicle =
        vehicle ?: Vehicle(context.applicationContext).also {
            it.initialize()
            vehicle = it
        }

    /** 해제할 때처럼 새로 만들면 안 되는 곳에서 씁니다. */
    fun vehicleOrNull(): Vehicle? = vehicle

    fun release() {
        vehicle?.release()
        vehicle = null
    }
}
