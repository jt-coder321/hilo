package com.hilotrainer.engine

import kotlin.math.floor
import kotlin.random.Random

enum class Phase { BETTING, INSURANCE, PLAYER, ROUND_OVER }

enum class Outcome(val label: String) {
    BLACKJACK("Blackjack!"), WIN("Win"), PUSH("Push"), LOSE("Lose"),
    BUST("Bust"), SURRENDER("Surrender"),
}

data class HandResult(val outcome: Outcome, val net: Int)

enum class DecisionKind { PLAY, INSURANCE, BET }

data class Decision(
    val kind: DecisionKind,
    val situation: String,
    val chosen: String,
    val advised: String,
    val reason: String,
    val correct: Boolean,
    val isDeviation: Boolean = false,
)

enum class EventKind { DEAL, FLIP }

/** One card appearing on the table. The UI replays these in [seq] order. */
data class DealEvent(val seq: Int, val kind: EventKind, val tag: Int, val counted: Boolean)

data class GameState(
    val phase: Phase,
    val bankroll: Int,
    val hands: List<Hand>,
    val activeHand: Int,
    val bots: List<Hand>,
    val botResults: List<HandResult>,
    val dealerCards: List<Card>,
    val dealerSeqs: List<Int>,
    val holeRevealed: Boolean,
    val holeRevealSeq: Int,
    val insuranceBet: Int,
    val results: List<HandResult>,
    val insuranceNet: Int,
    val lastDecision: Decision?,
    val betDecision: Decision?,
    val runningCount: Int,
    val trueCountExact: Double,
    val decksRemaining: Double,
    val cardsDealt: Int,
    val totalCards: Int,
    val shuffledThisRound: Boolean,
    val options: Options,
    val advice: Advice?,
    val basicAdvice: Advice?,
    val insuranceAdvised: Boolean,
    /** Every card event this round, in deal order. */
    val events: List<DealEvent>,
    /** The seq the next event will get; everything below it has happened. */
    val nextSeq: Int,
    val roundStartSeq: Int,
    /** Cards counted since the shuffle, oldest first. */
    val seenSinceShuffle: List<Card>,
    val satOut: Boolean,
    /** How each computer player plays. */
    val botStyles: List<BotStyle> = emptyList(),
    /** Plays the other players got wrong this round, in order, like "Seat 2 stood on 12 vs 10". */
    val botMistakes: List<String> = emptyList(),
    val perfectPairsBet: Int = 0,
    /** Net result of the Perfect Pairs bet this round, or null if there was none. */
    val perfectPairsNet: Int? = null,
    val perfectPairsResult: String? = null,
)

