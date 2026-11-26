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
import com.hilotrainer.engine.PlayerHeat
import com.hilotrainer.engine.PitMove
import com.hilotrainer.engine.TableStreak
import com.hilotrainer.engine.Achievements
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.Archetype
import com.hilotrainer.engine.Backoff
import com.hilotrainer.engine.BlackjackGame
import com.hilotrainer.engine.BotStyle
import com.hilotrainer.engine.CasualDealers
import com.hilotrainer.engine.Comps
import com.hilotrainer.engine.Dare
import com.hilotrainer.engine.Dares
import com.hilotrainer.engine.FunTalk
import com.hilotrainer.engine.LuckyCards
import com.hilotrainer.engine.Tier
import com.hilotrainer.engine.Tiers
import com.hilotrainer.engine.Wheel
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
import com.hilotrainer.engine.MoreTalk
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.PowerUp
import com.hilotrainer.engine.Rival
import com.hilotrainer.engine.Story
import com.hilotrainer.engine.NightRun
import com.hilotrainer.engine.NightState
import com.hilotrainer.engine.LuckyLadies
import com.hilotrainer.engine.NextCard
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

/** One side bet's result after a hand, for the panel. [net] is the win or loss on that bet. */
data class SideLine(val name: String, val result: String, val net: Int)

data class CasualBanner(val title: String, val amount: Int, val big: Boolean, val key: Long)

data class PitState(val seat: Int, val key: Long)

/** One line in the table chat. Everyone's talk lands here in order, so nothing is hidden behind anything else. */
/** A perk or surprise shown as a toast: a comp, an invitation, a rush. */
data class Perk(val id: Long, val title: String, val desc: String)

