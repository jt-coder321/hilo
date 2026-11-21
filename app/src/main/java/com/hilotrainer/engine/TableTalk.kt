package com.hilotrainer.engine

import kotlin.random.Random

/** Moments that can get people talking at the table. */
enum class Talk {
    ARRIVE, WIN, BIG_WIN, LOSE, BUST, BLACKJACK, PUSH, DEALER_BUST, YOU_BLACKJACK, YOU_BIG_WIN,
    WIN_STREAK, LOSS_STREAK, BIG_BET, REBUY, IDLE, REPLY, BUSTED_OUT, WIN_GOAL, COLD_SHOE, TILT, BACKOFF_REPLY,
    DEALER_WEAK, DEALER_STRONG, NEW_PLAYER, SHUFFLE, YOU_BUST, YOU_LOSE_BIG, PEEK, DOUBLE, TIP, ETIQUETTE_REPLY,
}

/**
 * Table talk: every line people say in Just Play, grouped by who says it and when.
 * [pick] avoids repeating anything said recently.
 */
object TableTalk {
    private val G = Archetype.entries.toSet()
    private fun only(vararg a: Archetype) = a.toSet()

    /** (who, when, lines). An archetype with no line of its own for a moment uses the shared lines. */
    private val PLAYER: List<Triple<Set<Archetype>, Talk, List<String>>> by lazy { BASE + MoreTalk.player }
    private val BASE: List<Triple<Set<Archetype>, Talk, List<String>>> = listOf(
        // Tourists and party guests
        Triple(only(Archetype.TOURIST), Talk.WIN, listOf("Woo! Vacation's paying for itself!", "Honey, I'm winning! Oh, she can't hear me.", "Is it always this easy?")),
        Triple(only(Archetype.TOURIST), Talk.LOSE, listOf("Shoot. Well, it's only money.", "That's a souvenir I didn't want.", "Back home this would be a nice dinner.")),
        Triple(only(Archetype.TOURIST), Talk.LOSS_STREAK, listOf("It's due. It has to be due.", "Okay, doubling up. That's how it works, right?", "Three in a row? Now it has to turn.")),
        Triple(only(Archetype.TOURIST), Talk.IDLE, listOf("We saw the fountains today. Gorgeous.", "Anybody know a good steakhouse around here?", "My brother-in-law said never split tens. Is that true?", "The pool here is the size of my whole town.")),
        Triple(only(Archetype.PARTY), Talk.WIN, listOf("Yesss! Wedding fund!", "Somebody take a picture of this!", "I'm so good at this game!")),
        Triple(only(Archetype.PARTY), Talk.LOSE, listOf("Noooo! Okay, it's fine. It's fine.", "Rude, dealer. Very rude.", "That one doesn't count, I wasn't ready.")),
        Triple(only(Archetype.PARTY), Talk.IDLE, listOf("The girls are at the bar. I snuck away!", "Do you think the dealer is single? Asking for a friend.", "Saturday I'm getting married! Can you believe it?", "Does anyone have a hair tie?")),
        Triple(only(Archetype.PARTY, Archetype.DRUNK), Talk.BLACKJACK, listOf("BLACKJACK! Shots for everyone!", "Woooo! Did everybody see that?", "Twenty one! That's my lucky number now!")),
        // High roller
        Triple(only(Archetype.HIGH_ROLLER), Talk.WIN, listOf("Let it ride.", "Press it.", "Now we're playing.", "Thank you, sweetheart.")),
        Triple(only(Archetype.HIGH_ROLLER), Talk.LOSE, listOf("Rounding error.", "That's nothing. Deal.", "Hm.")),
        Triple(only(Archetype.HIGH_ROLLER), Talk.BIG_BET, listOf("Max it out.", "Let's make it worth getting up for.", "Black chips only, please.")),
        Triple(only(Archetype.HIGH_ROLLER), Talk.IDLE, listOf("My jet leaves at nine. Plenty of time.", "I own a few car washes. Don't laugh, it's a living.", "Last time I was here I won a boat. Long story.", "Someone send a bottle of the good stuff over.")),
        Triple(only(Archetype.HIGH_ROLLER), Talk.BLACKJACK, listOf("Now that's how you do it.", "Pay me, please.", "Beautiful.")),
        // Professionals and counters: few words, all business
        Triple(only(Archetype.PRO, Archetype.COUNTER), Talk.WIN, listOf("Thank you.", "Good.", "Nice.")),
        Triple(only(Archetype.PRO, Archetype.COUNTER), Talk.LOSE, listOf("Variance.", "It happens.", "Next.")),
        Triple(only(Archetype.PRO, Archetype.COUNTER), Talk.BLACKJACK, listOf("There it is.", "Thank you, dealer.")),
        Triple(only(Archetype.PRO), Talk.IDLE, listOf("Always set a loss limit before you sit down.", "The math doesn't care how you feel.", "Never take insurance. Trust me.", "I keep a spreadsheet of every session.")),
        Triple(only(Archetype.COUNTER), Talk.IDLE, listOf("Nice shoe so far.", "How many decks are you dealing, six?", "Long day. Just here to unwind.", "Is the buffet any good this week?")),
        Triple(only(Archetype.COUNTER), Talk.BIG_BET, listOf("Feeling lucky this hand.", "Let's try something bigger.", "Why not, it's Saturday.")),
        Triple(only(Archetype.COUNTER), Talk.BACKOFF_REPLY, listOf("Fair enough. I'll cash out.", "I was just having a good night.", "No problem. Have a good one.", "Wow. Okay.")),
        Triple(only(Archetype.COUNTER), Talk.COLD_SHOE, listOf("This shoe's done for me.", "I'll find another table.", "Going to stretch my legs.")),
        // Loud regular
        Triple(only(Archetype.REGULAR), Talk.WIN, listOf("That's what I'm talking about!", "Lou's back, baby!", "Pay the man!", "Who's hot? Lou's hot!")),
        Triple(only(Archetype.REGULAR), Talk.LOSE, listOf("Come on, dealer, give us something!", "This shoe is a disaster.", "I've been coming here twenty years and this never happens!")),
        Triple(only(Archetype.REGULAR), Talk.BUST, listOf("Every time! Every single time!", "Who keeps putting tens in there?", "Of course. Of course it's a king.")),
        Triple(only(Archetype.REGULAR), Talk.IDLE, listOf("You see the game last night? Brutal.", "Back in my day the minimum was two bucks.", "I know every dealer in this joint by name.", "My wife thinks I'm at bowling.")),
        Triple(only(Archetype.REGULAR, Archetype.TILTER), Talk.TILT, listOf("This dealer hates me. I'm done!", "I'm out. This table is cursed.", "Rigged. The whole place is rigged.")),
        // A few drinks in
        Triple(only(Archetype.DRUNK), Talk.WIN, listOf("I win? I win! Hahaha!", "See, I told you I'm good at this.", "Drinks on me! Wait, they're free.")),
        Triple(only(Archetype.DRUNK), Talk.BUST, listOf("Hahaha, too many! Again!", "Oops. Was that a lot?", "Who counts that high anyway?")),
        Triple(only(Archetype.DRUNK), Talk.IDLE, listOf("Is this the craps table? Doesn't matter!", "I love you guys. I really do.", "Somebody get me another one of these pink ones.", "Have we met? You look like my cousin.")),
        // Cautious older players
        Triple(only(Archetype.CAUTIOUS), Talk.WIN, listOf("Oh, how lovely.", "Well, isn't that nice.", "I'll take it, thank you.")),
        Triple(only(Archetype.CAUTIOUS), Talk.LOSE, listOf("Oh well, dear.", "Can't win them all.", "That's alright.")),
        Triple(only(Archetype.CAUTIOUS), Talk.BUST, listOf("Oh dear, too many.", "I should have known better.")),
        Triple(only(Archetype.CAUTIOUS), Talk.IDLE, listOf("My grandson set up my phone. I still can't use it.", "We came on the senior bus. Lovely driver.", "In my day we dressed up to come here.", "I never bet more than I brought. That's the rule.")),
        Triple(only(Archetype.CAUTIOUS), Talk.BLACKJACK, listOf("Oh my! Blackjack!", "Well, would you look at that.")),
        // First timer
        Triple(only(Archetype.ROOKIE), Talk.WIN, listOf("Wait, I won? Sick!", "Is this beginner's luck?", "Okay, I could get used to this.")),
        Triple(only(Archetype.ROOKIE), Talk.LOSE, listOf("Oh. I lost. Okay.", "How did they get 20 again?", "Was I supposed to do something different?")),
        Triple(only(Archetype.ROOKIE), Talk.BUST, listOf("Oh no, that's over 21, isn't it.", "I didn't know a king was ten!", "Bust? Like, I lose?")),
        Triple(only(Archetype.ROOKIE), Talk.BLACKJACK, listOf("Wait, is that good? That's good, right?", "Blackjack! Like the game!")),
        Triple(only(Archetype.ROOKIE), Talk.IDLE, listOf("Do I tip the dealer? How much?", "What does soft 17 mean?", "My friends are at the club. I wanted to try this.", "Do I tap the table to hit? Like this?")),
        // Hunch player
        Triple(only(Archetype.HUNCH), Talk.WIN, listOf("I knew it. I just knew it.", "The universe provides.", "My crystal never lies.")),
        Triple(only(Archetype.HUNCH), Talk.LOSE, listOf("The energy was off that hand.", "Someone sat down and broke the flow.", "I should have worn my lucky socks.")),
        Triple(only(Archetype.HUNCH), Talk.IDLE, listOf("My horoscope said seven is my number tonight.", "Don't touch my chips, they're aligned.", "I can feel a blackjack coming. Any second.", "Red cards are lucky on Saturdays.")),
        // Chases losses
        Triple(only(Archetype.TILTER), Talk.WIN, listOf("Here it comes. Here comes the comeback.", "That's one back. Keep 'em coming.", "Finally!")),
        Triple(only(Archetype.TILTER), Talk.LOSE, listOf("You've got to be kidding me.", "Unbelievable. Again?", "Okay, now I'm angry.")),
        Triple(only(Archetype.TILTER), Talk.BIG_BET, listOf("All of it. I'm winning it back.", "Double it. I can feel it.", "Watch this. Big one.")),
        Triple(only(Archetype.TILTER), Talk.IDLE, listOf("I was up two grand an hour ago. Two grand!", "This is the hand. I can feel it.", "Don't tell my brother I'm here.")),

        // Shared lines for everyone
        Triple(G, Talk.WIN, listOf("Winner winner!", "Yes!", "Nice!", "I'll take that.", "Come to papa.")),
        Triple(G, Talk.BIG_WIN, listOf("Now that's a hand!", "Did you see that? Big money!", "I'm framing this one.")),
        Triple(G, Talk.LOSE, listOf("Next one.", "Ugh.", "Close one.", "Come on.")),
        Triple(G, Talk.BUST, listOf("Too many.", "Ugh, bust.", "Should've stayed.", "Why do I always hit that?")),
        Triple(G, Talk.BLACKJACK, listOf("Blackjack, baby!", "Pay me!", "Twenty one!")),
        Triple(G, Talk.PUSH, listOf("Push. I'll take it.", "A tie? Fine.", "Nobody wins, nobody cries.")),
        Triple(G, Talk.DEALER_BUST, listOf("Dealer busts! Everybody wins!", "Bust, bust, bust!", "Ha! Pay the table!", "There's the bust we needed!")),
        Triple(G, Talk.YOU_BLACKJACK, listOf("Nice blackjack!", "Look at you!", "Share some of that luck!", "The new player's hot!")),
        Triple(G, Talk.YOU_BIG_WIN, listOf("Big hand, friend!", "Whoa, nice one!", "You're on fire!")),
        Triple(G, Talk.WIN_STREAK, listOf("I'm on fire tonight!", "Don't anybody move. Nobody touch anything.", "Three in a row!")),
        Triple(G, Talk.LOSS_STREAK, listOf("This shoe is killing me.", "When does it turn?", "I haven't won since I sat down.")),
        Triple(G, Talk.BIG_BET, listOf("Going big this time.", "Let's see what happens.")),
        Triple(G, Talk.REBUY, listOf("Okay, okay, just a little more.", "One more buy-in. Last one.", "Hold my seat, ATM run!", "Change this, please. All of it.")),
        Triple(G, Talk.IDLE, listOf("How's everybody doing tonight?", "Is it hot in here or is it me?", "Anybody know what time it is? No clocks in here.")),
        Triple(G, Talk.REPLY, listOf("Tell me about it.", "Ha! Same.", "You and me both.", "Right?", "Couldn't agree more.")),
        Triple(G, Talk.BUSTED_OUT, listOf("I'm tapped out.", "That's all of it. Good luck, everybody.", "Well, that went fast.", "And that's my night.")),
        Triple(G, Talk.WIN_GOAL, listOf("Quit while you're ahead, right? Color me up!", "That's a good night. I'm out.", "Cashing out before it turns.")),
    )

