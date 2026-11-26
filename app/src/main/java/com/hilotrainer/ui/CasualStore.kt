package com.hilotrainer.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.hilotrainer.engine.CasualStats
import com.hilotrainer.engine.Rules

/** Felt colors for the Just Play table. */
enum class FeltTheme(val label: String, val base: Color, val light: Color, val print: Color) {
    GREEN("Classic green", Color(0xFF0F5A37), Color(0xFF1C8A55), Color(0xFFE9C46A)),
    BLUE("Royal blue", Color(0xFF123C78), Color(0xFF2B63B5), Color(0xFFE9D9A6)),
    RED("Casino red", Color(0xFF6E1420), Color(0xFFA32638), Color(0xFFF3D78A)),
    PURPLE("Velvet purple", Color(0xFF3E1A5E), Color(0xFF64308F), Color(0xFFE6C9FF)),
    BLACK("Midnight", Color(0xFF15181C), Color(0xFF2E343B), Color(0xFFC9A553)),
}

/** Just Play's own choices. Kept apart from the training settings. */
data class CasualSettings(
    val decks: Int = 6,
    val dealerHitsSoft17: Boolean = false,
    val sixToFive: Boolean = false,
    val surrender: Boolean = true,
    val doubleAfterSplit: Boolean = true,
    val tableMin: Int = 10,
    /** Other seats at the table, 0 to 4. You sit at the bottom of the arc. */
    val seats: Int = 4,
    val comeAndGo: Boolean = true,
    val startPlayers: Int = 3,
    val felt: Int = 0,
    val cardBack: Int = 0,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    /** 0 relaxed, 1 normal, 2 fast. */
    val speed: Int = 1,
    val hints: Boolean = false,
    val offerInsurance: Boolean = true,
    val sideBets: Boolean = true,
    val tableTalk: Boolean = true,
    /** 0 lane, 1 bubbles over heads, 2 captions, 3 table log, 4 bubbles and floating small talk. */
    val chatStyle: Int = 2,
    /** Show the Hi-Lo running and true count on the table. */
    val showCount: Boolean = false,
) {
    val rules: Rules get() = Rules(decks = decks, dealerHitsSoft17 = dealerHitsSoft17, doubleAfterSplit = doubleAfterSplit, surrenderAllowed = surrender, sixToFive = sixToFive)
    val theme: FeltTheme get() = FeltTheme.entries.getOrElse(felt) { FeltTheme.GREEN }
}

/** Saves Just Play's bankroll, stats, achievements and settings. */
class CasualStore(context: Context?) {
    private val p: SharedPreferences? = context?.getSharedPreferences("just_play", Context.MODE_PRIVATE)
    private val mem = HashMap<String, Any>()

    private fun int(k: String, d: Int) = p?.getInt(k, d) ?: (mem[k] as? Int ?: d)
    private fun long(k: String, d: Long) = p?.getLong(k, d) ?: (mem[k] as? Long ?: d)
    private fun bool(k: String, d: Boolean) = p?.getBoolean(k, d) ?: (mem[k] as? Boolean ?: d)
    private fun put(f: SharedPreferences.Editor.() -> Unit, vararg kv: Pair<String, Any>) {
        if (p != null) p.edit().apply(f).apply() else kv.forEach { mem[it.first] = it.second }
    }

    var bankroll: Int
        get() = int("bankroll", START)
        set(v) = put({ putInt("bankroll", v) }, "bankroll" to v)

    private fun str(k: String) = (p?.getString(k, null) ?: (mem[k] as? String))?.takeIf { it.isNotEmpty() }

    /** Each room keeps its own shoe, so the cards and the count are where you left them. */
    fun loadShoe(key: String): String? = str("shoe_$key")
    fun saveShoe(key: String, text: String) = put({ putString("shoe_$key", text) }, "shoe_$key" to text)
    fun clearShoes() {
        if (p != null) p.edit().apply { p.all.keys.filter { it.startsWith("shoe_") }.forEach { remove(it) } }.apply()
        else mem.keys.filter { it.startsWith("shoe_") }.toList().forEach { mem.remove(it) }
    }