data class ChatLine(val key: Long, val who: Int, val name: String, val text: String, val minor: Boolean = false)

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
    /** Hi-Lo running count of the cards you have seen so far this shoe. Follows the cards as they are shown. */
    var countRun by mutableIntStateOf(game.shoe.runningCount); private set
    /** True count, running count over the decks left in the shoe (to the nearest quarter deck, at least half a deck). */
    val countTrue: Double get() = countRun / decksLeft
    val decksLeft: Double get() {
        val pending = (gs.nextSeq - shown).coerceAtLeast(0)
        val dealt = (gs.cardsDealt - pending).coerceAtLeast(0)
        return (Math.round((gs.totalCards - dealt) / 52.0 * 4) / 4.0).coerceAtLeast(0.5)
    }
    private fun syncCount() { countRun = game.shoe.runningCount - gs.events.filter { it.counted }.sumOf { it.tag } }

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
    /** Lucky Ladies and Next Card, the casual mini game side bets. */
    var llBet by mutableIntStateOf(0); private set
    var ncBet by mutableIntStateOf(0); private set
    var ncCall by mutableIntStateOf(0); private set
    var llResult by mutableStateOf<String?>(null); private set
    var ncResult by mutableStateOf<String?>(null); private set
    private var llNet = 0
    private var ncNet = 0
    var miniOpen by mutableStateOf(false)

    /** Power-ups you hold, in the order of [PowerUp]. Casual only. */
    val power = mutableStateListOf<Int>().also { l ->
        if (!store.powerSeeded) { store.powerSeeded = true; repeat(3) { store.setPowerUp(it, 1) } }
        l.addAll(store.powerUps())
    }
    var armedRefund by mutableStateOf(false); private set

    /** The rival who keeps score against you, and the story night you are playing. Casual only. */
    var rivalName by mutableStateOf(store.rivalName); private set
    var rivalWins by mutableIntStateOf(store.rivalWins); private set
    var rivalLosses by mutableIntStateOf(store.rivalLosses); private set
    var storyDone by mutableIntStateOf(store.storyDone); private set
    var night by mutableStateOf(NightRun.decode(store.nightRun)); private set
    val nightBadge: String? get() = night?.let { r -> Story.chapters.getOrNull(r.chapter)?.let { Story.badge(it, r, bankroll, settings.tableMin, r.chapter) } }

    fun startNight(i: Int) {
        if (!canBet || night != null || i !in 0..storyDone || i !in Story.chapters.indices) return
        val c = Story.chapters[i]
        night = NightRun(i, bankroll); store.nightRun = night!!.encode()
        perk("Night ${i + 1}: ${c.title}", Story.describe(c, settings.tableMin))
        say(DEALER, c.intro)
        fx(Fx.CHIP)
    }

    fun quitNight() { night = null; store.nightRun = null }
    var armedCover by mutableStateOf(false); private set
    /** The hole card, shown after a Hole peek and only for this round. */
    var peeked by mutableStateOf<Card?>(null); private set
    var roundOver by mutableStateOf(false); private set
    var dealtOnce by mutableStateOf(false); private set

    val bubbles = mutableStateMapOf<Int, Bubble>()
    /** The last few things said at the table, oldest first. */
    val chat = mutableStateListOf<ChatLine>()
    val moods = mutableStateMapOf<Long, Mood>()
    var reach by mutableStateOf<Reach?>(null); private set
    var dealerIdx by mutableIntStateOf(rnd.nextInt(CasualDealers.ALL.size)); private set
    val dealer get() = CasualDealers.ALL[dealerIdx]
    var dealerMood by mutableStateOf(Mood.NEUTRAL); private set
    private var shiftRounds = 0
    var pit by mutableStateOf<PitState?>(null); private set
    /** The roguelike run screen is open over the table. */
    var runOpen by mutableStateOf(false)

    // Table streak (cosmetic) and the heat you draw from the pit boss (casual only).
    private val streak = TableStreak()
    var streakLevel by mutableIntStateOf(0); private set
    private val heat = PlayerHeat()
    var heatLevel by mutableIntStateOf(0); private set
    var cooloff by mutableIntStateOf(0); private set
    var flatLeft by mutableIntStateOf(0); private set
    private var pendingMove = PitMove.NONE
    private fun syncHeat() { heatLevel = heat.level; cooloff = heat.cooloff; flatLeft = heat.flatLeft; streakLevel = streak.level }
    private fun resetHeat() { streak.clear(); heat.reset(); pendingMove = PitMove.NONE; syncHeat() }

    /** Tip the dealer: small cost, cools the heat. Acting the part. */
    val tipCost: Int get() = settings.tableMin * 2
    val canTip: Boolean get() = canBet && heat.level >= 3 && bankroll >= tipCost + settings.tableMin
    fun tipDealer() {
        if (!canTip) return
        game.tip(tipCost); bankroll = game.state().bankroll; store.bankroll = bankroll
        heat.tip(); syncHeat(); fx(Fx.CHIP)
        say(DEALER, "Thank you, sir. Much appreciated.")
        if (bet > bankroll) bet = (bankroll / settings.tableMin * settings.tableMin).coerceAtLeast(0)
    }
    var shuffling by mutableStateOf(false); private set
    /** True while the dealer looks at the hole card to check for blackjack. */
    var peeking by mutableStateOf(false); private set
    /** Shoe procedure shown at the dealer: 0 none, 1 cut card going in, 2 burn card to the tray. */
    var proc by mutableStateOf(0); private set
    private var peekPending = false
    var banner by mutableStateOf<CasualBanner?>(null); private set
    /** The dealer is sweeping last round's cards into the discard tray. */
    var sweeping by mutableStateOf(false); private set
    val payouts = mutableStateListOf<Payout>()
    /** What a player just did at the table, like "Hit" or "Double", for a moment. */
    val actions = mutableStateMapOf<Int, String>()
    var hint by mutableStateOf<String?>(null); private set
    val perks = mutableStateListOf<Perk>()
    /** A dare on offer, and whether you took it. */
    var dare by mutableStateOf<Dare?>(null); private set
    var dareOn by mutableStateOf(false); private set
    /** Double Down Rush: hands left where wins pay 50% extra. */
    var rushLeft by mutableIntStateOf(0); private set
    private var untilRush = rnd.nextInt(10, 17)
    /** A free drink on your rail, for a few hands. */
    var drinkHands by mutableIntStateOf(0); private set
    var wheelReady by mutableStateOf(false); private set
    var wheelIdx by mutableStateOf<Int?>(null); private set
    /** The three lucky cards after claiming the daily bonus, and which one you picked. */
    var luckyPrizes by mutableStateOf<List<Int>?>(null); private set
    var luckyPick by mutableStateOf<Int?>(null); private set
    /** Chips added or taken by rush bonuses, dares and comps in the last round, on top of the hand result. */
    var roundExtras by mutableIntStateOf(0); private set
    private val usedBanter = mutableSetOf<Int>()
    private val notifiedTiers = mutableSetOf<Int>()
    val toasts = mutableStateListOf<Achievement>()
    var stats by mutableStateOf(store.loadStats()); private set
    var earned by mutableStateOf(store.achievements); private set

    private var seq = 0L
    private fun k() = ++seq
    private var eventsJob: Job? = null

    init {
        life.exclude = Tiers.excluded(Tiers.forMin(settings.tableMin))
        life.setOpenSeats(settings.seats)
        life.fill(settings.startPlayers.coerceAtMost(settings.seats))
        life.seats.filterNotNull().forEach { store.addMet(it.who.name) }
        seatView = life.seats.toList()
        bet = store.lastBet.takeIf { it in settings.tableMin..bankroll } ?: settings.tableMin.coerceAtMost(bankroll)
        Tiers.ALL.filter { tierUnlocked(it) }.forEach { notifiedTiers += it.id }
    }

    // Rooms, perks and surprises
    fun tierUnlocked(t: Tier) = t.unlockAt <= maxOf(stats.biggestBankroll, bankroll) || t.id.toString() in store.invites || t.min == settings.tableMin

    private fun credit(amount: Int) {
        bankroll += amount
        game.resetBankroll(bankroll)
        store.bankroll = bankroll
    }

    fun perk(title: String, desc: String) { perks.add(Perk(k(), title, desc)); while (perks.size > 3) perks.removeAt(0) }

    fun acceptDare() {
        val d = dare ?: return
        if (!canBet || dareOn || bankroll < bet + d.stake) return
        dareOn = true
        say(d.seat, (if (d.duel) Rival.accepted else Dares.accepted).let { it[rnd.nextInt(it.size)] }, d.name)
        fx(Fx.CHIP)
    }
    fun declineDare() {
        val d = dare ?: return
        if (dareOn) return
        dare = null
        say(d.seat, (if (d.duel) Rival.declined else Dares.declined).let { it[rnd.nextInt(it.size)] }, d.name)
    }

    /** Spin the bonus wheel earned by a streak. Returns the segment it lands on. */
    fun spinWheel(): Int {
        if (!wheelReady || wheelIdx != null) return -1
        val i = Wheel.pick(rnd); wheelIdx = i
        return i
    }
    fun collectWheel() {
        val i = wheelIdx ?: return
        val amt = Wheel.UNITS[i] * settings.tableMin
        credit(amt); fx(Fx.WIN)
        perk("Bonus wheel: +${money(amt)}", "Nice spin.")
        wheelIdx = null; wheelReady = false
    }
    fun pickLucky(i: Int) {
        val p = luckyPrizes ?: return
        if (luckyPick != null || i !in p.indices) return
        luckyPick = i; credit(p[i]); fx(Fx.WIN)
    }
    fun closeLucky() { luckyPrizes = null; luckyPick = null }
    internal fun giveWheelForTest() { wheelReady = true }
    internal fun rushForTest(n: Int) { rushLeft = n }
    internal fun drinkForTest(n: Int) { drinkHands = n }
    internal fun giveDareForTest(seat: Int, stake: Int) { dare = Dare(seat, life.seats[seat]?.who?.name ?: "Someone", stake, "Bet you can't beat the dealer this hand. \$$stake says you can't.") }

    private fun settleDuel(d: Dare, extra: (Int) -> Unit) {
        val b = roundSeats.indexOfFirst { it.second == d.seatId }
        val theirs = gs.bots.getOrNull(b)
        if (b < 0 || theirs == null) { say(d.seat, "We didn't get our hand in. Rain check.", d.name); return }
        val tag = money(d.stake)
        when (Rival.compare(gs.hands, theirs)) {
            1 -> {
                extra(d.stake); rivalWins += 1; store.rivalWins = rivalWins
                val sc = Rival.scoreText(rivalWins, rivalLosses, d.name)
                say(d.seat, Rival.iLose[rnd.nextInt(Rival.iLose.size)].replace("\$STAKE", tag).replace("\$SCORE", sc + "."), d.name)
                perk("Rival: you won ${tag}", sc)
            }
            -1 -> {
                extra(-minOf(d.stake, bankroll)); rivalLosses += 1; store.rivalLosses = rivalLosses
                val sc = Rival.scoreText(rivalWins, rivalLosses, d.name)
                say(d.seat, Rival.iWin[rnd.nextInt(Rival.iWin.size)].replace("\$STAKE", tag).replace("\$SCORE", sc + "."), d.name)
                perk("Rival: ${d.name} won ${tag}", sc)
            }
            else -> say(d.seat, Rival.push[rnd.nextInt(Rival.push.size)], d.name)
        }
    }

    private fun funAfterRound(handNet: Int, net: Int, wins: Int) {
        roundExtras = 0
        fun extra(n: Int) { credit(n); roundExtras += n }
        if (drinkHands > 0) drinkHands--
        // Double Down Rush: a few hands where winning pays extra.
        if (rushLeft > 0) {
            if (net > 0 && net / 2 > 0) { extra(net / 2); perk("Rush bonus +${money(net / 2)}", "Wins pay 50% extra during the rush.") }
            rushLeft--
            if (rushLeft == 0) say(DEALER, "That's the end of the rush.")
        } else if (--untilRush <= 0) {
            rushLeft = 3; untilRush = rnd.nextInt(14, 23)
            perk("Double Down Rush!", "The next 3 hands pay 50% extra on wins.")
            say(DEALER, "Double Down Rush is on! Wins pay extra for three hands.")
        }
        // A friendly dare, settled on this hand.
        val d = if (dareOn) dare else null
        if (d != null) {
            dare = null; dareOn = false
            val tag = money(d.stake)
            if (d.duel) settleDuel(d, ::extra) else
            when {
                handNet > 0 -> { extra(d.stake); say(d.seat, Dares.won[rnd.nextInt(Dares.won.size)].replace("\$STAKE", tag), d.name) }
                handNet < 0 -> { extra(-minOf(d.stake, bankroll)); say(d.seat, Dares.lost[rnd.nextInt(Dares.lost.size)].replace("\$STAKE", tag), d.name) }
                else -> say(d.seat, "A push. Nobody pays.", d.name)
            }
        }
        // Comps for rated play.
        val every = maxOf(500, settings.tableMin * 40)
        if (stats.wagered - store.compMark >= every) {
            store.compMark = stats.wagered
            val c = Comps.roll(rnd)
            if (c.drink) { drinkHands = 4; say(DEALER, "A drink for you, compliments of the house.") } else extra(c.units * settings.tableMin)
            perk(c.title + (if (c.units > 0) " +${money(c.units * settings.tableMin)}" else ""), c.desc)
        }
        // New rooms: unlocked by bankroll, or by an invitation from the pit boss.
        Tiers.ALL.filter { it.id !in notifiedTiers && tierUnlocked(it) }.forEach { notifiedTiers += it.id; perk("Unlocked: ${it.name}", "Open the Table menu to move rooms.") }
        val locked = Tiers.ALL.firstOrNull { !tierUnlocked(it) }
        if (locked != null && net >= settings.tableMin * 8 && maxOf(stats.biggestBankroll, bankroll) >= locked.unlockAt / 2 && rnd.nextInt(100) < 45) {
            store.invites = store.invites + locked.id.toString()
            notifiedTiers += locked.id
            perk("Invitation: ${locked.name}", "The pit boss invited you to play there. Open the Table menu.")
            say(PIT, "You've been playing well. There's a seat for you in the ${locked.name} whenever you want it.")
        }
        // Three wins in a row earns a spin.
        if (wins > 0 && net > 0 && stats.winStreak > 0 && stats.winStreak % 3 == 0 && !wheelReady) {
            wheelReady = true
            perk("Bonus wheel!", "${stats.winStreak} wins in a row. Tap Spin the wheel.")
        }
        // Your rival challenges you head to head.
        if (dare == null && settings.tableTalk && bankroll >= settings.tableMin * 15 && stats.hands >= 6) {
            val rivalSeat = if (rivalName == null) null else life.seats.indices.firstOrNull { life.seats[it]?.who?.name == rivalName }
            val eligible = life.seats.indices.filter { life.seats[it]?.who?.name in Rival.ELIGIBLE }
            if (rivalSeat != null && rnd.nextInt(100) < 30) {
                val st = life.seats[rivalSeat]!!
                val dd = Rival.make(rivalSeat, st.id, st.who.name, settings.tableMin, rnd)
                dare = dd; say(rivalSeat, dd.line, dd.name)
            } else if (rivalName == null && eligible.isNotEmpty() && rnd.nextInt(100) < 25) {
                val i = eligible[rnd.nextInt(eligible.size)]
                val st = life.seats[i]!!
                rivalName = st.who.name; store.rivalName = rivalName
                perk("New rival: ${st.who.name}", "They want a running score against you. Watch for their challenges.")
                val dd = Rival.make(i, st.id, st.who.name, settings.tableMin, rnd)
                dare = dd; say(i, dd.line, dd.name)
            }
        }
        // Someone dares you between hands, now and then.
        if (dare == null && settings.tableTalk && bankroll >= settings.tableMin * 10 && rnd.nextInt(100) < 12) {
            val seated = life.seats.indices.filter { life.seats[it] != null }
            if (seated.isNotEmpty()) {
                val i = seated[rnd.nextInt(seated.size)]
                val dd = Dares.make(i, life.seats[i]!!.who.name, settings.tableMin, rnd)
                dare = dd; say(i, dd.line, dd.name)
            }
        }
    }

    private suspend fun banter(seated: List<Int>) {
        val a = seated[rnd.nextInt(seated.size)]
        val b = seated.filter { it != a }.let { it[rnd.nextInt(it.size)] }
        var i = rnd.nextInt(FunTalk.banter.size); var tries = 0
        while (i in usedBanter && tries++ < 30) i = rnd.nextInt(FunTalk.banter.size)
        usedBanter += i; if (usedBanter.size >= FunTalk.banter.size - 2) usedBanter.clear()
        val (x, y) = FunTalk.banter[i]
        say(a, x, minor = true); delay(2300); say(b, y, minor = true)
        life.seats[b]?.let { mood(it.id, Mood.HAPPY, 1800) }
    }

    private suspend fun sendDrink(i: Int) {
        val s = life.seats[i] ?: return
        say(i, FunTalk.drinkSend[rnd.nextInt(FunTalk.drinkSend.size)]); delay(1900)
        drinkHands = 4; fx(Fx.CHIP)
        perk("Drink from ${s.who.name}", "Cheers!")
        mood(s.id, Mood.HAPPY, 1800)
    }

    private fun shoeKey() = "${settings.tableMin}_${settings.decks}"
    private fun newGame() = BlackjackGame(settings.rules, bankroll0(), botCount = 0, unit = settings.tableMin).also { g ->
        store.loadShoe(shoeKey())?.let { g.shoe.restore(it) }
    }
    private fun saveShoe() = store.saveShoe(shoeKey(), game.shoe.snapshot())
    private fun bankroll0() = try { store.bankroll } catch (e: Exception) { CasualStore.START }

    /** A random seated player (not [except]) says something for [talk], with [pct] percent chance. */
    private fun playerSay(talk: Talk, pct: Int, except: Int = -1): Int? {
        if (rnd.nextInt(100) >= pct) return null
        val seats = life.seats.indices.filter { life.seats[it] != null && it != except }
        if (seats.isEmpty()) return null
        val i = seats[rnd.nextInt(seats.size)]
        val line = TableTalk.say(life.seats[i]!!.who, talk, rnd) ?: return null
        say(i, line)
        return i
    }

    private suspend fun doPeek() {
        say(DEALER, MoreTalk.dealerSays(dealer.name, "peek", rnd))
        peeking = true
        if (!instant) delay(1300)
        peeking = false
    }

    private suspend fun peekNoBlackjack() {
        if (!peekPending) return
        peekPending = false
        doPeek()
        say(DEALER, MoreTalk.dealerSays(dealer.name, "nobj", rnd))
        if (!instant) delay(500)
    }

    /** First deal of a shoe or after a shuffle: the player cuts, the dealer burns a card. */
    private suspend fun shoeProcedure() {
        if (rnd.nextInt(100) < 70) say(DEALER, MoreTalk.dealerSays(dealer.name, "cut", rnd))
        proc = 1; fx(Fx.DEAL)
        if (!instant) delay(900)
        proc = 2
        if (rnd.nextInt(100) < 50) say(DEALER, MoreTalk.dealerSays(dealer.name, "burn", rnd))
        if (!instant) delay(750)
        proc = 0
    }

    private fun upValue(): Int = gs.dealerCards.firstOrNull()?.let { handTotal(listOf(it)).first } ?: 0

    private fun say(where: Int, text: String, asName: String? = null, minor: Boolean = false) {
        if (!settings.tableTalk && where != DEALER && where != PIT) return
        // At most two players talk at once so bubbles stay readable.
        if (where >= 0) {
            val talking = bubbles.entries.filter { it.key >= 0 && it.key != where }.sortedBy { it.value.key }
            if (talking.size >= 2) talking.take(talking.size - 1).forEach { bubbles.remove(it.key) }
        }
        bubbles[where] = Bubble(text, k())
        val key = bubbles[where]!!.key
        if (!instant) viewModelScope.launch { delay(2600 + text.length * 35L); if (bubbles[where]?.key == key) bubbles.remove(where) }
        val who = asName ?: when (where) { DEALER -> dealer.name; PIT -> "Pit boss"; else -> (seatView.getOrNull(where) ?: life.seats.getOrNull(where))?.who?.name ?: "" }
        val line = ChatLine(key, where, who, text, minor)
        chat.add(line)
        while (chat.size > 6) chat.removeAt(0)
        if (!instant) viewModelScope.launch { delay(5200 + text.length * 60L); chat.remove(line) }
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
    val maxBet: Int get() = if (flatLeft > 0) PlayerHeat.flatCap(settings.tableMin) else settings.tableMin * 100
    val canBet: Boolean get() = !busy && (gs.phase == Phase.BETTING || gs.phase == Phase.ROUND_OVER)

    fun addChip(v: Int) {
        if (!canBet) return
        if (roundOver) clearTableForBet()
        val room = minOf(maxBet, bankroll - ppBet - tpBet - llBet - ncBet)
        if (bet + v > room) { if (bet < room) { bet = room; fx(Fx.CHIP) }; return }
        bet += v; fx(Fx.CHIP)
    }
    fun clearBet() { if (canBet) { bet = 0; fx(Fx.CHIP) } }
    fun doubleBet() { if (canBet) { bet = minOf(bet * 2, maxBet, bankroll - ppBet - tpBet - llBet - ncBet).coerceAtLeast(0); fx(Fx.CHIP) } }
    fun repeatBet() { if (canBet) { bet = store.lastBet.coerceAtMost(minOf(maxBet, bankroll)); fx(Fx.CHIP) } }

    /** Side bets step through off, 1, 2 and 5 table minimums. */
    fun cyclePP() { if (canBet) { ppBet = nextSide(ppBet); fx(Fx.CHIP) } }
    fun cycleTP() { if (canBet) { tpBet = nextSide(tpBet); fx(Fx.CHIP) } }
    fun cycleLL() { if (canBet) { llBet = nextSide(llBet); fx(Fx.CHIP) } }
    fun cycleNC() { if (canBet) { ncBet = nextSide(ncBet); fx(Fx.CHIP) } }
    fun callNC(i: Int) { if (canBet) { ncCall = i.coerceIn(0, 2); fx(Fx.CHIP) } }
    /** What each side bet did this round, one line per bet. Empty until the round is over. */
    var sidePanelKey by mutableStateOf(-1L)
    val sidePanelOn: Boolean get() = sideLines.isNotEmpty() && sidePanelKey != gs.roundStartSeq.toLong()
    fun hideSidePanel() { sidePanelKey = gs.roundStartSeq.toLong() }
    val sideLines: List<SideLine> get() {
        if (!roundOver || busy || !dealtOnce) return emptyList()
        val out = mutableListOf<SideLine>()
        if (gs.perfectPairsBet > 0) out += SideLine("Perfect Pairs", gs.perfectPairsResult ?: "No pair", gs.perfectPairsNet ?: -gs.perfectPairsBet)
        tpResult?.let { out += SideLine("21+3", it, tpNet) }
        llResult?.let { out += SideLine("Lucky Ladies", it, llNet) }
        ncResult?.let { out += SideLine("Next Card", it, ncNet) }
        return out
    }
    val miniTotal: Int get() = ppBet + tpBet + llBet + ncBet

    private fun grantPower(reason: String) {
        if (power.sum() >= 6) return
        val i = rnd.nextInt(PowerUp.entries.size)
        power[i] = power[i] + 1; store.setPowerUp(i, power[i])
        perk("Power-up: ${PowerUp.entries[i].label}", reason)
    }

    fun usePower(p: PowerUp) {
        val i = p.ordinal
        if (power[i] <= 0) return
        val ok = when (p) {
            PowerUp.PEEK -> !busy && gs.phase == Phase.PLAYER && peeked == null && gs.dealerCards.size >= 2
            PowerUp.REFUND -> !armedRefund && (canBet || (!busy && gs.phase == Phase.INSURANCE))
            PowerUp.COVER -> !armedCover && (canBet || (!busy && gs.phase == Phase.PLAYER))
        }
        if (!ok) return
        power[i] = power[i] - 1; store.setPowerUp(i, power[i])
        fx(Fx.CHIP)
        when (p) {
            PowerUp.PEEK -> { peeked = gs.dealerCards[1]; say(DEALER, "Fine, have a look.") }
            PowerUp.REFUND -> armedRefund = true
            PowerUp.COVER -> armedCover = true
        }
    }
    private fun nextSide(cur: Int): Int {
        val m = settings.tableMin
        val steps = listOf(0, m, m * 2, m * 5)
        val n = steps[(steps.indexOf(cur).coerceAtLeast(0) + 1) % steps.size]
        return if (n + bet + ppBet + tpBet + llBet + ncBet - cur > bankroll) 0 else n
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
        luckyPrizes = LuckyCards.deal(rnd); luckyPick = null
        return amt
    }

    // Play
    fun deal() = startRound(bet)

    /** Sit a round out: the table plays and you watch and count, with no bet. */
    fun watch() = startRound(0)

    private fun startRound(stake: Int) {
        if (!canBet) return
        if (stake != 0 && (stake < settings.tableMin || stake > bankroll)) return
        if (stake != 0 && (cooloff > 0 || stake > maxBet)) return
        eventsJob?.cancel(); syncSeats()
        if (!dareOn) dare = null
        busy = true; hint = null
        if (stake > 0) store.lastBet = stake
        if (game.state().bankroll != bankroll) game.resetBankroll(bankroll)
        viewModelScope.launch {
            // The dealer sweeps up last round's cards first.
            if (roundOver && dealtOnce && !instant && gs.dealerCards.isNotEmpty()) { sweeping = true; fx(Fx.DEAL); delay(520) }
            clearTableForBet(); tpResult = null; tpNet = 0; llResult = null; llNet = 0; ncResult = null; ncNet = 0; peeked = null; payouts.clear()
            life.planBets(game.upcomingTrueCount())
            peekPending = false
            if (life.shuffleUpNow) {
                life.shuffleUpNow = false
                game.shoe.shuffle(); countRun = game.shoe.runningCount
                say(DEALER, "Shuffle up.")
                shuffleAnim()
                playerSay(Talk.SHUFFLE, 40)
                shoeProcedure()
            } else if (!dealtOnce) shoeProcedure()
            val playing = life.playing()
            roundSeats = playing.map { it to life.seats[it]!!.id }
            game.changeSeats(playing.size)
            game.botStyles = playing.map { styleFor(life.seats[it]!!) }
            game.botBets = playing.map { life.seats[it]!!.bet }
            seatView = life.seats.toList()
            // Big bets get a comment.
            playing.forEach { i ->
                val s = life.seats[i]!!
                if (s.bet >= s.who.baseUnits * settings.tableMin * 3 && rnd.nextInt(100) < s.who.talky + 20) TableTalk.say(s.who, Talk.BIG_BET, rnd)?.let { say(i, it) }
            }
            val pp = if (settings.sideBets && stake > 0) ppBet.coerceAtMost(bankroll - stake) else 0
            val tp = if (settings.sideBets && stake > 0 && tpBet > 0 && bankroll - stake - pp >= tpBet) tpBet else 0
            if (tp > 0) game.tip(tp)
            val ll = if (settings.sideBets && stake > 0 && llBet > 0 && bankroll - stake - pp - tp - llBet >= 0) llBet else 0
            val nc = if (settings.sideBets && stake > 0 && ncBet > 0 && bankroll - stake - pp - tp - ll - ncBet >= 0) ncBet else 0
            if (ll > 0) game.tip(ll)
            if (nc > 0) game.tip(nc)
            val before = game.state().bankroll
            game.deal(stake, pp)
            dealtOnce = true
            bankroll = before - stake - game.state().perfectPairsBet.let { if (it > 0) it else 0 }
            gs = game.state()
            if (tp > 0) {
                val h = gs.hands[0]
                val r = TwentyOnePlusThree.evaluate(h.cards[0], h.cards[1], gs.dealerCards[0])
                tpNet = if (r == null) -tp else tp * r.second
                tpResult = r?.first ?: "No 21+3"
                if (r != null) game.resetBankroll(game.state().bankroll + tp * (r.second + 1))
            }
            if (ll > 0) {
                val h = gs.hands[0]
                val r = LuckyLadies.evaluate(h.cards[0], h.cards[1])
                llNet = if (r == null) -ll else ll * r.second
                llResult = r?.first ?: "No Lucky Ladies"
                if (r != null) game.resetBankroll(game.state().bankroll + ll * (r.second + 1))
            }
            if (nc > 0) {
                val first = gs.hands[0].cards[0]
                val r = NextCard.payout(ncCall, first, nc)
                ncNet = if (r == null) -nc else r.second
                ncResult = r?.first ?: "Missed: ${first.rank.label}"
                if (r != null) game.resetBankroll(game.state().bankroll + nc + r.second)
            }
            if (gs.shuffledThisRound || game.reshuffledByMachine) shuffleAnim()
            shown = gs.roundStartSeq
            syncCount()
            sweeping = false
            store.bankroll = game.state().bankroll
            val up = upValue()
            peekPending = up >= 10
            if (up in 4..6) playerSay(Talk.DEALER_WEAK, 35) else if (up >= 10) playerSay(Talk.DEALER_STRONG, 35)
            reveal()
            if (gs.phase != Phase.INSURANCE) peekNoBlackjack()
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
        actions[YOU] = when (a) { Action.HIT -> "Hit"; Action.STAND -> "Stand"; Action.DOUBLE -> "Double"; Action.SPLIT -> "Split"; else -> "Surrender" }
        if (!instant) viewModelScope.launch { delay(1600); actions.remove(YOU) }
        if (a == Action.DOUBLE && rnd.nextInt(100) < 40) say(DEALER, MoreTalk.dealerSays(dealer.name, "double", rnd))
        if (a == Action.SPLIT && rnd.nextInt(100) < 40) say(DEALER, MoreTalk.dealerSays(dealer.name, "split", rnd))
        if (a == Action.SPLIT) playerSay(Talk.SPLIT, 30)
        viewModelScope.launch { reveal(); after() }
    }

    fun insurance(take: Boolean) {
        if (busy || gs.phase != Phase.INSURANCE) return
        busy = true
        game.insurance(take)
        gs = game.state()
        viewModelScope.launch { reveal(); peekNoBlackjack(); after() }
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
            if (peekPending && s == gs.holeRevealSeq) { peekPending = false; doPeek() }
            // Another player taking a hit: a short think, and the action shows over them.
            if (owner in 0 until 4 && s >= gs.roundStartSeq + 2 * (gs.bots.size + 1) + 2) {
                val b = roundSeats.indexOfFirst { it.first == owner }
                val hand = gs.bots.getOrNull(b)
                val doubling = hand != null && hand.doubled && hand.seqs.lastOrNull() == s
                actions[owner] = if (doubling) "Double" else "Hit"
                if (doubling && rnd.nextInt(100) < 50) life.seats.getOrNull(owner)?.let { st -> TableTalk.say(st.who, Talk.DOUBLE, rnd)?.let { say(owner, it) } }
                if (!instant) { delay(thinkMs(life.seats.getOrNull(owner)?.who?.archetype)); viewModelScope.launch { delay(1200); actions.remove(owner) } }
            }
            reach = Reach(owner, k())
            fx(if (flip) Fx.FLIP else Fx.DEAL)
            shown = s + 1
            gs.events.firstOrNull { it.seq == s }?.let { if (it.counted) countRun += it.tag }
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

    /** How long each kind of player takes over a decision. */
    private fun thinkMs(a: Archetype?): Long = when (a) {
        Archetype.PRO -> 350L; Archetype.COUNTER -> 400L; Archetype.HIGH_ROLLER -> 450L; Archetype.TILTER -> 500L
        Archetype.REGULAR -> 600L; Archetype.PARTY -> 700L; Archetype.HUNCH -> 800L; Archetype.TOURIST -> 900L
        Archetype.DRUNK -> 1000L; Archetype.CAUTIOUS -> 1200L; Archetype.ROOKIE -> 1300L; null -> 650L
    }

    private fun ownerOf(seq: Int): Int {
        if (seq in gs.dealerSeqs || seq == gs.holeRevealSeq) return DEALER
        gs.hands.forEachIndexed { i, h -> if (seq in h.seqs) return YOU + i }
        gs.bots.forEachIndexed { i, h -> if (seq in h.seqs) return roundSeats.getOrNull(i)?.first ?: DEALER }
        return DEALER
    }

    private fun after() {
        when (gs.phase) {
            Phase.INSURANCE -> if (!settings.offerInsurance) { busy = false; insurance(false); return } else { busy = false; say(DEALER, MoreTalk.dealerSays(dealer.name, "ins", rnd)) }
            Phase.PLAYER -> { busy = false; bankroll = gs.bankroll }
            Phase.ROUND_OVER -> finishRound()
            Phase.BETTING -> busy = false
        }
    }

    /** One extra moment per round at most: a reaction to your bust or loss, a tip for the dealer, or a lapse in manners. */
    private fun roundExtrasTalk(net: Int, youBust: Boolean, nets: Map<Int, Int>) {
        val min = settings.tableMin
        when {
            youBust && playerSay(Talk.YOU_BUST, 35) != null -> {}
            net <= -min * 10 && playerSay(Talk.YOU_LOSE_BIG, 30) != null -> {}
            else -> {
                val tipper = nets.entries.firstOrNull { (seat, n) -> n >= min * 6 && life.seats.getOrNull(seat)?.who?.archetype in MoreTalk.tippers && rnd.nextInt(100) < 30 }
                if (tipper != null) {
                    val st = life.seats[tipper.key] ?: return
                    TableTalk.say(st.who, Talk.TIP, rnd)?.let { say(tipper.key, it) }
                    viewModelScope.launch {
                        if (!instant) delay(1500)
                        say(DEALER, MoreTalk.dealerSays(dealer.name, "thanks", rnd))
                        fx(Fx.CHIP)
                    }
                } else if (rnd.nextInt(100) < 7) {
                    val off = life.seats.indices.filter { life.seats[it]?.who?.archetype in MoreTalk.forgetful }.randomOrNull(rnd) ?: return
                    say(DEALER, MoreTalk.dealerSays(dealer.name, "etiquette", rnd))
                    viewModelScope.launch {
                        if (!instant) delay(1700)
                        TableTalk.say(life.seats[off]?.who ?: return@launch, Talk.ETIQUETTE_REPLY, rnd)?.let { say(off, it) }
                    }
                }
            }
        }
    }

    private fun updateNight(handNet: Int, wins: Int, doubledWin: Boolean) {
        val run = night ?: return
        val c = Story.chapters.getOrNull(run.chapter) ?: run { night = null; store.nightRun = null; return }
        val next = Story.after(run, handNet, wins, doubledWin)
        when (Story.state(c, next, bankroll, settings.tableMin)) {
            NightState.DONE -> {
                night = null; store.nightRun = null
                storyDone = maxOf(storyDone, run.chapter + 1); store.storyDone = storyDone
                val prize = c.rewardUnits * settings.tableMin
                credit(prize); roundExtras += prize
                perk("Night ${run.chapter + 1} complete: ${c.title}", "+${money(prize)} and a power-up." + if (run.chapter == Story.chapters.lastIndex) " ${Story.NAME} is finished." else "")
                say(DEALER, c.outro)
                grantPower("A story night reward.")
            }
            NightState.FAILED -> {
                night = null; store.nightRun = null
                perk("Night ${run.chapter + 1} missed", "Out of hands. Start it again from the Nights tab.")
                say(DEALER, "Not tonight. The weekend will still be here.")
            }
            NightState.ACTIVE -> { night = next; store.nightRun = next.encode() }
        }
    }

    /** Refunds from armed power-ups, then maybe a new power-up as a reward. Every credit is counted in [roundExtras]. */
    private fun settlePowerUps(g: GameState, handNet: Int, dealerBj: Boolean, bj: Boolean) {
        var back = 0
        if (armedRefund && dealerBj && handNet < 0) { back += -handNet; perk("Refund used", "The dealer had blackjack. Your bet came back.") }
        if (armedCover) {
            val lost = g.results.filter { it.outcome == Outcome.BUST }.sumOf { -it.net }
            if (lost > 0) { back += lost; perk("Bust cover used", "You busted. Your bet came back.") }
        }
        if (back > 0) { credit(back); roundExtras += back }
        armedRefund = false; armedCover = false; peeked = null
        store.powerHands = store.powerHands + 1
        val doubledWin = g.hands.zip(g.results).any { (h, r) -> h.doubled && r.net > 0 }
        when {
            bj -> grantPower("A blackjack earned you a power-up.")
            store.powerHands % 10 == 0 -> grantPower("Ten hands played. Here's a power-up.")
            doubledWin && rnd.nextInt(100) < 50 -> grantPower("A winning double earned you a power-up.")
        }
    }

    private fun finishRound() {
        val g = gs
        bankroll = game.state().bankroll
        store.bankroll = bankroll
        countRun = game.shoe.runningCount
        saveShoe()
        val watching = g.hands.isEmpty()
        val handNet = g.results.sumOf { it.net }
        val net = handNet + g.insuranceNet + (g.perfectPairsNet ?: 0) + tpNet + llNet + ncNet
        val wagered = g.hands.sumOf { it.bet } + g.insuranceBet + g.perfectPairsBet + (if (tpResult != null) tpBet else 0) + (if (llResult != null) llBet else 0) + (if (ncResult != null) ncBet else 0)
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
            sideWin = (g.perfectPairsNet ?: 0) > 0 || tpNet > 0 || llNet > 0 || ncNet > 0,
            fiveCardWin = g.hands.zip(g.results).any { (h, r) -> h.cards.size >= 5 && r.net > 0 },
            dealerBust = dealerBust, bankrollAfter = bankroll, maxBetSeen = g.hands.maxOfOrNull { it.bet } ?: 0,
        )
        stats = if (watching) stats else Achievements.apply(stats, summary).also { store.saveStats(it) }
        val newOnes = if (watching) emptyList() else Achievements.check(earned, stats, summary)
        if (newOnes.isNotEmpty()) { earned = earned + newOnes.map { it.id }; store.achievements = earned; toasts.addAll(newOnes) }

        if (!watching) funAfterRound(handNet, net, wins)
        if (!watching) settlePowerUps(g, handNet, dealerBj, bj)
        if (!watching) updateNight(handNet, wins, g.hands.zip(g.results).any { (h, r) -> h.doubled && r.net > 0 })

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
        banner = if (watching) null else CasualBanner(title, net, bj || net >= settings.tableMin * 10, k())
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

        // Table streak and pit heat. Both are flavor read from what just happened; neither touches the shoe except an early shuffle.
        run {
            val all = g.results.map { it.net } + g.botResults.map { it.net }
            val prev = streak.level
            streak.record(all.count { it > 0 }, all.count { it < 0 })
            if (streak.level != prev && kotlin.math.abs(streak.level) >= 1) {
                val pool = when (streak.level) { 2 -> TableStreak.hotTalk; 1 -> TableStreak.warmTalk; -1 -> TableStreak.coolTalk; else -> TableStreak.coldTalk }
                val seats = life.seats.indices.filter { life.seats[it] != null }
                if (seats.isNotEmpty() && rnd.nextInt(100) < 70) say(seats[rnd.nextInt(seats.size)], pool[rnd.nextInt(pool.size)])
            }
            val stake = if (watching) 0 else g.hands.sumOf { it.bet }
            pendingMove = heat.afterRound(stake, handNet, settings.tableMin, rnd)
            if (pendingMove == PitMove.FLAT_BET && bet > PlayerHeat.flatCap(settings.tableMin)) bet = PlayerHeat.flatCap(settings.tableMin)
            if (pendingMove == PitMove.ASKED_TO_LEAVE) { bet = 0; ppBet = 0; tpBet = 0; llBet = 0; ncBet = 0 }
            syncHeat()
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
            TableTalk.say(life.seats[i]!!.who, Talk.YOU_BIG_WIN, rnd)?.let { say(i, it) }
        }
        roundExtrasTalk(net, g.results.isNotEmpty() && g.results.all { it.outcome == Outcome.BUST }, nets)
        roundOver = true
        if (bet > bankroll) bet = (bankroll / settings.tableMin * settings.tableMin).coerceAtLeast(0)
        if (ppBet + bet > bankroll) ppBet = 0
        if (tpBet + ppBet + bet > bankroll) tpBet = 0
        if (llBet + ncBet + tpBet + ppBet + bet > bankroll) { llBet = 0; ncBet = 0 }
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
                    val met = store.met(e.s.who.name); store.addMet(e.s.who.name)
                    if (met >= 3 && rnd.nextInt(100) < 45) { if (!instant) delay(1500); say(e.seat, FunTalk.familiar[rnd.nextInt(FunTalk.familiar.size)]) }
                    if (rnd.nextInt(100) < dealer.talky + 30) {
                        if (!instant) delay(900)
                        say(DEALER, if (rnd.nextBoolean()) MoreTalk.dealerSays(dealer.name, "welcome", rnd) else listOf("Welcome. Changing ${money(e.s.buyIn)}.", "Welcome to the table.", "Good luck, ${e.s.who.name}.")[rnd.nextInt(3)])
                    }
                    if (!instant) delay(700)
                    playerSay(Talk.NEW_PLAYER, 35, except = e.seat)
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
        if (life.pitWatching < 0 && pit != null && !heat.watched) {
            if (!instant) delay(1200)
            if (life.pitWatching < 0) { if (events.any { it is TableEvent.PitAct || it is TableEvent.PitComp }) say(PIT, TableTalk.pit("leaving", rnd)); pit = null }
        }
        val mv = pendingMove; pendingMove = PitMove.NONE
        if (mv != PitMove.NONE) playerPit(mv)
        seatView = life.seats.toList()
        if (shiftRounds >= CasualDealers.SHIFT) changeDealer()
    }

    /** The pit boss deals with you: a look, a cap on bets, a shuffle, or a few hands on the rail. */
    private suspend fun playerPit(mv: PitMove) {
        pit = PitState(2, k())
        if (!instant) delay(1100)
        when (mv) {
            PitMove.WATCH -> say(PIT, TableTalk.pit("heat_watch", rnd))
            PitMove.RELAX -> say(PIT, TableTalk.pit("heat_relax", rnd))
            PitMove.FLAT_BET -> {
                say(PIT, TableTalk.pit("heat_flat", rnd))
                perk("Small bets only", "Bets are capped at ${money(PlayerHeat.flatCap(settings.tableMin))} for ${PlayerHeat.FLAT_ROUNDS} hands.")
            }
            PitMove.SHUFFLE_UP -> {
                say(PIT, TableTalk.pit("heat_shuffle", rnd)); life.shuffleUpNow = true; dealerReact(Mood.NERVOUS)
                perk("Early shuffle", "The shoe is reshuffled next hand, so the count starts over.")
            }
            PitMove.ASKED_TO_LEAVE -> {
                say(PIT, TableTalk.pit("heat_leave", rnd)); dealerReact(Mood.NERVOUS)
                perk("Cooling off", "Watch ${PlayerHeat.COOLOFF} hands from the rail. Tipping and small bets keep the heat down later.")
            }
            PitMove.RELEASE -> say(PIT, TableTalk.pit("heat_release", rnd))
            PitMove.NONE -> {}
        }
        if (!instant) delay(900)
        if (life.pitWatching < 0 && !heat.watched) { if (mv != PitMove.RELAX && mv != PitMove.RELEASE) say(PIT, TableTalk.pit("leaving", rnd)); pit = null }
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
            val seated = life.seats.indices.filter { life.seats[it] != null }
            val rs = if (rivalName == null) null else life.seats.indices.firstOrNull { life.seats[it]?.who?.name == rivalName }
            if (rs != null && rnd.nextInt(100) < 22) { say(rs, Rival.taunts[rnd.nextInt(Rival.taunts.size)]); continue }
            if (seated.size >= 2 && rnd.nextInt(100) < 24) { banter(seated); continue }
            if (seated.isNotEmpty() && drinkHands == 0 && rnd.nextInt(100) < 7) { sendDrink(seated[rnd.nextInt(seated.size)]); continue }
            if (rnd.nextInt(100) < dealer.talky) { say(DEALER, TableTalk.dealer(dealer.chatter, rnd)); continue }
            for (e in life.idleChat()) { say(e.seat, e.line, minor = true); delay(1600) }
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
            if (n.rules != old.rules) store.clearShoes()
            game = newGame().also { it.resetBankroll(bankroll) }
            gs = game.state(); shown = 0; countRun = game.shoe.runningCount; roundSeats = emptyList(); roundOver = false; banner = null
            life.unit = n.tableMin; life.clearPitBoss(); pit = null; resetHeat()
            if (n.tableMin != old.tableMin) { bet = n.tableMin.coerceAtMost(bankroll); ppBet = 0; tpBet = 0; llBet = 0; ncBet = 0 }
            if (n.tableMin != old.tableMin) {
                val t = Tiers.forMin(n.tableMin)
                life.exclude = Tiers.excluded(t)
                for (i in life.seats.indices) life.seats[i] = null
                life.fill(n.startPlayers.coerceAtMost(n.seats))
                seatView = life.seats.toList(); departing.clear(); chat.clear()
                life.seats.filterNotNull().forEach { store.addMet(it.who.name) }
                dare = null; dareOn = false
                say(DEALER, "Welcome to the ${t.name}.")
            }
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
    internal fun heatForTest(level: Int) { heat.force(level); syncHeat() }
    internal fun streakForTest(results: List<Int>) { streak.clear(); results.forEach { streak.record(maxOf(it, 0), maxOf(-it, 0)) }; syncHeat() }
    internal fun seatForTest(seat: Int, name: String, heat: Int = 0) {
        val who = com.hilotrainer.engine.Cast.byName(name)
        life.seats[seat] = Seat(1000L + seat, who, 50 * settings.tableMin, 50 * settings.tableMin, who.baseUnits * settings.tableMin, plan = 99, heat = heat)
        seatView = life.seats.toList()
    }
    internal fun giveDuelForTest(seat: Int, stake: Int) {
        val st = life.seats[seat]!!
        rivalName = st.who.name
        dare = Dare(seat, st.who.name, stake, "Head to head, you and me. \$$stake says my hand beats yours.", duel = true, seatId = st.id)
    }
    internal fun setStoryForTest(done: Int) { storyDone = done; store.storyDone = done }
    internal fun procForTest(stage: Int, peek: Boolean) { proc = stage; peeking = peek }
    internal fun signalForTest(text: String) { actions[YOU] = text }
    internal fun sayForTest(where: Int, text: String, minor: Boolean = false) = say(where, text, minor = minor)
    internal fun pitForTest(seat: Int) { pit = PitState(seat, k()) }
    internal fun moodForTest(id: Long, m: Mood) { moods[id] = m }


    companion object {
        const val DEALER = -1
        const val PIT = -2
        const val YOU = 10
    }
}
