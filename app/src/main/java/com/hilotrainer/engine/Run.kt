package com.hilotrainer.engine

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Roguelike run mode for Just Play. Beat a blind by scoring enough chips in a few hands. Every winning hand scores
 * chips times mult, perks bend the numbers, and money from each blind buys more perks. It plays on its own shoe and
 * never touches training stats.
 */
enum class Rarity(val label: String, val weight: Int) { COMMON("Common", 60), UNCOMMON("Uncommon", 30), RARE("Rare", 10) }

enum class RunPhase { SELECT, PLAY, CLEARED, SHOP, OVER, WON }

/** One finished hand as the perks see it. */
data class HandCtx(
    val outcome: Outcome, val cards: List<Card>, val total: Int, val soft: Boolean, val doubled: Boolean, val split: Boolean,
    val dealerBust: Boolean, val stake: Int, val winStreak: Int, val lossesThisBlind: Int, val trueCount: Double,
) {
    val won: Boolean get() = outcome == Outcome.WIN || outcome == Outcome.BLACKJACK
    val blackjack: Boolean get() = outcome == Outcome.BLACKJACK
}

data class Trigger(val perk: String, val text: String)

/** What the perks add to one hand's score. */
class Fx {
    var chips = 0; var mult = 0.0; var xmult = 1.0; var money = 0
    val notes = mutableListOf<Trigger>()
    fun hit(perk: String, text: String, chips: Int = 0, mult: Double = 0.0, x: Double = 1.0, money: Int = 0) {
        this.chips += chips; this.mult += mult; this.xmult *= x; this.money += money; notes += Trigger(perk, text)
    }
}

class Perk(
    val id: String, val name: String, val rarity: Rarity, val cost: Int, val desc: String,
    val handsBonus: Int = 0, val interestBonus: Int = 0, val comboBonus: Boolean = false, val showsCount: Boolean = false,
    val clearMoney: Int = 0, val saveFirstLoss: Boolean = false,
    val effect: (Perk.(HandCtx, Fx) -> Unit)? = null,
) {
    val sell: Int get() = (cost / 2).coerceAtLeast(1)
}

object Perks {
    private fun p(id: String, name: String, r: Rarity, cost: Int, desc: String, effect: Perk.(HandCtx, Fx) -> Unit) = Perk(id, name, r, cost, desc, effect = effect)

