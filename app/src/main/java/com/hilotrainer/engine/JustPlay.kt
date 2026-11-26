package com.hilotrainer.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/*
 * Just Play: casual blackjack with a living table. Everything in this file is plain
 * game logic (no Android), so it can be unit tested: the cast of players, how each
 * one bets and decides to stay or leave, the card counters and the pit boss who
 * watches them, the three dealers, 21+3, the daily bonus and achievements.
 */

/** How a kind of gambler behaves. */
enum class Archetype(val label: String) {
    TOURIST("Tourist"), PARTY("Party guest"), HIGH_ROLLER("High roller"), PRO("Professional"),
    COUNTER("Card counter"), REGULAR("Loud regular"), DRUNK("Had a few"), CAUTIOUS("Cautious"),
    ROOKIE("First timer"), HUNCH("Hunch player"), TILTER("Chases losses"),
}

/** Hair shapes the avatar can draw. */
enum class Hair { SHORT, LONG, BALD, BUN, CURLY, SLICK, PONYTAIL, BOB, BUZZ }
/** Hats and props. */
enum class Hat { NONE, BUCKET, CAP, VISOR, TIARA, BEANIE, FEDORA }
enum class Extra { NONE, SUNGLASSES, GLASSES, HEADPHONES, GOLD_CHAIN, PEARLS, SASH, MUSTACHE, BEARD, SCARF, BOW_TIE, LANYARD }

/** What a person looks like. Colors are ARGB longs so the engine stays Android free. */
data class Look(
    val skin: Long, val hair: Long, val hairStyle: Hair, val shirt: Long, val shirtTrim: Long,
    val hat: Hat = Hat.NONE, val hatColor: Long = 0xFF333333, val extras: List<Extra> = emptyList(),
    /** Hawaiian print, plaid and so on: draws dots or stripes on the shirt. */
    val pattern: Int = 0,
)

/** One named regular in the cast pool. */
data class CastMember(
    val name: String,
    val archetype: Archetype,
    val style: BotStyle,
    val look: Look,
    /** Buy-in in table minimums. */
    val buyInMin: Int, val buyInMax: Int,
    /** Normal bet in table minimums. */
    val baseUnits: Int,
    /** Percent chance of saying something when something happens. */
    val talky: Int,
    /** Hands they plan to play before calling it a night. */
    val handsMin: Int, val handsMax: Int,
    val bio: String,
    val hello: List<String>,
    val bye: List<String>,
)

object Cast {
    private const val SKIN1 = 0xFFF2D0B1; private const val SKIN2 = 0xFFE0AC85; private const val SKIN3 = 0xFFC68A5E
    private const val SKIN4 = 0xFF8D5A3B; private const val SKIN5 = 0xFF5E3B26; private const val SUNBURN = 0xFFF0A890