    /** How many of each power-up you hold, in order of [com.hilotrainer.engine.PowerUp]. */
    fun powerUps(): List<Int> = List(3) { int("pu_$it", 0) }
    fun setPowerUp(i: Int, n: Int) = put({ putInt("pu_$i", n) }, "pu_$i" to n)
    var powerSeeded: Boolean
        get() = bool("pu_seeded", false)
        set(v) = put({ putBoolean("pu_seeded", v) }, "pu_seeded" to v)
    var powerHands: Int
        get() = int("pu_hands", 0)
        set(v) = put({ putInt("pu_hands", v) }, "pu_hands" to v)

    /** Your rival and the running score, and how far you are in the story. Casual only. */
    var rivalName: String?
        get() = str("rival_name")
        set(v) = put({ putString("rival_name", v) }, "rival_name" to (v ?: ""))
    var rivalWins: Int
        get() = int("rival_w", 0)
        set(v) = put({ putInt("rival_w", v) }, "rival_w" to v)
    var rivalLosses: Int
        get() = int("rival_l", 0)
        set(v) = put({ putInt("rival_l", v) }, "rival_l" to v)
    var storyDone: Int
        get() = int("story_done", 0)
        set(v) = put({ putInt("story_done", v) }, "story_done" to v)
    var nightRun: String?
        get() = str("night_run")
        set(v) = put({ putString("night_run", v) }, "night_run" to (v ?: ""))

    var lastBet: Int
        get() = int("lastBet", 0)
        set(v) = put({ putInt("lastBet", v) }, "lastBet" to v)

    var bonusDay: Long
        get() = long("bonusDay", -1)
        set(v) = put({ putLong("bonusDay", v) }, "bonusDay" to v)
    var bonusStreak: Int
        get() = int("bonusStreak", 0)
        set(v) = put({ putInt("bonusStreak", v) }, "bonusStreak" to v)

    var achievements: Set<String>
        get() = p?.getStringSet("ach", emptySet())?.toSet() ?: (mem["ach"] as? Set<String> ?: emptySet())
        set(v) = put({ putStringSet("ach", v) }, "ach" to v)

    /** Rooms you were invited to before reaching their bankroll. */
    var invites: Set<String>
        get() = p?.getStringSet("invites", emptySet())?.toSet() ?: (mem["invites"] as? Set<String> ?: emptySet())
        set(v) = put({ putStringSet("invites", v) }, "invites" to v)

    /** Total wagered when the last comp was given. */
    var compMark: Long
        get() = long("compMark", 0)
        set(v) = put({ putLong("compMark", v) }, "compMark" to v)

    /** How many times this regular has sat at your table. */
    fun met(name: String): Int = int("met_$name", 0)
    fun addMet(name: String) { val n = met(name) + 1; put({ putInt("met_$name", n) }, "met_$name" to n) }

    fun loadSettings(): CasualSettings {
        val d = CasualSettings()
        return CasualSettings(
            decks = int("s_decks", d.decks), dealerHitsSoft17 = bool("s_h17", d.dealerHitsSoft17), sixToFive = bool("s_65", d.sixToFive),
            surrender = bool("s_sur", d.surrender), doubleAfterSplit = bool("s_das", d.doubleAfterSplit), tableMin = int("s_min", d.tableMin),
            seats = int("s_seats", d.seats).coerceIn(0, 4), comeAndGo = bool("s_cag", d.comeAndGo), startPlayers = int("s_start", d.startPlayers),
            felt = int("s_felt", d.felt), cardBack = int("s_back", d.cardBack), sound = bool("s_sound", d.sound), haptics = bool("s_hap", d.haptics),
            speed = int("s_speed", d.speed), hints = bool("s_hints", d.hints), offerInsurance = bool("s_ins", d.offerInsurance),
            sideBets = bool("s_side", d.sideBets), tableTalk = bool("s_talk", d.tableTalk), chatStyle = int("s_chat", d.chatStyle).coerceIn(0, 4), showCount = bool("s_count", d.showCount),
        )
    }

