package com.hilotrainer.ui

import android.content.Context
import android.content.SharedPreferences
import com.hilotrainer.engine.CountSystem
import com.hilotrainer.engine.Review
import com.hilotrainer.engine.Rules
import com.hilotrainer.engine.SpeedLadder
import com.hilotrainer.engine.TrueCount

data class AppSettings(
    val decks: Int = 6,
    val dealerHitsSoft17: Boolean = true,
    val surrender: Boolean = false,
    val showRunningCount: Boolean = false,
    val showHints: Boolean = false,
    val confirmMistakes: Boolean = true,
    /** Ask for the count every N rounds. 0 turns quizzes off. */
    val quizEvery: Int = 1,
    val askTrueCount: Boolean = true,
    /** Computer players at the table, 0 to 3. */
    val bots: Int = 2,
    val deviations: Boolean = false,
    val gradeBets: Boolean = true,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    /** 0 slow, 1 normal, 2 fast. */
    val dealSpeed: Int = 1,
    /** 0: 1 to 4, 1: 1 to 8, 2: 1 to 12. */
    val rampChoice: Int = 1,
    /** Cut card position as a percent of the shoe. */
    val penetrationPct: Int = 75,
    /** Coach sitting out at true count -1 or lower. */
    val wongOut: Boolean = false,
    /** Seconds per decision, 0 for no clock. */
    val shotClock: Int = 0,
    /** 0 Hi-Lo, 1 KO, 2 Zen. */
    val systemChoice: Int = 0,
    val doubleAfterSplit: Boolean = true,
    val sixToFive: Boolean = false,
    /** Table chatter, chip noise and drink orders while you play. */
    val distractions: Boolean = false,
    /** Real casino distractions (questions you must answer, number talk, walk-bys) instead of light ones. */
    val distractionsReal: Boolean = true,
    /** Coach bet camouflage and watch for "heat". */
    val coverPlay: Boolean = false,
    val reminderOn: Boolean = false,
    /** Hour of the day (0 to 23) for the practice reminder. */
    val reminderHour: Int = 19,
    /** Coach the extended index plays too, not just the Illustrious 18 and Fab 4. */
    val fullIndices: Boolean = false,
    /** 0 hand shuffle, 1 shuffle machine, 2 continuous shuffler. */
    val shuffleChoice: Int = 0,
    /** Other players make the mistakes real players make. */
    val realisticPlayers: Boolean = false,
    /** Perfect Pairs side bet each round in dollars, 0 for off. */
    val perfectPairs: Int = 0,
    /** Show a microphone so you can say the count out loud. */
    val voice: Boolean = false,
    /** Split the table and controls at the hinge when the Fold is half open. */
    val flexMode: Boolean = true,
    /** Controls and side panels on the left for left-handed play. */
    val leftHanded: Boolean = false,
    /** The dealer sometimes pays wrong or calls the wrong total, and you have to catch it. */
    val dealerErrors: Boolean = false,
    /** With cover play on, the pit can flat-bet you or back you off. */
    val pitBackoff: Boolean = false,
    /** Index into [com.hilotrainer.engine.Kelly.FRACTIONS]. */
    val kellyChoice: Int = 1,
    /** Practice days a week for the weekly goal. */
    val weeklyDays: Int = 5,
    /** 0 buttons only, 1 buttons and hand signals, 2 hand signals only for hit and stand. */
    val signals: Int = 1,
    /** Tilt the table as if you're sitting at it. */
    val seatedView: Boolean = false,
    /** Card back design, see [CardBacks]. */
    val cardBack: Int = 0,
    /** The dealer calls the game out loud with the phone's text to speech. */
    val dealerVoice: Boolean = true,
    /** You cut the cards after each shuffle. */
    val cutCards: Boolean = true,
) {
    val kellyFraction: Double get() = com.hilotrainer.engine.Kelly.FRACTIONS.getOrElse(kellyChoice) { com.hilotrainer.engine.Kelly.FRACTIONS[1] }.first
    val shuffleStyle: com.hilotrainer.engine.ShuffleStyle get() = com.hilotrainer.engine.ShuffleStyle.entries.getOrElse(shuffleChoice) { com.hilotrainer.engine.ShuffleStyle.HAND }
    val system: CountSystem get() = CountSystem.entries.getOrElse(systemChoice) { CountSystem.HI_LO }
    val ramp: List<Int> get() = when (rampChoice) { 0 -> TrueCount.RAMP_1_4; 2 -> TrueCount.RAMP_1_12; else -> TrueCount.RAMP_1_8 }
    val wongOutAt: Int? get() = if (wongOut) -1 else null

    /** Dealing speed 3 follows the speed ladder, see [Prefs.ladderRung]. */
    fun dealDelayMs(ladderRung: Int = 0): Long = when (dealSpeed) {
        0 -> 650L; 2 -> 200L
        3 -> (SpeedLadder.RUNGS_MS[ladderRung.coerceIn(0, SpeedLadder.RUNGS_MS.size - 1)] / 2.5).toLong()
        else -> 380L
    }

    fun rules() = Rules(
        decks = decks,
        dealerHitsSoft17 = dealerHitsSoft17,
        doubleAfterSplit = doubleAfterSplit,
        surrenderAllowed = surrender,
        penetration = penetrationPct / 100.0,
        sixToFive = sixToFive,
    )
}

