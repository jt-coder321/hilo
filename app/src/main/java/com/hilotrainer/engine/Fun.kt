package com.hilotrainer.engine

import kotlin.random.Random

/** A room on the casino floor. Higher rooms have bigger limits and a more serious crowd. */
data class Tier(val id: Int, val name: String, val min: Int, val unlockAt: Int, val blurb: String)

object Tiers {
    val ALL = listOf(
        Tier(0, "Penny Pit", 5, 0, "Cheap and cheerful. Lots of first timers."),
        Tier(1, "Main Floor", 10, 0, "The classic casino floor. Tourists and regulars."),
        Tier(2, "Silver Room", 25, 1500, "Quieter, sharper players and bigger stacks."),
        Tier(3, "High Limit", 100, 5000, "No tourists here. Serious money, serious faces."),
        Tier(4, "The Salon", 500, 25000, "The private room. Only the biggest players sit down."),
    )
    fun forMin(min: Int): Tier = ALL.firstOrNull { it.min == min } ?: ALL[1]
    fun excluded(t: Tier): Set<Archetype> = when {
        t.id >= 4 -> setOf(Archetype.TOURIST, Archetype.ROOKIE, Archetype.PARTY, Archetype.DRUNK, Archetype.HUNCH)
        t.id >= 3 -> setOf(Archetype.TOURIST, Archetype.ROOKIE, Archetype.PARTY)
        t.id >= 2 -> setOf(Archetype.TOURIST, Archetype.ROOKIE)
        else -> emptySet()
    }
}

/** The bonus wheel. Prizes are in table minimums so it scales with the room. */
object Wheel {
    val UNITS = listOf(2, 3, 5, 2, 10, 3, 5, 25)
    private val WEIGHT = listOf(22, 20, 14, 22, 8, 20, 12, 2)
    fun pick(rnd: Random): Int {
        var r = rnd.nextInt(WEIGHT.sum())
        for (i in WEIGHT.indices) { r -= WEIGHT[i]; if (r < 0) return i }
        return 0
    }
}

/** Three face down cards for the daily bonus: pick one for a surprise. */
object LuckyCards {
    fun deal(rnd: Random): List<Int> = listOf(25, 50, 150).shuffled(rnd)
}

/** A perk the casino gives you for playing, or a surprise from the table. */
data class CompAward(val title: String, val desc: String, val units: Int, val drink: Boolean = false)

object Comps {
    fun roll(rnd: Random): CompAward {
        val r = rnd.nextInt(100)
        return when {
            r < 35 -> CompAward("Free drink", "The cocktail waitress brought you a drink on the house.", 0, drink = true)
            r < 65 -> CompAward("Buffet comp", "The casino comped your dinner. Chips added to your stack.", 4)
            r < 90 -> CompAward("Show tickets", "Comped show tickets, cashed in for chips.", 6)
            else -> CompAward("Free play", "The host slipped you some free play chips.", 10)
        }
    }
}

/** A friendly bet from another player: beat the dealer this hand and they pay you. */
data class Dare(val seat: Int, val name: String, val stake: Int, val line: String)

object Dares {
    fun make(seat: Int, name: String, min: Int, rnd: Random): Dare {
        val stake = min * listOf(2, 3, 5)[rnd.nextInt(3)]
        val lines = listOf(
            "Bet you can't beat the dealer this hand. \$$stake says you can't.",
            "I'll put \$$stake on the dealer against you. Deal?",
            "\$$stake says you bust this hand. Want it?",
            "Feeling lucky? \$$stake, you against the house, and I'm backing the house.",
        )
        return Dare(seat, name, stake, lines[rnd.nextInt(lines.size)])
    }
    val accepted = listOf("You're on!", "Ha, now it's a game.", "Easy money.")
    val declined = listOf("Scared? Fine.", "No guts, no glory.", "Your loss. Or not.")
    val won = listOf("Well played. Here's your \$STAKE.", "Okay, okay. \$STAKE is yours.", "Unreal. Take it. \$STAKE.")
    val lost = listOf("Ha! Pay up, \$STAKE.", "I'll take that \$STAKE, thanks.", "Told you. \$STAKE, please.")
}

object FunTalk {
    val familiar = listOf(
        "Hey, look who's back!", "There's my lucky charm.", "Good to see you again.", "Back at it, I see.",
        "I was hoping you'd be here tonight.", "Same seat, same luck?",
    )
    val drinkSend = listOf("Waitress! A drink for our friend here.", "This one's on me. Cheers!", "Get this person a drink, on my tab.")
    val drinkThanks = listOf("Cheers!", "Thanks, that hits the spot.", "Now we're talking.")

    /** Two players chatting. The first line is said by one, the reply by the other. */
    val banter = listOf(
        "Third hot streak this week." to "Mine's been cold since Tuesday.",
        "Do you always play like that?" to "Only when I'm winning.",
        "This dealer is good luck." to "Or you're just overdue.",
        "Anyone watching the game later?" to "Wouldn't miss it.",
        "I have a system." to "Everyone has a system.",
        "Is it always this loud in here?" to "Wait until the weekend.",
        "Nice hand, by the way." to "Thanks. Pure luck.",
        "Two more hands and I'm done." to "You said that an hour ago.",
        "Where are you from?" to "Not from around here. You?",
        "My lucky number is seven." to "Mine's twenty one, obviously.",
        "Those chips look heavy." to "Not heavy enough.",
        "Ever get a blackjack on your first hand?" to "Once. Never again.",
        "I could use a drink." to "Just wave the waitress down.",
        "Hit on sixteen. Every time." to "Bold. Or crazy.",
        "How long have you been here?" to "Long enough to lose track.",
        "I read you should never take insurance." to "I read a lot of things.",
    )
}