    val ALL: List<CastMember> = listOf(
        CastMember(
            "Hank", Archetype.TOURIST, BotStyle.HUNCH,
            Look(SUNBURN, 0xFF8A6A45, Hair.SHORT, 0xFF19A7A0, 0xFFFFD54F, Hat.BUCKET, 0xFFE8D9B0, listOf(Extra.SUNGLASSES), pattern = 1),
            8, 20, 2, 45, 10, 25, "On vacation from Ohio. Plays hunches, chases losses, cashes out on a heater.",
            listOf("Aloha, folks! First night in town!", "Is this seat lucky? It looks lucky."),
            listOf("That's the vacation budget. Off to the buffet!", "Gotta call the wife. Good luck, all!"),
        ),
        CastMember(
            "Brenda", Archetype.PARTY, BotStyle.MIMIC,
            Look(SKIN2, 0xFFF0C24B, Hair.LONG, 0xFFF06292, 0xFFFFFFFF, Hat.TIARA, 0xFFFFE082, listOf(Extra.SASH)),
            5, 12, 2, 60, 6, 16, "Bachelorette party. Plays like the dealer and celebrates everything.",
            listOf("Woo! Bachelorette party, coming through!", "Deal me in, I'm getting married Saturday!"),
            listOf("The girls are going dancing. Byeee!", "Okay, that's the bar tab gone. Love you all!"),
        ),
        CastMember(
            "Victor", Archetype.HIGH_ROLLER, BotStyle.AGGRESSIVE,
            Look(SKIN3, 0xFF1A1A1A, Hair.SLICK, 0xFF1C1C24, 0xFFB0B0B0, extras = listOf(Extra.GOLD_CHAIN, Extra.SUNGLASSES)),
            150, 400, 15, 35, 15, 40, "Big bets, presses when he wins, doubles anything that moves.",
            listOf("Black chips, please.", "Evening. Let's make this interesting."),
            listOf("I've got a car waiting.", "Not my night. Tomorrow, then."),
        ),
        CastMember(
            "Mei", Archetype.PRO, BotStyle.BASIC,
            Look(SKIN1, 0xFF151515, Hair.BOB, 0xFF37474F, 0xFFECEFF1, extras = listOf(Extra.GLASSES)),
            40, 80, 3, 12, 25, 50, "Disciplined. Perfect basic strategy, a stop-loss and a win goal.",
            listOf("Good evening.", "Mind if I join?"),
            listOf("That's my goal for tonight. Good luck.", "Stop-loss hit. That's discipline.", "Time's up for me."),
        ),
        CastMember(
            "Dex", Archetype.COUNTER, BotStyle.BASIC,
            Look(SKIN4, 0xFF111111, Hair.BUZZ, 0xFF424242, 0xFF9E9E9E, Hat.CAP, 0xFF263238, listOf(Extra.HEADPHONES)),
            60, 120, 1, 10, 20, 45, "Card counter. Bets small until the shoe gets rich, then spreads big.",
            listOf("Just one shoe, then I'm out.", "Hey."),
            listOf("Shoe's gone cold. Later.", "That's enough for one night."),
        ),
        CastMember(
            "Ingrid", Archetype.COUNTER, BotStyle.BASIC,
            Look(SKIN1, 0xFFD9C08A, Hair.PONYTAIL, 0xFF8D6E63, 0xFFD7CCC8, extras = listOf(Extra.GLASSES, Extra.SCARF)),
            50, 100, 1, 18, 20, 45, "Looks like a librarian on a night out. Quietly counts every card.",
            listOf("Oh, is this table friendly?", "I'll just watch a hand or two first."),
            listOf("My friends are waiting for me.", "I think I'll try the slots."),
        ),
        CastMember(
            "Big Lou", Archetype.REGULAR, BotStyle.NEVER_BUST,
            Look(SKIN2, 0xFF5D4037, Hair.SHORT, 0xFFC62828, 0xFF212121, extras = listOf(Extra.MUSTACHE), pattern = 2),
            20, 50, 4, 70, 25, 60, "Here every Friday. Never busts, tilts when he's down, loves to talk.",
            listOf("Lou's here! Who's hot?", "Make room, make room!"),
            listOf("Alright, Lou's done. See you Friday.", "This table owes me. I'll be back."),
        ),
        CastMember(
            "Rita", Archetype.DRUNK, BotStyle.AGGRESSIVE,
            Look(SKIN2, 0xFFB71C1C, Hair.CURLY, 0xFF6A1B9A, 0xFFCE93D8, extras = listOf(Extra.PEARLS)),
            10, 25, 3, 65, 8, 22, "Three cocktails in. Doubles on hunches and laughs at every bust.",
            listOf("Is this the craps table? Doesn't matter!", "Somebody get me another one of these!"),
            listOf("I need a little lie down.", "Where did my purse go? Okay, bye!"),
        ),
        CastMember(
            "Grandma Dot", Archetype.CAUTIOUS, BotStyle.NEVER_BUST,
            Look(SKIN1, 0xFFE0E0E0, Hair.BUN, 0xFFB39DDB, 0xFFEDE7F6, extras = listOf(Extra.GLASSES, Extra.PEARLS)),
            6, 12, 1, 40, 15, 35, "Bets the minimum, never takes a card on 12, leaves when the bus comes.",
            listOf("Hello, dearies.", "Just a few hands before my bus."),
            listOf("That's my bus, dears. Be good!", "I'll quit while I still have cab fare."),
        ),
        CastMember(
            "Walt", Archetype.CAUTIOUS, BotStyle.BASIC,
            Look(SKIN3, 0xFFBDBDBD, Hair.SHORT, 0xFF2E7D32, 0xFFC8E6C9, Hat.CAP, 0xFF1B5E20, listOf(Extra.MUSTACHE)),
            10, 25, 2, 25, 20, 40, "Retired machinist. Plays the book, sets a loss limit and sticks to it.",
            listOf("Evening.", "Room for an old timer?"),
            listOf("That's my limit. Night, all.", "Wife says home by ten."),
        ),
        CastMember(
            "Tyler", Archetype.ROOKIE, BotStyle.MIMIC,
            Look(SKIN2, 0xFF6D4C41, Hair.CURLY, 0xFF1565C0, 0xFF90CAF9, Hat.BEANIE, 0xFFFF7043, listOf(Extra.LANYARD)),
            4, 8, 1, 55, 8, 20, "Just turned 21. Asks a lot of questions, plays like the dealer.",
            listOf("Uh, is it okay if I sit here?", "First time! Be nice."),
            listOf("I'm out of money. Is that normal?", "Okay that was fun. Bye!"),
        ),
        CastMember(
            "Jules", Archetype.HUNCH, BotStyle.HUNCH,
            Look(SKIN5, 0xFF212121, Hair.LONG, 0xFFFF8F00, 0xFF4E342E, extras = listOf(Extra.SCARF)),
            10, 30, 2, 50, 12, 30, "Superstitious. Hits stiffs against small cards because it feels right.",
            listOf("My horoscope said blackjack tonight.", "I can feel the energy at this table."),
            listOf("The energy has shifted. Farewell.", "Mercury's in retrograde, I'm going home."),
        ),
        CastMember(
            "Marco", Archetype.TILTER, BotStyle.BASIC,
            Look(SKIN3, 0xFF3E2723, Hair.SLICK, 0xFFECEFF1, 0xFF90A4AE, extras = listOf(Extra.BEARD, Extra.GOLD_CHAIN)),
            15, 40, 3, 45, 15, 40, "Plays fine when he's up. Down, he doubles his bets to win it back.",
            listOf("Let's go, I'm feeling it tonight.", "Okay, okay. Fresh start."),
            listOf("Rigged. This whole place is rigged.", "I'm up, I'm leaving. Never happens!"),
        ),
        CastMember(
            "Priya", Archetype.CAUTIOUS, BotStyle.BASIC,
            Look(SKIN3, 0xFF1B1B1B, Hair.BUN, 0xFF00838F, 0xFFE0F7FA, extras = listOf(Extra.LANYARD)),
            8, 20, 2, 35, 15, 35, "Night shift nurse on her break. Calm and dry, plays the book and never rattles.",
            listOf("Forty minutes on my break. Deal me in.", "Quiet table. Good."),
            listOf("Break's over. Back to the ward.", "Pager's going off. See you all."),
        ),
        CastMember(
            "Gus", Archetype.REGULAR, BotStyle.NEVER_BUST,
            Look(SKIN2, 0xFFCFCFCF, Hair.BALD, 0xFF1565C0, 0xFFFFFFFF, Hat.CAP, 0xFF0D47A1, listOf(Extra.GLASSES)),
            15, 40, 3, 75, 20, 50, "Retired mail carrier. A dad joke for every hand and a bad habit of never busting.",
            listOf("Special delivery! Got room for a mailman?", "Gus is here, and so are the jokes."),
            listOf("Time to make my last delivery. Home.", "That's the end of my route. Night, all."),
        ),
        CastMember(
            "Kenji", Archetype.PRO, BotStyle.BASIC,
            Look(SKIN1, 0xFF151515, Hair.SHORT, 0xFF455A64, 0xFFB0BEC5, extras = listOf(Extra.GLASSES, Extra.HEADPHONES)),
            30, 70, 2, 55, 25, 55, "Software engineer. Plays perfect strategy and cannot stop explaining the odds.",
            listOf("Evening. I brought a strategy chart.", "Mind if I run a little experiment here?"),
            listOf("Sample size reached. Thanks, all.", "That's enough data for one night."),
        ),
        CastMember(
            "Delilah", Archetype.PARTY, BotStyle.MIMIC,
            Look(SKIN4, 0xFF1A0F0A, Hair.CURLY, 0xFFC2185B, 0xFFFFD54F, Hat.NONE, 0xFF333333, listOf(Extra.PEARLS)),
            8, 20, 2, 70, 8, 22, "Lounge singer between sets. Dramatic, warm, and she treats every hand like a performance.",
            listOf("Hello, darlings! I have forty minutes.", "Make room for the entertainment!"),
            listOf("I'm on in ten, loves. Be good!", "That's my cue, darlings. Goodnight!"),
        ),
        CastMember(
            "Bruno", Archetype.REGULAR, BotStyle.BASIC,
            Look(SKIN3, 0xFF111111, Hair.BUZZ, 0xFF212121, 0xFFFFFFFF, extras = listOf(Extra.BEARD)),
            20, 45, 3, 40, 15, 40, "Off duty bouncer, soft inside and a bad loser. Plays by the book and sulks when it goes wrong.",
            listOf("Evening. Nobody's causing trouble, right?", "Mind if I sit? My feet are sore."),
            listOf("Back to the door. Good night.", "That's enough. Don't want to get grumpy."),
        ),
        CastMember(
            "Fiona", Archetype.HUNCH, BotStyle.HUNCH,
            Look(SKIN1, 0xFFB5651D, Hair.LONG, 0xFF2E7D32, 0xFFFFF59D, extras = listOf(Extra.SCARF, Extra.PEARLS)),
            10, 30, 2, 65, 12, 30, "Pub owner from County Clare. Lucky charms for everything and a hunch for every hand.",
            listOf("Top of the evening to ye all!", "Rubbed me lucky penny, so we're grand."),
            listOf("The pub won't run itself. Away I go!", "Luck's gone home. I'll follow."),
        ),
        CastMember(
            "Omar", Archetype.REGULAR, BotStyle.BASIC,
            Look(SKIN4, 0xFF262626, Hair.SHORT, 0xFF455A64, 0xFFFFC107, Hat.CAP, 0xFF212121, listOf(Extra.MUSTACHE)),
            15, 35, 2, 60, 20, 50, "Taxi driver on his night off. Storyteller, easy company, plays steady.",
            listOf("Evening, folks. Off the clock, finally.", "Anyone need a ride? I'm kidding. Deal me in."),
            listOf("The cab's waiting. Drive safe, everyone.", "That's the end of my shift, for real."),
        ),
        CastMember(
            "Chloe", Archetype.TOURIST, BotStyle.MIMIC,
            Look(SKIN2, 0xFFF4C27A, Hair.PONYTAIL, 0xFFFFFFFF, 0xFFFF80AB, Hat.VISOR, 0xFFFFFFFF, listOf(Extra.SUNGLASSES)),
            8, 20, 2, 70, 8, 22, "Influencer filming a casino vlog. Narrates every hand and plays like the dealer.",
            listOf("Hi guys! We're at the blackjack table!", "Okay, quick selfie, then I'll play."),
            listOf("That's a wrap, guys. Link in my bio!", "Battery's dead. So is my bankroll. Bye!"),
        ),
        CastMember(
            "Reggie", Archetype.TILTER, BotStyle.AGGRESSIVE,
            Look(SKIN5, 0xFF0D0D0D, Hair.BUZZ, 0xFF283593, 0xFFFFD600, extras = listOf(Extra.GOLD_CHAIN)),
            25, 60, 4, 55, 12, 35, "Retired pro athlete and a terrible loser. Competitive, loud, and chases losses.",
            listOf("Reggie's in the house. Save me a seat.", "Who's hot? I'm about to be."),
            listOf("This table is rigged. Complaint filed.", "I'm out. The refs were bad."),
        ),
        CastMember(
            "Yuki", Archetype.ROOKIE, BotStyle.MIMIC,
            Look(SKIN1, 0xFF1A1A1A, Hair.BOB, 0xFF7E57C2, 0xFFD1C4E9, extras = listOf(Extra.GLASSES, Extra.LANYARD)),
            4, 10, 1, 45, 8, 22, "Shy graduate student. Apologizes to everybody and plays like the dealer.",
            listOf("Um, hello. Is it okay if I sit here?", "Sorry. First time. I read a lot about it."),
            listOf("I should really study. Sorry. Bye.", "Thank you for letting me play. Sorry!"),
        ),
        CastMember(
            "Colonel Hargrove", Archetype.CAUTIOUS, BotStyle.BASIC,
            Look(SKIN2, 0xFFE0E0E0, Hair.BUZZ, 0xFF33691E, 0xFFFFD54F, extras = listOf(Extra.MUSTACHE, Extra.BOW_TIE)),
            20, 45, 2, 35, 20, 45, "Retired army colonel. Treats the table like a mission and sticks to his plan.",
            listOf("Evening. Let's keep this orderly.", "Colonel Hargrove, reporting for a few hands."),
            listOf("Mission complete. Dismissed.", "Fall out, everyone. Good night."),
        ),
        CastMember(
            "Savannah", Archetype.HIGH_ROLLER, BotStyle.AGGRESSIVE,
            Look(SKIN5, 0xFF0D0D0D, Hair.SLICK, 0xFF1A1A1A, 0xFFE91E63, extras = listOf(Extra.SUNGLASSES, Extra.GOLD_CHAIN)),
            100, 250, 10, 55, 12, 30, "Professional poker player on a night off. Big bets, big talk, and no patience for timid play.",
            listOf("Blackjack. How quaint. Deal me in.", "Let's see how brave this table is."),
            listOf("Too slow for me. I'm going back to poker.", "This was a warm-up. Night."),
        ),
    )