    val ALL: List<Perk> = listOf(
        p("sevens", "Lucky Sevens", Rarity.COMMON, 4, "Winning hands score +50 chips for each 7 in them.") { c, f ->
            val n = c.cards.count { it.rank == Rank.SEVEN }; if (c.won && n > 0) f.hit(name, "+${50 * n} chips", chips = 50 * n)
        },
        p("court", "Royal Court", Rarity.COMMON, 4, "Winning hands score +30 chips for each face card in them.") { c, f ->
            val n = c.cards.count { it.rank in listOf(Rank.JACK, Rank.QUEEN, Rank.KING) }; if (c.won && n > 0) f.hit(name, "+${30 * n} chips", chips = 30 * n)
        },
        p("ace", "Ace in the Hole", Rarity.COMMON, 5, "Winning hands that hold an Ace score +3 mult.") { c, f ->
            if (c.won && c.cards.any { it.rank.isAce }) f.hit(name, "+3 mult", mult = 3.0)
        },
        p("tell", "Dealer's Tell", Rarity.COMMON, 4, "+100 chips when you win because the dealer busts.") { c, f ->
            if (c.won && c.dealerBust) f.hit(name, "+100 chips", chips = 100)
        },
        p("soft", "Soft Touch", Rarity.COMMON, 5, "Winning soft hands (counting an Ace as 11) score +4 mult.") { c, f ->
            if (c.won && c.soft) f.hit(name, "+4 mult", mult = 4.0)
        },
        p("twenty", "Twenty", Rarity.COMMON, 5, "Winning with exactly 20 scores +4 mult.") { c, f ->
            if (c.won && c.total == 20) f.hit(name, "+4 mult", mult = 4.0)
        },
        p("penny", "Penny Saver", Rarity.COMMON, 4, "Earn $1 for every winning hand.") { c, f ->
            if (c.won) f.hit(name, "+\$1", money = 1)
        },
        Perk("cut", "Cut Card", Rarity.COMMON, 6, "+1 hand in every blind.", handsBonus = 1),
        Perk("spotter", "Spotter", Rarity.COMMON, 5, "Shows the Hi-Lo running count on the table.", showsCount = true),
        p("sharp", "Card Sharp", Rarity.UNCOMMON, 7, "Winning hands dealt at a true count of +2 or more score x2 mult.") { c, f ->
            if (c.won && c.trueCount >= 2.0) f.hit(name, "x2 mult (count +2)", x = 2.0)
        },
        p("contrarian", "Contrarian", Rarity.UNCOMMON, 6, "Winning hands dealt at a true count of -1 or less score +6 mult.") { c, f ->
            if (c.won && c.trueCount <= -1.0) f.hit(name, "+6 mult (cold shoe)", mult = 6.0)
        },
        p("trouble", "Double Trouble", Rarity.UNCOMMON, 7, "Doubled wins score x2 mult.") { c, f ->
            if (c.won && c.doubled) f.hit(name, "x2 mult", x = 2.0)
        },
        p("splitter", "Splitter", Rarity.UNCOMMON, 6, "Each winning split hand scores +80 chips and +3 mult.") { c, f ->
            if (c.won && c.split) f.hit(name, "+80 chips +3 mult", chips = 80, mult = 3.0)
        },
        p("minimal", "Minimalist", Rarity.UNCOMMON, 6, "Winning with exactly two cards (not a blackjack) scores +6 mult.") { c, f ->
            if (c.won && !c.blackjack && c.cards.size == 2 && !c.split) f.hit(name, "+6 mult", mult = 6.0)
        },
        p("roller", "High Roller", Rarity.UNCOMMON, 7, "Wins at stake 3 score x1.5 mult.") { c, f ->
            if (c.won && c.stake == 3) f.hit(name, "x1.5 mult", x = 1.5)
        },
        Perk("banker", "Banker", Rarity.UNCOMMON, 6, "Interest pays up to \$5 more at the end of a blind.", interestBonus = 5),
        Perk("combo", "Combo King", Rarity.UNCOMMON, 7, "Win streak bonuses are doubled.", comboBonus = true),
        p("charlie", "Charlie", Rarity.RARE, 9, "Winning with five or more cards scores x4 mult.") { c, f ->
            if (c.won && c.cards.size >= 5) f.hit(name, "x4 mult", x = 4.0)
        },
        p("sore", "Sore Loser", Rarity.RARE, 8, "Winning hands score +2 mult for each hand you lost this blind.") { c, f ->
            if (c.won && c.lossesThisBlind > 0) f.hit(name, "+${2 * c.lossesThisBlind} mult", mult = 2.0 * c.lossesThisBlind)
        },
        p("wild", "Wild Card", Rarity.RARE, 10, "Every winning hand scores x2 mult.") { c, f ->
            if (c.won) f.hit(name, "x2 mult", x = 2.0)
        },
        p("baron", "Blackjack Baron", Rarity.RARE, 9, "Blackjacks score x3 mult.") { c, f ->
            if (c.blackjack) f.hit(name, "x3 mult", x = 3.0)
        },
        Perk("ticket", "Golden Ticket", Rarity.RARE, 8, "Earn \$3 every time you clear a blind.", clearMoney = 3),
        Perk("net", "Safety Net", Rarity.RARE, 8, "The first lost hand of each blind does not use up a hand.", saveFirstLoss = true),
        p("echo", "Echo", Rarity.RARE, 8, "Wins over a busted dealer score x1.5 mult.") { c, f ->
            if (c.won && c.dealerBust) f.hit(name, "x1.5 mult", x = 1.5)
        },
    )
    private val byId = ALL.associateBy { it.id }
    fun get(id: String): Perk? = byId[id]
}

