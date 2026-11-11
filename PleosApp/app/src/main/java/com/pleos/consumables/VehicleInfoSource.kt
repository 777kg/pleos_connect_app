package com.pleos.consumables

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 차량 동력 방식을 AAOS 표준 `INFO_FUEL_TYPE`으로 한 번 읽습니다(퍼미션 `android.car.permission.CAR_INFO`).
 * S07 설정에서 내연기관·전기차 전용 항목에 '이 차량에 해당 없음'을 붙이는 데만 씁니다. 항목을 자동으로 끄지 않습니다.
 * 읽지 못하면 [VehicleKind.UNKNOWN]. VIN 같은 식별 정보는 읽지 않습니다.
 */
object VehicleInfoSource {

    private const val TAG = "VehicleInfo"

    // android.car.hardware.property.FuelType 값(표준). 상수 클래스가 빌드에 없을 수 있어 값으로 둡니다.
    private const val FUEL_ELECTRIC = 10

    fun readKind(context: Context, onResult: (VehicleKind) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            val kind = try {
                val car = Car.createCar(context.applicationContext)
                try {
                    val manager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    val types = manager?.getIntArrayProperty(VehiclePropertyIds.INFO_FUEL_TYPE, 0)
                    Log.i(TAG, "INFO_FUEL_TYPE=${types?.toList()}")
                    kindOf(types)
                } finally {
                    car?.disconnect()
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "INFO_FUEL_TYPE unavailable: ${e.message}")
                VehicleKind.UNKNOWN
            }
            main.post { onResult(kind) }
        }.start()
    }

    fun kindOf(types: IntArray?): VehicleKind {
        if (types == null || types.isEmpty()) return VehicleKind.UNKNOWN
        val electric = FUEL_ELECTRIC in types
        return when {
            electric && types.size == 1 -> VehicleKind.EV
            electric -> VehicleKind.HYBRID
            else -> VehicleKind.ICE
        }
    }
}
