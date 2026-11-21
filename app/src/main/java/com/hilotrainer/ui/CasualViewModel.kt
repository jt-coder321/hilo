package com.hilotrainer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hilotrainer.engine.Achievement
import com.hilotrainer.engine.Achievements
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.Archetype
import com.hilotrainer.engine.Backoff
import com.hilotrainer.engine.BlackjackGame
import com.hilotrainer.engine.BotStyle
import com.hilotrainer.engine.CasualDealers
import com.hilotrainer.engine.DailyBonus
import com.hilotrainer.engine.DealerCalls
import com.hilotrainer.engine.EventKind
import com.hilotrainer.engine.GameState
import com.hilotrainer.engine.LeaveReason
import com.hilotrainer.engine.Outcome
import com.hilotrainer.engine.Phase
import com.hilotrainer.engine.RoundSummary
import com.hilotrainer.engine.Seat
import com.hilotrainer.engine.TableEvent
import com.hilotrainer.engine.TableLife
import com.hilotrainer.engine.TableTalk
import com.hilotrainer.engine.Talk
import com.hilotrainer.engine.TwentyOnePlusThree
import com.hilotrainer.engine.handTotal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** How a person at the table feels right now; drives their animation. */
enum class Mood { NEUTRAL, HAPPY, CHEER, SAD, ANGRY, NERVOUS, WAVE }

/** A speech bubble. [key] makes repeated lines animate again. */
data class Bubble(val text: String, val key: Long)

/** Who the dealer's hand reaches toward: seat 0 to 4, [YOU] plus hand index, or [DEALER]. */
data class Reach(val target: Int, val key: Long)

/** Someone getting up, kept on screen while they walk away. */
data class Departing(val seat: Int, val s: Seat, val key: Long)

/** Chips moving between a seat's bet spot and the dealer's rack after a hand. */
data class Payout(val key: Long, val seat: Int, val amount: Int, val toDealer: Boolean)

data class CasualBanner(val title: String, val amount: Int, val big: Boolean, val key: Long)

data class PitState(val seat: Int, val key: Long)

/**
 * Just Play: blackjack for fun. No count, no coaching, no grading. A persistent bankroll,
 * a table of named regulars who come and go, three rotating dealers and a pit boss.
 */
class CasualViewModel(val store: CasualStore, private val rnd: Random = Random.Default) : ViewModel() {
    var settings by mutableStateOf(store.loadSettings()); private set
    /** Tests turn this on to skip every delay. */
    var instant = false
    var fx: (Fx) -> Unit = {}

    private val life = TableLife(4, settings.tableMin, rnd, settings.comeAndGo)
    private var game = newGame()
    var gs: GameState by mutableStateOf(game.state()); private set

    /** What the screen shows in each seat. Follows the table's events, a step behind the logic. */
    var seatView by mutableStateOf<List<Seat?>>(List(4) { null }); private set
    val departing = mutableStateListOf<Departing>()
    /** Seat ids that just sat down, so they animate in. */
    val arriving = mutableStateMapOf<Long, Long>()
    /** Which seat played engine bot i this round, and who was sitting there. */
    var roundSeats by mutableStateOf<List<Pair<Int, Long>>>(emptyList()); private set

    var shown by mutableIntStateOf(0); private set
    var busy by mutableStateOf(false); private set
    var bankroll by mutableIntStateOf(store.bankroll); private set
    var bet by mutableIntStateOf(0); private set
    var ppBet by mutableIntStateOf(0); private set
    var tpBet by mutableIntStateOf(0); private set
    var tpResult by mutableStateOf<String?>(null); private set
    private var tpNet = 0
    var roundOver by mutableStateOf(false); private set
    var dealtOnce by mutableStateOf(false); private set

