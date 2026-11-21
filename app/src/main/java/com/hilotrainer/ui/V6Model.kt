package com.hilotrainer.ui

import com.hilotrainer.engine.ErrorMap
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// ---------------------------------------------------------------------------
// Badges and personal bests
// ---------------------------------------------------------------------------

/** Everything a badge can look at. */
data class BadgeContext(
    val stats: LifetimeStats,
    val coursePassed: Int = 0,
    val careerLevel: Int = 0,
    val dailyBest: Int = 0,
)

data class Badge(val id: String, val title: String, val how: String, val earned: (BadgeContext) -> Boolean)

object Badges {
    private fun acc(ok: Int, n: Int) = if (n == 0) 0.0 else ok * 100.0 / n

    val ALL = listOf(
        Badge("first", "First hand", "Play your first hand") { it.stats.handsPlayed >= 1 },
        Badge("h100", "Regular", "Play 100 hands") { it.stats.handsPlayed >= 100 },
        Badge("h1000", "Grinder", "Play 1,000 hands") { it.stats.handsPlayed >= 1000 },
        Badge("run25", "Clean run", "25 right plays in a row") { it.stats.bestStrategyStreak >= 25 },
        Badge("run100", "Perfect hundred", "100 right plays in a row") { it.stats.bestStrategyStreak >= 100 },
        Badge("count", "Counter", "50 count checks at 90% or better") { it.stats.countQuizzes >= 50 && acc(it.stats.countCorrect, it.stats.countQuizzes) >= 90 },
        Badge("deck30", "Fast hands", "Count down a deck in 30 seconds or less") { it.stats.bestDeckSeconds in 1..30 },
        Badge("gold", "Gold standard", "Pass Gold in the casino test") { it.stats.casinoBest >= 2 },
        Badge("index", "Index player", "30 count plays at 85% or better") { it.stats.deviationDecisions >= 30 && acc(it.stats.deviationCorrect, it.stats.deviationDecisions) >= 85 },
        Badge("week", "Week streak", "Meet your daily goal 7 days in a row") { it.stats.bestDailyStreak >= 7 },
        Badge("month", "Month streak", "Meet your daily goal 30 days in a row") { it.stats.bestDailyStreak >= 30 },
        Badge("grad", "Graduate", "Finish the guided course") { it.coursePassed >= 10 },
        Badge("wong", "Back-counter", "20 back-counting calls at 80% or better") { it.stats.backCountCalls >= 20 && acc(it.stats.backCountCorrect, it.stats.backCountCalls) >= 80 },
        Badge("spot", "Spotter", "20 team play calls at 80% or better") { it.stats.teamCalls >= 20 && acc(it.stats.teamCorrect, it.stats.teamCalls) >= 80 },
        Badge("eagle", "Eagle eye", "Catch 10 dealer mistakes") { it.stats.dealerCaught >= 10 },
        Badge("blind", "Blind count", "Get 10 haptic count drills right") { it.stats.hapticCorrect >= 10 },
        Badge("hour", "Iron session", "Finish a full one-hour session") { it.stats.sessionsCompleted >= 1 },
        Badge("career", "High roller", "Reach the top level in career mode") { it.careerLevel >= CareerLevels.ALL.size - 1 },
        Badge("daily", "Perfect day", "Score 100 on a daily challenge") { it.dailyBest >= 100 },
        Badge("eyes", "Sharp eyes", "20 deck estimates right") { it.stats.deckEstimatesOk >= 20 },
    )

    fun earned(ctx: BadgeContext): List<Badge> = ALL.filter { it.earned(ctx) }
}

data class Best(val label: String, val value: String)

fun personalBests(ctx: BadgeContext, history: List<DayStats>): List<Best> {
    val s = ctx.stats
    val busiest = history.maxByOrNull { it.decisions }
    return listOf(
        Best("Longest run of right plays", "${s.bestStrategyStreak}"),
        Best("Fastest deck countdown", if (s.bestDeckSeconds > 0) "${s.bestDeckSeconds} s" else "Not yet"),
        Best("Casino test", com.hilotrainer.engine.CasinoTest.LEVELS.getOrNull(s.casinoBest)?.name ?: "Not passed yet"),
        Best("Speed ladder rung", "${s.ladderBest + 1} of ${com.hilotrainer.engine.SpeedLadder.RUNGS_MS.size}"),
        Best("Longest daily goal streak", "${s.bestDailyStreak} day${if (s.bestDailyStreak == 1) "" else "s"}"),
        Best("1-minute drill", if (s.quickBest > 0) "${s.quickBest} right" else "Not yet"),
        Best("Daily challenge", if (ctx.dailyBest > 0) "${ctx.dailyBest} of ${com.hilotrainer.engine.DailyChallenge.MAX_SCORE}" else "Not yet"),
        Best("Most decisions in a day", busiest?.let { "${it.decisions} (${it.date})" } ?: "Not yet"),
        Best("Longest session", if (s.bestSessionMinutes > 0) "${s.bestSessionMinutes} min" else "Not yet"),
        Best("Career level", CareerLevels.ALL[ctx.careerLevel.coerceIn(0, CareerLevels.ALL.size - 1)].name),
    )
}

