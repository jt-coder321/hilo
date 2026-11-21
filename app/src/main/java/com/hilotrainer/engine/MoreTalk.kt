package com.hilotrainer.engine

import kotlin.random.Random

/**
 * More table talk, each line tied to a moment: what the dealer shows, a new player sitting down, a shuffle,
 * a bust, a tip, a dealer checking for blackjack. Everyone has their own voice. See [TableTalk] for how lines are picked.
 */
object MoreTalk {
    private object A {
        val TOURIST = Archetype.TOURIST; val PARTY = Archetype.PARTY; val HIGH_ROLLER = Archetype.HIGH_ROLLER
        val PRO = Archetype.PRO; val COUNTER = Archetype.COUNTER; val REGULAR = Archetype.REGULAR
        val DRUNK = Archetype.DRUNK; val CAUTIOUS = Archetype.CAUTIOUS; val ROOKIE = Archetype.ROOKIE
        val HUNCH = Archetype.HUNCH; val TILTER = Archetype.TILTER
    }
    private val ALL = Archetype.entries.toSet()
    private val out = mutableListOf<Triple<Set<Archetype>, Talk, List<String>>>()

    private fun own(t: Talk, a: Archetype, vararg lines: String) { out += Triple(setOf(a), t, lines.toList()) }
    private fun shared(t: Talk, vararg lines: String) { out += Triple(ALL, t, lines.toList()) }

    /** Everything a player might say, merged into [TableTalk]. */
    val player: List<Triple<Set<Archetype>, Talk, List<String>>> by lazy { build(); extraShared(); out.toList() }