data class LifetimeStats(
    val handsPlayed: Int = 0,
    val decisions: Int = 0,
    val correctDecisions: Int = 0,
    val countQuizzes: Int = 0,
    val countCorrect: Int = 0,
    val trueQuizzes: Int = 0,
    val trueCorrect: Int = 0,
    val drillsRun: Int = 0,
    val drillsCorrect: Int = 0,
    val bestDeckSeconds: Int = 0,
    val strategyDrills: Int = 0,
    val strategyDrillsCorrect: Int = 0,
    val tcDrills: Int = 0,
    val tcDrillsCorrect: Int = 0,
    val betDecisions: Int = 0,
    val betCorrect: Int = 0,
    val deviationDecisions: Int = 0,
    val deviationCorrect: Int = 0,
    val hintsUsed: Int = 0,
    val peeks: Int = 0,
    val bestStrategyStreak: Int = 0,
    val slowDecisions: Int = 0,
    val trayDrills: Int = 0,
    val trayCorrect: Int = 0,
    val watchRounds: Int = 0,
    /** Index into [com.hilotrainer.engine.CasinoTest.LEVELS] of the best level passed, or -1. */
    val casinoBest: Int = -1,
    val casinoTests: Int = 0,
    val casinoPasses: Int = 0,
    val ladderBest: Int = 0,
    val pairDrills: Int = 0,
    val pairCorrect: Int = 0,
    val reviews: Int = 0,
    val reviewsCorrect: Int = 0,
    val coverDrills: Int = 0,
    val coverCorrect: Int = 0,
    val backCountCalls: Int = 0,
    val backCountCorrect: Int = 0,
    val teamCalls: Int = 0,
    val teamCorrect: Int = 0,
    val weakSpotDrills: Int = 0,
    val weakSpotCorrect: Int = 0,
    val voiceAnswers: Int = 0,
    val bestDailyStreak: Int = 0,
    val quickDrills: Int = 0,
    val quickBest: Int = 0,
    val hapticDrills: Int = 0,
    val hapticCorrect: Int = 0,
    val deckEstimates: Int = 0,
    val deckEstimatesOk: Int = 0,
    val dealerChecks: Int = 0,
    val dealerChecksOk: Int = 0,
    val dealerCaught: Int = 0,
    val backoffs: Int = 0,
    val flatBets: Int = 0,
    val sessionsCompleted: Int = 0,
    val bestSessionMinutes: Int = 0,
    val midShoeJoins: Int = 0,
    val dailyChallenges: Int = 0,
    val shares: Int = 0,
    val holeFlashesUsed: Int = 0,
    val holeFlashes: Int = 0,
    val tipsGiven: Int = 0,
    /** Count checks after rounds with distractions, and how many were right. */
    val distractChecks: Int = 0,
    val distractChecksOk: Int = 0,
    /** Count checks after quiet rounds. */
    val quietChecks: Int = 0,
    val quietChecksOk: Int = 0,
    /** Table questions with a right answer, and how many you got right. */
    val distractQs: Int = 0,
    val distractQsOk: Int = 0,
)