    fun byName(name: String) = ALL.first { it.name == name }
}

/** Why someone got up. */
enum class LeaveReason { BUSTED, WIN_GOAL, STOP_LOSS, TIME, COLD_SHOE, BACKED_OFF, TILT, FLAT_BET }

/** Someone in a seat right now. */
data class Seat(
    val id: Long,
    val who: CastMember,
    val chips: Int,
    val buyIn: Int,
    val bet: Int,
    val hands: Int = 0,
    val plan: Int,
    val winStreak: Int = 0,
    val lossStreak: Int = 0,
    val rebuys: Int = 0,
    /** Counter skipping hands while the count is bad. */
    val sittingOut: Boolean = false,
    /** Pit boss has told this player to flat bet. */
    val flatOnly: Boolean = false,
    /** How much the pit boss suspects them, 0 up. */
    val heat: Int = 0,
    /** Largest bet seen from them, for the spread the pit boss watches. */
    val maxBet: Int = 0,
    val tilted: Boolean = false,
) {
    val net: Int get() = chips - buyIn * (rebuys + 1)
    val baseBet: Int get() = bet
}

/** The pit boss's options when a counter gets too hot. */
enum class Backoff { FLAT_BET, SHUFFLE_UP, ASKED_TO_LEAVE }

/** A person-level event the table UI animates and voices. */
sealed class TableEvent {
    data class Arrive(val seat: Int, val s: Seat, val line: String) : TableEvent()
    data class Leave(val seat: Int, val s: Seat, val reason: LeaveReason, val line: String) : TableEvent()
    data class Rebuy(val seat: Int, val s: Seat, val line: String) : TableEvent()
    data class Say(val seat: Int, val line: String) : TableEvent()
    data class PitWatch(val seat: Int, val line: String) : TableEvent()
    data class PitAct(val seat: Int, val kind: Backoff, val boss: String, val reply: String) : TableEvent()
    data class PitComp(val seat: Int, val line: String) : TableEvent()
}

