package com.pleos.consumables

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle

/**
 * 소모품 교체 거리 기록 화면 상태. 차량 값 조회는 "화면이 보이고(onStart~onStop) + 주행 규제 초기값을 받았고
 * + 규제가 없을 때"만 합니다.
 * - 숨김·규제: 진행 중 응답 무효화, 값은 "확인 중"으로 되돌림(오래된 값을 현재 값처럼 보이지 않게, UI_SPEC §0)
 * - 복귀: 규제 초기값을 먼저 받은 뒤 다시 조회
 * - Vehicle release는 ViewModel이 끝날 때(onCleared). 회전·리사이즈로는 해제하지 않습니다.
 *
 * 차량 값은 저장·복원하지 않습니다. 교체 기록·기준 거리는 [RecordStore]에 두고,
 * 화면·선택 항목·편집 초안은 [SavedStateHandle]로 프로세스 재생성 뒤에도 복원합니다(QA-007).
 */
class MainViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {

    private val gateway = VehicleGateway(application)
    private val source = OdometerSource(gateway, ::onReading)
    private val signalSource = VehicleSignalSource(gateway) { signal, value ->
        update { it.copy(signals = it.signals + (signal to value)) }
    }
    private val store = RecordStore(application)

    // 규제 초기값을 받기 전에는 값을 보이지 않습니다(정차 중 전용).
    private val _state = MutableLiveData(
        restoreNavigation(store.records())
            .copy(
                restricted = true, baselines = store.baselines(), enabledItems = store.enabledItems(),
                homeListView = store.homeListView(),
                kindOverride = store.vehicleKindOverride(),
                vehicleKind = store.vehicleKindOverride() ?: VehicleKind.UNKNOWN,
            ),
    )
    val state: LiveData<AppUiState> = _state

    private var visible = false
    private var restriction: UxRestrictionState? = null // null = 이번 표시에서 아직 초기값을 받지 못함
    private var reading = false

    /** S04 저장을 누른 뒤 재조회 결과를 기다리는 중인 항목과 화면에 보였던 거리(QA-008). */
    private var pendingSave: Pair<Consumable, Float>? = null

    private val current: AppUiState get() = _state.value ?: AppUiState()

    fun onScreenStarted() {
        visible = true
        reconcile()
    }

    fun onScreenStopped() {
        visible = false
        restriction = null
        reconcile()
        update { it.copy(restricted = true) } // 복귀 후 초기값을 받을 때까지 가림
    }

    /** DrivingRestrictionMonitor가 메인 스레드에서 부릅니다(onStart 초기값 + 변경). */
    fun onRestrictionsChanged(state: UxRestrictionState) {
        restriction = state
        update { it.copy(restricted = state.restricted) }
        reconcile()
    }

    fun refresh() {
        if (current.refreshing || pendingSave != null) return // 요청 중 중복 탭 방지
        source.refresh()
        signalSource.refresh()
    }

    // ── 화면 이동 ──

    /** S05 아이콘 선택 → S01. */
    fun select(item: Consumable) = update { it.copy(screen = Screen.Detail(item)) }

    /** Gleo 음성 요청(아이디어 ⑦)으로 S01을 엽니다. 이 차량 동력 방식에 맞지 않는 항목이면 열지 않고 false. */
    fun openFromVoice(item: Consumable): Boolean {
        if (item.notApplicableTo(current.vehicleKind)) return false
        update { it.copy(screen = Screen.Detail(item), baselineDraft = null, recordDraft = null) }
        return true
    }

    /** S05 `탑뷰`/`목록` 전환. 선택한 보기는 기기에 남깁니다(PLAN.md §3 기능 6). */
    fun setHomeListView(list: Boolean) {
        store.setHomeListView(list)
        update { it.copy(homeListView = list) }
    }