    private val PIT = mapOf(
        "watch" to listOf("How's everybody doing tonight?", "Keeping an eye on this one.", "Nice run you're on.", "Mind if I watch a few hands?", "Big bets tonight, huh?"),
        "flat" to listOf("From now on you can flat bet, friend. Same bet every hand.", "Do me a favor. Pick a bet and stick with it.", "We'd like you to keep your bets the same."),
        "shuffle" to listOf("Dealer, shuffle up whenever this player raises.", "Shuffle that, please.", "Let's get a fresh shoe going."),
        "leave" to listOf("Sir, you're welcome to play any other game. Not blackjack.", "We're going to have to ask you to stop playing blackjack tonight.", "Your action's too good for us. Cash out, please."),
        "comp" to listOf("Dinner's on us tonight.", "Can I get you anything? Room upgrade?", "Show front row seats, on the house.", "Whatever you're drinking, it's on us."),
        "leaving" to listOf("Thanks, everyone. Carry on.", "Enjoy your night, folks."),
    )

    private val recent = ArrayDeque<String>()
    private const val MEMORY = 60

    private fun choose(lines: List<String>, rnd: Random): String {
        val fresh = lines.filter { it !in recent }
        val l = if (fresh.isNotEmpty()) fresh else lines
        val pick = l[rnd.nextInt(l.size)]
        recent.addLast(pick)
        while (recent.size > MEMORY) recent.removeFirst()
        return pick
    }