    val bubbles = mutableStateMapOf<Int, Bubble>()
    val moods = mutableStateMapOf<Long, Mood>()
    var reach by mutableStateOf<Reach?>(null); private set
    var dealerIdx by mutableIntStateOf(rnd.nextInt(CasualDealers.ALL.size)); private set
    val dealer get() = CasualDealers.ALL[dealerIdx]
    var dealerMood by mutableStateOf(Mood.NEUTRAL); private set
    private var shiftRounds = 0
    var pit by mutableStateOf<PitState?>(null); private set
    var shuffling by mutableStateOf(false); private set
    var banner by mutableStateOf<CasualBanner?>(null); private set
    /** The dealer is sweeping last round's cards into the discard tray. */
    var sweeping by mutableStateOf(false); private set
    val payouts = mutableStateListOf<Payout>()
    /** What a player just did at the table, like "Hit" or "Double", for a moment. */
    val actions = mutableStateMapOf<Int, String>()
    var hint by mutableStateOf<String?>(null); private set
    val toasts = mutableStateListOf<Achievement>()
    var stats by mutableStateOf(store.loadStats()); private set
    var earned by mutableStateOf(store.achievements); private set

    private var seq = 0L
    private fun k() = ++seq
    private var eventsJob: Job? = null

    init {
        life.setOpenSeats(settings.seats)
        life.fill(settings.startPlayers.coerceAtMost(settings.seats))
        seatView = life.seats.toList()
        bet = store.lastBet.takeIf { it in settings.tableMin..bankroll } ?: settings.tableMin.coerceAtMost(bankroll)
    }

    private fun newGame() = BlackjackGame(settings.rules, bankroll0(), botCount = 0, unit = settings.tableMin)
    private fun bankroll0() = try { store.bankroll } catch (e: Exception) { CasualStore.START }

    private fun say(where: Int, text: String) {
        if (!settings.tableTalk && where != DEALER && where != PIT) return
        // At most two players talk at once so bubbles stay readable.
        if (where >= 0) {
            val talking = bubbles.entries.filter { it.key >= 0 && it.key != where }.sortedBy { it.value.key }
            if (talking.size >= 2) talking.take(talking.size - 1).forEach { bubbles.remove(it.key) }
        }
        bubbles[where] = Bubble(text, k())
        val key = bubbles[where]!!.key
        if (!instant) viewModelScope.launch { delay(2600 + text.length * 35L); if (bubbles[where]?.key == key) bubbles.remove(where) }
    }

    private fun mood(id: Long, m: Mood, ms: Long = 2400) {
        moods[id] = m
        if (!instant) viewModelScope.launch { delay(ms); if (moods[id] == m) moods.remove(id) }
    }

    private fun dealerReact(m: Mood) {
        dealerMood = m
        if (!instant) viewModelScope.launch { delay(2000); if (dealerMood == m) dealerMood = Mood.NEUTRAL }
    }

    private val cardMs: Long get() = (dealer.cardMs * when (settings.speed) { 0 -> 1.45; 2 -> 0.6; else -> 1.0 }).toLong()

    // Betting
    val maxBet: Int get() = settings.tableMin * 100
    val canBet: Boolean get() = !busy && (gs.phase == Phase.BETTING || gs.phase == Phase.ROUND_OVER)

    fun addChip(v: Int) {
        if (!canBet) return
        if (roundOver) clearTableForBet()
        val room = minOf(maxBet, bankroll - ppBet - tpBet)
        if (bet + v > room) { if (bet < room) { bet = room; fx(Fx.CHIP) }; return }
        bet += v; fx(Fx.CHIP)
    }
    fun clearBet() { if (canBet) { bet = 0; fx(Fx.CHIP) } }
    fun doubleBet() { if (canBet) { bet = minOf(bet * 2, maxBet, bankroll - ppBet - tpBet).coerceAtLeast(0); fx(Fx.CHIP) } }
    fun repeatBet() { if (canBet) { bet = store.lastBet.coerceAtMost(minOf(maxBet, bankroll)); fx(Fx.CHIP) } }