    private var built = false
    private fun build() {
        if (built) return
        built = true

        // The dealer shows a weak card (4, 5 or 6).
        own(Talk.DEALER_WEAK, A.TOURIST, "Six showing? That's good for us, right?", "Is a five bad for the dealer? I think it is.")
        own(Talk.DEALER_WEAK, A.PARTY, "Dealer's got a little card! Bust, bust, bust!", "Come on, little card, be a bad card!")
        own(Talk.DEALER_WEAK, A.HIGH_ROLLER, "Weak card. Time to press.", "I like that upcard. Let's go bigger next time.")
        own(Talk.DEALER_WEAK, A.PRO, "Dealer's weak.", "Good upcard for us.")
        own(Talk.DEALER_WEAK, A.COUNTER, "Nice upcard.", "That's what I want to see.")
        own(Talk.DEALER_WEAK, A.REGULAR, "Look at that little five! Lou's gonna eat tonight!", "Dealer's showing a baby card! Here we go!")
        own(Talk.DEALER_WEAK, A.DRUNK, "Hey, the dealer has a small one. Like me! Hahaha.", "Small card! Small card! Hahaha.")
        own(Talk.DEALER_WEAK, A.CAUTIOUS, "Oh, that's a nice weak card for the dealer, isn't it?", "A four. That's good for us, dear.")
        own(Talk.DEALER_WEAK, A.ROOKIE, "Wait, a six is bad for the dealer, right? I read that.", "Is that good, a five? I think it's good.")
        own(Talk.DEALER_WEAK, A.HUNCH, "I told you. A six. The universe is aligned.", "That four is a sign. A good sign.")
        own(Talk.DEALER_WEAK, A.TILTER, "Finally, a weak card. Don't mess this up.", "Dealer's weak. This is my hand. I can feel it.")

        // The dealer shows a ten or an ace.
        own(Talk.DEALER_STRONG, A.TOURIST, "A ten showing. That's bad, isn't it?", "Ace! Is that the best card? Uh oh.")
        own(Talk.DEALER_STRONG, A.PARTY, "Oh no, the dealer has a face card! Nobody panic!", "An ace? Rude.")
        own(Talk.DEALER_STRONG, A.HIGH_ROLLER, "Ten up. Fine. I've seen worse.", "Ace showing. Careful, folks.")
        own(Talk.DEALER_STRONG, A.PRO, "Ten up. Play it by the book.", "Ace. Here comes the peek.")
        own(Talk.DEALER_STRONG, A.COUNTER, "Ten up. Not great.", "Ace showing, as usual.")
        own(Talk.DEALER_STRONG, A.REGULAR, "Ace! Every time Lou bets big, there's an ace!", "Of course. A ten. Of course!")
        own(Talk.DEALER_STRONG, A.DRUNK, "A ten? Is ten good? Hahaha.", "Ace is one or eleven! Math is hard!")
        own(Talk.DEALER_STRONG, A.CAUTIOUS, "Oh dear, a ten. That's a strong card.", "An ace. I'll stay put, I think.")
        own(Talk.DEALER_STRONG, A.ROOKIE, "Ten! The dealer's good now, right?", "Why does everyone groan when there's an ace?")
        own(Talk.DEALER_STRONG, A.HUNCH, "I felt that ace coming. I just didn't say.", "Ten up. The vibe just changed.")
        own(Talk.DEALER_STRONG, A.TILTER, "Of course it's a ten. Of course.", "Ace? Seriously? Seriously?")

        // Someone sits down in the middle of a shoe.
        own(Talk.NEW_PLAYER, A.TOURIST, "Hi there! Take a seat, there's plenty of room!", "Oh, a new friend! Welcome!")
        own(Talk.NEW_PLAYER, A.PARTY, "Yay, party of one! Welcome!", "New player! Do you want a drink? I'll find a waitress.")
        own(Talk.NEW_PLAYER, A.HIGH_ROLLER, "Pull up a chair. Bring chips.", "Welcome. Try not to jinx the shoe.")
        own(Talk.NEW_PLAYER, A.PRO, "Welcome.", "Evening.")
        own(Talk.NEW_PLAYER, A.COUNTER, "Hello.", "Welcome to the table.")
        own(Talk.NEW_PLAYER, A.REGULAR, "Hey, new blood! Sit, sit! Lou doesn't bite.", "Welcome to the best table in the house!")
        own(Talk.NEW_PLAYER, A.DRUNK, "Heyyy, new person! I like your shirt!", "Come sit by me! I'm very friendly!")
        own(Talk.NEW_PLAYER, A.CAUTIOUS, "Welcome, dear. Have a seat.", "Oh, a new face. How nice.")
        own(Talk.NEW_PLAYER, A.ROOKIE, "Hi! Oh, another beginner, I hope?", "Welcome! I'm new too. Sort of.")
        own(Talk.NEW_PLAYER, A.HUNCH, "Sitting down mid shoe? The cards will feel that.", "New energy at the table. Interesting.")
        own(Talk.NEW_PLAYER, A.TILTER, "Great, now the shoe's going to change.", "Oh sure, sit down. Mess up the cards.")

        // The dealer shuffles.
        own(Talk.SHUFFLE, A.TOURIST, "Why are we shuffling? Did I do something?")
        own(Talk.SHUFFLE, A.PARTY, "Shuffle! Shuffle! Is that a dance move?")
        own(Talk.SHUFFLE, A.HIGH_ROLLER, "Fresh cards. Fresh start.")
        own(Talk.SHUFFLE, A.PRO, "Shoe's done.")
        own(Talk.SHUFFLE, A.COUNTER, "Well, there goes that shoe.")
        own(Talk.SHUFFLE, A.REGULAR, "Shuffle? We were on a roll!")
        own(Talk.SHUFFLE, A.DRUNK, "Hey, I was winning! Wait, was I?")
        own(Talk.SHUFFLE, A.CAUTIOUS, "A new shuffle. I always like watching this.")
        own(Talk.SHUFFLE, A.ROOKIE, "Is the shuffle because of me? Sorry!")
        own(Talk.SHUFFLE, A.HUNCH, "A shuffle resets the energy. I needed that.")
        own(Talk.SHUFFLE, A.TILTER, "Shuffle? Good, the last shoe was garbage.")
        shared(Talk.SHUFFLE, "Shuffle up!", "Here we go again.", "Fresh shoe, fresh luck.", "Time for a stretch.", "New cards, new luck.")

        // You bust.
        shared(Talk.YOU_BUST, "Ooh, tough break.", "Sorry, friend, that was rough.", "Happens to the best of us.", "Shake it off, you'll get the next one.", "That's a heartbreaker.", "I thought you had that one.")
        own(Talk.YOU_BUST, A.TOURIST, "Aw, don't feel bad. I do it all the time.")
        own(Talk.YOU_BUST, A.PARTY, "Noooo, hugs! Virtual hugs!")
        own(Talk.YOU_BUST, A.HIGH_ROLLER, "Variance, my friend. Reload.")
        own(Talk.YOU_BUST, A.PRO, "Unlucky.")
        own(Talk.YOU_BUST, A.COUNTER, "Tough one.")
        own(Talk.YOU_BUST, A.REGULAR, "That's the game, pal. The dealer giveth and taketh.")
        own(Talk.YOU_BUST, A.DRUNK, "Hahaha, whoops! I mean, oh no!")
        own(Talk.YOU_BUST, A.CAUTIOUS, "Oh, I'm sorry, dear. That was unlucky.")
        own(Talk.YOU_BUST, A.ROOKIE, "Is that a bust? Is that bad? Oh no, it's bad.")
        own(Talk.YOU_BUST, A.HUNCH, "The cards tested you. You'll pass.")
        own(Talk.YOU_BUST, A.TILTER, "Welcome to my night, friend.")

        // You lose a big hand.
        shared(Talk.YOU_LOSE_BIG, "Ouch. That's a big one to lose.", "Easy now. The next shoe is a new shoe.", "Take a breath. It's only cards.", "That one stung just watching.", "Walk it off if you need to.", "Stay in the game, it'll come back.")

        // The dealer checks the hole card for blackjack.
        own(Talk.PEEK, A.TOURIST, "Why is the dealer peeking? Is that allowed?")
        own(Talk.PEEK, A.PARTY, "Please no blackjack, please no blackjack.")
        own(Talk.PEEK, A.HIGH_ROLLER, "Check it. I've got nothing to hide.")
        own(Talk.PEEK, A.PRO, "Standard peek.")
        own(Talk.PEEK, A.COUNTER, "Here we go.")
        own(Talk.PEEK, A.REGULAR, "Come on, no blackjack, no blackjack!")
        own(Talk.PEEK, A.DRUNK, "What's the dealer looking at? Hahaha.")
        own(Talk.PEEK, A.CAUTIOUS, "I always hold my breath for this part.")
        own(Talk.PEEK, A.ROOKIE, "What's he checking for?")
        own(Talk.PEEK, A.HUNCH, "Don't look at it. Looking makes it real.")
        own(Talk.PEEK, A.TILTER, "If that's blackjack I'm flipping this table.")
        shared(Talk.PEEK, "Fingers crossed.", "Nobody breathe.", "Here comes the moment of truth.")

        // Someone doubles down.
        own(Talk.DOUBLE, A.TOURIST, "Doubling! I think I know what I'm doing.")
        own(Talk.DOUBLE, A.PARTY, "Double it! Live a little!")
        own(Talk.DOUBLE, A.HIGH_ROLLER, "Double it. Obviously.")
        own(Talk.DOUBLE, A.PRO, "Double.")
        own(Talk.DOUBLE, A.COUNTER, "Doubling.")
        own(Talk.DOUBLE, A.REGULAR, "Double down! Lou's feeling it!")
        own(Talk.DOUBLE, A.DRUNK, "Doubling! That's the one where you put more chips! Hahaha.")
        own(Talk.DOUBLE, A.CAUTIOUS, "I'll double. Just this once, dear.")
        own(Talk.DOUBLE, A.ROOKIE, "Wait, I can double? Okay. Doing it.")
        own(Talk.DOUBLE, A.HUNCH, "I feel a ten coming. Double.")
        own(Talk.DOUBLE, A.TILTER, "Double. Get it all back right now.")
        shared(Talk.DOUBLE, "Doubling down.", "Let's double it.", "I like this spot, doubling.", "Double it up.")

        // A tip for the dealer, called a toke.
        own(Talk.TIP, A.HIGH_ROLLER, "This one's for the dealer.", "Take a little something for yourself, dealer.", "For the house. I mean for you.")
        own(Talk.TIP, A.PARTY, "Tip for the dealer! You're awesome!", "A toke for the lovely dealer.", "Here's something for you, dealer! Buy yourself a drink!")
        own(Talk.TIP, A.TOURIST, "Is it okay to tip? Here, this is for you.", "Thank you for being so nice, dealer.", "A little extra for you, dealer.")
        own(Talk.TIP, A.DRUNK, "Hey dealer, you're my best friend. Take a chip.", "Take two chips. I don't need them.", "This one's for you, dealer! Don't tell anyone.")
        own(Talk.TIP, A.REGULAR, "Tip for the dealer, always.", "Taking care of my dealer, like every night.", "Here you go, kid. Toke for you.")

        // A dealer's reminder about table etiquette, and the guilty party's reply.
        own(Talk.ETIQUETTE_REPLY, A.TOURIST, "Oh, sorry! I didn't know.", "Whoops, my mistake.")
        own(Talk.ETIQUETTE_REPLY, A.ROOKIE, "Sorry, I'm still learning the rules.", "Oh no, did I break a rule?")
        own(Talk.ETIQUETTE_REPLY, A.DRUNK, "Hahaha, sorry, sorry, my hands have minds of their own.", "Oops! Won't happen again. Probably.")
        own(Talk.ETIQUETTE_REPLY, A.PARTY, "Sorry! Total beginner moment.", "Oops, my bad! Don't kick me out!")
        own(Talk.ETIQUETTE_REPLY, A.HUNCH, "I was just clearing the bad energy.", "Sorry, I thought the chips needed straightening.")

        // More idle talk, for the long stretches between hands.
        own(Talk.IDLE, A.TOURIST, "This casino has a whole indoor sky. How do they do that?", "We're going to see a show tomorrow. Anyone seen it?", "I thought it'd be louder. It's actually kind of calming.", "Is there a coupon for the buffet? My wife said there was.", "The carpet in here is wild. I love it.")
        own(Talk.IDLE, A.PARTY, "I need a playlist for tonight. Any suggestions?", "My sister's going to flip when I show her my winnings.", "Is there a pool party anywhere? Asking for the bridal party.", "Do they have a dance floor in here somewhere?", "I lost a heel somewhere in this building. Anyone seen it?")
        own(Talk.IDLE, A.HIGH_ROLLER, "I flew in for this. Make it count.", "I'll take a table in the back next time. This is fine.", "I used to play for the cameras. Now I play for me.", "Tell the kitchen I want the steak medium. Not well, medium.", "Someone told me this was the lucky table. I like it.")
        own(Talk.IDLE, A.PRO, "Bankroll management, that's the whole game.", "Tired. Long travel day.", "Never chase. Never.", "Good table. Quiet.", "I like a shoe that's deep.")
        own(Talk.IDLE, A.COUNTER, "How's the shoe looking, dealer? Plenty of cards left?", "I like the dealer here. Smooth hands.", "I could do this all night.", "Anybody else here for the weekend?", "Slow night, but I'm in no rush.")
        own(Talk.IDLE, A.REGULAR, "I'll have what Dot's having. Wait, what is she having?", "Fifteen years at this table. Fifteen!", "The coffee here used to be better.", "I tipped the valet a twenty and he still parked me by the dumpsters.", "Anybody remember the old sign? The neon one?")
        own(Talk.IDLE, A.DRUNK, "Did somebody say cake? I heard cake.", "I should call someone. Who should I call? Hahaha.", "These chips are so pretty. Look at the colors.", "I think I lost my shoe. No wait, it's here.", "Everyone is so nice here. I love this table.")
        own(Talk.IDLE, A.CAUTIOUS, "My knees don't like these stools, but the company's good.", "I bring a cardigan everywhere. These air conditioners!", "I'll have a ginger ale if the girl comes by.", "We play bingo on Thursdays. This is a treat.", "I never learned to count cards. Too many numbers.")
        own(Talk.IDLE, A.ROOKIE, "Is there a rule about phones at the table?", "I watched a video about this game. It's harder in person.", "What does the dealer say when the cards are done? Pays?", "How many decks is this, anyway?", "Be gentle with me, I'm still figuring this out.")
        own(Talk.IDLE, A.HUNCH, "I wore blue today. Blue is lucky for me.", "I feel a shift. Don't you feel a shift?", "I always sit in the third seat. Always.", "Cards have a mood. This shoe's in a good one.", "I saw a rainbow on the way in. That's a sign.")
        own(Talk.IDLE, A.TILTER, "I'm not chasing, I'm just catching up.", "Two hundred more and I'm even. Just two hundred.", "This is the last shoe. I mean it.", "Don't look at me like that. I know what I'm doing.", "Why does the dealer always get twenty?")

        // More wins, losses and busts in everyone's own voice.
        own(Talk.WIN, A.TOURIST, "Ha! I'm paying for the whole trip tonight!", "My wife is not going to believe this.")
        own(Talk.WIN, A.PARTY, "Cha ching! Drinks for the bridal party!", "I'm doing a happy dance in my head.")
        own(Talk.WIN, A.HIGH_ROLLER, "Pay the lady.", "Good. Keep them coming.")
        own(Talk.WIN, A.PRO, "Fine.", "Good hand.")
        own(Talk.WIN, A.COUNTER, "Good.", "Right on schedule.")
        own(Talk.WIN, A.REGULAR, "Hot dog! That's a payday!", "Ha ha, Lou's got the touch tonight!")
        own(Talk.WIN, A.DRUNK, "I won again? I love this game!", "Everybody get a chip! No, wait, they're mine.")
        own(Talk.WIN, A.CAUTIOUS, "Why, thank you very much.", "That's a lovely surprise.")
        own(Talk.WIN, A.ROOKIE, "Okay, I think I get it now!", "Wow, this is so much fun!")
        own(Talk.WIN, A.HUNCH, "The numbers are lining up for me tonight.", "I felt it. Always trust the feeling.")
        own(Talk.WIN, A.TILTER, "Yes! Back in the game.", "That's what I needed. More of that.")
        own(Talk.LOSE, A.TOURIST, "Aw, well, at least it's pretty in here.", "Okay, I'll try again.")
        own(Talk.LOSE, A.PARTY, "Boo! That's not fair!", "Okay, next hand I'm winning, I promise.")
        own(Talk.LOSE, A.HIGH_ROLLER, "Meh. Deal.", "That's what they pay me for. Well, what I pay them for.")
        own(Talk.LOSE, A.PRO, "Move on.", "Part of the math.")
        own(Talk.LOSE, A.COUNTER, "It happens.", "Okay.")
        own(Talk.LOSE, A.REGULAR, "Ah, come on! That's highway robbery!", "This dealer owes me an apology.")
        own(Talk.LOSE, A.DRUNK, "Whoops, there go my chips! Bye, chips!", "Hahaha, that one got away from me.")
        own(Talk.LOSE, A.CAUTIOUS, "That's a shame, but I'll manage.", "Oh my, that wasn't what I hoped for.")
        own(Talk.LOSE, A.ROOKIE, "Okay, I'm learning. I'm learning.", "Hmm. I'll try standing next time.")
        own(Talk.LOSE, A.HUNCH, "Interesting. The cards are testing me.", "That's the universe asking me to be patient.")
        own(Talk.LOSE, A.TILTER, "Seriously? Every time!", "That's it, I'm getting it back.")
        own(Talk.BUST, A.TOURIST, "I just keep taking cards, don't I?")
        own(Talk.BUST, A.PARTY, "Bust! Okay, I'm too greedy.")
        own(Talk.BUST, A.HIGH_ROLLER, "Over. Fine.")
        own(Talk.BUST, A.PRO, "Bust.")
        own(Talk.BUST, A.COUNTER, "Too many.")
        own(Talk.BUST, A.REGULAR, "Bust! The shoe hates Lou!")
        own(Talk.BUST, A.DRUNK, "Bust? Is that like a bust of Elvis? Hahaha.")
        own(Talk.BUST, A.CAUTIOUS, "Oh my. I should have stood.")
        own(Talk.BUST, A.ROOKIE, "Wait, I should have stopped at twelve?")
        own(Talk.BUST, A.HUNCH, "Bust. I misread the signs.")
        own(Talk.BUST, A.TILTER, "Bust! Great, just great.")
        own(Talk.BIG_WIN, A.TOURIST, "Is this real money? This is real money!", "I'm going to buy everyone dinner!")
        own(Talk.BIG_WIN, A.PARTY, "Cha ching! Rounds on me! Okay, one round.", "I'm screaming inside. And outside!")
        own(Talk.BIG_WIN, A.HIGH_ROLLER, "Now we're talking real numbers.", "Color me up soon, I like how this feels.")
        own(Talk.BIG_WIN, A.PRO, "Good. Don't get greedy.", "Let's keep it steady.")
        own(Talk.BIG_WIN, A.COUNTER, "Nice. Quiet and clean.", "That'll do.")
        own(Talk.BIG_WIN, A.REGULAR, "Somebody get Lou a cigar! Wait, no smoking.", "Eat your heart out, boys!")
        own(Talk.BIG_WIN, A.DRUNK, "I'm rich! I'm rich! Everybody, I'm rich!", "Is this a dream? Pinch me. Not too hard.")
        own(Talk.BIG_WIN, A.CAUTIOUS, "Heavens, that's more than my bingo winnings.", "Well, I never!")
        own(Talk.BIG_WIN, A.ROOKIE, "Is that how much I get?! Wow.", "I'm going to frame this chip.")
        own(Talk.BIG_WIN, A.HUNCH, "I knew it. The stars aligned.", "That's the power of positive energy.")
        own(Talk.BIG_WIN, A.TILTER, "That's more like it! Time to press.", "Now we're recovering.")
        own(Talk.BIG_BET, A.TOURIST, "Let's go for a big one, why not?")
        own(Talk.BIG_BET, A.PARTY, "Big bet! Everyone cheer!")
        own(Talk.BIG_BET, A.HIGH_ROLLER, "Make it a statement.")
        own(Talk.BIG_BET, A.PRO, "That's within my limits.")
        own(Talk.BIG_BET, A.COUNTER, "Bump it a little.")
        own(Talk.BIG_BET, A.REGULAR, "Lou's going big! Watch this!")
        own(Talk.BIG_BET, A.DRUNK, "Put it ALL on! Wait, how much is that?")
        own(Talk.BIG_BET, A.CAUTIOUS, "A little more than usual. Don't tell my son.")
        own(Talk.BIG_BET, A.ROOKIE, "Is it okay to bet this much?")
        own(Talk.BIG_BET, A.HUNCH, "My gut says go big.")
        own(Talk.BIG_BET, A.TILTER, "Big one. Last chance.")
        own(Talk.REBUY, A.TOURIST, "One more buy-in. The vacation fund can take it.")
        own(Talk.REBUY, A.PARTY, "Round two! More chips, please!")
        own(Talk.REBUY, A.HIGH_ROLLER, "Another stack. Quick.")
        own(Talk.REBUY, A.PRO, "Reloading. Sticking to the plan.")
        own(Talk.REBUY, A.COUNTER, "Another buy-in.")
        own(Talk.REBUY, A.REGULAR, "Lou needs reinforcements!")
        own(Talk.REBUY, A.DRUNK, "More chips! These ones disappeared!")
        own(Talk.REBUY, A.CAUTIOUS, "Just one more small buy-in, dear.")
        own(Talk.REBUY, A.ROOKIE, "Do I just hand over cash? Here you go.")
        own(Talk.REBUY, A.HUNCH, "The omens say reload.")
        own(Talk.REBUY, A.TILTER, "Give me more. I'm not done.")
    }

