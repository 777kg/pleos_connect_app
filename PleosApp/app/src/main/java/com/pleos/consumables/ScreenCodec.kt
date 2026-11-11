package com.pleos.consumables

/** 화면을 문자열로 저장·복원합니다(프로세스 재생성 복원, QA-007). [MainViewModel]이 SavedStateHandle에 씁니다. */
object ScreenCodec {

    /**
     * 예: `home`, `detail:oil`, `history:tire:oil`, `edit:oil:3:tire`, `baseline:oil:confirm:oil`
     * (History·Edit은 S02를 연 항목을, Baseline은 돌아갈 화면을 뒤에 붙임. 예전 `history:oil`·`edit:oil:3`도 읽습니다)
     */
    fun encode(screen: Screen): String = when (screen) {
        Screen.Home -> "home"
        Screen.Settings -> "settings"
        is Screen.Detail -> "detail:${screen.item.key}"
        is Screen.History -> "history:${screen.item.key}:${screen.from.key}"
        is Screen.Edit -> "edit:${screen.item.key}:${screen.recordId}:${screen.from.key}"
        is Screen.Confirm -> "confirm:${screen.item.key}"
        is Screen.Baseline -> "baseline:${screen.item.key}:${encode(screen.returnTo)}"
    }

    fun decode(text: String): Screen? {
        val parts = text.split(":", limit = 3)
        if (parts[0] == "home") return Screen.Home
        if (parts[0] == "settings") return Screen.Settings
        val item = parts.getOrNull(1)?.let(Consumable::fromKey) ?: return null
        return when (parts[0]) {
            "detail" -> Screen.Detail(item)
            "history" -> Screen.History(item, parts.getOrNull(2)?.let(Consumable::fromKey) ?: item)
            "edit" -> {
                val rest = parts.getOrNull(2)?.split(":").orEmpty()
                val id = rest.getOrNull(0)?.toLongOrNull() ?: return null
                Screen.Edit(item, id, rest.getOrNull(1)?.let(Consumable::fromKey) ?: item)
            }
            "confirm" -> Screen.Confirm(item)
            "baseline" -> Screen.Baseline(item, parts.getOrNull(2)?.let(::decode) ?: Screen.Detail(item))
            else -> null
        }
    }
}
