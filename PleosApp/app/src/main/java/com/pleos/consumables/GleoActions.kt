package com.pleos.consumables

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Gleo Interface(창의성 아이디어 ⑦ "말로 묻고, 화면은 정차 때"). `assets/schema.json`의 actionCalls 3개를 처리합니다.
 *
 * | function | component | 하는 일 |
 * |---|---|---|
 * | GET_CONSUMABLE_STATUS | service(broadcast) | 한 항목의 교체 후 달린 거리·내 교체 거리 대비 남은 비율을 음성으로 답함 |
 * | LIST_CONSUMABLES_BY_REMAINING | service(broadcast) | 내 교체 거리 대비 남은 비율이 적은 항목 최대 3개 |
 * | OPEN_CONSUMABLE_RECORD | activity | 앱을 열고 그 항목의 S01을 보여 줌(주행 중이면 기존 규제 화면이 가림) |
 *
 * 정차 중 전용 결정(DECISIONS 2026-11-10)에 따라 주행 규제 중에는 기록 내용을 답하지 않고 정차 후 확인하라고만 답합니다.
 * 교체 권고 문구는 쓰지 않습니다(남은 비율은 사용자가 정한 거리 기준).
 * Callback message는 문서대로 짧은 영어 완결 문장이며, 항목 이름은 한국어로 넣습니다.
 *
 * 퍼미션·승인 기준은 문서에 없어 [확인 필요]. 승인 전에는 Gleo가 스키마를 쓰지 않으므로 앱 동작에는 영향이 없습니다.
 */
object GleoActions {
    const val GET_STATUS = "GET_CONSUMABLE_STATUS"
    const val LIST_BY_REMAINING = "LIST_CONSUMABLES_BY_REMAINING"
    const val OPEN_RECORD = "OPEN_CONSUMABLE_RECORD"

    const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    /**
     * Callback을 받는 Gleo 음성비서 앱(QA-023). 에뮬레이터 v2.1.0에서 `APP_ACTION_RESPONSE`를 등록한 앱은 이것뿐이며
     * (`dumpsys activity broadcasts`, 2026-11-10), 응답의 기록 요약이 다른 앱에 가지 않게 이 패키지로만 보냅니다.
     * 실차 패키지명이 같은지, Gleo Action을 보내는 쪽도 이 앱인지는 [확인 필요](GLEO_ACTIONS.md).
     */
    const val GLEO_AGENT_PACKAGE = "ai.umos.ambientai"
    const val EXTRA_ACTION_PARAM = "ACTION_PARAM"
    const val PARAM_ITEM = "item"

    /** Gleo는 7초 뒤 취소하므로 차량 값 조회는 이보다 짧게 끊습니다. */
    const val TIMEOUT_MS = 5_000L

    private const val TAG = "GleoActions"

    /** 스키마 enum 값 ↔ 소모품. 저장 키(`filter`=에어컨 필터 등)가 음성 해석에 모호해 별도 이름을 씁니다. */
    val voiceKeys: Map<String, Consumable> = mapOf(
        "engine_oil" to Consumable.OIL,
        "engine_air_filter" to Consumable.AIR_FILTER,
        "wiper_blade" to Consumable.WIPER,
        "cabin_air_filter" to Consumable.FILTER,
        "tire" to Consumable.TIRE,
        "brake_pad" to Consumable.BRAKE_PAD,
        "brake_disc" to Consumable.BRAKE_DISC,
        "tire_rotation" to Consumable.TIRE_ROTATION,
        "wheel_alignment" to Consumable.ALIGNMENT,
        "brake_fluid" to Consumable.BRAKE_FLUID,
        "coolant" to Consumable.COOLANT,
        "12v_battery" to Consumable.BATTERY,
        "spark_plug" to Consumable.SPARK_PLUG,
        "drive_belt" to Consumable.DRIVE_BELT,
        "transmission_oil" to Consumable.TRANSMISSION_OIL,
        "fuel_filter" to Consumable.FUEL_FILTER,
        "reduction_gear_oil" to Consumable.REDUCTION_OIL,
        "battery_coolant" to Consumable.BATTERY_COOLANT,
    )