enum class Tonic(val label: String, val cost: Int, val desc: String) {
    HAND("Lucky Charm", 3, "One extra hand in the next blind."),
    COUNT("Count Notes", 3, "Shows the running count during the next blind."),
}

class Offer(val perk: Perk?, val tonic: Tonic?, var sold: Boolean = false) {
    val name: String get() = perk?.name ?: tonic!!.label
    val cost: Int get() = perk?.cost ?: tonic!!.cost
    val desc: String get() = perk?.desc ?: tonic!!.desc
    val rarity: Rarity get() = perk?.rarity ?: Rarity.COMMON
}

class Boss(
    val id: String, val name: String, val desc: String, val targetMul: Double = 1.0, val handsDelta: Int = 0, val onlyHands: Int = 0,
    val noDoubleSplit: Boolean = false, val noCombo: Boolean = false, val lossCost: Int = 0, val minWinTotal: Int = 0,
    val hideCount: Boolean = false, val noRepeatStake: Boolean = false,
)

object Bosses {
    val ALL = listOf(
        Boss("needle", "The Needle", "One hand only. Make it count.", targetMul = 0.45, onlyHands = 1),
        Boss("hook", "The Hook", "No doubling and no splitting.", noDoubleSplit = true),
        Boss("ice", "The Ice", "Win streak bonuses are switched off.", noCombo = true),
        Boss("tax", "The Tax", "Every lost hand costs \$3 more.", lossCost = 3),
        Boss("pillar", "The Pillar", "Wins with less than 18 score nothing.", minWinTotal = 18),
        Boss("fog", "The Fog", "The running count is hidden.", hideCount = true),
        Boss("eye", "The Eye", "You cannot use the same stake twice in a row.", noRepeatStake = true),
        Boss("wall", "The Wall", "A taller wall to climb, but one more hand.", targetMul = 1.35, handsDelta = 1),
    )
}

enum class StartDeck(val label: String, val desc: String, val money: Int, val hands: Int, val perk: String?, val unlockAnte: Int) {
    STANDARD("Standard Deck", "\$8 and 5 hands a blind. No frills.", 8, 5, null, 0),
    COUNTER("Counter's Deck", "\$6, 5 hands a blind and the Spotter perk, so you see the running count from the first hand.", 6, 5, "spotter", 0),
    ROLLER("High Roller Deck", "\$10, only 4 hands a blind, and the High Roller perk. Clear Ante 3 once to unlock.", 10, 4, "roller", 3),
}

data class ScoreEvent(
    val key: Int, val outcome: Outcome, val baseChips: Int, val chips: Int, val mult: Double, val total: Int, val money: Int,
    val triggers: List<Trigger>, val note: String,
)

data class Cashout(val lines: List<Pair<String, Int>>) { val total: Int get() = lines.sumOf { it.second } }

class Run(val deck: StartDeck, private val rnd: Random = Random.Default) {
    companion object {
        const val MAX_ANTE = 6
        const val SLOTS = 5
        const val UNIT = 10
        val BASE = intArrayOf(100, 250, 550, 1150, 2400, 5000)
        val BLIND_MUL = doubleArrayOf(1.0, 1.5, 2.0)
        val BLIND_NAMES = listOf("Small Blind", "Big Blind", "Boss Blind")
        val REWARD = intArrayOf(3, 4, 5)
        const val SKIP_TAG = 4
        val RULES = Rules(decks = 6, dealerHitsSoft17 = false, doubleAfterSplit = true, surrenderAllowed = false, maxHands = 4, penetration = 0.75)
    }

    var phase = RunPhase.SELECT; private set
    var ante = 1; private set
    var blind = 0; private set
    var money = deck.money; private set
    val perks = mutableListOf<Perk>()
    var boss: Boss = Bosses.ALL[rnd.nextInt(Bosses.ALL.size)]; private set
    var game = BlackjackGame(RULES, 1_000_000, rnd)
        private set

