package com.pleos.consumables

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Gleo `component: "service"` 호출(sendBroadcast)을 받습니다: [GleoActions.GET_STATUS], [GleoActions.LIST_BY_REMAINING].
 *
 * - 정차 허용이 아니면 기록을 읽지 않고 "정차 후 확인" 답만 보냅니다.
 * - 누적 주행거리는 이번 호출에서 한 번만 읽고([OdometerSource] 재사용), [GleoActions.TIMEOUT_MS] 안에 못 받으면 실패로 답합니다.
 * - Vehicle은 답을 보낸 뒤 다음 메시지 루프에서 해제합니다(콜백 안에서 바로 release 금지, ch02).
 */
class GleoActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val function = intent.action ?: return
        if (function != GleoActions.GET_STATUS && function != GleoActions.LIST_BY_REMAINING) return
        val app = context.applicationContext

        if (!GleoActions.isParked(app)) {
            GleoActions.send(app, GleoActions.drivingAnswer(function))
            return
        }

        val store = RecordStore(app)
        val nameOf: (Consumable) -> String = { app.getString(it.nameRes) }
        val target: Consumable? = if (function == GleoActions.GET_STATUS) {
            GleoActions.itemOf(GleoActions.paramFrom(intent, GleoActions.PARAM_ITEM), nameOf)
                ?: run {
                    GleoActions.send(app, GleoActions.missingItemAnswer(function))
                    return
                }
        } else {
            null
        }

        val pending = goAsync()
        val main = Handler(Looper.getMainLooper())
        val gateway = VehicleGateway(app)
        var done = false
        lateinit var source: OdometerSource
        // QA-024: 화면과 같은 동력 방식(직접 고른 값, 없으면 INFO_FUEL_TYPE 자동 판정)으로 항목을 거릅니다.
        var kind: VehicleKind? = store.vehicleKindOverride()
        var reading: OdometerReading? = null

        fun finish(result: OdometerReading, resolvedKind: VehicleKind) {
            if (done) return
            done = true
            source.stop()
            GleoActions.send(app, answer(function, target, result, resolvedKind, store, nameOf))
            main.post {
                gateway.release()
                pending.finish()
            }
        }

        fun tryFinish() {
            val r = reading ?: return
            val k = kind ?: return
            finish(r, k)
        }

        source = OdometerSource(gateway) { value ->
            if (value != OdometerReading.Checking) {
                reading = value
                tryFinish()
            }
        }
        // 시간 안에 동력 방식을 못 읽으면 UNKNOWN(모든 항목)으로, 주행거리를 못 받으면 실패로 답합니다.
        main.postDelayed({ finish(reading ?: OdometerReading.Failed, kind ?: VehicleKind.UNKNOWN) }, GleoActions.TIMEOUT_MS)
        if (kind == null) {
            VehicleInfoSource.readKind(app) { detected ->
                kind = detected
                tryFinish()
            }
        }
        source.start()
    }

    private fun answer(
        function: String,
        target: Consumable?,
        reading: OdometerReading,
        kind: VehicleKind,
        store: RecordStore,
        nameOf: (Consumable) -> String,
    ): GleoActions.Answer {
        val records = store.records()
        val baselines = store.baselines()
        fun statusOf(item: Consumable) = ConsumableCalculator.status(reading, records.filter { it.item == item }, baselines[item])

        if (target != null) {
            if (target.notApplicableTo(kind)) return GleoActions.notApplicableAnswer(function, nameOf(target))
            return GleoActions.statusAnswer(nameOf(target), statusOf(target))
        }
        // 홈에 켠 항목 중 이 차량에 맞는 것만(화면과 같은 기준).
        val items = Consumable.ordered(store.enabledItems().filterNot { it.notApplicableTo(kind) })
        val ranked = ConsumableCalculator.urgencyOrder(items, ::statusOf)
            .mapNotNull { item -> (statusOf(item) as? ConsumableStatus.Normal)?.let { nameOf(item) to it } }
        return GleoActions.listAnswer(ranked, reading is OdometerReading.Received)
    }
}