    /** S01 교체 기록 → S04. 차량 값이 없거나 비교 불가면 열지 않습니다. */
    fun openConfirm(item: Consumable) {
        if (!current.status(item).canAddRecord) return
        update { it.copy(screen = Screen.Confirm(item)) }
    }

    /** S01·S04 기준 거리 → S06. 초안은 저장된 값에서 시작합니다(없으면 미선택). */
    fun openBaseline(item: Consumable) =
        update { it.copy(screen = Screen.Baseline(item, it.screen), baselineDraft = it.baselines[item]) }

    init {
        // 동력 방식은 차량 값과 달리 바뀌지 않으므로 한 번만 읽습니다. 사용자가 S07에서 고른 값이 있으면 그쪽이 우선입니다.
        VehicleInfoSource.readKind(application) { kind -> applyKind(detected = kind, override = current.kindOverride) }
    }

    /** S07 동력 방식 직접 선택(null = 자동). CAR_INFO가 승인되지 않아도 항목을 맞게 걸러 낼 수 있게 합니다. */
    fun setVehicleKindOverride(kind: VehicleKind?) {
        store.setVehicleKindOverride(kind)
        applyKind(detected = current.detectedKind, override = kind)
    }

    /**
     * 동력 방식을 정하고, 지금 보는 항목이 이 차량에 맞지 않게 되면 홈(미선택)으로 돌립니다(QA-014).
     */
    private fun applyKind(detected: VehicleKind, override: VehicleKind?) {
        val kind = override ?: detected
        update {
            val hidden = it.selected?.notApplicableTo(kind) == true
            val next = it.copy(vehicleKind = kind, detectedKind = detected, kindOverride = override)
            if (hidden) next.copy(screen = Screen.Home, baselineDraft = null, recordDraft = null) else next
        }
    }

    // ── S07 소모품 설정 ──

    fun openSettings() = update { it.copy(screen = Screen.Settings) }

    fun setItemEnabled(item: Consumable, enabled: Boolean) {
        val next = if (enabled) current.enabledItems + item else current.enabledItems - item
        store.setEnabledItems(next)
        update { it.copy(enabledItems = next) }
    }

    /** 화면 안의 Back·취소. 최상위(S05)에서는 false를 돌려 앱을 숨기게 합니다. */
    fun back(): Boolean {
        val next = when (val s = current.screen) {
            Screen.Home -> return false
            Screen.Settings -> Screen.Home
            is Screen.Detail -> Screen.Home
            // 꺼진 항목 탭을 보고 있어도 S02를 연 항목의 S01로 돌아갑니다. 그 항목도 숨겨졌으면 홈(QA-016).
            is Screen.History -> if (s.from in current.visibleItems) Screen.Detail(s.from) else Screen.Home
            is Screen.Edit -> Screen.History(s.item, s.from)
            is Screen.Confirm -> Screen.Detail(s.item)
            is Screen.Baseline -> s.returnTo
        }
        update { it.copy(screen = next, baselineDraft = null, recordDraft = null) }
        return true
    }

    // ── S02 이력 ──

    /** S01 이력 → S02(선택 항목 탭). 탭 전환도 같은 함수를 쓰며, 이때 S02를 연 항목([Screen.History.from])은 유지합니다. */
    fun openHistory(item: Consumable) = update {
        val from = (it.screen as? Screen.History)?.from ?: item
        it.copy(screen = Screen.History(item, from))
    }

    fun deleteRecord(id: Long) {
        if (current.restricted) return
        store.deleteRecord(id)
        update { it.copy(records = store.records()) }
    }

    /** 모든 항목의 기록과 기준 거리를 지웁니다. 삭제 후 S01·S05는 같은 상태 판정으로 다시 그려집니다. */
    fun deleteAll() {
        if (current.restricted) return
        store.deleteAll()
        update { it.copy(records = store.records(), baselines = store.baselines()) }
    }

    // ── S03 기록 수정 ──