/**
 * Who sits where and how they behave. Seats are numbered left to right; null is empty.
 * [unit] is the table minimum.
 */
class TableLife(
    val seatCount: Int,
    var unit: Int,
    private val rnd: Random = Random.Default,
    var comeAndGo: Boolean = true,
) {
    val seats: Array<Seat?> = arrayOfNulls(seatCount)
    /** Seats that can be used, spread around the table for fewer players. */
    var open: Set<Int> = seatCount.let { (0 until it).toSet() }
        private set

    /** Limit the table to [n] other players. Anyone in a closed seat gets up. */
    fun setOpenSeats(n: Int): List<TableEvent> {
        open = spread(n.coerceIn(0, seatCount))
        return seats.indices.filter { seats[it] != null && it !in open }.map { leave(it, LeaveReason.TIME) }
    }

    private fun spread(n: Int): Set<Int> = when {
        seatCount != 4 -> (0 until n).toSet()
        n == 0 -> emptySet(); n == 1 -> setOf(1); n == 2 -> setOf(1, 2); n == 3 -> setOf(0, 1, 2)
        else -> (0 until 4).toSet()
    }
    private var nextId = 1L
    /** Seat the pit boss is watching, or -1. */
    var pitWatching = -1
        private set
    private var pitRounds = 0
    /** Shuffle the shoe before the next round (a shuffle-up backoff). */
    var shuffleUpNow = false
    /** Counters who were backed off tonight and can't come back. */
    private val barred = mutableSetOf<String>()
    /** Kinds of players this room does not seat (no tourists in the high limit room). */
    var exclude: Set<Archetype> = emptySet()

    val maxBet: Int get() = unit * 100

    private fun roundBet(x: Double, chips: Int): Int {
        val step = if (unit >= 25) unit else 5
        val b = (Math.round(x / step) * step).toInt()
        return b.coerceIn(unit, maxBet).coerceAtMost(chips)
    }

    private fun seated() = seats.count { it != null }

    fun fill(n: Int, trueCount: Double = 0.0) {
        repeat(n.coerceAtMost(seatCount)) { arrive(trueCount, force = true) }
    }

    /** Someone new sits down, if there's an open seat and someone wants it. Returns the event or null. */
    fun arrive(trueCount: Double, force: Boolean = false): TableEvent.Arrive? {
        val open = seats.indices.filter { seats[it] == null && it in this.open }
        if (open.isEmpty()) return null
        val here = seats.mapNotNull { it?.who?.name }.toSet()
        val pool = Cast.ALL.filter { it.name !in here && it.name !in barred && it.archetype !in exclude }
        if (pool.isEmpty()) return null
        // Counters prefer to walk in on a good count (back-counting). Everyone else is random.
        val weighted = pool.flatMap { m ->
            val w = when (m.archetype) {
                Archetype.COUNTER -> if (trueCount >= 1.0) 6 else 1
                Archetype.HIGH_ROLLER -> 1
                Archetype.PRO -> 2
                else -> 3
            }
            List(w) { m }
        }
        if (!force && weighted.isEmpty()) return null
        val who = weighted[rnd.nextInt(weighted.size)]
        val seat = open[rnd.nextInt(open.size)]
        val buyIn = roundBuyIn(who)
        val s = Seat(
            id = nextId++, who = who, chips = buyIn, buyIn = buyIn,
            bet = roundBet(who.baseUnits * unit.toDouble(), buyIn),
            plan = rnd.nextInt(who.handsMin, who.handsMax + 1),
        )
        seats[seat] = s
        return TableEvent.Arrive(seat, s, who.hello[rnd.nextInt(who.hello.size)])
    }

    private fun roundBuyIn(who: CastMember): Int {
        val raw = rnd.nextInt(who.buyInMin, who.buyInMax + 1) * unit
        val step = if (raw >= 1000) 100 else 20
        return max(unit * 2, (raw / step) * step)
    }

    /** The bet each seated player puts out for the next round, given the true count. */
    fun planBets(trueCount: Double) {
        val tc = floor(trueCount).toInt()
        for (i in seats.indices) {
            val s = seats[i] ?: continue
            val base = roundBet(s.who.baseUnits * unit.toDouble(), s.chips)
            var bet: Double = when (s.who.archetype) {
                Archetype.COUNTER, Archetype.PRO -> if (s.who.archetype == Archetype.COUNTER && !s.flatOnly) {
                    // A 1 to 8 spread keyed to the true count.
                    base * TrueCount.suggestedUnits(tc).toDouble()
                } else base.toDouble()
                Archetype.TOURIST, Archetype.PARTY -> when {
                    s.lossStreak >= 2 -> base * min(4.0, Math.pow(2.0, (s.lossStreak - 1).toDouble()))
                    s.winStreak >= 2 -> base * 1.5
                    else -> base.toDouble()
                }
                Archetype.HIGH_ROLLER -> if (s.winStreak >= 1) base * min(3.0, 1.0 + s.winStreak * 0.5) else base.toDouble()
                Archetype.REGULAR, Archetype.TILTER -> when {
                    s.tilted -> base * if (s.who.archetype == Archetype.TILTER) 3.0 else 2.0
                    s.winStreak >= 3 -> base * 1.5
                    else -> base.toDouble()
                }
                Archetype.DRUNK -> base * (1.0 + rnd.nextInt(3))
                Archetype.CAUTIOUS, Archetype.ROOKIE -> base.toDouble()
                Archetype.HUNCH -> if (rnd.nextInt(4) == 0) base * 2.0 else base.toDouble()
            }
            // Counters wong out (skip hands) when the count is bad.
            val sitOut = s.who.archetype == Archetype.COUNTER && tc <= -1
            if (s.who.archetype == Archetype.COUNTER && s.flatOnly) bet = base.toDouble()
            val b = roundBet(bet, s.chips)
            seats[i] = s.copy(bet = b, sittingOut = sitOut, maxBet = if (sitOut) s.maxBet else max(s.maxBet, b))
        }
        // The pit boss's shuffle-up: if a watched counter raises their bet, the dealer reshuffles.
        val w = seats.getOrNull(pitWatching)
        if (w != null && w.who.archetype == Archetype.COUNTER && w.bet > roundBet(w.who.baseUnits * unit.toDouble(), w.chips) && pitBackoff == Backoff.SHUFFLE_UP) {
            shuffleUpNow = true
        }
    }

    /** Seats playing this round, in deal order. */
    fun playing(): List<Int> = seats.indices.filter { seats[it] != null && !seats[it]!!.sittingOut }

    var pitBackoff: Backoff? = null
        private set

    /**
     * Settle a round for the other players and decide who stays, who rebuys, who leaves,
     * and what the pit boss does. [nets] maps seat to the player's net for the round.
     * [outcomes] maps seat to how their hand ended. Returns events in the order they happen.
     */
    fun afterRound(nets: Map<Int, Int>, outcomes: Map<Int, Outcome>, trueCount: Double, dealerBusted: Boolean, youBlackjack: Boolean): List<TableEvent> {
        val out = mutableListOf<TableEvent>()
        val tc = floor(trueCount).toInt()
        for (i in seats.indices) {
            val s0 = seats[i] ?: continue
            val net = nets[i]
            if (net == null) {
                // Sat out (a counter wonging). Maybe they walk away from a cold shoe.
                if (s0.who.archetype == Archetype.COUNTER && tc <= -2 && rnd.nextInt(100) < 35) {
                    out += leave(i, LeaveReason.COLD_SHOE)
                }
                continue
            }
            val won = net > 0; val lost = net < 0
            var s = s0.copy(
                chips = s0.chips + net, hands = s0.hands + 1,
                winStreak = if (won) s0.winStreak + 1 else if (lost) 0 else s0.winStreak,
                lossStreak = if (lost) s0.lossStreak + 1 else if (won) 0 else s0.lossStreak,
            )
            // Card counters draw heat when they spread their bets and win.
            if (s.who.archetype == Archetype.COUNTER) {
                val base = max(unit, s.who.baseUnits * unit)
                val spread = s.maxBet / base
                var h = s.heat
                if (s0.bet >= base * 2) h += 1
                if (s0.bet >= base * 4) h += 2
                if (won && s0.bet >= base * 4) h += 2
                if (s.net > base * 20) h += 1
                if (spread >= 6) h += 1
                s = s.copy(heat = h)
            }
            // Tilt: heavy losers start chasing.
            val downPct = -s.net * 100 / max(1, s.buyIn * (s.rebuys + 1))
            if ((s.who.archetype == Archetype.REGULAR || s.who.archetype == Archetype.TILTER) && downPct >= 35) s = s.copy(tilted = true)
            if (s.tilted && s.net >= 0) s = s.copy(tilted = false)
            // Winners stay longer.
            if (won && s.winStreak >= 2 && s.who.archetype != Archetype.PRO && s.who.archetype != Archetype.COUNTER) s = s.copy(plan = s.plan + 2)
            seats[i] = s

            val outcome = outcomes[i]
            if (outcome != null && rnd.nextInt(100) < s.who.talky) {
                react(s, outcome, net, dealerBusted, youBlackjack)?.let { out += TableEvent.Say(i, it) }
            }
            leaveReason(s)?.let { r ->
                if (r == LeaveReason.BUSTED && canRebuy(s)) {
                    val add = s.buyIn
                    seats[i] = s.copy(chips = s.chips + add, rebuys = s.rebuys + 1, lossStreak = 0, tilted = true)
                    out += TableEvent.Rebuy(i, seats[i]!!, if (rnd.nextBoolean()) rebuyLine(s.who.archetype) else TableTalk.say(s.who, Talk.REBUY, rnd)!!)
                } else out += leave(i, r)
            }
        }
        out += pitBoss()
        // Fresh faces: someone may sit down at an open seat.
        if (comeAndGo) {
            val chance = if (seated() == 0) 70 else if (seated() <= 2) 35 else 18
            if (rnd.nextInt(100) < chance) arrive(trueCount)?.let { out += it }
        }
        return out
    }

    private fun canRebuy(s: Seat): Boolean {
        if (s.rebuys >= 1) return false
        val pct = when (s.who.archetype) {
            Archetype.REGULAR, Archetype.TILTER, Archetype.HIGH_ROLLER -> 60
            Archetype.TOURIST, Archetype.DRUNK, Archetype.PARTY -> 40
            else -> 0
        }
        return rnd.nextInt(100) < pct
    }

    private fun rebuyLine(a: Archetype) = when (a) {
        Archetype.HIGH_ROLLER -> "Another marker, please."
        Archetype.TILTER -> "Change this. All of it. I'm winning it back."
        Archetype.REGULAR -> "Hold my seat, ATM run!"
        Archetype.DRUNK -> "Found more money in my other pocket!"
        else -> "Okay, okay, just a little more."
    }

    /** Why this player would get up now, or null to keep playing. */
    fun leaveReason(s: Seat): LeaveReason? {
        if (s.chips < unit) return LeaveReason.BUSTED
        val stake = max(1, s.buyIn * (s.rebuys + 1))
        val upPct = s.net * 100 / stake
        val downPct = -upPct
        val overTime = s.hands >= s.plan
        return when (s.who.archetype) {
            Archetype.PRO -> when { upPct >= 40 -> LeaveReason.WIN_GOAL; downPct >= 30 -> LeaveReason.STOP_LOSS; overTime -> LeaveReason.TIME; else -> null }
            Archetype.COUNTER -> when { downPct >= 50 -> LeaveReason.STOP_LOSS; overTime -> LeaveReason.TIME; s.flatOnly && rnd.nextInt(100) < 40 -> LeaveReason.FLAT_BET; else -> null }
            Archetype.TOURIST, Archetype.PARTY -> when {
                upPct >= 50 && rnd.nextInt(100) < 35 -> LeaveReason.WIN_GOAL
                overTime && upPct <= 0 -> LeaveReason.TIME
                overTime && rnd.nextInt(100) < 30 -> LeaveReason.TIME
                else -> null
            }
            Archetype.CAUTIOUS -> when { downPct >= 50 -> LeaveReason.STOP_LOSS; upPct >= 50 -> LeaveReason.WIN_GOAL; overTime -> LeaveReason.TIME; else -> null }
            Archetype.HIGH_ROLLER -> when { downPct >= 60 -> LeaveReason.STOP_LOSS; overTime && upPct < 20 -> LeaveReason.TIME; else -> null }
            Archetype.REGULAR -> when { downPct >= 70 && rnd.nextInt(100) < 40 -> LeaveReason.TILT; overTime && upPct < 0 -> LeaveReason.TIME; else -> null }
            Archetype.TILTER -> when { downPct >= 80 -> LeaveReason.TILT; upPct >= 30 && rnd.nextInt(100) < 25 -> LeaveReason.WIN_GOAL; overTime && upPct < 0 -> LeaveReason.TIME; else -> null }
            Archetype.DRUNK, Archetype.ROOKIE, Archetype.HUNCH -> if (overTime) LeaveReason.TIME else null
        }
    }

    private fun leave(i: Int, r: LeaveReason): TableEvent.Leave {
        val s = seats[i]!!
        seats[i] = null
        if (pitWatching == i) { pitWatching = -1; pitBackoff = null }
        val line = when (r) {
            LeaveReason.BUSTED -> TableTalk.say(s.who, Talk.BUSTED_OUT, rnd)!!
            LeaveReason.WIN_GOAL -> if (rnd.nextBoolean()) TableTalk.say(s.who, Talk.WIN_GOAL, rnd)!! else s.who.bye[0]
            LeaveReason.STOP_LOSS -> s.who.bye.getOrElse(1) { s.who.bye[0] }
            LeaveReason.COLD_SHOE -> TableTalk.say(s.who, Talk.COLD_SHOE, rnd) ?: "This shoe's dead. I'll find another table."
            LeaveReason.BACKED_OFF -> "Fine. Plenty of casinos in this town."
            LeaveReason.TILT -> TableTalk.say(s.who, Talk.TILT, rnd) ?: "I'm done!"
            LeaveReason.FLAT_BET -> "Flat bet only? Then I'm out."
            LeaveReason.TIME -> s.who.bye[rnd.nextInt(s.who.bye.size)]
        }
        return TableEvent.Leave(i, s, r, line)
    }

    /** Pit boss logic: watch a hot counter for a few rounds, then back them off. High rollers on a heater get a comp. */
    private fun pitBoss(): List<TableEvent> {
        val out = mutableListOf<TableEvent>()
        if (pitWatching >= 0) {
            val s = seats[pitWatching]
            if (s == null) { pitWatching = -1; pitBackoff = null; return out }
            pitRounds++
            if (pitBackoff == null && pitRounds >= 2 && s.heat >= HEAT_BACKOFF) {
                val kind = when {
                    s.heat >= HEAT_BACKOFF + 6 || s.net > unit * 40 -> Backoff.ASKED_TO_LEAVE
                    rnd.nextBoolean() -> Backoff.FLAT_BET
                    else -> Backoff.SHUFFLE_UP
                }
                pitBackoff = kind
                when (kind) {
                    Backoff.ASKED_TO_LEAVE -> {
                        out += TableEvent.PitAct(pitWatching, kind, TableTalk.pit("leave", rnd), TableTalk.say(s.who, Talk.BACKOFF_REPLY, rnd)!!)
                        barred += s.who.name
                        out += leave(pitWatching, LeaveReason.BACKED_OFF)
                    }
                    Backoff.FLAT_BET -> {
                        seats[pitWatching] = s.copy(flatOnly = true)
                        out += TableEvent.PitAct(pitWatching, kind, TableTalk.pit("flat", rnd), "Sure. No problem.")
                    }
                    Backoff.SHUFFLE_UP -> out += TableEvent.PitAct(pitWatching, kind, TableTalk.pit("shuffle", rnd), "Seriously?")
                }
            } else if (pitRounds >= 8 && s.heat < HEAT_BACKOFF) {
                pitWatching = -1; pitBackoff = null
            }
            return out
        }
        val hot = seats.indices.filter { seats[it]?.who?.archetype == Archetype.COUNTER && seats[it]!!.heat >= HEAT_WATCH }
            .maxByOrNull { seats[it]!!.heat }
        if (hot != null) {
            pitWatching = hot; pitRounds = 0; pitBackoff = null
            out += TableEvent.PitWatch(hot, TableTalk.pit("watch", rnd))
            return out
        }
        val roller = seats.indices.firstOrNull { seats[it]?.who?.archetype == Archetype.HIGH_ROLLER && seats[it]!!.net > unit * 30 }
        if (roller != null && rnd.nextInt(100) < 25) {
            out += TableEvent.PitComp(roller, "Mr. ${seats[roller]!!.who.name}. " + TableTalk.pit("comp", rnd))
        }
        return out
    }

    fun clearPitBoss() { pitWatching = -1; pitBackoff = null; pitRounds = 0 }

    /** What a player says after a hand, matched to their personality and how the hand went. */
    fun react(s: Seat, o: Outcome, net: Int, dealerBusted: Boolean, youBlackjack: Boolean): String? {
        val a = s.who.archetype
        val t = when {
            youBlackjack && rnd.nextInt(3) == 0 -> Talk.YOU_BLACKJACK
            o == Outcome.BLACKJACK -> Talk.BLACKJACK
            o == Outcome.WIN && dealerBusted && rnd.nextBoolean() -> Talk.DEALER_BUST
            o == Outcome.WIN && net >= unit * 10 -> Talk.BIG_WIN
            o == Outcome.WIN && s.winStreak >= 3 -> Talk.WIN_STREAK
            o == Outcome.WIN -> Talk.WIN
            o == Outcome.BUST -> Talk.BUST
            o == Outcome.LOSE && s.lossStreak >= 3 -> Talk.LOSS_STREAK
            o == Outcome.LOSE -> Talk.LOSE
            o == Outcome.PUSH -> Talk.PUSH
            else -> Talk.LOSE
        }
        return TableTalk.say(s.who, t, rnd)
    }

    /** Small talk between hands from someone at the table, sometimes with a reply. */
    fun idleChat(): List<TableEvent.Say> {
        val here = seats.indices.filter { seats[it] != null }
        if (here.isEmpty()) return emptyList()
        val i = here[rnd.nextInt(here.size)]
        val s = seats[i]!!
        if (rnd.nextInt(100) >= s.who.talky) return emptyList()
        val line = TableTalk.say(s.who, Talk.IDLE, rnd) ?: return emptyList()
        val out = mutableListOf(TableEvent.Say(i, line))
        val others = here.filter { it != i && seats[it]!!.who.talky >= 40 }
        if (others.isNotEmpty() && rnd.nextInt(100) < 35) {
            val j = others[rnd.nextInt(others.size)]
            TableTalk.say(seats[j]!!.who, Talk.REPLY, rnd)?.let { out += TableEvent.Say(j, it) }
        }
        return out
    }

    companion object {
        /** Heat when the pit boss walks over to watch. */
        const val HEAT_WATCH = 5
        /** Heat when the pit boss acts. */
        const val HEAT_BACKOFF = 8
    }
}