    /** Side bets step through off, 1, 2 and 5 table minimums. */
    fun cyclePP() { if (canBet) { ppBet = nextSide(ppBet); fx(Fx.CHIP) } }
    fun cycleTP() { if (canBet) { tpBet = nextSide(tpBet); fx(Fx.CHIP) } }
    private fun nextSide(cur: Int): Int {
        val m = settings.tableMin
        val steps = listOf(0, m, m * 2, m * 5)
        val n = steps[(steps.indexOf(cur).coerceAtLeast(0) + 1) % steps.size]
        return if (n + bet > bankroll) 0 else n
    }

    private fun clearTableForBet() { roundOver = false; banner = null }

    // Money
    val broke: Boolean get() = canBet && bankroll < settings.tableMin

    fun rebuy() {
        if (!canBet) return
        bankroll += CasualStore.REBUY
        game.resetBankroll(bankroll)
        store.bankroll = bankroll
        stats = stats.copy(rebuys = stats.rebuys + 1).also { store.saveStats(it) }
        bet = settings.tableMin
        fx(Fx.CHIP)
        say(DEALER, "Changing ${money(CasualStore.REBUY)}. Good luck.")
    }

    /** Today's bonus amount and streak, or null if already claimed. */
    fun bonusAvailable(today: Long = java.time.LocalDate.now().toEpochDay()): Pair<Int, Int>? =
        DailyBonus.claim(store.bonusDay, store.bonusStreak, today)

    fun claimBonus(today: Long = java.time.LocalDate.now().toEpochDay()): Int {
        if (!canBet) return 0
        val (amt, streak) = bonusAvailable(today) ?: return 0
        store.bonusDay = today; store.bonusStreak = streak
        bankroll += amt; game.resetBankroll(bankroll); store.bankroll = bankroll
        fx(Fx.WIN)
        return amt
    }

    // Play
    fun deal() {
        if (!canBet || bet < settings.tableMin || bet > bankroll) return
        eventsJob?.cancel(); syncSeats()
        busy = true; hint = null
        store.lastBet = bet
        if (game.state().bankroll != bankroll) game.resetBankroll(bankroll)
        viewModelScope.launch {
            // The dealer sweeps up last round's cards first.
            if (roundOver && dealtOnce && !instant && gs.dealerCards.isNotEmpty()) { sweeping = true; fx(Fx.DEAL); delay(520) }
            clearTableForBet(); tpResult = null; tpNet = 0; payouts.clear()
            life.planBets(game.upcomingTrueCount())
            if (life.shuffleUpNow) {
                life.shuffleUpNow = false
                game.shoe.shuffle()
                say(DEALER, "Shuffle up.")
                shuffleAnim()
            }
            val playing = life.playing()
            roundSeats = playing.map { it to life.seats[it]!!.id }
            game.changeSeats(playing.size)
            game.botStyles = playing.map { styleFor(life.seats[it]!!) }
            game.botBets = playing.map { life.seats[it]!!.bet }
            seatView = life.seats.toList()
            // Big bets get a comment.
            playing.forEach { i ->
                val s = life.seats[i]!!
                if (s.bet >= s.who.baseUnits * settings.tableMin * 3 && rnd.nextInt(100) < s.who.talky + 20) TableTalk.say(s.who.archetype, Talk.BIG_BET, rnd)?.let { say(i, it) }
            }
            val pp = if (settings.sideBets) ppBet.coerceAtMost(bankroll - bet) else 0
            val tp = if (settings.sideBets && tpBet > 0 && bankroll - bet - pp >= tpBet) tpBet else 0
            if (tp > 0) game.tip(tp)
            val before = game.state().bankroll
            game.deal(bet, pp)
            dealtOnce = true
            bankroll = before - bet - game.state().perfectPairsBet.let { if (it > 0) it else 0 }
            gs = game.state()
            if (tp > 0) {
                val h = gs.hands[0]
                val r = TwentyOnePlusThree.evaluate(h.cards[0], h.cards[1], gs.dealerCards[0])
                tpNet = if (r == null) -tp else tp * r.second
                tpResult = r?.first ?: "No 21+3"
                if (r != null) game.resetBankroll(game.state().bankroll + tp * (r.second + 1))
            }
            if (gs.shuffledThisRound || game.reshuffledByMachine) shuffleAnim()
            shown = gs.roundStartSeq
            sweeping = false
            store.bankroll = game.state().bankroll
            reveal()
            after()
        }
    }

