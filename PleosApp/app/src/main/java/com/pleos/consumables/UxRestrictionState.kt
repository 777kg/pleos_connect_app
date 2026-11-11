package com.pleos.consumables

import android.car.drivingstate.CarUxRestrictions

/**
 * 플랫폼이 내려준 ActiveRestrictions 비트를 화면에서 쓰기 쉬운 형태로 옮긴 값.
 * 속도·기어·지역은 앱이 판정하지 않습니다(ch03).
 */
data class UxRestrictionState(
    val restricted: Boolean = false,
    val noKeyboard: Boolean = false,
    val noSetup: Boolean = false,
    val noVideo: Boolean = false,
    val noFiltering: Boolean = false,
    val noDialpad: Boolean = false,
    val limitContent: Boolean = false,
    val limitStringLength: Boolean = false,
) {
    companion object {
        val NONE = UxRestrictionState()

        /** 규제 서비스를 쓸 수 없을 때. 정차 중 전용 화면은 안전하게 "규제 중"으로 다룹니다. */
        val UNKNOWN = UxRestrictionState(restricted = true)

        fun from(r: CarUxRestrictions?): UxRestrictionState {
            if (r == null || !r.isRequiresDistractionOptimization) return NONE
            val active = r.activeRestrictions
            fun has(bit: Int) = active and bit != 0
            return UxRestrictionState(
                restricted = true,
                noKeyboard = has(CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD),
                noSetup = has(CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP),
                noVideo = has(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO),
                noFiltering = has(CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING),
                noDialpad = has(CarUxRestrictions.UX_RESTRICTIONS_NO_DIALPAD),
                limitContent = has(CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT),
                limitStringLength = has(CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH),
            )
        }
    }
}
