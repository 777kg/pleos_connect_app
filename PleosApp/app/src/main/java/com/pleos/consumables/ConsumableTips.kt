package com.pleos.consumables

/**
 * S01 `알아두기` 카드 문구(소모품 역할 · 교체가 늦어지면). 일반 정보이며 정비 권고·진단이 아닙니다.
 * 차량과 운행 조건마다 다를 수 있다는 안내(`tip_note`)와 함께 보여 줍니다.
 */
object ConsumableTips {

    /** (역할, 늦어지면) 문자열 리소스 */
    fun of(item: Consumable): Pair<Int, Int> = when (item) {
        Consumable.OIL -> R.string.tip_role_oil to R.string.tip_late_oil
        Consumable.AIR_FILTER -> R.string.tip_role_air_filter to R.string.tip_late_air_filter
        Consumable.WIPER -> R.string.tip_role_wiper to R.string.tip_late_wiper
        Consumable.FILTER -> R.string.tip_role_filter to R.string.tip_late_filter
        Consumable.TIRE_FL, Consumable.TIRE_FR, Consumable.TIRE_RL, Consumable.TIRE_RR ->
            R.string.tip_role_tire to R.string.tip_late_tire
        Consumable.BRAKE_PAD -> R.string.tip_role_brake_pad to R.string.tip_late_brake_pad
        Consumable.BRAKE_DISC -> R.string.tip_role_brake_disc to R.string.tip_late_brake_disc
        Consumable.TIRE_ROTATION -> R.string.tip_role_tire_rotation to R.string.tip_late_tire_rotation
        Consumable.ALIGNMENT -> R.string.tip_role_alignment to R.string.tip_late_alignment
        Consumable.BRAKE_FLUID -> R.string.tip_role_brake_fluid to R.string.tip_late_brake_fluid
        Consumable.COOLANT -> R.string.tip_role_coolant to R.string.tip_late_coolant
        Consumable.BATTERY -> R.string.tip_role_battery to R.string.tip_late_battery
        Consumable.SPARK_PLUG -> R.string.tip_role_spark_plug to R.string.tip_late_spark_plug
        Consumable.DRIVE_BELT -> R.string.tip_role_drive_belt to R.string.tip_late_drive_belt
        Consumable.TRANSMISSION_OIL -> R.string.tip_role_transmission_oil to R.string.tip_late_transmission_oil
        Consumable.FUEL_FILTER -> R.string.tip_role_fuel_filter to R.string.tip_late_fuel_filter
        Consumable.REDUCTION_OIL -> R.string.tip_role_reduction_oil to R.string.tip_late_reduction_oil
        Consumable.BATTERY_COOLANT -> R.string.tip_role_battery_coolant to R.string.tip_late_battery_coolant
    }
}