    fun openEdit(recordId: Long) {
        val record = current.records.firstOrNull { it.id == recordId } ?: return
        update {
            it.copy(
                screen = Screen.Edit(record.item, record.id, (it.screen as? Screen.History)?.from ?: record.item),
                recordDraft = RecordDraft(record.recordKm, record.replacedAtMillis),
            )
        }
    }

    fun adjustRecordKm(deltaKm: Float) {
        val draft = current.recordDraft ?: return
        val next = draft.recordKm + deltaKm
        if (ConsumableCalculator.isValidRecordKm(next, editMaxKm())) update { it.copy(recordDraft = draft.copy(recordKm = next)) }
    }

    fun adjustRecordTime(deltaMillis: Long) {
        val draft = current.recordDraft ?: return
        val next = draft.replacedAtMillis + deltaMillis
        if (ConsumableCalculator.isValidReplacedAt(next, System.currentTimeMillis())) {
            update { it.copy(recordDraft = draft.copy(replacedAtMillis = next)) }
        }
    }

    /** S03에서 고치는 기록의 거리 상한(현재 누적 주행거리 기준). */
    private fun editMaxKm(): Float {
        val s = current
        val original = (s.screen as? Screen.Edit)?.let { e -> s.records.firstOrNull { it.id == e.recordId } }?.recordKm ?: 0f
        return ConsumableCalculator.editMaxKm((s.reading as? OdometerReading.Received)?.km, original)
    }

    fun saveEdit() {
        val s = current
        val screen = s.screen as? Screen.Edit ?: return
        val draft = s.recordDraft ?: return
        if (s.restricted || s.records.none { it.id == screen.recordId }) return
        if (!ConsumableCalculator.isValidRecordKm(draft.recordKm, editMaxKm()) ||
            !ConsumableCalculator.isValidReplacedAt(draft.replacedAtMillis, System.currentTimeMillis())
        ) return
        store.updateRecord(screen.recordId, draft.recordKm, draft.replacedAtMillis)
        update { it.copy(records = store.records(), screen = Screen.History(screen.item, screen.from), recordDraft = null) }
    }

    // ── S04 새 기록 저장 ──

    /**
     * 저장 직전 정차 허용·capability·권한·수신값·거리 비교 상태를 다시 확인합니다(UI_SPEC S04 동작, QA-008).
     * 차량 값을 한 번 더 조회해 그 결과로 [completePendingSave]에서 저장 여부를 정합니다.
     */
    /**
     * '기준 다시 잡기'(DECISIONS 2026-11-10 #3): 거리 비교 불가 상태에서 사용자가 확인하면 지금 누적 주행거리로
     * 새 기준 기록을 저장합니다. 이전 기록은 이력에 그대로 두고 자동으로 이어 붙이지 않습니다.
     */
    fun rebaseline(item: Consumable, confirmed: OdometerReading.Received) {
        val s = current
        if (s.restricted || s.reading !is OdometerReading.Received || s.status(item) !is ConsumableStatus.ComparisonBlocked) return
        // QA-021: 확인창을 띄운 뒤 수신값이 바뀌어도 사용자가 확인한 거리만 저장합니다.
        store.addRecord(item, confirmed.km, confirmed.checkedAtMillis, System.currentTimeMillis(), rebased = true)
        update { it.copy(records = store.records()) }
    }

    fun saveRecord() {
        val s = current
        val item = (s.screen as? Screen.Confirm)?.item ?: return
        val received = s.reading as? OdometerReading.Received ?: return
        if (s.restricted || pendingSave != null || !s.status(item).canAddRecord) return
        pendingSave = item to received.km
        source.refresh()
    }

