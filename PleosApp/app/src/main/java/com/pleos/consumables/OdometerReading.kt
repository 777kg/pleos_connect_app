package com.pleos.consumables

/**
 * 차량 누적 주행거리 조회 결과(UI_SPEC §0 데이터·판정 1). 값을 추측해서 채우지 않습니다.
 * - [Received.km]: Odometer `getCurrentDistanceDriven`이 돌려준 km 값
 * - [Received.checkedAtMillis]: 응답을 받은 시각(조회 시각 표시용)
 */
sealed interface OdometerReading {
    data object Checking : OdometerReading
    data object Unsupported : OdometerReading
    data object PermissionDenied : OdometerReading
    data object Failed : OdometerReading
    data class Received(val km: Float, val checkedAtMillis: Long) : OdometerReading
}

/** 지금 보이는 화면(SCREENS.md). Home=S05, Detail=S01, History=S02, Edit=S03, Confirm=S04, Baseline=S06, Settings=S07. */
sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Detail(val item: Consumable) : Screen
    /** [item]: 지금 보는 탭. [from]: S02를 연 S01 항목(Back 복귀 대상, QA-016). 탭을 바꿔도 [from]은 유지합니다. */
    data class History(val item: Consumable, val from: Consumable = item) : Screen
    data class Edit(val item: Consumable, val recordId: Long, val from: Consumable = item) : Screen
    data class Confirm(val item: Consumable) : Screen
    data class Baseline(val item: Consumable, val returnTo: Screen) : Screen
}

/** S03 편집 초안. 저장 전까지 기록에 쓰지 않습니다. */
data class RecordDraft(val recordKm: Float, val replacedAtMillis: Long)

data class AppUiState(
    val restricted: Boolean = false,
    val reading: OdometerReading = OdometerReading.Checking,
    val screen: Screen = Screen.Home,
    val records: List<ReplacementRecord> = emptyList(),
    val baselines: Map<Consumable, Int> = emptyMap(),
    /** S06에서 고르는 중인 기준 거리. 저장 전 초안이며 null이면 미선택. */
    val baselineDraft: Int? = null,
    /** S03에서 고치는 중인 기록 초안. */
    val recordDraft: RecordDraft? = null,
    /** 홈에 보일 소모품(S07 설정). */
    val enabledItems: Set<Consumable> = Consumable.DEFAULT_ENABLED,
    /** 화면에 쓰는 동력 방식: 사용자가 고른 값([kindOverride]), 없으면 자동 판정값([detectedKind]). */
    val vehicleKind: VehicleKind = VehicleKind.UNKNOWN,
    /** INFO_FUEL_TYPE 자동 판정값. */
    val detectedKind: VehicleKind = VehicleKind.UNKNOWN,
    /** S07에서 직접 고른 동력 방식. null이면 자동. */
    val kindOverride: VehicleKind? = null,
    /** S05 보기: 임박 순 목록(true) / 탑뷰(false). */
    val homeListView: Boolean = false,
    /** 차량이 알려 주는 액량 신호(아이디어 ③). 내 기록 계산에는 쓰지 않고 S01에 따로 보여 줍니다. */
    val signals: Map<VehicleSignal, SignalReading> = emptyMap(),
) {
    /**
     * 홈·이력 탭에 보일 항목(차 앞쪽부터). 켠 항목 중 이 차량 동력 방식에 맞는 것만입니다
     * (전기차면 내연기관 전용, 내연기관이면 전기차 전용을 숨김. 모르거나 하이브리드면 모두).
     */
    val visibleItems: List<Consumable>
        get() = Consumable.ordered(enabledItems.filterNot { it.notApplicableTo(vehicleKind) })

    /** 이 차량 동력 방식에 맞는 모든 항목(이력 탭, 켜고 끔과 무관). */
    val applicableItems: List<Consumable>
        get() = Consumable.ordered(Consumable.entries.filterNot { it.notApplicableTo(vehicleKind) })

    val refreshing: Boolean get() = reading == OdometerReading.Checking

    /** S05에서 선택된 항목(landscape 오른쪽 패널). */
    val selected: Consumable?
        get() = when (val s = screen) {
            Screen.Home, Screen.Settings -> null
            is Screen.Detail -> s.item
            // 이력 탭은 꺼진 항목도 고를 수 있으므로 선택 항목은 S02를 연 S01 항목입니다(QA-016).
            is Screen.History -> s.from
            is Screen.Edit -> s.from
            is Screen.Confirm -> s.item
            is Screen.Baseline -> s.item
        }

    fun status(item: Consumable): ConsumableStatus =
        ConsumableCalculator.status(reading, records.filter { it.item == item }, baselines[item])

    fun lastRecord(item: Consumable): ReplacementRecord? =
        ConsumableCalculator.lastRecord(records.filter { it.item == item })
}
