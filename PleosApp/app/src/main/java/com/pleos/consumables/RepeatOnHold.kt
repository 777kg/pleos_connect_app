package com.pleos.consumables

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * ± 버튼을 길게 누르면 클릭을 반복합니다(UI_SPEC S03 "길게 누르기 반복·키보드 없음", S06 ±100 km).
 * - 짧게 누르면 보통 클릭 한 번입니다.
 * - 손을 떼거나(UP/CANCEL), 버튼이 비활성·안 보이게 되면(범위 끝, 주행 규제) 바로 멈춥니다.
 * - 화면이 보일 때 사용자가 누르고 있는 동안만 돌므로 백그라운드 타이머가 아닙니다.
 */
object RepeatOnHold {

    private const val INTERVAL_MS = 120L

    @SuppressLint("ClickableViewAccessibility") // 접근성 클릭은 기본 OnClickListener가 그대로 처리합니다.
    fun attach(view: View) {
        val handler = Handler(Looper.getMainLooper())
        val repeat = object : Runnable {
            override fun run() {
                if (!view.isShown || !view.isEnabled || !view.isPressed) return
                view.performClick()
                handler.postDelayed(this, INTERVAL_MS)
            }
        }
        view.setOnLongClickListener {
            handler.removeCallbacks(repeat)
            handler.post(repeat)
            true
        }
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(repeat)
            }
            false
        }
    }
}