    private fun extraShared() {
        shared(Talk.SHUFFLE, "Fresh cards, fresh luck.", "New shoe, new me.", "Good, the old ones were cursed.", "I was just getting warm.")
        shared(Talk.YOU_BUST, "Ouch. That one hurt to watch.", "Happens to everyone. Even the pros.", "Don't worry, the next one's yours.", "I felt that from here.")
        shared(Talk.YOU_LOSE_BIG, "Rough hand. Shake it off.", "You'll win it back. Stay calm.", "That's why they call it gambling.", "Take a breath. It's only chips.")
        shared(Talk.DEALER_WEAK, "Six! Everybody's happy!", "The dealer looks shaky.", "Little card showing. Good sign.", "This is our hand, folks.")
        shared(Talk.DEALER_STRONG, "Ten up. Here we go.", "Not that card again.", "The dealer always has a ten.", "Brace yourselves, folks.")
        shared(Talk.NEW_PLAYER, "Plenty of room, come on in.", "Pull up a chair, friend.", "Fresh blood. I like it.", "Hope you brought luck with you.")
        shared(Talk.DOUBLE, "Double! That takes nerve.", "Look at that, a bold one.", "Brave. I'd have just hit.", "Go on, make it count.")
        shared(Talk.PEEK, "Here we go, the moment of truth.", "Don't be a blackjack, don't be a blackjack.", "Hold your breath, everybody.", "Every time, my heart skips.")
    }

