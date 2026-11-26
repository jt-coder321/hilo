package com.hilotrainer.engine

/**
 * Lines that belong to one named person at the table, in their own voice. Each of the newer characters has
 * about forty. They are mixed with the archetype lines in [TableTalk.say], and every line here is unique to its speaker.
 */
object CharTalk {
    private val M = LinkedHashMap<String, Map<Talk, List<String>>>()

    private fun c(
        name: String, win: List<String>, bigWin: List<String>, lose: List<String>, bust: List<String>, blackjack: List<String>,
        push: List<String>, winStreak: List<String>, lossStreak: List<String>, bigBet: List<String>, idle: List<String>,
        double: List<String>, split: List<String>,
    ) {
        M[name] = mapOf(
            Talk.WIN to win, Talk.BIG_WIN to bigWin, Talk.LOSE to lose, Talk.BUST to bust, Talk.BLACKJACK to blackjack,
            Talk.PUSH to push, Talk.WIN_STREAK to winStreak, Talk.LOSS_STREAK to lossStreak, Talk.BIG_BET to bigBet,
            Talk.IDLE to idle, Talk.DOUBLE to double, Talk.SPLIT to split,
        )
    }

    init {
        // A night nurse on her break. Calm, dry, never rattled.
        c("Priya",
            win = listOf("That's the first good news since my shift started.", "I'll take it. Charting that one as a win.", "Stable and improving.", "Vitals look great on that hand."),
            bigWin = listOf("Okay, that is not in the protocol.", "Somebody check my pulse, because it's up.", "That's better than the coffee in the break room."),
            lose = listOf("Mild discomfort. I'll live.", "Noted. No treatment required.", "That one stung, but it's not fatal.", "Losing is easier than a double shift."),
            bust = listOf("Over twenty one. Classic overcorrection.", "I should have trusted my training.", "Too aggressive. Doctor's orders ignored.", "That's a complication."),
            blackjack = listOf("Textbook presentation.", "Clean, simple, perfect.", "Now that is a good diagnosis."),
            push = listOf("No change in condition.", "Holding steady."),
            winStreak = listOf("Three in a row. I'm suspicious of my own luck.", "Everything is trending up tonight.", "Keep monitoring me. It's going well."),
            lossStreak = listOf("A rough patch. It passes.", "I've had worse nights on the ward.", "Fluids and patience, everyone."),
            bigBet = listOf("A little more than my usual dose.", "Raising the dosage. Stand clear.", "I earned this bet with eight hours of paperwork."),
            idle = listOf("Forty minutes until my break ends.", "Does anyone else hear a pager that isn't there?", "I can sleep for exactly four hours after this.", "People are friendlier here than in the ER.", "My feet have opinions about this carpet."),
            double = listOf("Calculated risk. Doubling.", "I'm going to be bold for once."),
            split = listOf("Two patients, one doctor. Let's go.", "Splitting up. Divide and conquer."),
        )
        // A retired mail carrier with a dad joke for every hand.
        c("Gus",
            win = listOf("I'm delivering a win, right on time.", "Neither rain nor sleet, that's a win.", "That one came special delivery.", "I knew I'd get a package tonight."),
            bigWin = listOf("Thirty years of walking and that was easy money!", "Stamp it, seal it, cash it!", "Now we're talking return to sender, dealer!"),
            lose = listOf("Return to sender, I guess.", "That hand went to the dead letter office.", "No forwarding address for that one.", "Lost in the mail."),
            bust = listOf("I bust like a loose mailbox door.", "Too many cards. Postage due.", "That's what I get for going over the weight limit.", "Special delivery of bad news."),
            blackjack = listOf("Twenty one! First class!", "Overnight express, baby!", "Priority mail, and it's mine!"),
            push = listOf("Undeliverable, so it comes back.", "Nobody signed for that one."),
            winStreak = listOf("I'm on a route and every house has a winner.", "Delivered three in a row. Where's my tip?", "My knees hurt but my luck doesn't."),
            lossStreak = listOf("Dogs chased me for thirty years and this is worse.", "Even the mailbox is empty tonight.", "I've seen worse routes. Not many."),
            bigBet = listOf("Putting a big stamp on this one.", "Heavy package coming through.", "Going certified mail on this hand."),
            idle = listOf("What do you call a dealer with no cards? Retired.", "My wife says I tell too many jokes. She's wrong. Right, dear? She's not here.", "I carried bags heavier than this shoe.", "Anybody here from out of town? I know every road.", "A blackjack walks into a bar. Everyone says twenty one."),
            double = listOf("Double the postage, double the fun.", "Sending two of these. Watch the dealer."),
            split = listOf("Splitting the route, as they say.", "One pile for me, one for the neighbor."),
        )
        // A software engineer who treats every hand as a spreadsheet.
        c("Kenji",
            win = listOf("Expected value, realized.", "Positive outcome. Sample size still small.", "That one landed in the good tail.", "A win, within the margin of error."),
            bigWin = listOf("Three standard deviations in my favor.", "That's variance and I'm enjoying it.", "Statistically weird. Emotionally wonderful."),
            lose = listOf("Negative outcome. Correct play, though.", "That's just variance, not a bug.", "Logging that as noise.", "The math is still on my side."),
            bust = listOf("Edge case. I handled it badly.", "Exception thrown. Over twenty one.", "I got a stack overflow on that hit.", "Bad luck, correct decision. Mostly."),
            blackjack = listOf("Probability of that is about four point eight percent.", "Rare event, confirmed.", "Best possible two card hand, per spec."),
            push = listOf("Zero net change. Efficient.", "A null result, which is still a result."),
            winStreak = listOf("Streaks are meaningless, and I'm having fun anyway.", "My model says this changes nothing about the next hand.", "Still independent events. Still delighted."),
            lossStreak = listOf("Regression to the mean is going to be excellent.", "Variance is a harsh teacher.", "I refuse to believe the cards are due."),
            bigBet = listOf("Kelly criterion says go a bit larger.", "Bankroll supports this. I checked.", "Raising the bet. Please don't read into it."),
            idle = listOf("I've been refactoring my strategy chart for fun.", "Six decks means about three hundred twelve cards. People forget.", "Does the casino publish its penetration? Asking for me.", "I did this in a spreadsheet and the spreadsheet lost.", "I came here to relax. I'm doing math instead."),
            double = listOf("Basic strategy says double. I'm just following it.", "The table says double, so I double."),
            split = listOf("Splitting is plus EV here. I trust the table.", "Two hands. Two chances to be right."),
        )
        // A lounge singer, theatrical and warm.
        c("Delilah",
            win = listOf("Cue the applause, darlings!", "Encore! I'll take an encore!", "And the crowd goes wild!", "That was a pretty note, wasn't it?"),
            bigWin = listOf("Standing ovation, please!", "I could sing for a week on that one!", "Take a bow, Delilah. Take two!"),
            lose = listOf("Every singer misses a note.", "The show must go on, even after that.", "A little flat, but I'll recover.", "Tragic. Operatic, really."),
            bust = listOf("A dramatic collapse in the third act.", "Oh, the cards have betrayed me!", "Curtain down on that hand.", "I over-performed. It happens."),
            blackjack = listOf("The perfect finale, darling!", "Hit the high note!", "Twenty one, with feeling!"),
            push = listOf("An intermission. Lovely.", "No applause, no boos."),
            winStreak = listOf("I'm on a roll, a showstopping roll!", "Third song, third standing ovation.", "The spotlight likes me tonight."),
            lossStreak = listOf("Tough crowd tonight.", "Even the best divas have bad runs.", "I will not cry on the felt. Maybe a little."),
            bigBet = listOf("I'm going full chorus on this one.", "Big finish, big bet.", "Hit the lights, darlings, this one is a solo."),
            idle = listOf("I do two shows a night and my feet are done.", "Anybody want a song? I take requests, not tips. Okay, tips.", "That shoe has a lovely rhythm.", "The lighting in here is flattering, I'll give them that.", "Honey, I've sung to quieter tables than this."),
            double = listOf("A key change! Doubling!", "Double the drama, double the reward."),
            split = listOf("A duet! Splitting!", "Two voices are better than one."),
        )
        // A bouncer with a soft heart who hates losing.
        c("Bruno",
            win = listOf("That's what I'm talking about.", "Easy. Next.", "Nobody gets past me tonight.", "Good. Keep it coming."),
            bigWin = listOf("Boom! Pay the man!", "I'm buying everybody a drink. Not really.", "That's a knockout!"),
            lose = listOf("I'll get you next time, cards.", "That's fine. I've been hit harder.", "Lost that one. Won't lose the next.", "Cards are cheating. I'm kidding. Mostly."),
            bust = listOf("Aw, come on!", "I was gentle with that hit, I swear.", "Too big for my own good.", "That's my fault. I got greedy."),
            blackjack = listOf("Ha! Check my ID, that's twenty one!", "Perfect. Now that's service.", "Big man, big hand!"),
            push = listOf("A tie? Fine. Nobody wins, nobody cries.", "Stalemate. I respect it."),
            winStreak = listOf("Nobody's getting in my way tonight.", "Three wins. Door's closed.", "I'm on a streak and you're all my guests."),
            lossStreak = listOf("I don't lose this many in a row. Something's off.", "Four hands. Still standing, though.", "I'm a patient man, but come on."),
            bigBet = listOf("Big man, big bet.", "I'm putting a stack on this. Please behave, cards.", "This one's got my name on it."),
            idle = listOf("I stand at a door for a living. This is nicer.", "Anybody giving you trouble, dealer, I'm right here.", "I cried at a movie last week. Don't tell anyone.", "My mom says I should play slots. I like to look people in the eye.", "Nice table, nice people. Let's keep it that way."),
            double = listOf("Double. I can handle it.", "Doubling down, like a good bouncer."),
            split = listOf("Splitting them up. Like a bar fight.", "Two hands, same bad attitude."),
        )
        // A pub owner with a lucky charm for everything.
        c("Fiona",
            win = listOf("Ha! The clover did it again!", "That's the wee leprechaun working for me.", "Pour me a pint, I've earned it!", "God bless the dealer and his hands."),
            bigWin = listOf("Jaysus, that's a gold pot!", "I'm kissing my lucky penny for that one!", "Drinks on the house! Well, my house!"),
            lose = listOf("Ah, the penny's tired tonight.", "Och, that's a sin.", "The luck of the Irish is on holiday.", "Well, there's a poor show."),
            bust = listOf("Ach, over the line again!", "I hit and I regretted it, the way I regret most things.", "That's a curse on my hand.", "Wrong card, wrong night."),
            blackjack = listOf("Twenty one, and no one cheated!", "Would you look at that beauty!", "Top of the morning to me!"),
            push = listOf("A draw, like a game of darts.", "Neither fish nor fowl."),
            winStreak = listOf("I've the whole table blessed now!", "The charm bracelet's working overtime.", "Three wins, and I haven't even touched the whiskey."),
            lossStreak = listOf("Somebody's put a hex on me, I swear.", "Time to rub the lucky stone harder.", "My pub has never been this quiet."),
            bigBet = listOf("In for a penny, in for a pound.", "I'm throwing a wee fortune at this one.", "Lucky seven pennies in my pocket for this bet."),
            idle = listOf("I run a pub in County Clare and I've never seen a shoe this shiny.", "Anyone know a good limerick? I know twelve.", "Never trust a quiet table, I always say.", "My grandmother read tea leaves. She'd have loved this place.", "The coaster in my pocket is from my own bar. Good luck charm."),
            double = listOf("Double or nothing, as me da would say.", "Doubling. May the road rise to meet me."),
            split = listOf("Split like a good Guinness pour.", "Two hands, two chances, two blessings."),
        )
        // A taxi driver with a story about every passenger.
        c("Omar",
            win = listOf("Just like a good fare at midnight.", "That one's a tip and a half.", "I drove across town for that one.", "Smooth ride, smooth win."),
            bigWin = listOf("This pays for a month of gas!", "I once had a fare that tipped like this. This is better.", "My meter would be jealous."),
            lose = listOf("Bumpy road on that one.", "That's like getting stuck in traffic.", "A flat tire of a hand.", "No fare, no luck."),
            bust = listOf("Wrong turn. Way over.", "I missed my exit on that hit.", "I should've stayed on the highway.", "Over the limit, officer."),
            blackjack = listOf("Right on time, right on the money!", "Perfect fare, my friend!", "Twenty one! Meter's running, boys!"),
            push = listOf("Dropped off at the same corner. A push.", "No fare, no tip, no loss."),
            winStreak = listOf("I'm on the right road tonight.", "Green light, green light, green light.", "Every hand is a downhill drive."),
            lossStreak = listOf("Road construction on every street tonight.", "I've had slow nights before. This is one.", "Even my GPS says I'm lost."),
            bigBet = listOf("Putting a full tank on this hand.", "I'm taking the long route and risking it.", "High fare, high risk."),
            idle = listOf("Had a passenger last week who tipped in poems. True story.", "You learn a lot about people in a cab. Everyone's the same at three a.m.", "The drive here took forty minutes. The wait for a seat took ten.", "I know a shortcut to every casino in the city.", "Strange, driving all day and now I want to sit."),
            double = listOf("Double fare tonight.", "Doubling. Hope the road holds up."),
            split = listOf("Splitting the bill. I mean the hand.", "Two passengers, two destinations."),
        )
        // An influencer filming content at every table.
        c("Chloe",
            win = listOf("Guys, we just won! Smash that like!", "That's going straight in the highlights reel.", "Okay, that was cinematic.", "Content! That's content!"),
            bigWin = listOf("This is the thumbnail, right here!", "I can't even. Chat, we are rich!", "Sponsor me, casino! Please!"),
            lose = listOf("We're not putting that in the vlog.", "Okay, cut. We never speak of that hand.", "Ugh, that's a low engagement hand.", "I'm editing that out."),
            bust = listOf("Plot twist, I'm a disaster.", "That's the blooper reel, folks.", "Over. Delete. Retake.", "Nope. No. Not for the algorithm."),
            blackjack = listOf("Blackjack! Content gold!", "Everybody, look at my cards! Twenty one!", "That's the clip right there!"),
            push = listOf("A tie. Boring for the feed.", "No views on that one."),
            winStreak = listOf("This is going to go viral, I can feel it.", "Part two of the winning series!", "My followers are going to freak."),
            lossStreak = listOf("This episode is not getting renewed.", "I'm in my villain era, evidently.", "Please don't cancel me, cards."),
            bigBet = listOf("Big bet, big views. Let's go!", "Chat, should I do it? Yes. I'm doing it.", "I'm going all in for the plot."),
            idle = listOf("Can you move a little left? You're in my shot.", "Good lighting at this table, honestly.", "I have eleven thousand followers and zero idea what a split is.", "Hi, I'm filming a thing. You're in it now. Say hi!", "Hold on, I need a casino selfie. Dealer, you too!"),
            double = listOf("Double! This is the climax!", "Doubling for the plot twist."),
            split = listOf("Split screen! Get it?", "Two hands, two angles."),
        )
        // A retired athlete who is a terrible loser.
        c("Reggie",
            win = listOf("That's a championship hand!", "Still got it, still got it!", "Another one for the trophy case.", "Call it, ref. Winner."),
            bigWin = listOf("MVP! They're giving me MVP!", "Hall of fame hand, right there!", "I should get a parade for that one!"),
            lose = listOf("That call was terrible.", "Replay that. The dealer's hand is illegal.", "I want a review of that hand.", "Cheap shot, cards."),
            bust = listOf("That's a foul on me. Fine.", "I choked. I admit it. This once.", "Dropped the ball on that hit.", "Rookie mistake and I'm no rookie."),
            blackjack = listOf("Slam dunk!", "Hole in one! Wrong sport, same feeling!", "Touchdown, baby!"),
            push = listOf("Tie game. Overtime would be nice.", "Nobody scores. Great, now I'm bored."),
            winStreak = listOf("I'm on a heater, call it a winning season.", "Three in a row, hat trick!", "Can't be stopped, can't be guarded."),
            lossStreak = listOf("Bad season. Bad coaching. Bad dealer.", "This is a slump, nothing more.", "I've come back from worse. Way worse."),
            bigBet = listOf("Fourth quarter, I'm taking the shot.", "Big game, big bet.", "I live for the clutch. Put it on me."),
            idle = listOf("I used to play ball, in case you didn't know. Everyone knows.", "My knee hurts every time it rains and every time I lose.", "A dealer's got more composure than a ref. Compliment, dealer.", "Coaches never take the blame. I'm not changing that.", "I'm not mad. I'm just competitive. Different thing."),
            double = listOf("Going for two. Doubling!", "Full court press, doubling."),
            split = listOf("Split the defense!", "Two plays, one huddle."),
        )
        // A shy grad student who apologizes to everyone.
        c("Yuki",
            win = listOf("Oh! I won? Sorry. Thank you.", "Oh, that worked. I didn't expect that.", "Um, I think that's a win. Is that right?", "Wow. Sorry. I'm just surprised."),
            bigWin = listOf("Is that real? Sorry, is that real?", "I think I just paid for my textbooks.", "Oh my gosh. Sorry. Thank you!"),
            lose = listOf("Oh. Sorry, that's okay.", "I figured. It's fine.", "That's all right. I'll try again. Sorry.", "Mm. Yes. I understand."),
            bust = listOf("Sorry. Sorry. I went over.", "Oh no, I hit. I shouldn't have.", "That was my fault. I'm sorry, everyone.", "Um, that was too many. Sorry."),
            blackjack = listOf("Um, is this a blackjack? It is, right?", "Oh! Twenty one! Sorry, I'm excited.", "I did a good thing, I think."),
            push = listOf("Is a tie okay? It feels okay.", "Um, that's a push. I've read about those."),
            winStreak = listOf("I'm sorry for winning so much.", "I keep winning. Is that allowed?", "This is statistically weird. Sorry."),
            lossStreak = listOf("I'm sorry for losing so much.", "It's okay. It's okay. I have a thesis due anyway.", "I should probably study instead."),
            bigBet = listOf("Is it okay if I bet this much? Sorry.", "I'm nervous, but I'm doing it.", "This is my entire grocery budget. Don't tell me."),
            idle = listOf("Sorry, am I in the way?", "I came because my labmate said it was fun. It's kind of fun.", "Is the dealer allowed to talk to us? I'm so sorry if not.", "I'm studying probability. This is not helping.", "The cards are beautiful. Sorry, random thought."),
            double = listOf("Um, doubling? Okay. Sorry.", "I read that doubling is correct here."),
            split = listOf("Splitting. I think. Sorry.", "Two hands, sorry, I need more chips."),
        )
        // A retired colonel who treats the table like a mission.
        c("Colonel Hargrove",
            win = listOf("Mission accomplished.", "Objective secured.", "That's how it's done, soldier.", "Victory is orderly."),
            bigWin = listOf("A decisive engagement!", "Hoo-ah! That's a campaign win!", "Medals all around!"),
            lose = listOf("A tactical setback.", "Regroup. We'll retake the hill.", "Casualties noted. Morale is intact.", "Intelligence was faulty on that one."),
            bust = listOf("A breach of the perimeter.", "I overextended. Rookie error.", "Over the line of advance. Retreat!", "That one is on me, not the troops."),
            blackjack = listOf("Flawless execution!", "Twenty one. Precisely as planned.", "Operation Blackjack, complete."),
            push = listOf("A stalemate. Hold position.", "Neither side advances."),
            winStreak = listOf("We have momentum. Press the advantage.", "Three successful sorties.", "The enemy is on the back foot."),
            lossStreak = listOf("A hard campaign. We endure.", "Fall back and consolidate.", "I've lost battles before. Not wars."),
            bigBet = listOf("Committing reserves to this one.", "Full force on this hand.", "A calculated assault. Proceed."),
            idle = listOf("In my day, you counted the cards and told no one.", "Dealer, you have excellent posture. I approve.", "I served thirty years. This is the quietest battlefield I've seen.", "Cigars were allowed in here once. I miss it.", "Discipline wins. Always has."),
            double = listOf("Doubling the force.", "Reinforcements! Double down."),
            split = listOf("Dividing our forces. Risky, but sound.", "Two fronts. Prepare accordingly."),
        )
        // A poker pro who trash talks the blackjack table.
        c("Savannah",
            win = listOf("Too easy. Next.", "You all play like you're afraid of the river.", "I could do this blindfolded.", "Pay me, sweetheart."),
            bigWin = listOf("That's called reading the dealer. Look it up.", "Stack up, folks. The pro just ate.", "I'll take the chips, you take the lesson."),
            lose = listOf("Variance. You wouldn't know it.", "I've lost bigger pots on a Tuesday.", "That's not a loss. That's tuition.", "Blackjack is a donkey's game anyway."),
            bust = listOf("Don't act like you've never busted.", "That was a bluff. A bad one.", "I misread my own hand. Don't write it down.", "The dealer got lucky."),
            blackjack = listOf("The nuts. Just so you know.", "Twenty one. Show me what you've got.", "Natural. And pretty."),
            push = listOf("Chop. Fine.", "Split the pot. Boring."),
            winStreak = listOf("I'm running hot and I know it.", "Count my chips, I dare you.", "That's three. Want to make it four?"),
            lossStreak = listOf("Downswing. It happens to the best.", "I'll be back to even by midnight.", "Run bad, play good. That's the motto."),
            bigBet = listOf("Shove it all in. Why not?", "I'm putting a real number out there.", "Let's see who has the nerve."),
            idle = listOf("I play poker for a living. This is my vacation. Don't ask.", "You people stand on twelve against a six? Bold.", "Every table has a fish. I'm still looking for the fish here.", "I'd have made a great dealer. Terrible, but great.", "Tell me about your tells. I'll tell you mine. Just kidding."),
            double = listOf("I'll make it two. Watch this.", "Doubling. Because I can."),
            split = listOf("Split. Let me play both and win both.", "Two hands for the price of one bad attitude."),
        )
    }

    fun has(name: String): Boolean = M.containsKey(name)

    /** This person's lines for [talk], or empty. */
    fun lines(name: String, talk: Talk): List<String> = M[name]?.get(talk).orEmpty()

    fun names(): Set<String> = M.keys

    fun allLines(): List<String> = M.values.flatMap { it.values.flatten() }
}
