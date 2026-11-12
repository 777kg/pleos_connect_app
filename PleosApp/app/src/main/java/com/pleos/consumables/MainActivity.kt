package com.pleos.consumables

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pleos.consumables.databinding.ActivityMainBinding
import com.pleos.consumables.databinding.ViewHomeRowBinding
import com.pleos.consumables.databinding.ViewItemIconBinding
import com.pleos.consumables.databinding.ViewRecordRowBinding
import com.pleos.consumables.databinding.ViewSettingRowBinding
import java.text.NumberFormat
import java.util.Date
import kotlin.math.roundToLong

/**
 * 소모품 교체 거리 기록(docs/plan/service-mileage-log/ui/UI_SPEC.md).
 * 화면: S05 홈(차량 탑뷰) → S01 상세 → S04 새 기록 확인 / S06 기준 거리. portrait는 한 화면씩,
 * landscape는 왼쪽 S05·오른쪽 S01이고 S04·S06은 전체를 덮습니다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val numberFormat = NumberFormat.getIntegerInstance()
    private var confirmDialog: AlertDialog? = null

    /** landscape(2칸) 배치인지. selectHint는 layout-land에만 있습니다. */
    private val twoPane: Boolean get() = binding.selectHint != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycle.addObserver(DrivingRestrictionMonitor(applicationContext, viewModel::onRestrictionsChanged))
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = viewModel.onScreenStarted()
            override fun onStop(owner: LifecycleOwner) = viewModel.onScreenStopped()
        })

        bindHome()
        bindDetail()
        bindSettings()
        bindHistory()
        bindEdit()
        bindConfirm()
        bindBaseline()
        viewModel.state.observe(this, ::render)

        // 회전·재생성 때 같은 음성 요청을 다시 처리하지 않습니다.
        if (savedInstanceState == null) handleGleoIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // CCP 하드키 포함. 화면 안에서 한 단계 뒤로, 최상위(S05)에서는 숨기기만 합니다. finish() 금지(ch04).
                if (!viewModel.back()) moveTaskToBack(true)
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGleoIntent(intent)
    }

    /**
     * 아이디어 ⑦ Gleo `OPEN_CONSUMABLE_RECORD`(component=activity): 그 항목의 S01을 엽니다.
     * 주행 중이면 기존 규제 화면이 내용을 가리고, 정차하면 S01이 보입니다.
     */
    private fun handleGleoIntent(intent: Intent?) {
        if (intent?.action != GleoActions.OPEN_RECORD) return
        // QA-023: 보내는 쪽 패키지가 확인되지 않아 막지 않고 기록만 남깁니다(실제 Gleo 호출 때 확인용).
        android.util.Log.i("GleoActions", "OPEN_CONSUMABLE_RECORD from ${referrer?.host ?: "unknown"}")
        val function = GleoActions.OPEN_RECORD
        val raw = GleoActions.paramFrom(intent, GleoActions.PARAM_ITEM)
        val item = GleoActions.itemOf(raw) { getString(it.nameRes) }
        val answer = when {
            item == null -> GleoActions.missingItemAnswer(function, raw)
            !viewModel.openFromVoice(item) -> GleoActions.notApplicableAnswer(function, getString(item.nameRes))
            else -> GleoActions.openAnswer(getString(item.nameRes), GleoActions.isParked(this))
        }
        GleoActions.send(this, answer)
    }

    // ── 이벤트 연결 ──

    private fun bindHome() = with(binding.home) {
        settingsButton.setOnClickListener { viewModel.openSettings() }
        viewTopviewButton.setOnClickListener { viewModel.setHomeListView(false) }
        viewListButton.setOnClickListener { viewModel.setHomeListView(true) }
        urgentSummaryButton.setOnClickListener { (it.tag as? Consumable)?.let(viewModel::select) }
    }

    private fun bindSettings() = with(binding.settings) {
        settingsBackButton.setOnClickListener { viewModel.back() }
    }

    private fun bindDetail() = with(binding.detail) {
        refreshButton.setOnClickListener { viewModel.refresh() }
        addRecordButton.setOnClickListener { selectedItem()?.let(viewModel::openConfirm) }
        editBaselineButton.setOnClickListener { selectedItem()?.let(viewModel::openBaseline) }
        historyButton.setOnClickListener { selectedItem()?.let(viewModel::openHistory) }
        detailBackButton.setOnClickListener { viewModel.back() }
        rebaselineButton.setOnClickListener {
            val item = selectedItem() ?: return@setOnClickListener
            val received = viewModel.state.value?.reading as? OdometerReading.Received ?: return@setOnClickListener
            confirmAction(
                getString(R.string.rebaseline_confirm, km(received.km)),
                cancelRes = R.string.cancel,
                confirmRes = R.string.rebaseline,
                onConfirm = { viewModel.rebaseline(item, received) },
            )
        }
    }

    private fun bindHistory() = with(binding.history) {
        historyBackButton.setOnClickListener { viewModel.back() }
        deleteAllButton.setOnClickListener {
            // 전체 삭제는 기준 거리도 지운다는 것과 되돌릴 수 없음을 먼저 알립니다(UI_SPEC S02-06).
            confirmDelete(getString(R.string.delete_all_confirm), viewModel::deleteAll)
        }
    }

    private fun bindEdit() = with(binding.edit) {
        kmMinus.setOnClickListener { viewModel.adjustRecordKm(-ConsumableCalculator.RECORD_STEP_KM) }
        kmPlus.setOnClickListener { viewModel.adjustRecordKm(ConsumableCalculator.RECORD_STEP_KM) }
        dayMinus.setOnClickListener { viewModel.adjustRecordTime(-DAY_MS) }
        dayPlus.setOnClickListener { viewModel.adjustRecordTime(DAY_MS) }
        hourMinus.setOnClickListener { viewModel.adjustRecordTime(-HOUR_MS) }
        hourPlus.setOnClickListener { viewModel.adjustRecordTime(HOUR_MS) }
        minMinus.setOnClickListener { viewModel.adjustRecordTime(-TEN_MIN_MS) }
        minPlus.setOnClickListener { viewModel.adjustRecordTime(TEN_MIN_MS) }
        // 저장 전 확인(UI_SPEC S03-06): 변경 전후와 마지막 기록이 바뀔 수 있음을 보여 주고 '계속 수정'/'저장'을 고릅니다.
        saveEditButton.setOnClickListener {
            confirmAction(
                "${beforeAfterText.text}\n${getString(R.string.edited_source)}\n${getString(R.string.latest_may_change)}",
                cancelRes = R.string.keep_editing,
                confirmRes = R.string.save_changes,
                onConfirm = viewModel::saveEdit,
            )
        }
        editCancelButton.setOnClickListener { viewModel.back() }
        editBackButton.setOnClickListener { viewModel.back() }
        listOf(kmMinus, kmPlus, dayMinus, dayPlus, hourMinus, hourPlus, minMinus, minPlus).forEach(RepeatOnHold::attach)
    }

    private fun bindConfirm() = with(binding.confirm) {
        saveRecordButton.setOnClickListener { viewModel.saveRecord() }
        confirmCancelButton.setOnClickListener { viewModel.back() }
        setBaselineButton.setOnClickListener { selectedItem()?.let(viewModel::openBaseline) }
    }

    private fun bindBaseline() = with(binding.baseline) {
        baselineInput.doAfterTextChanged { text -> viewModel.setBaselineDraft(text?.toString()?.toIntOrNull()) }
        baselineInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                viewModel.saveBaseline()
            }
            false
        }
        baselineBackButton.setOnClickListener { viewModel.back() }
        baselineCancelButton.setOnClickListener { viewModel.back() }
        saveBaselineButton.setOnClickListener { viewModel.saveBaseline() }
        // S06-09 빠른 선택: 누르면 입력칸을 그 값으로 채웁니다(render가 입력칸을 맞춤).
        presetButtons().forEach { (km, button) ->
            button.text = getString(R.string.km_value, numberFormat.format(km))
            button.setOnClickListener { hideKeyboard(); viewModel.setBaselineDraft(km) }
        }
        // S06-10 내 평균으로 채우기
        baselineSide.useAverageButton.setOnClickListener {
            (it.tag as? Int)?.let { km -> hideKeyboard(); viewModel.setBaselineDraft(km) }
        }
    }

    private fun presetButtons(): List<Pair<Int, Button>> = with(binding.baseline.baselineSide) {
        ConsumableCalculator.BASELINE_PRESETS_KM.zip(listOf(preset5000, preset10000, preset15000, preset20000))
    }

    // ── 그리기 ──

    private fun render(state: AppUiState) {
        // 정차 중 전용: 규제 중에는 차량 그림·값·기록·조작을 모두 숨기고 안내만 남깁니다(ch03).
        binding.restrictedMessage.visibility = visibleIf(state.restricted)
        if (state.restricted || (state.screen !is Screen.History && state.screen !is Screen.Edit && state.screen !is Screen.Detail)) {
            // 주행 규제·화면 이탈 시 확인 대화상자를 남겨 두지 않습니다.
            confirmDialog?.dismiss()
            confirmDialog = null
        }
        if (state.restricted || state.screen !is Screen.Baseline) {
            // 주행 중에는 키보드를 쓰지 않습니다(ch03 NO_KEYBOARD). 화면을 벗어나도 닫습니다.
            hideKeyboard()
        }
        val screen = if (state.restricted) null else state.screen
        // S05·S01 밖의 화면은 두 방향 모두 전체를 덮습니다.
        val overlay = screen is Screen.Settings || screen is Screen.History || screen is Screen.Edit ||
            screen is Screen.Confirm || screen is Screen.Baseline

        val showHome = screen != null && !overlay && (twoPane || screen == Screen.Home)
        val showDetail = screen is Screen.Detail
        binding.home.root.visibility = visibleIf(showHome)
        binding.detail.root.visibility = visibleIf(showDetail)
        binding.selectHint?.visibility = visibleIf(twoPane && screen == Screen.Home)
        binding.panes?.visibility = visibleIf(screen != null && !overlay)
        binding.settings.root.visibility = visibleIf(screen == Screen.Settings)
        binding.history.root.visibility = visibleIf(screen is Screen.History)
        binding.edit.root.visibility = visibleIf(screen is Screen.Edit)
        binding.confirm.root.visibility = visibleIf(screen is Screen.Confirm)
        binding.baseline.root.visibility = visibleIf(screen is Screen.Baseline)
        if (screen == null) return

        if (showHome) renderHome(state)
        when (screen) {
            is Screen.Detail -> renderDetail(state, screen.item)
            is Screen.History -> renderHistory(state, screen.item)
            is Screen.Edit -> renderEdit(state, screen)
            is Screen.Confirm -> renderConfirm(state, screen.item)
            is Screen.Baseline -> renderBaseline(state, screen.item)
            Screen.Settings -> renderSettings(state)
            Screen.Home -> Unit
        }
    }

    private fun renderHome(state: AppUiState) {
        renderHomeHeader(state)
        syncHomeIcons(state.visibleItems)
        binding.home.noItemsText.visibility = visibleIf(state.visibleItems.isEmpty())
        homeIcons.forEach { (item, icon) ->
            val status = state.status(item)
            // 계산 불가여도 어떤 소모품인지 사진으로 알아볼 수 있게 대표 사진을 흐리게 둡니다. 이유는 상태 문구로 알립니다.
            icon.image.setImageResource(ConsumableImages.homeDrawableFor(this, item, status))
            icon.image.imageAlpha = ConsumableImages.imageAlpha(status)
            icon.status.text = shortStatus(status)
            icon.root.isSelected = state.selected == item
            icon.root.contentDescription = imageDescription(item, status)
        }
    }

    /** S02-08 내 교체 주기 통계: 기록 0건이면 숨기고, 1건이면 평균 대신 안내를 둡니다. */
    private fun renderStats(stats: ConsumableCalculator.IntervalStats) = with(binding.history) {
        statsCard.visibility = visibleIf(stats.count > 0)
        statsCountText.text = getString(R.string.stats_count, stats.count)
        statsAverageText.text = when {
            stats.count < 2 -> getString(R.string.stats_need_two)
            stats.averageKm == null || stats.averageMonths == null -> getString(R.string.stats_no_interval)
            else -> getString(
                R.string.stats_average,
                km(stats.averageKm.toFloat()),
                // 반올림 전 값으로 1개월 미만을 판단합니다(29일이 1.0개월로 보이지 않게, QA-019).
                if (stats.averageMonths < 1.0) getString(R.string.stats_under_month)
                else getString(R.string.stats_months, String.format(java.util.Locale.getDefault(), "%.1f", stats.averageMonths)),
            )
        }
        // 같은 거리와 줄어든 거리를 나눠 알립니다(QA-018).
        statsExcludedText.visibility = visibleIf(stats.excludedIntervals > 0)
        statsExcludedText.text = getString(
            R.string.stats_excluded,
            listOfNotNull(
                stats.sameKmIntervals.takeIf { it > 0 }?.let { getString(R.string.stats_excluded_same, it) },
                stats.decreasedKmIntervals.takeIf { it > 0 }?.let { getString(R.string.stats_excluded_decreased, it) },
            ).joinToString(", "),
        )
    }

    /** S05-09·10·11: 보기 전환, `가장 임박` 요약, 임박 순 목록(PLAN.md §3 기능 6). */
    private fun renderHomeHeader(state: AppUiState) = with(binding.home) {
        val list = state.homeListView
        markSelected(viewTopviewButton, !list)
        markSelected(viewListButton, list)
        topviewRow.visibility = visibleIf(!list)
        listScroll.visibility = visibleIf(list)

        val urgent = ConsumableCalculator.mostUrgent(state.visibleItems, state::status)
        urgentSummaryButton.visibility = visibleIf(urgent != null)
        urgentSummaryButton.tag = urgent?.first
        if (urgent != null) {
            val (item, normal) = urgent
            urgentSummaryButton.text = if (normal.remaining.reached) {
                getString(R.string.urgent_summary_reached, getString(item.nameRes))
            } else {
                getString(R.string.urgent_summary, getString(item.nameRes), normal.remaining.displayPercent)
            }
        }
        if (list) syncHomeList(state)
    }

    /** 임박 순 목록: 순서가 바뀔 때만 줄을 다시 만들고, 값은 매번 갱신합니다. */
    private fun syncHomeList(state: AppUiState) = with(binding.home) {
        val ordered = ConsumableCalculator.urgencyOrder(state.visibleItems, state::status)
        if (homeRows.keys.toList() != ordered) {
            homeList.removeAllViews()
            homeRows.clear()
            ordered.forEach { item ->
                val row = ViewHomeRowBinding.inflate(layoutInflater, homeList, false)
                row.rowName.setText(item.nameRes)
                row.root.setOnClickListener { viewModel.select(item) }
                homeList.addView(row.root)
                homeRows[item] = row
            }
        }
        homeRows.forEach { (item, row) ->
            val status = state.status(item)
            row.rowImage.setImageResource(ConsumableImages.homeDrawableFor(this@MainActivity, item, status))
            row.rowImage.imageAlpha = ConsumableImages.imageAlpha(status)
            row.rowStatus.text = if (status is ConsumableStatus.Normal && !status.remaining.reached) {
                getString(R.string.list_status_normal, status.remaining.displayPercent, km(status.elapsedKm))
            } else {
                shortStatus(status)
            }
            row.root.isSelected = state.selected == item
            row.root.contentDescription = listOf(getString(item.nameRes), row.rowStatus.text).joinToString(", ")
        }
    }

    private fun renderDetail(state: AppUiState, item: Consumable) = with(binding.detail) {
        val status = state.status(item)
        itemName.setText(item.nameRes)
        // S01-11 알아두기: 항목 역할·교체가 늦어지면(일반 정보)
        val (roleRes, lateRes) = ConsumableTips.of(item)
        tipRoleText.text = getString(R.string.tip_role, getString(roleRes))
        tipLateText.text = getString(R.string.tip_late, getString(lateRes))
        // 2칸에서는 왼쪽 탑뷰가 함께 보이므로 뒤로 버튼 없이 선택 항목만 표시합니다(UI_SPEC S01-01).
        detailBackButton.visibility = visibleIf(!twoPane)
        itemImage.setImageResource(ConsumableImages.homeDrawableFor(this@MainActivity, item, status))
        itemImage.imageAlpha = ConsumableImages.imageAlpha(status)
        itemImage.contentDescription = imageDescription(item, status)

        primaryText.text = when (status) {
            is ConsumableStatus.Normal ->
                if (status.remaining.reached) getString(R.string.distance_reached)
                else getString(R.string.remaining_percent, status.remaining.displayPercent)
            is ConsumableStatus.BaselineUnset -> getString(R.string.baseline_unset)
            else -> shortStatus(status)
        }
        val elapsed = when (status) {
            is ConsumableStatus.Normal -> status.elapsedKm
            is ConsumableStatus.BaselineUnset -> status.elapsedKm
            else -> null
        }
        secondaryText.visibility = visibleIf(elapsed != null)
        elapsed?.let { secondaryText.text = getString(R.string.elapsed_km, km(it)) }

        val baseline = state.baselines[item]
        // 미설정이면 핵심 문구나 상태가 이미 알려 주므로, 설정된 값만 보조 줄로 보여 줍니다.
        baselineText.visibility = visibleIf(baseline != null)
        baseline?.let { baselineText.text = getString(R.string.baseline_km, numberFormat.format(it)) }

        // 현재 값은 실제 수신값만. 못 받았으면 숨깁니다(이전 값을 현재 값처럼 보이지 않게).
        val received = state.reading as? OdometerReading.Received
        currentKmText.visibility = visibleIf(received != null)
        checkedAtText.visibility = visibleIf(received != null)
        received?.let {
            currentKmText.text = getString(R.string.current_km, km(it.km))
            checkedAtText.text = getString(R.string.checked_at, time(it.checkedAtMillis))
        }

        val last = state.lastRecord(item)
        lastReplacementText.visibility = visibleIf(last != null)
        editedSourceText.visibility = visibleIf(last?.edited == true)
        last?.let { lastReplacementText.text = getString(R.string.last_replacement, km(it.recordKm), time(it.replacedAtMillis)) }

        renderSignal(state, item)

        addRecordButton.isEnabled = status.canAddRecord
        blockedText.visibility = visibleIf(status is ConsumableStatus.ComparisonBlocked)
        rebaselineRow.visibility = visibleIf(status is ConsumableStatus.ComparisonBlocked && state.reading is OdometerReading.Received)
        refreshButton.isEnabled = !state.refreshing
        refreshButton.setText(if (state.refreshing) R.string.refreshing else R.string.refresh)
    }

    /** 아이디어 ③: 이 항목과 짝인 차량 신호를 받았을 때만 보여 줍니다(조회 중·미지원이면 숨김). */
    private fun renderSignal(state: AppUiState, item: Consumable) = with(binding.detail) {
        val signal = VehicleSignal.of(item)
        val received = signal?.let { state.signals[it] } as? SignalReading.Received
        signalCard.visibility = visibleIf(signal != null && received != null)
        if (signal == null || received == null) return@with
        val label = getString(signal.labelRes)
        val level = getString(received.level.labelRes)
        signalValueText.text = getString(R.string.signal_value, label, level)
        signalCheckedText.text = getString(R.string.checked_at, time(received.checkedAtMillis))
        signalCard.contentDescription = listOf(getString(R.string.signal_title), signalValueText.text, getString(R.string.signal_note)).joinToString(", ")
    }

    private fun renderHistory(state: AppUiState, item: Consumable) = with(binding.history) {
        // 꺼 둔 항목의 이력도 볼 수 있게, 동력 방식에 맞는 항목은 모두 탭으로 둡니다(QA-013).
        syncHistoryTabs(state.applicableItems, item)

        val records = ConsumableCalculator.historyOrder(state.records.filter { it.item == item })
        val last = state.lastRecord(item)
        val blocked = state.status(item) is ConsumableStatus.ComparisonBlocked
        recordList.removeAllViews()
        records.forEach { record ->
            val row = ViewRecordRowBinding.inflate(layoutInflater, recordList, true)
            val label = recordLabel(record)
            row.rowText.text = label
            row.latestBadge.visibility = visibleIf(record == last)
            row.blockedBadge.visibility = visibleIf(record == last && blocked)
            row.editedBadge.visibility = visibleIf(record.edited)
            row.rebasedBadge.visibility = visibleIf(record.rebased)
            row.editButton.setOnClickListener { viewModel.openEdit(record.id) }
            row.deleteButton.setOnClickListener {
                confirmDelete(getString(R.string.delete_one_confirm, getString(item.nameRes), km(record.recordKm), time(record.replacedAtMillis))) {
                    viewModel.deleteRecord(record.id)
                }
            }
            row.editButton.contentDescription = "${getString(R.string.edit)}, $label"
            row.deleteButton.contentDescription = "${getString(R.string.delete)}, $label"
        }
        emptyText.visibility = visibleIf(records.isEmpty())
        renderStats(ConsumableCalculator.intervalStats(records))
        // 전체 삭제는 선택 탭과 상관없이 모든 항목에 적용됩니다. 지울 것이 없으면 비활성.
        deleteAllButton.isEnabled = state.records.isNotEmpty() || state.baselines.isNotEmpty()
    }

    private fun renderEdit(state: AppUiState, screen: Screen.Edit) = with(binding.edit) {
        editTitle.text = getString(R.string.edit_record_title, getString(screen.item.nameRes))
        val record = state.records.firstOrNull { it.id == screen.recordId }
        val draft = state.recordDraft
        // 다른 곳에서 지워진 기록이면 편집을 막고 S02로 돌아가게 합니다(UI_SPEC S03 '기록 없음').
        notFoundText.visibility = visibleIf(record == null || draft == null)
        editContent.visibility = visibleIf(record != null && draft != null)
        saveEditButton.visibility = visibleIf(record != null && draft != null)
        if (record == null || draft == null) return@with

        distanceText.text = getString(R.string.record_distance, km(draft.recordKm))
        timeText.text = getString(R.string.replacement_time, time(draft.replacedAtMillis))
        val now = System.currentTimeMillis()
        val maxKm = ConsumableCalculator.editMaxKm((state.reading as? OdometerReading.Received)?.km, record.recordKm)
        kmMinus.isEnabled = ConsumableCalculator.isValidRecordKm(draft.recordKm - ConsumableCalculator.RECORD_STEP_KM, maxKm)
        kmPlus.isEnabled = ConsumableCalculator.isValidRecordKm(draft.recordKm + ConsumableCalculator.RECORD_STEP_KM, maxKm)
        listOf(dayMinus to -DAY_MS, dayPlus to DAY_MS, hourMinus to -HOUR_MS, hourPlus to HOUR_MS, minMinus to -TEN_MIN_MS, minPlus to TEN_MIN_MS)
            .forEach { (button, delta) -> button.isEnabled = ConsumableCalculator.isValidReplacedAt(draft.replacedAtMillis + delta, now) }

        val changed = draft.recordKm != record.recordKm || draft.replacedAtMillis != record.replacedAtMillis
        beforeAfterText.text = getString(
            R.string.before_after,
            "${km(record.recordKm)} km · ${time(record.replacedAtMillis)}",
            "${km(draft.recordKm)} km · ${time(draft.replacedAtMillis)}",
        )
        editSourceText.visibility = visibleIf(changed || record.edited)
        saveEditButton.isEnabled = changed
    }

    private fun renderConfirm(state: AppUiState, item: Consumable) = with(binding.confirm) {
        val status = state.status(item)
        confirmItemName.setText(item.nameRes)
        val received = state.reading as? OdometerReading.Received
        receivedKmText.visibility = visibleIf(received != null)
        confirmCheckedAtText.visibility = visibleIf(received != null)
        receivedNoteText.visibility = visibleIf(received != null)
        received?.let {
            receivedKmText.text = getString(R.string.received_km, km(it.km))
            confirmCheckedAtText.text = getString(R.string.checked_at, time(it.checkedAtMillis))
        }

        val baselineMissing = state.baselines[item] == null
        confirmInfoText.text = when {
            status is ConsumableStatus.ComparisonBlocked -> getString(R.string.comparison_blocked)
            !status.canAddRecord -> shortStatus(status)
            baselineMissing -> getString(R.string.baseline_optional)
            else -> ""
        }
        confirmInfoText.visibility = visibleIf(confirmInfoText.text.isNotEmpty())
        setBaselineButton.visibility = visibleIf(baselineMissing)
        saveRecordButton.isEnabled = received != null && status.canAddRecord
    }

    private fun renderBaseline(state: AppUiState, item: Consumable) = with(binding.baseline) {
        baselineTitle.text = getString(R.string.baseline_title_item, getString(item.nameRes))
        val saved = state.baselines[item]
        currentBaselineText.text =
            if (saved == null) getString(R.string.baseline_unset) else getString(R.string.baseline_km, numberFormat.format(saved))

        // 입력 칸은 사용자가 치는 값이 기준입니다. 화면을 처음 열 때(또는 복원 때)만 초안 값으로 채웁니다.
        val draft = state.baselineDraft
        if (baselineInput.text.toString().toIntOrNull() != draft) {
            baselineInput.setText(draft?.toString().orEmpty())
            baselineInput.setSelection(baselineInput.text.length)
        }
        val valid = draft != null && ConsumableCalculator.isValidBaseline(draft)
        // 범위 밖 숫자를 입력했을 때만 안내를 강조합니다.
        baselineErrorText.setTextColor(getColor(if (draft != null && !valid) R.color.text_error else R.color.text_secondary))
        saveBaselineButton.isEnabled = valid
        renderBaselineSide(state, item, if (valid) draft else null)
    }

    /** S06-09~11: 빠른 선택 표시, 내 평균 교체 간격, 입력 중인 값으로 본 잔여 미리보기(S01과 같은 판정). */
    private fun renderBaselineSide(state: AppUiState, item: Consumable, validDraft: Int?) = with(binding.baseline.baselineSide) {
        presetButtons().forEach { (km, button) -> markSelected(button, state.baselineDraft == km) }

        val records = state.records.filter { it.item == item }
        val stats = ConsumableCalculator.intervalStats(records)
        val average = stats.averageKm
        myAverageCard.visibility = visibleIf(average != null)
        if (average != null) {
            myAverageText.text = getString(R.string.baseline_my_average, km(average.toFloat()), stats.count)
            val fill = ConsumableCalculator.baselineFromAverage(average)
            useAverageButton.tag = fill
            useAverageButton.text = "${getString(R.string.baseline_use_average)} (${getString(R.string.km_value, numberFormat.format(fill))})"
            markSelected(useAverageButton, state.baselineDraft == fill)
        }

        val preview = validDraft?.let { ConsumableCalculator.status(state.reading, records, it) }
        previewImage.setImageResource(
            if (preview == null) ConsumableImages.thumbnail(this@MainActivity, item)
            else ConsumableImages.homeDrawableFor(this@MainActivity, item, preview),
        )
        previewImage.imageAlpha = if (preview == null) ConsumableImages.imageAlpha(ConsumableStatus.NoRecord) else ConsumableImages.imageAlpha(preview)
        when {
            validDraft == null -> {
                previewPrimaryText.text = getString(R.string.baseline_preview_need_input)
                previewSecondaryText.visibility = View.GONE
            }
            preview is ConsumableStatus.Normal -> {
                previewPrimaryText.text = if (preview.remaining.reached) getString(R.string.baseline_preview_reached)
                else getString(R.string.baseline_preview_remaining, preview.remaining.displayPercent)
                previewSecondaryText.visibility = View.VISIBLE
                previewSecondaryText.text = getString(
                    R.string.baseline_preview_detail, km(preview.elapsedKm), numberFormat.format(validDraft),
                )
            }
            else -> {
                previewPrimaryText.text = getString(R.string.baseline_preview_unavailable)
                previewSecondaryText.visibility = View.VISIBLE
                previewSecondaryText.text = shortStatus(preview ?: ConsumableStatus.Loading)
            }
        }
        previewCard.contentDescription = listOf(previewPrimaryText.text, previewSecondaryText.text.takeIf { previewSecondaryText.visibility == View.VISIBLE })
            .filterNotNull().joinToString(", ")
    }

    private fun hideKeyboard() {
        val focused = currentFocus ?: return
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    // ── 문구 ──

    /** S05 아이콘·S01 상태 줄에 쓰는 짧은 상태. */
    private fun shortStatus(status: ConsumableStatus): String = when (status) {
        ConsumableStatus.Loading -> getString(R.string.state_checking)
        is ConsumableStatus.Unavailable -> getString(
            when (status.reading) {
                OdometerReading.Unsupported -> R.string.state_unsupported
                OdometerReading.PermissionDenied -> R.string.state_permission
                else -> R.string.state_failed
            },
        )
        ConsumableStatus.NoRecord -> getString(R.string.state_no_record)
        is ConsumableStatus.ComparisonBlocked -> getString(R.string.state_comparison_blocked)
        is ConsumableStatus.BaselineUnset -> getString(R.string.baseline_unset)
        is ConsumableStatus.Normal ->
            if (status.remaining.reached) getString(R.string.distance_reached)
            else getString(R.string.elapsed_km, km(status.elapsedKm))
    }

    private fun imageDescription(item: Consumable, status: ConsumableStatus): String {
        val name = getString(item.nameRes)
        return if (status is ConsumableStatus.Normal) {
            getString(R.string.image_alt_step, name, status.remaining.displayPercent, status.remaining.stepPercent)
        } else {
            getString(R.string.image_alt_state, name, shortStatus(status))
        }
    }

    // ── 도우미 ──

    /** 탭·세그먼트 선택 표시: 검정 채움·아래 막대(tab_background) + 굵은 글자 + 스크린리더 "선택됨"(QA-017). */
    private fun markSelected(button: Button, selected: Boolean) {
        button.isSelected = selected
        button.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        androidx.core.view.ViewCompat.setStateDescription(button, if (selected) getString(R.string.tab_selected_state) else null)
    }

    private fun selectedItem(): Consumable? = viewModel.state.value?.selected

    private fun recordLabel(record: ReplacementRecord): String =
        getString(R.string.record_row, getString(record.item.nameRes), km(record.recordKm), time(record.replacedAtMillis))

    /** 삭제 확인(S02-06): 대상·범위 → 되돌릴 수 없음 → 취소/삭제. */
    private fun confirmDelete(message: String, onConfirm: () -> Unit) =
        confirmAction("$message\n${getString(R.string.irreversible)}", R.string.cancel, R.string.delete, onConfirm)

    /** 확인 대화상자(S02 삭제, S03 변경 저장). 주행 규제나 화면 이탈 때 render()가 닫습니다. */
    private fun confirmAction(message: String, cancelRes: Int, confirmRes: Int, onConfirm: () -> Unit) {
        confirmDialog?.dismiss()
        confirmDialog = AlertDialog.Builder(this)
            .setMessage(message)
            .setNegativeButton(cancelRes, null)
            .setPositiveButton(confirmRes) { _, _ -> onConfirm() }
            .show()
            .also(::enlarge)
    }

    /** 기본 대화상자는 글자·버튼이 작아 차량 화면에서 누르기 어렵습니다. 본문·버튼을 앱 버튼 크기에 맞춥니다(임시값). */
    private fun enlarge(dialog: AlertDialog) {
        val body = resources.getDimension(R.dimen.text_body)
        val minTouch = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, body)
        listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE).forEach { which ->
            dialog.getButton(which)?.apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, body)
                minHeight = minTouch
                minWidth = minTouch * 2
            }
        }
    }

    // ── 켠 소모품에 따라 바뀌는 뷰(홈 아이콘·이력 탭·설정 줄) ──

    private val homeIcons = linkedMapOf<Consumable, ViewItemIconBinding>()
    private val historyTabButtons = linkedMapOf<Consumable, Button>()
    private val homeRows = linkedMapOf<Consumable, ViewHomeRowBinding>()
    private val settingRows = linkedMapOf<Consumable, ViewSettingRowBinding>()
    private val settingHeaders = linkedMapOf<Consumable.Powertrain, android.widget.TextView>()

    /**
     * 홈 양쪽 열: 차 앞쪽 항목부터 채웁니다. 위치별 타이어는 자기 쪽 열(왼쪽·오른쪽)에, 나머지는 덜 찬 열(같으면 왼쪽)에 놓습니다.
     * 목록이 바뀔 때만 다시 만듭니다.
     */
    private fun syncHomeIcons(items: List<Consumable>) = with(binding.home) {
        if (homeIcons.keys.toList() == items) return@with
        leftColumn.removeAllViews()
        rightColumn.removeAllViews()
        homeIcons.clear()
        items.forEach { item ->
            val column = when (item.side) {
                Consumable.Side.LEFT -> leftColumn
                Consumable.Side.RIGHT -> rightColumn
                Consumable.Side.ANY -> if (rightColumn.childCount < leftColumn.childCount) rightColumn else leftColumn
            }
            val icon = ViewItemIconBinding.inflate(layoutInflater, column, false)
            icon.name.setText(item.nameRes)
            icon.root.setOnClickListener { viewModel.select(item) }
            column.addView(icon.root, android.widget.LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.item_tile_width), android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = resources.getDimensionPixelSize(R.dimen.gap_small) })
            homeIcons[item] = icon
        }
    }

    /** 이력 탭: 홈에 켠 항목. 지금 보는 항목이 꺼져 있어도 탭은 보여 줍니다. */
    private fun syncHistoryTabs(items: List<Consumable>, current: Consumable) = with(binding.history) {
        val wanted = if (current in items) items else Consumable.ordered(items + current)
        if (historyTabButtons.keys.toList() != wanted) {
            historyTabs.removeAllViews()
            historyTabButtons.clear()
            wanted.forEach { item ->
                val tab = Button(this@MainActivity, null, 0, R.style.Widget_PleosApp_Button_Tab).apply {
                    setText(item.nameRes)
                    setOnClickListener { viewModel.openHistory(item) }
                }
                historyTabs.addView(tab, android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = -resources.getDimensionPixelSize(R.dimen.button_stroke) /* 테두리를 겹쳐 세그먼트처럼 붙임 */ })
                historyTabButtons[item] = tab
            }
        }
        // 선택 탭: 검정 채움·아래쪽 막대(tab_background)에 굵은 글자를 더하고, 스크린리더에는 "선택됨"을 알립니다(QA-017).
        historyTabButtons.forEach { (item, tab) -> markSelected(tab, item == current) }
    }

    /** S07: 공통·내연기관·전기차 순으로 모든 소모품을 보여 주고 켜고 끕니다. */
    private fun renderSettings(state: AppUiState) = with(binding.settings) {
        val kindName = getString(
            when (state.vehicleKind) {
                VehicleKind.ICE -> R.string.vehicle_kind_ice
                VehicleKind.EV -> R.string.vehicle_kind_ev
                VehicleKind.HYBRID -> R.string.vehicle_kind_hybrid
                VehicleKind.UNKNOWN -> R.string.vehicle_kind_unknown
            },
        )
        vehicleKindText.text = getString(
            R.string.vehicle_kind_label,
            getString(if (state.kindOverride == null) R.string.vehicle_kind_source_auto else R.string.vehicle_kind_source_manual, kindName),
        )
        listOf(kindAuto to null, kindEv to VehicleKind.EV, kindIce to VehicleKind.ICE, kindHybrid to VehicleKind.HYBRID)
            .forEach { (button, kind) ->
                button.isSelected = state.kindOverride == kind
                button.setOnClickListener { viewModel.setVehicleKindOverride(kind) }
            }
        if (settingRows.isEmpty()) {
            Consumable.Powertrain.entries.forEach { group ->
                val header = android.widget.TextView(this@MainActivity, null, 0, R.style.CheckCardTitle).apply {
                    setText(group.labelRes)
                    setPadding(0, resources.getDimensionPixelSize(R.dimen.gap), 0, 0)
                }
                settingsList.addView(header)
                settingHeaders[group] = header
                Consumable.ordered(Consumable.entries.filter { it.powertrain == group }).forEach { item ->
                    val row = ViewSettingRowBinding.inflate(layoutInflater, settingsList, true)
                    row.rowName.setText(item.nameRes)
                    row.rowImage.setImageResource(ConsumableImages.thumbnail(this@MainActivity, item))
                    row.root.setOnClickListener { row.rowSwitch.toggle() }
                    settingRows[item] = row
                }
            }
        }
        settingRows.forEach { (item, row) ->
            row.rowSwitch.setOnCheckedChangeListener(null)
            row.rowSwitch.isChecked = item in state.enabledItems
            row.rowSwitch.setOnCheckedChangeListener { _, checked -> viewModel.setItemEnabled(item, checked) }
            // 이 차량 동력 방식에 맞지 않는 항목은 설정에서도 숨깁니다(켜고 끈 값은 그대로 보관).
            row.root.visibility = visibleIf(!item.notApplicableTo(state.vehicleKind))
            row.root.contentDescription = getString(
                R.string.setting_row_a11y, getString(item.nameRes),
                getString(if (item in state.enabledItems) R.string.state_on else R.string.state_off),
            )
        }
        settingHeaders.forEach { (group, header) ->
            header.visibility = visibleIf(settingRows.any { (item, row) -> item.powertrain == group && row.root.visibility == View.VISIBLE })
        }
    }

    /** 거리는 정수 km로 보여 줍니다(소수 이하 반올림). 단위는 문구에 있습니다. */
    private fun km(value: Float): String = numberFormat.format(value.roundToLong())

    private fun time(millis: Long): String =
        "${DateFormat.getDateFormat(this).format(Date(millis))} ${DateFormat.getTimeFormat(this).format(Date(millis))}"

    private fun visibleIf(show: Boolean) = if (show) View.VISIBLE else View.GONE

    private companion object {
        // S03 교체 시각 증감 폭(임시: 일·시간·10분. UI_SPEC '시각 증감 폭 확인 필요')
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val HOUR_MS = 60 * 60 * 1000L
        const val TEN_MIN_MS = 10 * 60 * 1000L
    }
}