class Prefs(val context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("hilo_trainer", Context.MODE_PRIVATE)

    fun loadSettings() = AppSettings(
        decks = sp.getInt("decks", 6),
        dealerHitsSoft17 = sp.getBoolean("h17", true),
        surrender = sp.getBoolean("surrender", false),
        showRunningCount = sp.getBoolean("showCount", false),
        showHints = sp.getBoolean("showHints", false),
        confirmMistakes = sp.getBoolean("confirmMistakes", true),
        quizEvery = sp.getInt("quizEvery", 1),
        askTrueCount = sp.getBoolean("askTrue", true),
        bots = sp.getInt("bots", 2),
        deviations = sp.getBoolean("deviations", false),
        gradeBets = sp.getBoolean("gradeBets", true),
        sound = sp.getBoolean("sound", true),
        haptics = sp.getBoolean("haptics", true),
        dealSpeed = sp.getInt("dealSpeed", 1),
        rampChoice = sp.getInt("ramp", 1),
        penetrationPct = sp.getInt("pen", 75),
        wongOut = sp.getBoolean("wongOut", false),
        shotClock = sp.getInt("shotClock", 0),
        systemChoice = sp.getInt("system", 0),
        doubleAfterSplit = sp.getBoolean("das", true),
        sixToFive = sp.getBoolean("sixToFive", false),
        distractions = sp.getBoolean("distract", false),
        distractionsReal = sp.getBoolean("distract_real", true),
        coverPlay = sp.getBoolean("cover", false),
        reminderOn = sp.getBoolean("remind", false),
        reminderHour = sp.getInt("remindHour", 19),
        fullIndices = sp.getBoolean("fullIdx", false),
        shuffleChoice = sp.getInt("shuffle", 0),
        realisticPlayers = sp.getBoolean("realistic", false),
        perfectPairs = sp.getInt("pp", 0),
        voice = sp.getBoolean("voice", false),
        flexMode = sp.getBoolean("flex", true),
        leftHanded = sp.getBoolean("lefty", false),
        dealerErrors = sp.getBoolean("dealerErr", false),
        pitBackoff = sp.getBoolean("backoff", false),
        kellyChoice = sp.getInt("kelly", 1),
        weeklyDays = sp.getInt("weeklyDays", 5),
        signals = sp.getInt("signals", 1),
        seatedView = sp.getBoolean("seated", false),
        cardBack = sp.getInt("cardBack", 0),
        dealerVoice = sp.getBoolean("dealerVoice", true),
        cutCards = sp.getBoolean("cutCards", true),
    )

    fun saveSettings(s: AppSettings) {
        sp.edit()
            .putInt("decks", s.decks)
            .putBoolean("h17", s.dealerHitsSoft17)
            .putBoolean("surrender", s.surrender)
            .putBoolean("showCount", s.showRunningCount)
            .putBoolean("showHints", s.showHints)
            .putBoolean("confirmMistakes", s.confirmMistakes)
            .putInt("quizEvery", s.quizEvery)
            .putBoolean("askTrue", s.askTrueCount)
            .putInt("bots", s.bots)
            .putBoolean("deviations", s.deviations)
            .putBoolean("gradeBets", s.gradeBets)
            .putBoolean("sound", s.sound)
            .putBoolean("haptics", s.haptics)
            .putInt("dealSpeed", s.dealSpeed)
            .putInt("ramp", s.rampChoice)
            .putInt("pen", s.penetrationPct)
            .putBoolean("wongOut", s.wongOut)
            .putInt("shotClock", s.shotClock)
            .putInt("system", s.systemChoice)
            .putBoolean("das", s.doubleAfterSplit)
            .putBoolean("sixToFive", s.sixToFive)
            .putBoolean("distract", s.distractions)
            .putBoolean("distract_real", s.distractionsReal)
            .putBoolean("cover", s.coverPlay)
            .putBoolean("remind", s.reminderOn)
            .putInt("remindHour", s.reminderHour)
            .putBoolean("fullIdx", s.fullIndices)
            .putInt("shuffle", s.shuffleChoice)
            .putBoolean("realistic", s.realisticPlayers)
            .putInt("pp", s.perfectPairs)
            .putBoolean("voice", s.voice)
            .putBoolean("flex", s.flexMode)
            .putBoolean("lefty", s.leftHanded)
            .putBoolean("dealerErr", s.dealerErrors)
            .putBoolean("backoff", s.pitBackoff)
            .putInt("kelly", s.kellyChoice)
            .putInt("weeklyDays", s.weeklyDays)
            .putInt("signals", s.signals)
            .putBoolean("seated", s.seatedView)
            .putInt("cardBack", s.cardBack)
            .putBoolean("dealerVoice", s.dealerVoice)
            .putBoolean("cutCards", s.cutCards)
            .apply()
    }

    fun loadStats() = LifetimeStats(
        handsPlayed = sp.getInt("st_hands", 0),
        decisions = sp.getInt("st_dec", 0),
        correctDecisions = sp.getInt("st_dec_ok", 0),
        countQuizzes = sp.getInt("st_cq", 0),
        countCorrect = sp.getInt("st_cq_ok", 0),
        trueQuizzes = sp.getInt("st_tq", 0),
        trueCorrect = sp.getInt("st_tq_ok", 0),
        drillsRun = sp.getInt("st_dr", 0),
        drillsCorrect = sp.getInt("st_dr_ok", 0),
        bestDeckSeconds = sp.getInt("st_best_deck", 0),
        strategyDrills = sp.getInt("st_sd", 0),
        strategyDrillsCorrect = sp.getInt("st_sd_ok", 0),
        tcDrills = sp.getInt("st_tcd", 0),
        tcDrillsCorrect = sp.getInt("st_tcd_ok", 0),
        betDecisions = sp.getInt("st_bet", 0),
        betCorrect = sp.getInt("st_bet_ok", 0),
        deviationDecisions = sp.getInt("st_dev", 0),
        deviationCorrect = sp.getInt("st_dev_ok", 0),
        hintsUsed = sp.getInt("st_hints", 0),
        peeks = sp.getInt("st_peeks", 0),
        bestStrategyStreak = sp.getInt("st_best_streak", 0),
        slowDecisions = sp.getInt("st_slow", 0),
        trayDrills = sp.getInt("st_tray", 0),
        trayCorrect = sp.getInt("st_tray_ok", 0),
        watchRounds = sp.getInt("st_watch", 0),
        casinoBest = sp.getInt("st_casino_best", -1),
        casinoTests = sp.getInt("st_casino", 0),
        casinoPasses = sp.getInt("st_casino_ok", 0),
        ladderBest = sp.getInt("st_ladder_best", 0),
        pairDrills = sp.getInt("st_pair", 0),
        pairCorrect = sp.getInt("st_pair_ok", 0),
        reviews = sp.getInt("st_rev", 0),
        reviewsCorrect = sp.getInt("st_rev_ok", 0),
        coverDrills = sp.getInt("st_cover", 0),
        coverCorrect = sp.getInt("st_cover_ok", 0),
        backCountCalls = sp.getInt("st_bc", 0),
        backCountCorrect = sp.getInt("st_bc_ok", 0),
        teamCalls = sp.getInt("st_team", 0),
        teamCorrect = sp.getInt("st_team_ok", 0),
        weakSpotDrills = sp.getInt("st_weak", 0),
        weakSpotCorrect = sp.getInt("st_weak_ok", 0),
        voiceAnswers = sp.getInt("st_voice", 0),
        bestDailyStreak = sp.getInt("st_best_daily", 0),
        quickDrills = sp.getInt("st_quick", 0),
        quickBest = sp.getInt("st_quick_best", 0),
        hapticDrills = sp.getInt("st_haptic", 0),
        hapticCorrect = sp.getInt("st_haptic_ok", 0),
        deckEstimates = sp.getInt("st_deckest", 0),
        deckEstimatesOk = sp.getInt("st_deckest_ok", 0),
        dealerChecks = sp.getInt("st_dchk", 0),
        dealerChecksOk = sp.getInt("st_dchk_ok", 0),
        dealerCaught = sp.getInt("st_dcaught", 0),
        backoffs = sp.getInt("st_backoff", 0),
        flatBets = sp.getInt("st_flat", 0),
        sessionsCompleted = sp.getInt("st_sess", 0),
        bestSessionMinutes = sp.getInt("st_sess_best", 0),
        midShoeJoins = sp.getInt("st_mid", 0),
        dailyChallenges = sp.getInt("st_dc", 0),
        shares = sp.getInt("st_share", 0),
        holeFlashesUsed = sp.getInt("st_flash_ok", 0),
        holeFlashes = sp.getInt("st_flash", 0),
        tipsGiven = sp.getInt("st_tips", 0),
        distractChecks = sp.getInt("st_dist_chk", 0),
        distractChecksOk = sp.getInt("st_dist_chk_ok", 0),
        quietChecks = sp.getInt("st_qchk", 0),
        quietChecksOk = sp.getInt("st_qchk_ok", 0),
        distractQs = sp.getInt("st_dq", 0),
        distractQsOk = sp.getInt("st_dq_ok", 0),
    )

    fun saveStats(s: LifetimeStats) {
        sp.edit()
            .putInt("st_hands", s.handsPlayed)
            .putInt("st_dec", s.decisions)
            .putInt("st_dec_ok", s.correctDecisions)
            .putInt("st_cq", s.countQuizzes)
            .putInt("st_cq_ok", s.countCorrect)
            .putInt("st_tq", s.trueQuizzes)
            .putInt("st_tq_ok", s.trueCorrect)
            .putInt("st_dr", s.drillsRun)
            .putInt("st_dr_ok", s.drillsCorrect)
            .putInt("st_best_deck", s.bestDeckSeconds)
            .putInt("st_sd", s.strategyDrills)
            .putInt("st_sd_ok", s.strategyDrillsCorrect)
            .putInt("st_tcd", s.tcDrills)
            .putInt("st_tcd_ok", s.tcDrillsCorrect)
            .putInt("st_bet", s.betDecisions)
            .putInt("st_bet_ok", s.betCorrect)
            .putInt("st_dev", s.deviationDecisions)
            .putInt("st_dev_ok", s.deviationCorrect)
            .putInt("st_hints", s.hintsUsed)
            .putInt("st_peeks", s.peeks)
            .putInt("st_best_streak", s.bestStrategyStreak)
            .putInt("st_slow", s.slowDecisions)
            .putInt("st_tray", s.trayDrills)
            .putInt("st_tray_ok", s.trayCorrect)
            .putInt("st_watch", s.watchRounds)
            .putInt("st_casino_best", s.casinoBest)
            .putInt("st_casino", s.casinoTests)
            .putInt("st_casino_ok", s.casinoPasses)
            .putInt("st_ladder_best", s.ladderBest)
            .putInt("st_pair", s.pairDrills)
            .putInt("st_pair_ok", s.pairCorrect)
            .putInt("st_rev", s.reviews)
            .putInt("st_rev_ok", s.reviewsCorrect)
            .putInt("st_cover", s.coverDrills)
            .putInt("st_cover_ok", s.coverCorrect)
            .putInt("st_bc", s.backCountCalls)
            .putInt("st_bc_ok", s.backCountCorrect)
            .putInt("st_team", s.teamCalls)
            .putInt("st_team_ok", s.teamCorrect)
            .putInt("st_weak", s.weakSpotDrills)
            .putInt("st_weak_ok", s.weakSpotCorrect)
            .putInt("st_voice", s.voiceAnswers)
            .putInt("st_best_daily", s.bestDailyStreak)
            .putInt("st_quick", s.quickDrills)
            .putInt("st_quick_best", s.quickBest)
            .putInt("st_haptic", s.hapticDrills)
            .putInt("st_haptic_ok", s.hapticCorrect)
            .putInt("st_deckest", s.deckEstimates)
            .putInt("st_deckest_ok", s.deckEstimatesOk)
            .putInt("st_dchk", s.dealerChecks)
            .putInt("st_dchk_ok", s.dealerChecksOk)
            .putInt("st_dcaught", s.dealerCaught)
            .putInt("st_backoff", s.backoffs)
            .putInt("st_flat", s.flatBets)
            .putInt("st_sess", s.sessionsCompleted)
            .putInt("st_sess_best", s.bestSessionMinutes)
            .putInt("st_mid", s.midShoeJoins)
            .putInt("st_dc", s.dailyChallenges)
            .putInt("st_share", s.shares)
            .putInt("st_flash_ok", s.holeFlashesUsed)
            .putInt("st_flash", s.holeFlashes)
            .putInt("st_tips", s.tipsGiven)
            .putInt("st_dist_chk", s.distractChecks)
            .putInt("st_dist_chk_ok", s.distractChecksOk)
            .putInt("st_qchk", s.quietChecks)
            .putInt("st_qchk_ok", s.quietChecksOk)
            .putInt("st_dq", s.distractQs)
            .putInt("st_dq_ok", s.distractQsOk)
            .apply()
    }

    fun updateStats(f: (LifetimeStats) -> LifetimeStats) = saveStats(f(loadStats()))

    fun resetStats() {
        saveStats(LifetimeStats())
        sp.edit().remove("mistakes").remove("review").remove("hist").remove("errmap").remove("errtc").remove("errtcw").remove("career").remove("daily_scores").apply()
    }

    var tourSeen: Boolean
        get() = sp.getBoolean("tourSeen", false)
        set(v) { sp.edit().putBoolean("tourSeen", v).apply() }

    /** Practice points for today, the daily goal and the run of days the goal was met. */
    fun daily(today: java.time.LocalDate = java.time.LocalDate.now()): Daily {
        val day = sp.getString("d_day", "") ?: ""
        val points = if (day == today.toString()) sp.getInt("d_points", 0) else 0
        val last = sp.getString("d_streak_last", "") ?: ""
        var streak = sp.getInt("d_streak", 0)
        // A streak survives until a full day is missed.
        if (last.isNotEmpty() && last != today.toString() && last != today.minusDays(1).toString()) streak = 0
        return Daily(points, DAILY_GOAL, streak, last == today.toString())
    }

    fun addPoints(n: Int, today: java.time.LocalDate = java.time.LocalDate.now()) {
        track(points = n, today = today)
        val d = daily(today)
        val points = d.points + n
        val e = sp.edit().putString("d_day", today.toString()).putInt("d_points", points)
        if (!d.metToday && points >= DAILY_GOAL) {
            e.putInt("d_streak", d.streak + 1).putString("d_streak_last", today.toString())
            if (d.streak + 1 > loadStats().bestDailyStreak) updateStats { it.copy(bestDailyStreak = d.streak + 1) }
        } else if (!d.metToday) {
            e.putInt("d_streak", d.streak)
        }
        e.apply()
    }

    /** Current speed ladder rung, shared by the ladder drill and ladder dealing speed. */
    var ladderRung: Int
        get() = sp.getInt("ladder", 0)
        set(v) { sp.edit().putInt("ladder", v.coerceIn(0, SpeedLadder.RUNGS_MS.size - 1)).apply() }

    /** Error heat map by hand and dealer card, and by true count bucket. */
    fun errorMap(): Map<String, com.hilotrainer.engine.ErrorMap.Cell> = com.hilotrainer.engine.ErrorMap.decode(sp.getString("errmap", "") ?: "")
    fun errorByCount(): Map<String, com.hilotrainer.engine.ErrorMap.Cell> = com.hilotrainer.engine.ErrorMap.decode(sp.getString("errtc", "") ?: "")

    /** Record a graded play for the heat map. [tc] is the true count at the time, if known. */
    fun errorByWeek(): Map<String, com.hilotrainer.engine.ErrorMap.Cell> = com.hilotrainer.engine.ErrorMap.decode(sp.getString("errtcw", "") ?: "")

    fun recordPlay(cellKey: String?, tc: Double?, correct: Boolean, today: java.time.LocalDate = java.time.LocalDate.now()) {
        val e = sp.edit()
        if (cellKey != null) e.putString("errmap", com.hilotrainer.engine.ErrorMap.encode(com.hilotrainer.engine.ErrorMap.add(errorMap(), cellKey, correct)))
        if (tc != null) {
            val b = com.hilotrainer.engine.ErrorMap.tcBucket(tc).toString()
            e.putString("errtc", com.hilotrainer.engine.ErrorMap.encode(com.hilotrainer.engine.ErrorMap.add(errorByCount(), b, correct)))
            val wk = TcTrend.key(Weekly.weekStart(today), com.hilotrainer.engine.ErrorMap.tcBucket(tc))
            e.putString("errtcw", com.hilotrainer.engine.ErrorMap.encode(com.hilotrainer.engine.ErrorMap.add(errorByWeek(), wk, correct)))
        }
        e.apply()
    }

    /** Number of course lessons passed, which is also the index of the next lesson to unlock. */
    var coursePassed: Int
        get() = sp.getInt("course", 0)
        set(v) { sp.edit().putInt("course", v).apply() }

    var career: CareerProgress
        get() = (sp.getString("career", "") ?: "").split(',').mapNotNull { it.toIntOrNull() }.let {
            if (it.size < 5) CareerProgress() else CareerProgress(it[0], it[1], it[2], it[3], it[4])
        }
        set(v) { sp.edit().putString("career", listOf(v.level, v.decisions, v.correct, v.counts, v.countsOk).joinToString(",")).apply() }

    /** Best daily challenge score for each date (epoch day). */
    fun dailyScores(): Map<Long, Int> = (sp.getString("daily_scores", "") ?: "").lines().mapNotNull { l ->
        val p = l.split('\t'); if (p.size < 2) null else (p[0].toLongOrNull() ?: return@mapNotNull null) to (p[1].toIntOrNull() ?: 0)
    }.toMap()

    fun saveDailyScore(day: Long, score: Int) {
        val all = dailyScores().toMutableMap()
        all[day] = maxOf(all[day] ?: 0, score)
        sp.edit().putString("daily_scores", all.entries.sortedBy { it.key }.takeLast(400).joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }

    fun badgeContext(): BadgeContext = BadgeContext(loadStats(), coursePassed, career.level, dailyScores().values.maxOrNull() ?: 0)

    /** Every saved setting, stat and history as JSON, for moving to a new phone. */
    fun exportJson(): String {
        val o = org.json.JSONObject()
        o.put("app", "hilo-trainer"); o.put("format", 1)
        val data = org.json.JSONObject()
        for ((k, v) in sp.all) {
            val e = org.json.JSONObject()
            when (v) {
                is Int -> { e.put("t", "i"); e.put("v", v) }
                is Long -> { e.put("t", "l"); e.put("v", v) }
                is Boolean -> { e.put("t", "b"); e.put("v", v) }
                is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                is String -> { e.put("t", "s"); e.put("v", v) }
                else -> continue
            }
            data.put(k, e)
        }
        o.put("data", data)
        return o.toString()
    }

    /** Replace everything with a backup made by [exportJson]. Returns false if the file isn't one. */
    fun importJson(text: String): Boolean {
        val o = try { org.json.JSONObject(text) } catch (_: Exception) { return false }
        if (o.optString("app") != "hilo-trainer") return false
        val data = o.optJSONObject("data") ?: return false
        val e = sp.edit().clear()
        for (k in data.keys()) {
            val x = data.getJSONObject(k)
            when (x.optString("t")) {
                "i" -> e.putInt(k, x.getInt("v"))
                "l" -> e.putLong(k, x.getLong("v"))
                "b" -> e.putBoolean(k, x.getBoolean("v"))
                "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                "s" -> e.putString(k, x.getString("v"))
            }
        }
        e.commit()
        return true
    }

    /** Missed plays scheduled for review. */
    fun loadReview(): List<Review.Item> = Review.decode(sp.getString("review", "") ?: "")
    fun saveReview(items: List<Review.Item>) { sp.edit().putString("review", Review.encode(items.take(200))).apply() }
    fun reviewMissed(key: String, now: Long = System.currentTimeMillis()) = saveReview(Review.missed(loadReview(), key, now))
    fun reviewAnswered(key: String, ok: Boolean, now: Long = System.currentTimeMillis()) = saveReview(Review.answered(loadReview(), key, ok, now))

    /** Per-day practice totals for the progress chart, oldest first. */
    fun history(): List<DayStats> = (sp.getString("hist", "") ?: "").lines().mapNotNull { DayStats.parse(it) }

    fun track(
        decisions: Int = 0, correct: Int = 0, counts: Int = 0, countsOk: Int = 0, points: Int = 0,
        today: java.time.LocalDate = java.time.LocalDate.now(),
    ) {
        val all = history().toMutableList()
        val key = today.toString()
        val i = all.indexOfFirst { it.date == key }
        val cur = if (i >= 0) all[i] else DayStats(key)
        val next = cur.copy(
            decisions = cur.decisions + decisions, correct = cur.correct + correct,
            counts = cur.counts + counts, countsOk = cur.countsOk + countsOk, points = cur.points + points,
        )
        if (i >= 0) all[i] = next else all += next
        sp.edit().putString("hist", all.sortedBy { it.date }.takeLast(180).joinToString("\n") { it.encode() }).apply()
    }

    /** Recent mistakes, newest first. Stored as tab-separated lines. */
    fun loadMistakes(): List<MistakeEntry> =
        (sp.getString("mistakes", "") ?: "").lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 5) null else MistakeEntry(p[0], p[1], p[2], p[3], p[4].toLongOrNull() ?: 0L)
        }

    fun addMistake(m: MistakeEntry) {
        val clean = { s: String -> s.replace('\t', ' ').replace('\n', ' ') }
        val line = listOf(m.situation, m.chosen, m.advised, m.reason, m.time.toString()).joinToString("\t") { clean(it) }
        val all = (listOf(line) + (sp.getString("mistakes", "") ?: "").lines().filter { it.isNotBlank() }).take(100)
        sp.edit().putString("mistakes", all.joinToString("\n")).apply()
    }
}