/** The three dealers who rotate through the table. */
data class CasualDealer(
    val name: String,
    val look: Look,
    val personality: String,
    /** Milliseconds per card at normal speed. */
    val cardMs: Int,
    /** 0 hand riffle, 1 strip shuffle, 2 shuffle machine. */
    val shuffle: Int,
    /** How the cards fly: 0 low slide, 1 high arc pitch, 2 deliberate place. */
    val pitch: Int,
    val talky: Int,
    val greet: List<String>,
    val chatter: List<String>,
    val onBust: List<String>,
    val onBlackjack: List<String>,
    val onPlayerBlackjack: List<String>,
    val shiftOut: List<String>,
)

object CasualDealers {
    val ALL = listOf(
        CasualDealer(
            "Vera", Look(0xFFE8C4A6, 0xFF9E9E9E, Hair.BOB, 0xFF111111, 0xFFE9C46A, extras = listOf(Extra.BOW_TIE)),
            "Veteran of 20 years. Fast, precise, few words. Uses the shuffle machine.", 240, 2, 0, 15,
            listOf("Evening. Bets down, please.", "Vera, taking over. Good luck.", "Good evening, everyone."),
            listOf("Next hand.", "Thank you.", "Bets, please.", "Checks play.", "Good luck."),
            listOf("Dealer busts. Paying the table.", "Twenty two. Winners all around."),
            listOf("Dealer has blackjack.", "Blackjack. Sorry, folks."),
            listOf("Blackjack. Nicely done.", "Blackjack pays three to two."),
            listOf("Break time. Good luck, everyone.", "That's my shift. Take care."),
        ),
        CasualDealer(
            "Danny", Look(0xFFC68A5E, 0xFF2B1B10, Hair.CURLY, 0xFFB71C1C, 0xFFFFE082, extras = listOf(Extra.BOW_TIE)),
            "Chatty and friendly. Roots for the table. Riffles by hand.", 360, 0, 1, 45,
            listOf("Hey hey! Danny's here, let's get this table hot!", "What's up, everybody? Let's win some money!", "Your favorite dealer has arrived!"),
            listOf("Where's everybody from tonight?", "I've got a good feeling about this shoe.", "Come on, table, let's go!", "Big money, no whammies!",
                "Anybody catch the fight last weekend?", "Tip the dealer, it's good luck. Kidding! Mostly.", "This shoe's gonna be a monster, I can feel it.", "Who's celebrating something tonight?"),
            listOf("I busted! Everybody gets paid!", "Too many for me, sorry not sorry!", "Dealer busts! Chips for everybody!"),
            listOf("Ah, I hate doing that to you guys.", "Sorry, folks, dealer blackjack.", "Don't hate me! The cards did it!"),
            listOf("Blackjack! Love to see it!", "Yes! Pay that one!", "Look at that! Twenty one!"),
            listOf("Danny's going on break. Be good while I'm gone!", "Gotta go, you guys were awesome!"),
        ),
        CasualDealer(
            "Mr. Grant", Look(0xFF8D5A3B, 0xFF1A1A1A, Hair.BALD, 0xFF263238, 0xFFB0BEC5, extras = listOf(Extra.BEARD)),
            "Stern and by the book. Slow, careful, quiet. Strip shuffles.", 470, 1, 2, 12,
            listOf("Good evening. Hands off the cards, please.", "Grant. Place your bets.", "Bets in the circle, please."),
            listOf("Hand signals, please.", "Keep your drinks off the rail.", "One player to a hand.", "Please wait for the payout before touching your chips."),
            listOf("Dealer busts.", "Twenty two. Paying."),
            listOf("Dealer blackjack.", "Blackjack. House wins."),
            listOf("Blackjack pays.", "Blackjack."),
            listOf("Relief is here. Good evening.", "My break. Good luck."),
        ),
    )
    /** Rounds per dealer shift. */
    const val SHIFT = 20
}