    var score = 0; private set
    var handsLeft = 0; private set
    var handsMax = 0; private set
    var winStreak = 0; private set
    var losses = 0; private set
    private var lastStake = 0
    var stake = 2
    var inRound = false; private set
    var lastScore: List<ScoreEvent> = emptyList(); private set
    var cashout: Cashout? = null; private set
    var bonusHands = 0; private set
    var countNotes = false; private set
    private var netUsed = false
    private var trueAtDeal = 0.0
    private var evKey = 0

    // Shop
    var offers: List<Offer> = emptyList(); private set
    var rerollCost = 2; private set

    // The run's own record, shown at the end.
    var blindsCleared = 0; private set
    var bestHand = 0; private set
    var skips = 0; private set
    var cause = ""; private set

    init {
        deck.perk?.let { id -> Perks.get(id)?.let { perks += it } }
    }

    val gs: GameState get() = game.state()
    val target: Int get() = targetFor(ante, blind, boss)
    fun targetFor(a: Int, b: Int, bs: Boss): Int {
        val raw = BASE[(a - 1).coerceIn(0, MAX_ANTE - 1)] * BLIND_MUL[b] * (if (b == 2) bs.targetMul else 1.0)
        return ((raw / 10).roundToInt() * 10).coerceAtLeast(10)
    }
    val reward: Int get() = REWARD[blind]
    val isBoss: Boolean get() = blind == 2
    val blindName: String get() = if (isBoss) boss.name else BLIND_NAMES[blind]
    val activeBoss: Boss? get() = if (isBoss) boss else null
    val countVisible: Boolean get() = (countNotes || perks.any { it.showsCount }) && activeBoss?.hideCount != true
    val cleared: Boolean get() = score >= target
    val comboNow: Double get() = comboFor(winStreak)
    private fun comboFor(streak: Int): Double {
        if (activeBoss?.noCombo == true || streak < 2) return 0.0
        val per = if (perks.any { it.comboBonus }) 2.0 else 1.0
        val cap = if (perks.any { it.comboBonus }) 8.0 else 4.0
        return minOf((streak - 1) * per, cap)
    }
    val interestCap: Int get() = 5 + perks.sumOf { it.interestBonus }

    // Flow
    fun startBlind(): Boolean {
        if (phase != RunPhase.SELECT) return false
        handsMax = when {
            activeBoss?.onlyHands ?: 0 > 0 -> activeBoss!!.onlyHands
            else -> deck.hands + perks.sumOf { it.handsBonus } + bonusHands + (activeBoss?.handsDelta ?: 0)
        }
        handsLeft = handsMax; score = 0; winStreak = 0; losses = 0; lastStake = 0; netUsed = false
        lastScore = emptyList(); cashout = null; phase = RunPhase.PLAY
        return true
    }

    fun skipBlind(): Boolean {
        if (phase != RunPhase.SELECT || isBoss) return false
        money += SKIP_TAG; skips++
        nextBlind()
        return true
    }

    private fun nextBlind() {
        bonusHands = 0; countNotes = false
        if (blind == 2) {
            if (ante >= MAX_ANTE) { phase = RunPhase.WON; return }
            ante++; blind = 0; boss = Bosses.ALL[rnd.nextInt(Bosses.ALL.size)]
        } else blind++
        phase = RunPhase.SELECT
    }

    fun canDeal(s: Int): Boolean = phase == RunPhase.PLAY && !inRound && handsLeft > 0 && s in 1..3 && !(activeBoss?.noRepeatStake == true && s == lastStake)

    fun deal(s: Int): Boolean {
        if (!canDeal(s)) return false
        stake = s
        trueAtDeal = game.upcomingTrueCount()
        inRound = true
        game.deal(UNIT * s)
        if (game.state().phase == Phase.INSURANCE) game.insurance(false)
        if (game.state().phase == Phase.ROUND_OVER) settle()
        return true
    }

    fun canAct(a: Action): Boolean {
        if (phase != RunPhase.PLAY || !inRound || game.state().phase != Phase.PLAYER) return false
        if (activeBoss?.noDoubleSplit == true && (a == Action.DOUBLE || a == Action.SPLIT)) return false
        val o = game.options()
        return when (a) { Action.DOUBLE -> o.canDouble; Action.SPLIT -> o.canSplit; Action.SURRENDER -> false; else -> true }
    }