const val DAILY_GOAL = 50

data class DayStats(
    val date: String,
    val decisions: Int = 0,
    val correct: Int = 0,
    val counts: Int = 0,
    val countsOk: Int = 0,
    val points: Int = 0,
) {
    val strategyPct: Double? get() = if (decisions == 0) null else correct * 100.0 / decisions
    val countPct: Double? get() = if (counts == 0) null else countsOk * 100.0 / counts
    fun encode() = listOf(date, decisions, correct, counts, countsOk, points).joinToString("\t")
    companion object {
        fun parse(line: String): DayStats? {
            val p = line.split('\t')
            if (p.size < 6) return null
            return DayStats(p[0], p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0, p[5].toIntOrNull() ?: 0)
        }
    }
}

data class Daily(val points: Int, val goal: Int, val streak: Int, val metToday: Boolean)

data class SkillLevel(val name: String, val next: String?)

/** The app's own practice levels, based on lifetime stats. */
fun skillLevel(s: LifetimeStats): SkillLevel {
    fun acc(ok: Int, n: Int) = if (n == 0) 0.0 else ok * 100.0 / n
    val strat = acc(s.correctDecisions, s.decisions)
    val count = acc(s.countCorrect, s.countQuizzes)
    val dev = acc(s.deviationCorrect, s.deviationDecisions)
    val bets = acc(s.betCorrect, s.betDecisions)
    val levels = listOf(
        "Learner" to (s.decisions >= 100 && strat >= 85) to "100 decisions at 85% strategy accuracy",
        "Strategy solid" to (s.decisions >= 300 && strat >= 95) to "300 decisions at 95% strategy accuracy",
        "Counter" to (s.countQuizzes >= 50 && count >= 90) to "50 count checks at 90% accuracy",
        "Advanced counter" to (s.deviationDecisions >= 30 && dev >= 85 && s.bestDeckSeconds in 1..40) to
            "30 count plays at 85% and a deck countdown in 40 seconds or less",
        "Pro" to (s.decisions >= 1000 && strat >= 98 && s.betDecisions >= 100 && bets >= 90) to
            "1,000 decisions at 98% and 100 bets at 90% sizing accuracy",
    )
    var name = "Beginner"
    for ((pair, need) in levels) {
        val (n, ok) = pair
        if (ok) name = n else return SkillLevel(name, "Next: $n, $need")
    }
    return SkillLevel(name, null)
}

data class MistakeEntry(
    val situation: String,
    val chosen: String,
    val advised: String,
    val reason: String,
    val time: Long,
)