    fun act(a: Action) {
        if (busy || gs.phase != Phase.PLAYER) return
        val o = gs.options
        if ((a == Action.DOUBLE && !o.canDouble) || (a == Action.SPLIT && !o.canSplit) || (a == Action.SURRENDER && !o.canSurrender)) return
        busy = true; hint = null
        if (a == Action.DOUBLE || a == Action.SPLIT) fx(Fx.CHIP)
        game.act(a)
        gs = game.state()
        if (gs.phase != Phase.ROUND_OVER) bankroll = gs.bankroll
        viewModelScope.launch { reveal(); after() }
    }

    fun insurance(take: Boolean) {
        if (busy || gs.phase != Phase.INSURANCE) return
        busy = true
        game.insurance(take)
        gs = game.state()
        viewModelScope.launch { reveal(); after() }
    }

    fun showHint() {
        val a = gs.basicAdvice ?: return
        hint = "Basic strategy: ${a.action.label}"
    }

    private suspend fun shuffleAnim() {
        shuffling = true; fx(Fx.SHUFFLE)
        if (!instant) delay(if (dealer.shuffle == 2) 900 else 1500)
        shuffling = false
    }

    /** Reveal each new card in deal order, with the dealer reaching toward whoever gets it. */
    private suspend fun reveal() {
        val target = gs.nextSeq
        while (shown < target) {
            val s = shown
            val flip = gs.events.firstOrNull { it.seq == s }?.kind == EventKind.FLIP
            val owner = ownerOf(s)
            // Another player taking a hit: a short think, and the action shows over them.
            if (owner in 0 until 4 && s >= gs.roundStartSeq + 2 * (gs.bots.size + 1) + 2) {
                val b = roundSeats.indexOfFirst { it.first == owner }
                val hand = gs.bots.getOrNull(b)
                actions[owner] = if (hand != null && hand.doubled && hand.seqs.lastOrNull() == s) "Double" else "Hit"
                if (!instant) { delay(600); viewModelScope.launch { delay(1200); actions.remove(owner) } }
            }
            reach = Reach(owner, k())
            fx(if (flip) Fx.FLIP else Fx.DEAL)
            shown = s + 1
            if (!instant) {
                delay((if (flip) cardMs + 220 else cardMs) * 6 / 10)
                // The hand comes back to the shoe between cards.
                reach = null
                delay((if (flip) cardMs + 220 else cardMs) * 4 / 10)
            }
        }
        reach = null
        if (instant) actions.clear()
    }

    private fun ownerOf(seq: Int): Int {
        if (seq in gs.dealerSeqs || seq == gs.holeRevealSeq) return DEALER
        gs.hands.forEachIndexed { i, h -> if (seq in h.seqs) return YOU + i }
        gs.bots.forEachIndexed { i, h -> if (seq in h.seqs) return roundSeats.getOrNull(i)?.first ?: DEALER }
        return DEALER
    }

    private fun after() {
        when (gs.phase) {
            Phase.INSURANCE -> if (!settings.offerInsurance) { busy = false; insurance(false); return } else busy = false
            Phase.PLAYER -> { busy = false; bankroll = gs.bankroll }
            Phase.ROUND_OVER -> finishRound()
            Phase.BETTING -> busy = false
        }
    }