/** 21+3: your two cards and the dealer's up card as a three-card poker hand. */
object TwentyOnePlusThree {
    // Pays (to 1): Evolution's live table as reported by casino.org and playusa.com, checked 2026-11-21.
    const val SUITED_TRIPS = 100
    const val STRAIGHT_FLUSH = 40
    const val TRIPS = 30
    const val STRAIGHT = 10
    const val FLUSH = 5

    private fun high(r: Rank) = r.ordinal + 2 // 2..14, ace high

    /** Returns (name, pays to 1) or null when it loses. Aces can be high (Q-K-A) or low (A-2-3). */
    fun evaluate(a: Card, b: Card, c: Card): Pair<String, Int>? {
        val cards = listOf(a, b, c)
        val flush = cards.all { it.suit == a.suit }
        val trips = cards.all { it.rank == a.rank }
        val v = cards.map { high(it.rank) }.sorted()
        val straight = (v[1] == v[0] + 1 && v[2] == v[1] + 1) || v == listOf(2, 3, 14)
        return when {
            trips && flush -> "Suited three of a kind" to SUITED_TRIPS
            straight && flush -> "Straight flush" to STRAIGHT_FLUSH
            trips -> "Three of a kind" to TRIPS
            straight -> "Straight" to STRAIGHT
            flush -> "Flush" to FLUSH
            else -> null
        }
    }
}

