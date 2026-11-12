package com.pleos.consumables

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 교체 기록과 항목별 기준 거리를 이 기기에만 저장합니다(DECISIONS: 단일 기기 기록, 서버·계정 없음).
 * 백업은 매니페스트(allowBackup=false, data_extraction_rules)에서 제외합니다. 토큰·개인정보는 담지 않습니다.
 *
 * 기록 수는 항목당 교체 횟수 정도라 SharedPreferences에 JSON 한 덩어리로 둡니다.
 */
class RecordStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun records(): List<ReplacementRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { i -> array.getJSONObject(i).toRecord() }
    }

    fun baselines(): Map<Consumable, Int> =
        Consumable.entries.mapNotNull { item ->
            val km = prefs.getInt(baselineKey(item), NO_BASELINE)
            if (km == NO_BASELINE) null else item to km
        }.toMap()

    /** 차량에서 받은 거리로 새 기록을 추가합니다. 교체 시각은 저장 시각입니다. */
    fun addRecord(
        item: Consumable,
        vehicleKm: Float,
        receivedAtMillis: Long,
        nowMillis: Long,
        rebased: Boolean = false,
    ): ReplacementRecord {
        val current = records()
        val record = ReplacementRecord(
            id = (current.maxOfOrNull { it.id } ?: 0L) + 1,
            item = item,
            replacedAtMillis = nowMillis,
            vehicleKm = vehicleKm,
            receivedAtMillis = receivedAtMillis,
            recordKm = vehicleKm,
            edited = false,
            saveOrder = (current.maxOfOrNull { it.saveOrder } ?: 0L) + 1,
            rebased = rebased,
        )
        writeRecords(current + record)
        return record
    }

    /**
     * 기록 거리·교체 시각을 고칩니다(S03). 원본 차량 거리([ReplacementRecord.vehicleKm])는 그대로 두고
     * 하나라도 바뀌면 [ReplacementRecord.edited]를 켭니다. 한 번 켜지면 되돌리지 않습니다.
     */
    fun updateRecord(id: Long, recordKm: Float, replacedAtMillis: Long) {
        writeRecords(
            records().map {
                if (it.id != id) it
                else it.copy(
                    recordKm = recordKm,
                    replacedAtMillis = replacedAtMillis,
                    edited = it.edited || recordKm != it.recordKm || replacedAtMillis != it.replacedAtMillis,
                )
            },
        )
    }

    fun deleteRecord(id: Long) = writeRecords(records().filterNot { it.id == id })

    /** 전체 삭제: 모든 항목의 교체 기록과 기준 거리(DECISIONS 2026-10-30). 홈에 보일 항목 설정은 남깁니다. */
    fun deleteAll() {
        val edit = prefs.edit().remove(KEY_RECORDS)
        Consumable.entries.forEach { edit.remove(baselineKey(it)) }
        edit.apply()
    }

    /** 홈에 보일 소모품(S07). 저장된 적이 없으면 기본값([Consumable.DEFAULT_ENABLED]). */
    /** S07에서 사용자가 직접 고른 동력 방식. null이면 자동 판정(INFO_FUEL_TYPE)을 따릅니다(결정 2026-11-10 #1). */
    fun vehicleKindOverride(): VehicleKind? =
        prefs.getString(KEY_KIND_OVERRIDE, null)?.let { name -> VehicleKind.entries.firstOrNull { it.name == name } }

    fun setVehicleKindOverride(kind: VehicleKind?) {
        prefs.edit().apply { if (kind == null) remove(KEY_KIND_OVERRIDE) else putString(KEY_KIND_OVERRIDE, kind.name) }.apply()
    }

    fun enabledItems(): Set<Consumable> {
        val keys = prefs.getStringSet(KEY_ENABLED, null) ?: return Consumable.DEFAULT_ENABLED
        val items = keys.mapNotNull(Consumable::fromKey).toSet()
        // 위치별 타이어로 나누기 전에 '타이어'를 켜 두었으면 4개 모두 켭니다.
        return if (Consumable.LEGACY_TIRE_KEY in keys) items + Consumable.TIRES else items
    }

    fun setEnabledItems(items: Set<Consumable>) {
        prefs.edit().putStringSet(KEY_ENABLED, items.map { it.key }.toSet()).apply()
    }

    /** S05 보기: true면 임박 순 목록, false(기본)면 탑뷰. 화면 설정이라 전체 삭제와 무관하게 남깁니다. */
    fun homeListView(): Boolean = prefs.getBoolean(KEY_HOME_LIST, false)

    fun setHomeListView(list: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_LIST, list).apply()
    }

    fun setBaseline(item: Consumable, km: Int) {
        require(ConsumableCalculator.isValidBaseline(km))
        prefs.edit().putInt(baselineKey(item), km).apply()
    }

    private fun writeRecords(records: List<ReplacementRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "service_mileage_log"
        const val KEY_RECORDS = "records"
        const val KEY_ENABLED = "enabled_items"
        const val KEY_KIND_OVERRIDE = "vehicle_kind_override"
        const val KEY_HOME_LIST = "home_list_view"
        const val NO_BASELINE = -1

        fun baselineKey(item: Consumable) = "baseline_${item.key}"

        fun ReplacementRecord.toJson() = JSONObject()
            .put("id", id)
            .put("item", item.key)
            .put("replacedAt", replacedAtMillis)
            .put("vehicleKm", vehicleKm.toDouble())
            .put("receivedAt", receivedAtMillis)
            .put("recordKm", recordKm.toDouble())
            .put("edited", edited)
            .put("rebased", rebased)
            .put("saveOrder", saveOrder)

        fun JSONObject.toRecord(): ReplacementRecord? {
            val item = Consumable.fromKey(optString("item")) ?: return null
            return ReplacementRecord(
                id = getLong("id"),
                item = item,
                replacedAtMillis = getLong("replacedAt"),
                vehicleKm = getDouble("vehicleKm").toFloat(),
                receivedAtMillis = getLong("receivedAt"),
                recordKm = getDouble("recordKm").toFloat(),
                edited = optBoolean("edited", false),
                rebased = optBoolean("rebased", false),
                saveOrder = getLong("saveOrder"),
            )
        }
    }
}