    private fun finishRound() {
        val g = gs
        bankroll = game.state().bankroll
        store.bankroll = bankroll
        val handNet = g.results.sumOf { it.net }
        val net = handNet + g.insuranceNet + (g.perfectPairsNet ?: 0) + tpNet
        val wagered = g.hands.sumOf { it.bet } + g.insuranceBet + g.perfectPairsBet + (if (tpResult != null) tpBet else 0)
        val wins = g.results.count { it.outcome == Outcome.WIN || it.outcome == Outcome.BLACKJACK }
        val losses = g.results.count { it.outcome == Outcome.LOSE || it.outcome == Outcome.BUST || it.outcome == Outcome.SURRENDER }
        val pushes = g.results.count { it.outcome == Outcome.PUSH }
        val bj = g.results.any { it.outcome == Outcome.BLACKJACK }
        val dTotal = handTotal(g.dealerCards).first
        val dealerBust = dTotal > 21
        val dealerBj = g.dealerCards.size == 2 && dTotal == 21
        val split = g.hands.size > 1
        val summary = RoundSummary(
            net = net, wagered = wagered, wins = wins, losses = losses, pushes = pushes, blackjack = bj,
            doubledWin = g.hands.zip(g.results).any { (h, r) -> h.doubled && r.net > 0 },
            split = split, splitBothWon = split && g.results.all { it.net > 0 },
            sideWin = (g.perfectPairsNet ?: 0) > 0 || tpNet > 0,
            fiveCardWin = g.hands.zip(g.results).any { (h, r) -> h.cards.size >= 5 && r.net > 0 },
            dealerBust = dealerBust, bankrollAfter = bankroll, maxBetSeen = g.hands.maxOfOrNull { it.bet } ?: 0,
        )
        stats = Achievements.apply(stats, summary).also { store.saveStats(it) }
        val newOnes = Achievements.check(earned, stats, summary)
        if (newOnes.isNotEmpty()) { earned = earned + newOnes.map { it.id }; store.achievements = earned; toasts.addAll(newOnes) }

        val first = g.results.firstOrNull()
        val title = when {
            g.results.size > 1 -> listOfNotNull(
                if (wins > 0) "$wins won" else null, if (losses > 0) "$losses lost" else null, if (pushes > 0) "$pushes push" else null,
            ).joinToString(", ")
            first == null -> ""
            first.outcome == Outcome.WIN && dealerBust -> "Dealer busts, you win"
            first.outcome == Outcome.LOSE && dealerBj -> "Dealer blackjack"
            else -> first.outcome.label
        }
        banner = CasualBanner(title, net, bj || net >= settings.tableMin * 10, k())
        // The dealer pays winners from the rack and collects losing bets into it.
        payouts.clear()
        val pk = k() * 10
        if (handNet != 0) payouts += Payout(pk, YOU, kotlin.math.abs(handNet), handNet < 0)
        roundSeats.forEachIndexed { i, (seat, _) -> g.botResults.getOrNull(i)?.let { r -> if (r.net != 0) payouts += Payout(pk + 1 + i, seat, kotlin.math.abs(r.net), r.net < 0) } }
        fx(when { bj -> Fx.BLACKJACK; net > 0 -> Fx.WIN; net < 0 -> Fx.LOSE; else -> Fx.CHIP })

        // The dealer reacts.
        val d = dealer
        when {
            dealerBust -> { say(DEALER, TableTalk.dealer(d.onBust, rnd)); dealerReact(Mood.SAD) }
            dealerBj -> { say(DEALER, TableTalk.dealer(d.onBlackjack, rnd)); dealerReact(Mood.NERVOUS) }
            bj -> { say(DEALER, TableTalk.dealer(d.onPlayerBlackjack, rnd)); dealerReact(Mood.HAPPY) }
            rnd.nextInt(100) < 40 -> say(DEALER, DealerCalls.result(g.dealerCards))
        }

        // The other players react, then the table changes.
        val nets = HashMap<Int, Int>(); val outs = HashMap<Int, Outcome>()
        roundSeats.forEachIndexed { i, (seat, _) -> g.botResults.getOrNull(i)?.let { nets[seat] = it.net; outs[seat] = it.outcome } }
        roundSeats.forEach { (seat, id) ->
            val o = outs[seat] ?: return@forEach
            mood(id, when (o) { Outcome.BLACKJACK -> Mood.CHEER; Outcome.WIN -> Mood.HAPPY; Outcome.BUST -> Mood.ANGRY; Outcome.LOSE -> Mood.SAD; else -> Mood.NEUTRAL })
        }
        if (bj || net >= settings.tableMin * 10) life.seats.forEach { s -> if (s != null && rnd.nextInt(3) == 0) mood(s.id, Mood.CHEER, 1600) }
        val events = life.afterRound(nets, outs, game.upcomingTrueCount(), dealerBust, bj)
        if (net >= settings.tableMin * 10 && !bj) life.seats.indices.firstOrNull { life.seats[it] != null && rnd.nextInt(100) < 40 }?.let { i ->
            TableTalk.say(life.seats[i]!!.who.archetype, Talk.YOU_BIG_WIN, rnd)?.let { say(i, it) }
        }
        roundOver = true
        if (bet > bankroll) bet = (bankroll / settings.tableMin * settings.tableMin).coerceAtLeast(0)
        if (ppBet + bet > bankroll) ppBet = 0
        if (tpBet + ppBet + bet > bankroll) tpBet = 0
        shiftRounds++
        busy = false
        eventsJob = viewModelScope.launch { play(events) }
    }