/** Once a day, free chips. Coming back on consecutive days builds a streak worth more. */
object DailyBonus {
    const val BASE = 250
    const val PER_DAY = 50
    const val MAX_STREAK = 7

    /** Returns (amount, new streak), or null when today's bonus is already claimed. */
    fun claim(lastDay: Long, streak: Int, today: Long): Pair<Int, Int>? {
        if (lastDay == today) return null
        val s = if (lastDay == today - 1) min(streak + 1, MAX_STREAK) else 1
        return BASE + PER_DAY * (s - 1) to s
    }
}

/** Lifetime numbers for Just Play. */
data class CasualStats(
    val hands: Int = 0, val wins: Int = 0, val losses: Int = 0, val pushes: Int = 0, val blackjacks: Int = 0,
    val biggestWin: Int = 0, val biggestBankroll: Int = 0, val winStreak: Int = 0, val bestWinStreak: Int = 0,
    val rebuys: Int = 0, val wagered: Long = 0, val net: Long = 0, val doublesWon: Int = 0, val splits: Int = 0,
    val sideWins: Int = 0, val fiveCard: Int = 0, val dealerBusts: Int = 0,
) {
    val decided: Int get() = wins + losses
    val winRate: Int get() = if (hands == 0) 0 else wins * 100 / hands
}