class BlackjackGame(
    val rules: Rules = Rules(),
    startingBankroll: Int = 1000,
    random: Random = Random.Default,
    /** Computer players seated before you. They play basic strategy without splitting. */
    val botCount: Int = 0,
    /** Grade plays against Illustrious 18 and Fab 4 index plays. */
    val useDeviations: Boolean = false,
    /** Grade bet size against the true count. */
    val gradeBets: Boolean = false,
    /** Table minimum, also one betting unit. */
    val unit: Int = 10,
    /** Units per true count step, see [TrueCount.RAMP_1_8]. */
    val ramp: List<Int> = TrueCount.RAMP_1_8,
    /** When set, sitting out is the right call at or below this true count. */
    val wongOutAt: Int? = null,
    /** Counting system the player is using. Index plays and insurance use Hi-Lo values only. */
    val system: CountSystem = CountSystem.HI_LO,
    /** Use the extended index plays as well as the Illustrious 18 and Fab 4. */
    val fullIndices: Boolean = false,
    val shuffleStyle: ShuffleStyle = ShuffleStyle.HAND,
    /** Play style of each computer player; missing seats play basic strategy. */
    var botStyles: List<BotStyle> = emptyList(),
) {
    /** Each computer player's bet this round; missing seats bet the table minimum. */
    var botBets: List<Int> = emptyList()

    val shoe = Shoe(rules.decks, rules.penetration, random, system)

    /** Index plays are Hi-Lo numbers, so they only apply when counting Hi-Lo. */
    val deviationsActive: Boolean get() = useDeviations && system == CountSystem.HI_LO

    var bankroll = startingBankroll
        private set
    private var phase = Phase.BETTING
    private val hands = mutableListOf<Hand>()
    private var active = 0
    private val bots = MutableList(botCount) { Hand() }
    /** The dealer let the hole card show, so it was counted early. */
    private var holeFlashed = false
    /** A card burned face down after the shuffle, never seen. */
    var burnedThisRound = false
        private set
    private var botResults = listOf<HandResult>()
    private val dealer = mutableListOf<Card>()
    private val dealerSeqs = mutableListOf<Int>()
    private var holeRevealed = false
    private var holeRevealSeq = -1
    private var insuranceBet = 0
    private var insuranceNet = 0
    private var results = listOf<HandResult>()
    private var lastDecision: Decision? = null
    private var betDecision: Decision? = null
    private var shuffledThisRound = false
    private var seq = 0
    private var roundStartSeq = 0
    private val events = mutableListOf<DealEvent>()

    /** Every graded decision the player made, for stats. */
    val decisions = mutableListOf<Decision>()

    private fun tcOrNull() = if (deviationsActive) shoe.trueCountExact else null
    private fun styleOf(i: Int) = botStyles.getOrElse(i) { BotStyle.BASIC }
    private var botMistakes = listOf<String>()
    private var ppBet = 0
    private var ppNet: Int? = null
    private var ppResult: String? = null

    /** True when the player sat this round out and only watched. */
    val satOut: Boolean get() = hands.isEmpty() && dealer.isNotEmpty()

    fun state(): GameState {
        val opts = options()
        val playing = phase == Phase.PLAYER && hands.isNotEmpty()
        return GameState(
            phase = phase,
            bankroll = bankroll,
            hands = hands.toList(),
            activeHand = active,
            bots = bots.toList(),
            botResults = botResults,
            dealerCards = dealer.toList(),
            dealerSeqs = dealerSeqs.toList(),
            holeRevealed = holeRevealed,
            holeRevealSeq = holeRevealSeq,
            insuranceBet = insuranceBet,
            results = results,
            insuranceNet = insuranceNet,
            lastDecision = lastDecision,
            betDecision = betDecision,
            runningCount = shoe.runningCount,
            trueCountExact = shoe.trueCountExact,
            decksRemaining = shoe.decksRemaining,
            cardsDealt = shoe.cardsDealt,
            totalCards = shoe.totalCards,
            shuffledThisRound = shuffledThisRound,
            options = opts,
            advice = if (playing) BasicStrategy.advise(hands[active], dealer[0], rules, opts, tcOrNull(), fullIndices) else null,
            basicAdvice = if (playing) BasicStrategy.basic(hands[active], dealer[0], rules, opts) else null,
            insuranceAdvised = insuranceAdvised(),
            events = events.toList(),
            nextSeq = seq,
            roundStartSeq = roundStartSeq,
            seenSinceShuffle = shoe.seenSinceShuffle,
            satOut = satOut,
            botStyles = bots.indices.map { styleOf(it) },
            botMistakes = botMistakes,
            perfectPairsBet = ppBet,
            perfectPairsNet = ppNet,
            perfectPairsResult = ppResult,
        )
    }

    private fun insuranceAdvised() = system == CountSystem.HI_LO && BasicStrategy.shouldInsure(shoe.trueCountExact)

    /**
     * Join a table mid-shoe: a fresh shuffle with [cards] already dealt before you
     * arrived. Those cards are never counted.
     */
    fun joinMidShoe(cards: Int) {
        require(phase == Phase.BETTING || phase == Phase.ROUND_OVER)
        shoe.shuffle()
        shoe.burnUnseen(cards.coerceIn(0, (shoe.totalCards * rules.penetration).toInt() - 52))
    }

    fun resetBankroll(amount: Int = 1000) {
        bankroll = amount
    }

    /** True count the next round will be bet at, after any reshuffle. */
    fun upcomingTrueCount(): Double = if (shoe.needsShuffle) 0.0 else shoe.trueCountExact

    /** Bet level (Hi-Lo true count scale) the next round will be bet at, after any reshuffle. */
    fun upcomingBetLevel(): Double = if (shoe.needsShuffle) system.betLevelExact(system.initialCount(rules.decks), rules.decks.toDouble()) else shoe.betLevelExact

    /** How the count the next bet is keyed on reads to the player. */
    fun upcomingCountText(): String =
        if (shoe.needsShuffle) system.describeCount(system.initialCount(rules.decks), rules.decks.toDouble())
        else system.describeCount(shoe.runningCount, shoe.decksRemaining)

    private fun draw(seen: Boolean = true): Pair<Card, Int> {
        val c = shoe.draw(seen)
        val s = seq++
        events += DealEvent(s, EventKind.DEAL, system.tag(c), seen)
        return c to s
    }

    /** True when a continuous shuffler put last round's cards back before this round. */
    var reshuffledByMachine = false
        private set

    /**
     * Deal a round. A [bet] of 0 sits the round out: you watch and count without a hand.
     * [perfectPairs] is an optional Perfect Pairs side bet on your first two cards.
     */
    fun deal(bet: Int, perfectPairs: Int = 0) {
        require(phase == Phase.BETTING || phase == Phase.ROUND_OVER)
        require(bet == 0 || bet in 1..bankroll)
        val sitOut = bet == 0
        shuffledThisRound = false
        reshuffledByMachine = false
        botMistakes = emptyList()
        ppNet = null
        ppResult = null
        ppBet = if (sitOut || perfectPairs <= 0 || bankroll - bet < perfectPairs) 0 else perfectPairs
        if (shuffleStyle == ShuffleStyle.CONTINUOUS && shoe.cardsDealt > 0) {
            // The machine takes back every card from the last round, so the shoe is whole again.
            shoe.shuffle()
            reshuffledByMachine = true
        } else if (shoe.needsShuffle) {
            shoe.shuffle()
            shuffledThisRound = true
        }
        // After a shuffle the dealer burns the top card into the tray, face down.
        burnedThisRound = shuffledThisRound
        if (shuffledThisRound) shoe.burnUnseen(1)
        holeFlashed = false
        events.clear()
        roundStartSeq = seq
        hands.clear()
        dealer.clear()
        dealerSeqs.clear()
        for (i in bots.indices) bots[i] = Hand(bet = botBets.getOrElse(i) { unit })
        botResults = emptyList()
        results = emptyList()
        lastDecision = null
        insuranceBet = 0
        insuranceNet = 0
        holeRevealed = false
        holeRevealSeq = -1
        active = 0
        betDecision = if (gradeBets) gradeBet(bet) else null
        betDecision?.let { decisions += it }
        bankroll -= bet

        var h = Hand(bet = bet)
        // First card to each seat (bots first, you last), dealer up card,
        // second card to each seat, then the dealer's hole card face down.
        for (i in bots.indices) { val (c, s) = draw(); bots[i] = bots[i].add(c, s) }
        if (!sitOut) draw().let { (c, s) -> h = h.add(c, s) }
        draw().let { (c, s) -> dealer += c; dealerSeqs += s }
        for (i in bots.indices) { val (c, s) = draw(); bots[i] = bots[i].add(c, s) }
        if (!sitOut) draw().let { (c, s) -> h = h.add(c, s) }
        draw(seen = false).let { (c, s) -> dealer += c; dealerSeqs += s }
        if (!sitOut) hands += h
        if (ppBet > 0) {
            bankroll -= ppBet
            val (a, b) = h.cards[0] to h.cards[1]
            val (mult, name) = when {
                a.rank != b.rank -> 0 to null
                a.suit == b.suit -> SideBets.PP_PERFECT to "Perfect pair"
                a.suit.isRed == b.suit.isRed -> SideBets.PP_COLORED to "Colored pair"
                else -> SideBets.PP_MIXED to "Mixed pair"
            }
            ppNet = if (name == null) -ppBet else ppBet * mult
            ppResult = name ?: "No pair"
            if (name != null) bankroll += ppBet + ppBet * mult
        }

        if (!sitOut && dealer[0].rank.isAce && bankroll >= bet / 2 && bet / 2 > 0) {
            phase = Phase.INSURANCE
        } else {
            peekAndContinue()
        }
    }

    private fun gradeBet(bet: Int): Decision? {
        val tc = shoe.betLevelExact
        val tcFloor = floor(tc).toInt()
        val countText = system.describeCount(shoe.runningCount, shoe.decksRemaining)
        val scale = if (system == CountSystem.HI_LO) "true count" else "bet level"
        val suggested = TrueCount.suggestedUnits(tcFloor, ramp)
        val units = bet / unit
        val shouldSitOut = wongOutAt != null && tcFloor <= wongOutAt
        val plural = { n: Int -> "$n unit${if (n == 1) "" else "s"}" }
        if (bet == 0) {
            if (wongOutAt == null) return null
            return Decision(
                kind = DecisionKind.BET,
                situation = "Sat out at $countText",
                chosen = "Sit out",
                advised = if (shouldSitOut) "Sit out" else plural(suggested),
                reason = "Sit out at $scale $wongOutAt or lower, otherwise bet the ramp",
                correct = shouldSitOut,
            )
        }
        if (shouldSitOut) {
            return Decision(
                kind = DecisionKind.BET,
                situation = "Bet at $countText",
                chosen = plural(units),
                advised = "Sit out",
                reason = "Your sit-out point is $scale $wongOutAt. Below it the player has a bigger disadvantage",
                correct = false,
            )
        }
        // Accept one ramp step either side of the suggestion.
        val i = TrueCount.rampIndex(tcFloor)
        val lo = if (i == 0) ramp[0] else ramp[i - 1]
        val hi = if (i == 0) ramp[1] else ramp[minOf(ramp.size - 1, i + 1)]
        val ok = units in lo..hi || (i == ramp.size - 1 && units >= ramp[i - 1])
        return Decision(
            kind = DecisionKind.BET,
            situation = "Bet at $countText",
            chosen = plural(units),
            advised = plural(suggested),
            reason = "Your ramp" + (if (system == CountSystem.HI_LO) "" else " (${system.label} bet level ${fmt(tc)})") + ": " + TrueCount.describeRamp(ramp),
            correct = ok,
        )
    }

    fun insurance(take: Boolean) {
        require(phase == Phase.INSURANCE)
        val advised = insuranceAdvised()
        val hiLo = system == CountSystem.HI_LO
        val d = Decision(
            kind = DecisionKind.INSURANCE,
            situation = if (hiLo) "Insurance at true count ${fmt(shoe.trueCountExact)}" else "Insurance (${system.label})",
            chosen = if (take) "Insure" else "No insurance",
            advised = if (advised) "Insure" else "No insurance",
            reason = if (hiLo) "Hi-Lo index: insure only at true count +3 or higher" else "The trainer only has Hi-Lo insurance numbers, so with ${system.label} it follows basic strategy: never insure",
            correct = take == advised,
            isDeviation = advised,
        )
        lastDecision = d
        decisions += d
        if (take) {
            insuranceBet = hands[0].bet / 2
            bankroll -= insuranceBet
        }
        peekAndContinue()
    }

    private fun dealerHasBlackjack() = handTotal(dealer).first == 21 && dealer.size == 2

    private fun peekAndContinue() {
        val up = dealer[0].upValue
        val peeks = up == 10 || up == 11
        if (peeks && dealerHasBlackjack()) {
            revealHole()
            if (insuranceBet > 0) {
                insuranceNet = insuranceBet * 2
                bankroll += insuranceBet * 3
            }
            settle()
            return
        }
        if (insuranceBet > 0) insuranceNet = -insuranceBet
        playBots()
        if (hands.isEmpty() || hands[0].isBlackjack) {
            playDealer()
            return
        }
        phase = Phase.PLAYER
    }

    private fun playBots() {
        val noSplit = Options(canDouble = true, canSplit = false, canSurrender = false)
        for (i in bots.indices) {
            var b = bots[i]
            val style = styleOf(i)
            while (!b.isDone) {
                val canDouble = b.cards.size == 2
                val book = BasicStrategy.basic(b, dealer[0], rules, noSplit.copy(canDouble = canDouble))
                val a = style.override(b, dealer[0], canDouble)?.let { Advice(it, "") } ?: book
                if (a.action != book.action) {
                    val up = if (dealer[0].rank.isAce) "A" else dealer[0].upValue.toString()
                    val verb = when (a.action) { Action.STAND -> "stood on"; Action.HIT -> "hit"; Action.DOUBLE -> "doubled"; else -> a.action.label.lowercase() }
                    botMistakes = botMistakes + "Seat ${i + 1} ${verb} ${if (b.isSoft) "soft " else ""}${b.total} vs $up"
                }
                b = when (a.action) {
                    Action.DOUBLE -> draw().let { (c, s) -> b.copy(bet = b.bet * 2, doubled = true).add(c, s) }
                    Action.STAND -> b.copy(stood = true)
                    else -> draw().let { (c, s) -> b.add(c, s) }
                }
            }
            bots[i] = b
        }
    }

    fun options(): Options {
        if (phase != Phase.PLAYER || hands.isEmpty()) return Options(false, false, false)
        val h = hands[active]
        val two = h.cards.size == 2
        val canDouble = two && !h.isSplitAces && bankroll >= h.bet &&
            (!h.isSplitHand || rules.doubleAfterSplit)
        val canSplit = two && h.isPair && hands.size < rules.maxHands &&
            bankroll >= h.bet && !h.isSplitAces
        val canSurrender = rules.surrenderAllowed && two && hands.size == 1 && !h.isSplitHand
        return Options(canDouble, canSplit, canSurrender)
    }

    fun act(action: Action) {
        require(phase == Phase.PLAYER)
        val opts = options()
        when (action) {
            Action.DOUBLE -> require(opts.canDouble)
            Action.SPLIT -> require(opts.canSplit)
            Action.SURRENDER -> require(opts.canSurrender)
            else -> {}
        }
        val h = hands[active]
        val advice = BasicStrategy.advise(h, dealer[0], rules, opts, tcOrNull(), fullIndices)
        val tcNote = if (deviationsActive) " at true count ${fmt(shoe.trueCountExact)}" else ""
        val d = Decision(
            kind = DecisionKind.PLAY,
            situation = describe(h) + " vs " + dealer[0].rank.label + tcNote,
            chosen = action.label,
            advised = advice.action.label,
            reason = advice.reason,
            correct = action == advice.action,
            isDeviation = advice.isDeviation,
        )
        lastDecision = d
        decisions += d

        when (action) {
            Action.HIT -> hands[active] = draw().let { (c, s) -> h.add(c, s) }
            Action.STAND -> hands[active] = h.copy(stood = true)
            Action.DOUBLE -> {
                bankroll -= h.bet
                hands[active] = draw().let { (c, s) -> h.copy(bet = h.bet * 2, doubled = true).add(c, s) }
            }
            Action.SURRENDER -> hands[active] = h.copy(surrendered = true)
            Action.SPLIT -> {
                bankroll -= h.bet
                val aces = h.cards[0].rank.isAce
                val first = Hand(
                    cards = listOf(h.cards[0]), seqs = listOf(h.seqs[0]), bet = h.bet,
                    isSplitHand = true, isSplitAces = aces,
                ).let { f -> draw().let { (c, s) -> f.add(c, s) } }
                val second = Hand(
                    cards = listOf(h.cards[1]), seqs = listOf(h.seqs[1]), bet = h.bet,
                    isSplitHand = true, isSplitAces = aces,
                )
                hands[active] = first
                hands.add(active + 1, second)
            }
        }
        advance()
    }

    private fun advance() {
        while (active < hands.size) {
            var h = hands[active]
            if (h.cards.size == 1) {
                h = draw().let { (c, s) -> h.add(c, s) }
                hands[active] = h
            }
            if (!h.isDone) return
            active++
        }
        active = (hands.size - 1).coerceAtLeast(0)
        playDealer()
    }

    private fun revealHole() {
        if (!holeRevealed) {
            holeRevealed = true
            if (!holeFlashed) shoe.see(dealer[1])
            holeRevealSeq = seq++
            events += DealEvent(holeRevealSeq, EventKind.FLIP, system.tag(dealer[1]), !holeFlashed)
        }
    }

    /**
     * A sloppy dealer lets the hole card show while you're still playing. You saw it,
     * so it's counted now and not again when it's turned over.
     */
    fun flashHole(): Card? {
        if (phase != Phase.PLAYER || holeRevealed || holeFlashed || dealer.size < 2) return null
        holeFlashed = true
        shoe.see(dealer[1])
        return dealer[1]
    }

    val holeWasFlashed: Boolean get() = holeFlashed

    /** Tip the dealer between rounds. */
    fun tip(amount: Int): Boolean {
        if (phase == Phase.PLAYER || phase == Phase.INSURANCE || amount <= 0 || amount > bankroll) return false
        bankroll -= amount
        return true
    }

    /** Players sit down or leave between rounds. */
    fun changeSeats(n: Int) {
        require(phase == Phase.BETTING || phase == Phase.ROUND_OVER)
        while (bots.size < n) bots += Hand()
        while (bots.size > n && bots.isNotEmpty()) bots.removeAt(bots.size - 1)
    }

    val seats: Int get() = bots.size

    private fun playDealer() {
        revealHole()
        val live = (hands + bots).any { !it.isBust && !it.surrendered && !it.isBlackjack }
        if (live) {
            while (true) {
                val (t, soft) = handTotal(dealer)
                val hit = t < 17 || (t == 17 && soft && rules.dealerHitsSoft17)
                if (!hit) break
                draw().let { (c, s) -> dealer += c; dealerSeqs += s }
            }
        }
        settle()
    }

    private fun outcomeOf(h: Hand): HandResult {
        val dealerTotal = handTotal(dealer).first
        val dealerBj = dealerHasBlackjack()
        val outcome = when {
            h.surrendered -> Outcome.SURRENDER
            h.isBust -> Outcome.BUST
            h.isBlackjack && !dealerBj -> Outcome.BLACKJACK
            dealerBj && h.isBlackjack -> Outcome.PUSH
            dealerBj -> Outcome.LOSE
            dealerTotal > 21 -> Outcome.WIN
            h.total > dealerTotal -> Outcome.WIN
            h.total == dealerTotal -> Outcome.PUSH
            else -> Outcome.LOSE
        }
        val returned = when (outcome) {
            Outcome.BLACKJACK -> h.bet + if (rules.sixToFive) h.bet * 6 / 5 else h.bet * 3 / 2
            Outcome.WIN -> h.bet * 2
            Outcome.PUSH -> h.bet
            Outcome.SURRENDER -> h.bet / 2
            Outcome.LOSE, Outcome.BUST -> 0
        }
        return HandResult(outcome, returned - h.bet)
    }

    private fun settle() {
        results = hands.map { h -> outcomeOf(h).also { bankroll += it.net + h.bet } }
        botResults = bots.map { outcomeOf(it) }
        phase = Phase.ROUND_OVER
    }

    private fun describe(h: Hand): String = when {
        h.isPair -> "Pair of ${if (h.cards[0].rank.value == 10) "10" else h.cards[0].rank.label}s"
        h.isSoft -> "Soft ${h.total}"
        else -> "Hard ${h.total}"
    }

    companion object {
        fun fmt(tc: Double): String {
            val s = "%.1f".format(tc)
            return if (tc > 0.04) "+$s" else s
        }
    }
}