    /** Play out table events one by one so people visibly come, go and talk. */
    private suspend fun play(events: List<TableEvent>) {
        // Chip counts update for everyone still seated; anyone leaving stays until their turn.
        seatView = seatView.mapIndexed { i, old -> if (old != null && life.seats[i]?.id == old.id) life.seats[i] else old }
        if (!instant && events.isNotEmpty()) delay(900)
        for (e in events) {
            when (e) {
                is TableEvent.Say -> say(e.seat, e.line)
                is TableEvent.Arrive -> {
                    arriving[e.s.id] = k()
                    seatView = seatView.toMutableList().also { it[e.seat] = e.s }
                    if (!instant) delay(500)
                    say(e.seat, e.line)
                    if (rnd.nextInt(100) < dealer.talky + 30) {
                        if (!instant) delay(900)
                        say(DEALER, listOf("Welcome. Changing ${money(e.s.buyIn)}.", "Welcome to the table.", "Good luck, ${e.s.who.name}.")[rnd.nextInt(3)])
                    }
                }
                is TableEvent.Leave -> {
                    say(e.seat, e.line)
                    mood(e.s.id, if (e.reason == LeaveReason.BACKED_OFF || e.reason == LeaveReason.TILT) Mood.ANGRY else Mood.WAVE, 1400)
                    if (!instant) delay(1300)
                    bubbles.remove(e.seat)
                    seatView = seatView.toMutableList().also { it[e.seat] = null }
                    val dep = Departing(e.seat, e.s, k())
                    departing += dep
                    if (!instant) viewModelScope.launch { delay(1100); departing.remove(dep) } else departing.remove(dep)
                }
                is TableEvent.Rebuy -> {
                    say(e.seat, e.line); fx(Fx.CHIP)
                    seatView = seatView.toMutableList().also { it[e.seat] = e.s }
                }
                is TableEvent.PitWatch -> {
                    pit = PitState(e.seat, k())
                    if (!instant) delay(1200)
                    say(PIT, e.line)
                    seatView[e.seat]?.let { mood(it.id, Mood.NERVOUS, 3000) }
                }
                is TableEvent.PitAct -> {
                    pit = PitState(e.seat, k())
                    if (!instant) delay(900)
                    say(PIT, e.boss)
                    if (!instant) delay(1600)
                    say(e.seat, e.reply)
                    seatView[e.seat]?.let { mood(it.id, if (e.kind == Backoff.ASKED_TO_LEAVE) Mood.ANGRY else Mood.NERVOUS, 2500) }
                    if (e.kind == Backoff.SHUFFLE_UP) dealerReact(Mood.NERVOUS)
                }
                is TableEvent.PitComp -> {
                    pit = PitState(e.seat, k())
                    if (!instant) delay(900)
                    say(PIT, e.line)
                    seatView[e.seat]?.let { mood(it.id, Mood.HAPPY) }
                }
            }
            if (!instant) delay(650)
        }
        if (life.pitWatching < 0 && pit != null) {
            if (!instant) delay(1200)
            if (life.pitWatching < 0) { if (events.any { it is TableEvent.PitAct || it is TableEvent.PitComp }) say(PIT, TableTalk.pit("leaving", rnd)); pit = null }
        }
        seatView = life.seats.toList()
        if (shiftRounds >= CasualDealers.SHIFT) changeDealer()
    }