/** One round from your seat, for stats and achievements. */
data class RoundSummary(
    val net: Int, val wagered: Int, val wins: Int, val losses: Int, val pushes: Int, val blackjack: Boolean,
    val doubledWin: Boolean, val split: Boolean, val splitBothWon: Boolean, val sideWin: Boolean,
    val fiveCardWin: Boolean, val dealerBust: Boolean, val bankrollAfter: Int, val maxBetSeen: Int,
)

data class Achievement(val id: String, val title: String, val desc: String, val test: (CasualStats, RoundSummary) -> Boolean)

object Achievements {
    val ALL = listOf(
        Achievement("first_win", "First win", "Win a hand") { s, _ -> s.wins >= 1 },
        Achievement("first_bj", "Natural", "Get your first blackjack") { s, _ -> s.blackjacks >= 1 },
        Achievement("streak5", "On a heater", "Win 5 hands in a row") { s, _ -> s.bestWinStreak >= 5 },
        Achievement("streak10", "Unstoppable", "Win 10 hands in a row") { s, _ -> s.bestWinStreak >= 10 },
        Achievement("double", "Double trouble", "Win a doubled hand") { s, _ -> s.doublesWon >= 1 },
        Achievement("split2", "Split decision", "Split and win both hands") { _, r -> r.splitBothWon },
        Achievement("big500", "Big score", "Win \$500 or more in one round") { _, r -> r.net >= 500 },
        Achievement("big2500", "Whale watching", "Win \$2,500 or more in one round") { _, r -> r.net >= 2500 },
        Achievement("roll5k", "Five grand", "Reach a \$5,000 bankroll") { s, _ -> s.biggestBankroll >= 5000 },
        Achievement("roll25k", "High roller", "Reach a \$25,000 bankroll") { s, _ -> s.biggestBankroll >= 25000 },
        Achievement("hands100", "Regular", "Play 100 hands") { s, _ -> s.hands >= 100 },
        Achievement("hands1000", "Fixture", "Play 1,000 hands") { s, _ -> s.hands >= 1000 },
        Achievement("side", "Side action", "Hit a side bet") { s, _ -> s.sideWins >= 1 },
        Achievement("five", "Five card hand", "Win with five or more cards") { s, _ -> s.fiveCard >= 1 },
        Achievement("comeback", "Comeback kid", "Win a round after a rebuy") { s, r -> s.rebuys >= 1 && r.net > 0 },
    )

    /** Ids newly earned by this round. */
    fun check(have: Set<String>, s: CasualStats, r: RoundSummary): List<Achievement> =
        ALL.filter { it.id !in have && it.test(s, r) }

    fun apply(s: CasualStats, r: RoundSummary): CasualStats {
        val ws = if (r.net > 0) s.winStreak + 1 else if (r.net < 0) 0 else s.winStreak
        return s.copy(
            hands = s.hands + r.wins + r.losses + r.pushes, wins = s.wins + r.wins, losses = s.losses + r.losses, pushes = s.pushes + r.pushes,
            blackjacks = s.blackjacks + if (r.blackjack) 1 else 0, biggestWin = max(s.biggestWin, r.net),
            biggestBankroll = max(s.biggestBankroll, r.bankrollAfter), winStreak = ws, bestWinStreak = max(s.bestWinStreak, ws),
            wagered = s.wagered + r.wagered, net = s.net + r.net, doublesWon = s.doublesWon + if (r.doubledWin) 1 else 0,
            splits = s.splits + if (r.split) 1 else 0, sideWins = s.sideWins + if (r.sideWin) 1 else 0,
            fiveCard = s.fiveCard + if (r.fiveCardWin) 1 else 0, dealerBusts = s.dealerBusts + if (r.dealerBust) 1 else 0,
        )
    }
}