    fun act(a: Action): Boolean {
        if (!canAct(a)) return false
        game.act(a)
        if (game.state().phase == Phase.ROUND_OVER) settle()
        return true
    }

    private fun settle() {
        inRound = false
        handsLeft--
        lastStake = stake
        val g = game.state()
        val dBust = handTotal(g.dealerCards).first > 21
        val out = mutableListOf<ScoreEvent>()
        var refunded = false
        g.hands.forEachIndexed { i, h ->
            val r = g.results.getOrNull(i) ?: return@forEachIndexed
            val (total, soft) = handTotal(h.cards)
            val won = r.outcome == Outcome.WIN || r.outcome == Outcome.BLACKJACK
            val lost = r.outcome == Outcome.LOSE || r.outcome == Outcome.BUST
            if (won) winStreak++ else if (lost) winStreak = 0
            if (lost) losses++
            val ctx = HandCtx(r.outcome, h.cards, total, soft, h.doubled, h.isSplitHand, dBust, stake, winStreak, losses - (if (lost) 1 else 0), trueAtDeal)
            if (won) {
                val bossBlocks = activeBoss?.minWinTotal?.let { it > 0 && total < it && r.outcome != Outcome.BLACKJACK } == true
                if (bossBlocks) {
                    out += ScoreEvent(++evKey, r.outcome, 0, 0, 1.0, 0, 0, emptyList(), "${activeBoss!!.name}: a win under ${activeBoss!!.minWinTotal} scores nothing")
                } else {
                    val base = (if (r.outcome == Outcome.BLACKJACK) 75 else 50) * stake * (if (h.doubled) 2 else 1)
                    val fx = Fx()
                    perks.forEach { pk -> pk.effect?.invoke(pk, ctx, fx) }
                    val combo = comboFor(winStreak)
                    if (combo > 0) fx.notes += Trigger("Win streak", "+${combo.toInt()} mult (${winStreak} in a row)")
                    val chips = base + fx.chips
                    val mult = (1.0 + fx.mult + combo) * fx.xmult
                    val total2 = (chips * mult).roundToInt()
                    score += total2; if (total2 > bestHand) bestHand = total2
                    money += fx.money
                    out += ScoreEvent(++evKey, r.outcome, base, chips, mult, total2, fx.money, fx.notes.toList(), "")
                }
            } else if (lost) {
                val cost = stake + (activeBoss?.lossCost ?: 0)
                val pay = minOf(cost, money); money -= pay
                if (!netUsed && perks.any { it.saveFirstLoss } && !refunded) { netUsed = true; refunded = true; handsLeft++ }
                out += ScoreEvent(++evKey, r.outcome, 0, 0, 1.0, 0, -pay, if (refunded) listOf(Trigger("Safety Net", "The hand is not used up")) else emptyList(), "")
            } else out += ScoreEvent(++evKey, r.outcome, 0, 0, 1.0, 0, 0, emptyList(), "")
        }
        lastScore = out
        when {
            score >= target -> clearBlind()
            handsLeft <= 0 -> { phase = RunPhase.OVER; cause = "Fell ${target - score} short of $target on the $blindName of Ante $ante." }
        }
    }

    private fun clearBlind() {
        val lines = mutableListOf<Pair<String, Int>>()
        lines += "$blindName cleared" to reward
        if (handsLeft > 0) lines += "$handsLeft unused ${if (handsLeft == 1) "hand" else "hands"}" to handsLeft
        val interest = minOf(money / 5, interestCap)
        if (interest > 0) lines += "Interest on \$$money" to interest
        val tick = perks.sumOf { it.clearMoney }
        if (tick > 0) lines += "Golden Ticket" to tick
        cashout = Cashout(lines)
        money += cashout!!.total
        blindsCleared++
        phase = RunPhase.CLEARED
    }