    /** What someone of archetype [a] says at [t], or null when they'd stay quiet (pros say little). */
    fun say(a: Archetype, t: Talk, rnd: Random = Random.Default): String? {
        val own = PLAYER.filter { a in it.first && it.first != G && it.second == t }.flatMap { it.third }
        val quiet = a == Archetype.PRO || a == Archetype.COUNTER
        if (own.isEmpty() && quiet && t != Talk.BUSTED_OUT && t != Talk.WIN_GOAL && t != Talk.REBUY) return null
        val shared = PLAYER.filter { it.first == G && it.second == t }.flatMap { it.third }
        // Mostly personal lines, sometimes the shared ones, for variety.
        val pool = if (own.isNotEmpty() && (shared.isEmpty() || rnd.nextInt(100) < 75)) own else shared
        if (pool.isEmpty()) return null
        return choose(pool, rnd)
    }

    fun pit(kind: String, rnd: Random = Random.Default): String = choose(PIT.getValue(kind), rnd)

    fun dealer(lines: List<String>, rnd: Random = Random.Default): String = choose(lines, rnd)

    /** Every distinct line, for counting and for checking punctuation. */
    fun allLines(): List<String> =
        (PLAYER.flatMap { it.third } + MoreTalk.allLines() + PIT.values.flatten() +
            Cast.ALL.flatMap { it.hello + it.bye } +
            CasualDealers.ALL.flatMap { it.greet + it.chatter + it.onBust + it.onBlackjack + it.onPlayerBlackjack + it.shiftOut }).distinct()

    fun resetMemory() = recent.clear()
}
