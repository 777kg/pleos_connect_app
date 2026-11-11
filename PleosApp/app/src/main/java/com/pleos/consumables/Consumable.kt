package com.pleos.consumables

/**
 * 기록할 수 있는 소모품 목록(기본 제공, 앞으로 계속 추가). [key]는 저장·이미지 파일명에 씁니다(바꾸지 않음).
 * - [zone]: 홈(S05) 탑뷰 주변에서 놓이는 쪽(앞→뒤 순서로 정렬)
 * - [powertrain]: 내연기관·전기차 전용 항목 표시용. 차량 정보로 '해당 없음'만 안내하고 끄는 것은 사용자가 정합니다.
 * - [defaultOn]: 처음 설치 때 홈에 보이는지(설정에서 켜고 끔)
 * 교체 주기를 권장하지 않습니다(DECISIONS: 기준 거리는 사용자 설정).
 */
enum class Consumable(
    val key: String,
    val nameRes: Int,
    val zone: Zone,
    val powertrain: Powertrain,
    val defaultOn: Boolean,
) {
    OIL("oil", R.string.oil_name, Zone.FRONT, Powertrain.ICE, true),
    AIR_FILTER("air_filter", R.string.air_filter_name, Zone.FRONT, Powertrain.ICE, true),
    WIPER("wiper", R.string.wiper_name, Zone.FRONT, Powertrain.ALL, true),
    FILTER("filter", R.string.filter_name, Zone.CABIN, Powertrain.ALL, true),
    TIRE("tire", R.string.tire_name, Zone.WHEEL, Powertrain.ALL, true),
    BRAKE_PAD("brake_pad", R.string.brake_pad_name, Zone.WHEEL, Powertrain.ALL, true),
    BRAKE_DISC("brake_disc", R.string.brake_disc_name, Zone.WHEEL, Powertrain.ALL, false),
    TIRE_ROTATION("tire_rotation", R.string.tire_rotation_name, Zone.WHEEL, Powertrain.ALL, false),
    ALIGNMENT("alignment", R.string.alignment_name, Zone.WHEEL, Powertrain.ALL, false),
    BRAKE_FLUID("brake_fluid", R.string.brake_fluid_name, Zone.FRONT, Powertrain.ALL, false),
    COOLANT("coolant", R.string.coolant_name, Zone.FRONT, Powertrain.ALL, false),
    BATTERY("battery", R.string.battery_name, Zone.FRONT, Powertrain.ALL, false),
    SPARK_PLUG("spark_plug", R.string.spark_plug_name, Zone.FRONT, Powertrain.ICE, false),
    DRIVE_BELT("drive_belt", R.string.drive_belt_name, Zone.FRONT, Powertrain.ICE, false),
    TRANSMISSION_OIL("transmission_oil", R.string.transmission_oil_name, Zone.UNDER, Powertrain.ICE, false),
    FUEL_FILTER("fuel_filter", R.string.fuel_filter_name, Zone.UNDER, Powertrain.ICE, false),
    REDUCTION_OIL("reduction_oil", R.string.reduction_oil_name, Zone.UNDER, Powertrain.EV, false),
    BATTERY_COOLANT("battery_coolant", R.string.battery_coolant_name, Zone.UNDER, Powertrain.EV, false);

    enum class Zone { FRONT, CABIN, WHEEL, UNDER, REAR }

    enum class Powertrain(val labelRes: Int) {
        ALL(R.string.group_common),
        ICE(R.string.group_ice),
        EV(R.string.group_ev),
    }

    /** 이 차량 동력 방식에서 해당 없는 항목인지(모르면 false). */
    fun notApplicableTo(kind: VehicleKind): Boolean = when (powertrain) {
        Powertrain.ALL -> false
        Powertrain.ICE -> kind == VehicleKind.EV
        Powertrain.EV -> kind == VehicleKind.ICE
    }

    companion object {
        fun fromKey(key: String): Consumable? = entries.firstOrNull { it.key == key }

        val DEFAULT_ENABLED: Set<Consumable> = entries.filter { it.defaultOn }.toSet()

        /** 홈·이력 탭에 쓰는 순서: 차 앞쪽부터. */
        fun ordered(items: Collection<Consumable>): List<Consumable> =
            items.sortedWith(compareBy<Consumable> { it.zone.ordinal }.thenBy { it.ordinal })
    }
}

/** 차량 동력 방식(AAOS INFO_FUEL_TYPE). 읽지 못하면 UNKNOWN이고 모든 항목을 그대로 보여 줍니다. */
enum class VehicleKind { UNKNOWN, ICE, EV, HYBRID }

/**
 * 교체 기록 한 건(UI_SPEC §0 저장 데이터 모델).
 * - [vehicleKm]: 저장 때 차량에서 받은 원본 거리. 수정해도 바꾸지 않습니다.
 * - [recordKm]: 계산에 쓰는 거리. 처음에는 [vehicleKm]과 같고, 사용자가 고치면 바뀝니다([edited]).
 * - [saveOrder]: 같은 교체 시각일 때 나중에 저장한 기록을 고르기 위한 순번.
 */
data class ReplacementRecord(
    val id: Long,
    val item: Consumable,
    val replacedAtMillis: Long,
    val vehicleKm: Float,
    val receivedAtMillis: Long,
    val recordKm: Float,
    val edited: Boolean,
    val saveOrder: Long,
    /** '기준 다시 잡기'로 저장한 기록(주행거리 초기화·계기판 교체 뒤 새 기준). 이전 기록과 거리를 비교하지 않습니다. */
    val rebased: Boolean = false,
)