// ---------------------------------------------------------------------------
// Weekly goals and the practice calendar
// ---------------------------------------------------------------------------

object Weekly {
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    data class Week(val start: LocalDate, val practiceDays: Int, val points: Int, val goalDays: Int) {
        val met: Boolean get() = practiceDays >= goalDays
    }

    /** A day counts as practice once you earn at least 10 points. */
    const val PRACTICE_POINTS = 10

    fun week(history: List<DayStats>, start: LocalDate, goalDays: Int): Week {
        val days = (0..6).map { start.plusDays(it.toLong()).toString() }.toSet()
        val inWeek = history.filter { it.date in days }
        return Week(start, inWeek.count { it.points >= PRACTICE_POINTS }, inWeek.sumOf { it.points }, goalDays)
    }

    /** Weeks in a row (ending with last week, or this week if already met) that met the goal. */
    fun weekStreak(history: List<DayStats>, today: LocalDate, goalDays: Int): Int {
        var start = weekStart(today)
        var n = if (week(history, start, goalDays).met) 1 else 0
        start = start.minusWeeks(1)
        while (week(history, start, goalDays).met) { n++; start = start.minusWeeks(1) }
        return n
    }

    /** Points per day for a calendar grid of [weeks] weeks ending this week, Monday first. */
    fun calendar(history: List<DayStats>, today: LocalDate, weeks: Int = 6): List<List<Pair<LocalDate, Int>>> {
        val byDate = history.associateBy { it.date }
        val first = weekStart(today).minusWeeks((weeks - 1).toLong())
        return List(weeks) { w ->
            List(7) { d ->
                val day = first.plusDays((w * 7 + d).toLong())
                day to (byDate[day.toString()]?.points ?: 0)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Accuracy by true count over time
// ---------------------------------------------------------------------------

object TcTrend {
    /** Groups of the heat map's true count buckets. */
    val GROUPS = listOf("Negative" to 0..1, "0 to +2" to 2..4, "+3 or more" to 5..7)

    fun key(week: LocalDate, bucket: Int) = "${week}|$bucket"

    /** Accuracy (0 to 1) per group for each of the last [weeks] weeks, oldest first. Null when no data. */
    fun series(map: Map<String, ErrorMap.Cell>, today: LocalDate, weeks: Int = 8): List<List<Double?>> {
        val starts = (weeks - 1 downTo 0).map { Weekly.weekStart(today).minusWeeks(it.toLong()) }
        return GROUPS.map { (_, buckets) ->
            starts.map { w ->
                var n = 0; var wrong = 0
                for (b in buckets) map[key(w, b)]?.let { n += it.n; wrong += it.wrong }
                if (n == 0) null else (n - wrong).toDouble() / n
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Casino rule profiles
// ---------------------------------------------------------------------------

data class CasinoProfile(val name: String, val rules: String, val note: String, val apply: (AppSettings) -> AppSettings)

object CasinoProfiles {
    val ALL = listOf(
        CasinoProfile(
            "Six decks, dealer stands", "6 decks, dealer stands on soft 17, double after split, late surrender, 3:2",
            "Good rules for counting practice.",
        ) { it.copy(decks = 6, dealerHitsSoft17 = false, doubleAfterSplit = true, surrender = true, sixToFive = false, penetrationPct = 75, shuffleChoice = 0) },
        CasinoProfile(
            "Six decks, dealer hits", "6 decks, dealer hits soft 17, double after split, no surrender, 3:2",
            "A very common shoe game. Dealer hitting soft 17 adds about 0.21% to the house edge.",
        ) { it.copy(decks = 6, dealerHitsSoft17 = true, doubleAfterSplit = true, surrender = false, sixToFive = false, penetrationPct = 75, shuffleChoice = 0) },
        CasinoProfile(
            "Eight-deck shoe", "8 decks, dealer hits soft 17, double after split, shallow cut card, 3:2",
            "More decks and a shallow cut make the count move slowly.",
        ) { it.copy(decks = 8, dealerHitsSoft17 = true, doubleAfterSplit = true, surrender = false, sixToFive = false, penetrationPct = 65, shuffleChoice = 0) },
        CasinoProfile(
            "Deep-dealt four decks", "4 decks, dealer stands on soft 17, double after split, late surrender, deep cut, 3:2",
            "Fewer decks and a deep cut let the count swing further.",
        ) { it.copy(decks = 4, dealerHitsSoft17 = false, doubleAfterSplit = true, surrender = true, sixToFive = false, penetrationPct = 85, shuffleChoice = 0) },
        CasinoProfile(
            "6:5 table", "6 decks, dealer hits soft 17, blackjack pays 6:5",
            "Avoid: 6:5 adds about 1.36% to the house edge, more than counting usually wins back.",
        ) { it.copy(decks = 6, dealerHitsSoft17 = true, doubleAfterSplit = true, surrender = false, sixToFive = true, penetrationPct = 75, shuffleChoice = 0) },
        CasinoProfile(
            "Shuffle machine table", "6 decks, dealer hits soft 17, continuous shuffling machine",
            "Avoid for counting: the cards go back in every round, so the count never builds.",
        ) { it.copy(decks = 6, dealerHitsSoft17 = true, doubleAfterSplit = true, surrender = false, sixToFive = false, penetrationPct = 75, shuffleChoice = 2) },
    )

    /** The profile these settings match, if any. */
    fun matching(s: AppSettings): Int? = ALL.indexOfFirst { it.apply(s) == s }.takeIf { it >= 0 }
}

// ---------------------------------------------------------------------------
// Career mode
// ---------------------------------------------------------------------------

data class CareerLevel(
    val name: String,
    val table: String,
    val needDecisions: Int,
    val needAccuracy: Int,
    val needCounts: Int,
    val needCountAccuracy: Int,
    val apply: (AppSettings) -> AppSettings,
)

data class CareerProgress(val level: Int = 0, val decisions: Int = 0, val correct: Int = 0, val counts: Int = 0, val countsOk: Int = 0) {
    fun accuracy() = if (decisions == 0) 0 else correct * 100 / decisions
    fun countAccuracy() = if (counts == 0) 0 else countsOk * 100 / counts
}

object CareerLevels {
    val ALL = listOf(
        CareerLevel("Practice table", "Slow dealing, one other player, hints welcome", 40, 90, 5, 80) {
            it.copy(dealSpeed = 0, bots = 1, distractions = false, coverPlay = false, shotClock = 0, dealerErrors = false, quizEvery = 1)
        },
        CareerLevel("Low-limit shoe", "Normal speed, two other players", 60, 93, 8, 85) {
            it.copy(dealSpeed = 1, bots = 2, distractions = false, coverPlay = false, shotClock = 0, dealerErrors = false, quizEvery = 2)
        },
        CareerLevel("Busy weekend", "Fast dealing, a full table, chatter and drink orders", 80, 95, 10, 90) {
            it.copy(dealSpeed = 2, bots = 3, distractions = true, coverPlay = false, shotClock = 0, realisticPlayers = true, quizEvery = 2)
        },
        CareerLevel("Under the cameras", "Count plays on, the pit watches your bets and can back you off", 100, 95, 12, 90) {
            it.copy(dealSpeed = 2, bots = 3, distractions = true, coverPlay = true, pitBackoff = true, deviations = true, shotClock = 0, quizEvery = 3)
        },
        CareerLevel("High limit room", "Eight-second shot clock, dealer mistakes to catch, everything on", 120, 97, 15, 95) {
            it.copy(dealSpeed = 2, bots = 2, distractions = true, coverPlay = true, pitBackoff = true, deviations = true, shotClock = 8, dealerErrors = true, quizEvery = 3)
        },
    )

    fun ready(p: CareerProgress): Boolean {
        val l = ALL[p.level.coerceIn(0, ALL.size - 1)]
        return p.level < ALL.size - 1 && p.decisions >= l.needDecisions && p.accuracy() >= l.needAccuracy &&
            p.counts >= l.needCounts && p.countAccuracy() >= l.needCountAccuracy
    }
}