    /**
     * 재조회 결과로 저장을 끝냅니다. 미지원·권한 없음·값 미수신이면 저장하지 않고 S04에 원인이 보입니다.
     * 사용자가 확인한 거리와 새 값이 다르면 저장하지 않고 새 값으로 다시 확인받습니다.
     */
    private fun completePendingSave(value: OdometerReading) {
        val (item, shownKm) = pendingSave ?: return
        if (value == OdometerReading.Checking) return
        pendingSave = null
        val s = current
        if (value !is OdometerReading.Received || value.km != shownKm) return
        if (s.restricted || s.screen != Screen.Confirm(item) || !s.status(item).canAddRecord) return
        store.addRecord(item, value.km, value.checkedAtMillis, System.currentTimeMillis())
        update { it.copy(records = store.records(), screen = Screen.Detail(item)) }
    }

    // ── S06 기준 거리 ──

    /** S06 입력 칸의 숫자(입력이 비었거나 숫자가 아니면 null). 범위 검사는 저장 때 합니다. */
    fun setBaselineDraft(km: Int?) {
        if (current.baselineDraft != km) update { it.copy(baselineDraft = km) }
    }

    fun saveBaseline() {
        val s = current
        val screen = s.screen as? Screen.Baseline ?: return
        val draft = s.baselineDraft ?: return
        if (s.restricted || !ConsumableCalculator.isValidBaseline(draft)) return
        store.setBaseline(screen.item, draft)
        update { it.copy(baselines = store.baselines(), screen = screen.returnTo, baselineDraft = null) }
    }

    private fun reconcile() {
        val allowed = visible && restriction?.restricted == false
        if (allowed && !reading) {
            reading = true
            source.start()
            signalSource.start()
        } else if (!allowed && reading) {
            reading = false
            pendingSave = null // 숨김·규제 중에는 저장하지 않습니다.
            source.stop()
            signalSource.stop()
            update { it.copy(reading = OdometerReading.Checking, signals = emptyMap()) }
        }
    }

    private fun onReading(value: OdometerReading) {
        update { it.copy(reading = value) }
        completePendingSave(value)
    }

    private inline fun update(change: (AppUiState) -> AppUiState) {
        val next = change(current)
        _state.value = next
        saveNavigation(next)
    }

    // ── 프로세스 재생성 복원(QA-007): 화면·선택 항목·편집 초안. 차량 값은 복원하지 않고 다시 조회합니다. ──

    private fun saveNavigation(state: AppUiState) {
        saved[KEY_SCREEN] = ScreenCodec.encode(state.screen)
        saved[KEY_BASELINE_DRAFT] = state.baselineDraft
        saved[KEY_RECORD_DRAFT_KM] = state.recordDraft?.recordKm
        saved[KEY_RECORD_DRAFT_AT] = state.recordDraft?.replacedAtMillis
    }

    private fun restoreNavigation(records: List<ReplacementRecord>): AppUiState {
        val screen = saved.get<String>(KEY_SCREEN)?.let(ScreenCodec::decode)
            // 복원할 기록이 그새 지워졌으면 이력으로 돌립니다.
            ?.let { if (it is Screen.Edit && records.none { r -> r.id == it.recordId }) Screen.History(it.item, it.from) else it }
            ?: Screen.Home
        val km = saved.get<Float>(KEY_RECORD_DRAFT_KM)
        val at = saved.get<Long>(KEY_RECORD_DRAFT_AT)
        return AppUiState(
            screen = screen,
            records = records,
            baselineDraft = saved.get<Int>(KEY_BASELINE_DRAFT)?.takeIf { screen is Screen.Baseline },
            recordDraft = if (screen is Screen.Edit && km != null && at != null) RecordDraft(km, at) else null,
        )
    }

    private companion object {
        const val KEY_SCREEN = "screen"
        const val KEY_BASELINE_DRAFT = "baselineDraft"
        const val KEY_RECORD_DRAFT_KM = "recordDraftKm"
        const val KEY_RECORD_DRAFT_AT = "recordDraftAt"
    }

    override fun onCleared() {
        source.stop()
        signalSource.stop()
        gateway.release()
    }
}