    /** Dealer lines for procedure moments, in each dealer's own voice. */
    private val DEALER: Map<String, Map<String, List<String>>> = mapOf(
        "Vera" to mapOf(
            "peek" to listOf("Checking for blackjack.", "One moment, I'll check my hole card.", "Let me take a peek."),
            "cut" to listOf("Cut, please.", "Would anyone like to cut?", "Cut card, please."),
            "burn" to listOf("Burning a card.", "Burn one."),
            "ins" to listOf("Insurance is open.", "Insurance, anyone? It pays two to one."),
            "nobj" to listOf("No blackjack. Play continues.", "No blackjack. Decisions, please."),
            "welcome" to listOf("Welcome. You'll be in on the next hand.", "Good evening. Please make your bets.", "Welcome to the table."),
            "thanks" to listOf("Thank you very much.", "Much appreciated, sir.", "Thank you, that's kind."),
            "etiquette" to listOf("Please keep your hands off the cards, sir.", "Chips on the table only, please.", "Sir, no touching the bets once the cards are out.", "Please keep drinks off the felt."),
            "double" to listOf("Double down. Matching bet, please."),
            "split" to listOf("Splitting. Place a matching bet."),
        ),
        "Danny" to mapOf(
            "peek" to listOf("Let me peek. Fingers crossed, everybody.", "Little look under there, folks.", "Checking for the big one."),
            "cut" to listOf("Cut it up, boss.", "Who wants to cut? It's a big honor.", "Cut card coming your way, boss."),
            "burn" to listOf("Burning one for luck.", "One for the trash."),
            "ins" to listOf("Insurance, anybody? Dealer's showing an ace.", "Insurance is open, my friends."),
            "nobj" to listOf("No blackjack. Good news, folks.", "Nope, no blackjack. Carry on."),
            "welcome" to listOf("New friend! Welcome in, you'll play next hand.", "Pull up a stool, buddy, cards next round.", "Hey, welcome! Jump in whenever."),
            "thanks" to listOf("Thank you! You're the best.", "Ooh, thank you! Dinner's on you.", "Appreciate you, friend."),
            "etiquette" to listOf("Hands off the chips, friend, I've got them.", "Cards stay on the table, buddy, and so do your hands.", "Easy there, no touching the cards.", "Keep the drink off the felt, my friend."),
            "double" to listOf("Double down! Match it up, boss."),
            "split" to listOf("A split! Another bet, please."),
        ),
        "Mr. Grant" to mapOf(
            "peek" to listOf("Checking.", "Peek.", "Checking for blackjack."),
            "cut" to listOf("Cut.", "Cut, please.", "Place the cut card."),
            "burn" to listOf("Burn.", "Burning one."),
            "ins" to listOf("Insurance.", "Insurance is open."),
            "nobj" to listOf("No blackjack.", "No blackjack. Proceed."),
            "welcome" to listOf("Welcome. Next hand.", "You're in next hand.", "Welcome. Bets, please."),
            "thanks" to listOf("Thank you.", "Thank you, sir. Good luck.", "Appreciated."),
            "etiquette" to listOf("No touching the cards.", "Keep your hands off the bets.", "Drinks go on the rail, not the table.", "Please stay seated for the hand."),
            "double" to listOf("Double. Match the bet."),
            "split" to listOf("Split. Match the bet."),
        ),
    )

    /** What the named dealer says at [kind] ("peek", "cut", "burn", "ins", "nobj", "welcome", "thanks", "etiquette", "double", "split"). */
    fun dealerSays(name: String, kind: String, rnd: Random = Random.Default): String {
        val lines = DEALER[name]?.get(kind) ?: DEALER.getValue("Vera").getValue(kind)
        return TableTalk.dealer(lines, rnd)
    }

    /** Players who toke the dealer after a big win. */
    val tippers = setOf(Archetype.HIGH_ROLLER, Archetype.PARTY, Archetype.TOURIST, Archetype.DRUNK, Archetype.REGULAR)
    /** Players who sometimes forget table manners. */
    val forgetful = setOf(Archetype.TOURIST, Archetype.ROOKIE, Archetype.DRUNK, Archetype.PARTY, Archetype.HUNCH)

    /** Every line in this file, for counting and punctuation checks. */
    fun allLines(): List<String> = player.flatMap { it.third } + DEALER.values.flatMap { m -> m.values.flatten() }
}