    /** Take the cash and move on to the shop (or the win screen after the last boss). */
    fun collect(): Boolean {
        if (phase != RunPhase.CLEARED) return false
        if (blind == 2 && ante >= MAX_ANTE) { phase = RunPhase.WON; return true }
        phase = RunPhase.SHOP
        rerollCost = 2
        roll()
        return true
    }

    // Shop
    private fun roll() {
        val owned = perks.map { it.id }.toSet()
        val pool = Perks.ALL.filter { it.id !in owned }.toMutableList()
        val picks = mutableListOf<Offer>()
        repeat(3) {
            if (pool.isEmpty()) return@repeat
            val total = pool.sumOf { it.rarity.weight }
            var roll = rnd.nextInt(total)
            val pk = pool.first { roll -= it.rarity.weight; roll < 0 }
            pool.remove(pk); picks += Offer(pk, null)
        }
        picks += Offer(null, Tonic.entries[rnd.nextInt(Tonic.entries.size)])
        offers = picks
    }

    fun canBuy(i: Int): Boolean {
        val o = offers.getOrNull(i) ?: return false
        if (phase != RunPhase.SHOP || o.sold || money < o.cost) return false
        return o.perk == null || perks.size < SLOTS
    }

    fun buy(i: Int): Boolean {
        if (!canBuy(i)) return false
        val o = offers[i]
        money -= o.cost; o.sold = true
        o.perk?.let { perks += it }
        when (o.tonic) { Tonic.HAND -> bonusHands++; Tonic.COUNT -> countNotes = true; null -> {} }
        return true
    }

    fun reroll(): Boolean {
        if (phase != RunPhase.SHOP || money < rerollCost) return false
        money -= rerollCost; rerollCost++
        roll()
        return true
    }

    fun sell(i: Int): Boolean {
        if (phase != RunPhase.SHOP && phase != RunPhase.SELECT) return false
        val pk = perks.getOrNull(i) ?: return false
        money += pk.sell; perks.removeAt(i)
        return true
    }

    fun leaveShop(): Boolean {
        if (phase != RunPhase.SHOP) return false
        offers = emptyList()
        nextBlind()
        return true
    }

    // Saving: a run is saved between blinds, so a blind in progress restarts when you come back.
    fun save(): String = listOf(
        "1", deck.name, ante, blind, money, perks.joinToString(",") { it.id }, bonusHands, if (countNotes) 1 else 0,
        blindsCleared, bestHand, skips, if (phase == RunPhase.SHOP) 1 else 0,
    ).joinToString("|")

    /** True while it is safe to save: not in the middle of a blind. */
    val saveable: Boolean get() = phase == RunPhase.SELECT || phase == RunPhase.SHOP

    internal fun setState(a: Int, b: Int, m: Int, bh: Int, cn: Boolean, cleared: Int, best: Int, sk: Int, shop: Boolean) {
        ante = a.coerceIn(1, MAX_ANTE); blind = b.coerceIn(0, 2); money = m.coerceAtLeast(0); bonusHands = bh; countNotes = cn
        blindsCleared = cleared; bestHand = best; skips = sk
        phase = RunPhase.SELECT
        if (shop) { phase = RunPhase.SHOP; rerollCost = 2; roll() }
    }

    internal fun forceMoney(n: Int) { money = n }
    internal fun forceScore(n: Int) { score = n }

    fun lose(why: String) { phase = RunPhase.OVER; cause = why }
}

object RunSave {
    fun restore(text: String?, rnd: Random = Random.Default): Run? {
        if (text.isNullOrBlank()) return null
        return try {
            val p = text.split("|")
            if (p[0] != "1") return null
            val deck = StartDeck.valueOf(p[1])
            val r = Run(deck, rnd)
            r.perks.clear()
            p[5].split(",").filter { it.isNotEmpty() }.forEach { id -> Perks.get(id)?.let { r.perks += it } }
            r.setState(p[2].toInt(), p[3].toInt(), p[4].toInt(), p[6].toInt(), p[7] == "1", p[8].toInt(), p[9].toInt(), p[10].toInt(), p[11] == "1")
            r
        } catch (e: Exception) { null }
    }
}