    private suspend fun changeDealer() {
        shiftRounds = 0
        say(DEALER, TableTalk.dealer(dealer.shiftOut, rnd))
        if (!instant) delay(1800)
        bubbles.remove(DEALER)
        dealerIdx = (dealerIdx + 1 + rnd.nextInt(CasualDealers.ALL.size - 1)) % CasualDealers.ALL.size
        if (!instant) delay(900)
        say(DEALER, TableTalk.dealer(dealer.greet, rnd))
    }

    private fun syncSeats() {
        seatView = life.seats.toList()
        departing.clear()
    }

    /** Small talk between hands. Runs while the table is on screen. */
    suspend fun chatterLoop() {
        if (instant) return
        while (true) {
            delay(7000L + rnd.nextLong(0, 9000))
            if (busy || !settings.tableTalk || eventsJob?.isActive == true) continue
            if (rnd.nextInt(100) < dealer.talky) { say(DEALER, TableTalk.dealer(dealer.chatter, rnd)); continue }
            for (e in life.idleChat()) { say(e.seat, e.line); delay(1600) }
        }
    }

    /** Play style for this round. A tilted loss-chaser starts doubling everything. */
    private fun styleFor(s: Seat): BotStyle = if (s.who.archetype == Archetype.TILTER && s.tilted) BotStyle.AGGRESSIVE else s.who.style

    // Settings
    fun update(n: CasualSettings) {
        val old = settings
        settings = n
        store.saveSettings(n)
        val between = !busy && gs.phase != Phase.PLAYER && gs.phase != Phase.INSURANCE
        if (between && (n.rules != old.rules || n.tableMin != old.tableMin)) {
            game = newGame().also { it.resetBankroll(bankroll) }
            gs = game.state(); shown = 0; roundSeats = emptyList(); roundOver = false; banner = null
            life.unit = n.tableMin; life.clearPitBoss(); pit = null
            if (n.tableMin != old.tableMin) { bet = n.tableMin.coerceAtMost(bankroll); ppBet = 0; tpBet = 0 }
            say(DEALER, "New shoe. ${if (n.dealerHitsSoft17) "Dealer hits soft 17." else "Dealer stands on all 17s."}")
        }
        life.comeAndGo = n.comeAndGo
        if (n.seats != old.seats && between) {
            val ev = life.setOpenSeats(n.seats)
            if (n.seats > old.seats) repeat(n.seats - old.seats) { life.arrive(0.0, force = true)?.let { e -> arriving[e.s.id] = k() } }
            eventsJob?.cancel(); eventsJob = viewModelScope.launch { play(ev) }
        }
    }

    /** Test helper: seat a named player. */
    internal fun seatForTest(seat: Int, name: String, heat: Int = 0) {
        val who = com.hilotrainer.engine.Cast.byName(name)
        life.seats[seat] = Seat(1000L + seat, who, 50 * settings.tableMin, 50 * settings.tableMin, who.baseUnits * settings.tableMin, plan = 99, heat = heat)
        seatView = life.seats.toList()
    }
    internal fun sayForTest(where: Int, text: String) = say(where, text)
    internal fun pitForTest(seat: Int) { pit = PitState(seat, k()) }
    internal fun moodForTest(id: Long, m: Mood) { moods[id] = m }


    companion object {
        const val DEALER = -1
        const val PIT = -2
        const val YOU = 10
    }
}
