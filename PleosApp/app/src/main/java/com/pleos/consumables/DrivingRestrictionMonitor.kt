package com.pleos.consumables

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * CarUxRestrictionsManager 구독을 화면 생명주기에 묶습니다.
 * - onStart: 연결 → 리스너 등록 → 초기값 즉시 반영
 * - onStop: 리스너 해제 → 연결 해제 (백그라운드에서 구독 유지 안 함, ch04)
 */
class DrivingRestrictionMonitor(
    private val context: Context,
    private val onChanged: (UxRestrictionState) -> Unit,
) : DefaultLifecycleObserver {

    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null

    private val main = Handler(Looper.getMainLooper())

    // 변경 알림이 어느 스레드로 오든 메인 스레드에서 전달합니다(화면 상태는 메인에서만 바꿈).
    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
        main.post { if (uxManager != null) onChanged(UxRestrictionState.from(r)) }
    }

    override fun onStart(owner: LifecycleOwner) {
        // 정차 중 전용 앱: 규제 상태를 확인하지 못하면 "규제 중"으로 보고 값을 보여 주지 않습니다(QA-003).
        try {
            val connected = Car.createCar(context)
            val manager = connected?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            car = connected
            uxManager = manager
            if (manager == null) {
                Log.w(TAG, "CarUxRestrictionsManager unavailable")
                onChanged(UxRestrictionState.UNKNOWN)
                return
            }
            manager.registerListener(listener)
            // 초기값이 null이면 아직 판정을 모르는 것이므로 "규제 중"으로 둡니다. 이후 변경 알림으로 풀립니다.
            val current = manager.currentCarUxRestrictions
            onChanged(if (current == null) UxRestrictionState.UNKNOWN else UxRestrictionState.from(current))
        } catch (e: RuntimeException) {
            Log.w(TAG, "CarUxRestrictionsManager unavailable", e)
            onChanged(UxRestrictionState.UNKNOWN)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        uxManager?.unregisterListener()
        uxManager = null
        car?.disconnect()
        car = null
    }

    private companion object {
        const val TAG = "DrivingRestriction"
    }
}