    /** 스키마 enum 값, 저장 키, 화면 이름(한국어) 순으로 항목을 찾습니다. */
    fun itemOf(value: String?, nameOf: (Consumable) -> String): Consumable? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = v.lowercase(Locale.ROOT)
        return voiceKeys[lower] ?: Consumable.fromKey(lower)
            ?: Consumable.entries.firstOrNull { nameOf(it).replace(" ", "") == v.replace(" ", "") }
    }

    /**
     * 파라미터 한 개를 읽습니다. 문서 설명(파라미터마다 putExtra)과 예제(`ACTION_PARAM`에 JSON 문자열,
     * 바깥 따옴표로 한 번 더 감싼 형태)가 달라 둘 다 받습니다.
     */
    fun param(json: String?, name: String): String? {
        var raw = json?.trim() ?: return null
        if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length - 1).replace("\\\"", "\"")
        }
        return try {
            JSONObject(raw).optString(name).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    fun paramFrom(intent: Intent, name: String): String? =
        intent.getStringExtra(name) ?: param(intent.getStringExtra(EXTRA_ACTION_PARAM), name)

    /** Gleo에 돌려줄 응답 한 건. [data]는 key-value 객체 배열(JSON 문자열로 보냄). */
    data class Answer(
        val functionName: String,
        val status: String,
        val message: String,
        val data: List<Map<String, String>> = emptyList(),
    ) {
        fun toJson(): String = JSONObject().apply {
            put("functionName", functionName)
            put("status", status)
            put("message", message)
            if (data.isNotEmpty()) put("data", JSONArray(data.map { JSONObject(it) }).toString())
        }.toString()
    }

    private fun km(value: Float): String = NumberFormat.getIntegerInstance(Locale.US).format(value.roundToLong())
    private fun km(value: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

    fun drivingAnswer(function: String) =
        Answer(function, "fail", "Consumable records can be checked only while the vehicle is parked.")

    fun missingItemAnswer(function: String) =
        Answer(function, "asking", "Which consumable item should be checked?", listOf(mapOf("missingField" to PARAM_ITEM)))

    /** GET_CONSUMABLE_STATUS 답. 판정은 화면과 같은 [ConsumableCalculator.status]를 씁니다. */
    fun statusAnswer(name: String, status: ConsumableStatus): Answer = when (status) {
        ConsumableStatus.Loading, is ConsumableStatus.Unavailable ->
            Answer(GET_STATUS, "fail", "The vehicle odometer could not be read.")
        ConsumableStatus.NoRecord ->
            Answer(GET_STATUS, "success", "There is no replacement record for $name.", listOf(mapOf("item" to name, "record" to "none")))
        is ConsumableStatus.ComparisonBlocked ->
            Answer(
                GET_STATUS, "success",
                "The current odometer is lower than the last record for $name, so the distance cannot be compared.",
                listOf(mapOf("item" to name, "lastRecordKm" to km(status.last.recordKm))),
            )
        is ConsumableStatus.BaselineUnset ->
            Answer(
                GET_STATUS, "success",
                "$name has been driven ${km(status.elapsedKm)} km since the last replacement, and no replacement distance is set.",
                listOf(mapOf("item" to name, "drivenSinceReplacementKm" to km(status.elapsedKm))),
            )
        is ConsumableStatus.Normal -> {
            val message = if (status.remaining.reached) {
                "$name has been driven ${km(status.elapsedKm)} km since the last replacement, reaching your ${km(status.baselineKm)} km distance."
            } else {
                "$name has been driven ${km(status.elapsedKm)} km since the last replacement, with ${status.remaining.displayPercent}% of your ${km(status.baselineKm)} km distance left."
            }
            Answer(
                GET_STATUS, "success", message,
                listOf(
                    mapOf(
                        "item" to name,
                        "drivenSinceReplacementKm" to km(status.elapsedKm),
                        "yourDistanceKm" to km(status.baselineKm),
                        "leftPercent" to status.remaining.displayPercent.toString(),
                    ),
                ),
            )
        }
    }

    /** LIST_CONSUMABLES_BY_REMAINING 답: 정상 상태(기록+내 교체 거리) 항목 중 남은 비율이 적은 순 최대 3개. */
    fun listAnswer(ranked: List<Pair<String, ConsumableStatus.Normal>>, odometerRead: Boolean): Answer {
        if (!odometerRead) return Answer(LIST_BY_REMAINING, "fail", "The vehicle odometer could not be read.")
        val top = ranked.take(3)
        if (top.isEmpty()) {
            return Answer(LIST_BY_REMAINING, "success", "No item has both a replacement record and a replacement distance set.")
        }
        val parts = top.joinToString(", ") { (name, s) -> "$name ${s.remaining.displayPercent}% left" }
        return Answer(
            LIST_BY_REMAINING, "success",
            "By the distances you set: $parts.",
            top.map { (name, s) -> mapOf("item" to name, "leftPercent" to s.remaining.displayPercent.toString()) },
        )
    }

    fun openAnswer(name: String, parked: Boolean) =
        if (parked) Answer(OPEN_RECORD, "success", "Opened the $name record.", listOf(mapOf("item" to name)))
        else Answer(OPEN_RECORD, "success", "The $name record will be shown once the vehicle is parked.", listOf(mapOf("item" to name)))

    fun notApplicableAnswer(function: String, name: String) =
        Answer(function, "fail", "$name does not apply to this vehicle's powertrain.")

    fun send(context: Context, answer: Answer) {
        runCatching {
            context.sendBroadcast(
                Intent(RESPONSE_ACTION)
                    .setPackage(GLEO_AGENT_PACKAGE)
                    .putExtra("packageName", context.packageName)
                    .putExtra("response", answer.toJson()),
            )
            Log.d(TAG, "callback ${answer.functionName} ${answer.status}: ${answer.message}")
        }.onFailure { Log.w(TAG, "callback failed", it) }
    }

    /** 지금 정차 허용 상태인지 한 번 확인합니다. 확인하지 못하면 주행 중으로 봅니다(정차 중 전용, QA-003과 같은 기준). */
    fun isParked(context: Context): Boolean = try {
        val car = Car.createCar(context.applicationContext)
        try {
            val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            val current = manager?.currentCarUxRestrictions
            current != null && !UxRestrictionState.from(current).restricted
        } finally {
            car?.disconnect()
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "restriction check failed", e)
        false
    }
}