    fun saveSettings(s: CasualSettings) = put(
        {
            putInt("s_decks", s.decks); putBoolean("s_h17", s.dealerHitsSoft17); putBoolean("s_65", s.sixToFive); putBoolean("s_sur", s.surrender)
            putBoolean("s_das", s.doubleAfterSplit); putInt("s_min", s.tableMin); putInt("s_seats", s.seats); putBoolean("s_cag", s.comeAndGo)
            putInt("s_start", s.startPlayers); putInt("s_felt", s.felt); putInt("s_back", s.cardBack); putBoolean("s_sound", s.sound)
            putBoolean("s_hap", s.haptics); putInt("s_speed", s.speed); putBoolean("s_hints", s.hints); putBoolean("s_ins", s.offerInsurance)
            putBoolean("s_side", s.sideBets); putBoolean("s_talk", s.tableTalk); putInt("s_chat", s.chatStyle); putBoolean("s_count", s.showCount)
        },
        "s_decks" to s.decks, "s_h17" to s.dealerHitsSoft17, "s_65" to s.sixToFive, "s_sur" to s.surrender, "s_das" to s.doubleAfterSplit,
        "s_min" to s.tableMin, "s_seats" to s.seats, "s_cag" to s.comeAndGo, "s_start" to s.startPlayers, "s_felt" to s.felt,
        "s_back" to s.cardBack, "s_sound" to s.sound, "s_hap" to s.haptics, "s_speed" to s.speed, "s_hints" to s.hints,
        "s_ins" to s.offerInsurance, "s_side" to s.sideBets, "s_talk" to s.tableTalk, "s_chat" to s.chatStyle, "s_count" to s.showCount,
    )

    fun loadStats(): CasualStats = CasualStats(
        hands = int("t_hands", 0), wins = int("t_wins", 0), losses = int("t_losses", 0), pushes = int("t_push", 0), blackjacks = int("t_bj", 0),
        biggestWin = int("t_bigwin", 0), biggestBankroll = int("t_bigroll", START), winStreak = int("t_ws", 0), bestWinStreak = int("t_bws", 0),
        rebuys = int("t_rebuys", 0), wagered = long("t_wag", 0), net = long("t_net", 0), doublesWon = int("t_dbl", 0), splits = int("t_split", 0),
        sideWins = int("t_side", 0), fiveCard = int("t_five", 0), dealerBusts = int("t_dbust", 0),
    )

    fun saveStats(s: CasualStats) = put(
        {
            putInt("t_hands", s.hands); putInt("t_wins", s.wins); putInt("t_losses", s.losses); putInt("t_push", s.pushes); putInt("t_bj", s.blackjacks)
            putInt("t_bigwin", s.biggestWin); putInt("t_bigroll", s.biggestBankroll); putInt("t_ws", s.winStreak); putInt("t_bws", s.bestWinStreak)
            putInt("t_rebuys", s.rebuys); putLong("t_wag", s.wagered); putLong("t_net", s.net); putInt("t_dbl", s.doublesWon); putInt("t_split", s.splits)
            putInt("t_side", s.sideWins); putInt("t_five", s.fiveCard); putInt("t_dbust", s.dealerBusts)
        },
        "t_hands" to s.hands, "t_wins" to s.wins, "t_losses" to s.losses, "t_push" to s.pushes, "t_bj" to s.blackjacks, "t_bigwin" to s.biggestWin,
        "t_bigroll" to s.biggestBankroll, "t_ws" to s.winStreak, "t_bws" to s.bestWinStreak, "t_rebuys" to s.rebuys, "t_wag" to s.wagered,
        "t_net" to s.net, "t_dbl" to s.doublesWon, "t_split" to s.splits, "t_side" to s.sideWins, "t_five" to s.fiveCard, "t_dbust" to s.dealerBusts,
    )

    companion object {
        const val START = 1000
        const val REBUY = 1000
    }
}
