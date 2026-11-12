package com.apexy.alarm.missions

import kotlin.random.Random

/** Phrase lists for the typing mission. Poem lines are all from public-domain poems. */
object Phrases {
    const val MOTIVATIONAL = "motivational"
    const val SHORT = "short"
    const val POEM = "poem"
    const val TONGUE = "tongue"
    const val AWAKE = "awake"
    const val CUSTOM = "custom"
    val ALL = listOf(MOTIVATIONAL, SHORT, POEM, TONGUE, AWAKE, CUSTOM)

    data class Phrase(val text: String, val exactCase: Boolean = false)

    fun name(id: String) = when (id) {
        MOTIVATIONAL -> "Motivational"
        SHORT -> "Short"
        POEM -> "Poem lines"
        TONGUE -> "Tongue twisters"
        AWAKE -> "Repeated words"
        CUSTOM -> "Custom"
        else -> id
    }

    private val short = listOf(
        "Rise and shine", "Feet on the floor", "Open the curtains", "Today is a good day",
        "Coffee first then the world", "Wide awake now", "New day new chances", "Stretch and smile",
        "Up and at it", "Hello morning", "Drink some water", "Make the bed",
    )
    private val motivational = listOf(
        "The early hours belong to those who show up for them",
        "I will drink a glass of water before I check my phone",
        "Every morning is a fresh page in a story I get to write",
        "Small steps every morning add up to big changes",
        "My bed will still be here tonight so I can leave it now",
        "Getting up on time is a promise I keep to myself",
        "Discipline is choosing what I want most over what I want right now",
        "I am not snoozing again; I am standing up and starting my day with intent.",
        "Ten more minutes in bed never felt as good as being early and ready.",
        "Today I will finish one important task before lunch, starting now.",
    )
    private val poem = listOf(
        "Hope is the thing with feathers that perches in the soul",
        "I wandered lonely as a cloud that floats on high o'er vales and hills",
        "Tyger Tyger, burning bright, in the forests of the night",
        "Shall I compare thee to a summer's day?",
        "The woods are lovely, dark and deep",
        "A thing of beauty is a joy for ever",
        "To strive, to seek, to find, and not to yield",
        "Because I could not stop for Death, he kindly stopped for me",
        "My heart leaps up when I behold a rainbow in the sky",
        "Two roads diverged in a yellow wood",
        "Season of mists and mellow fruitfulness",
        "Water, water, every where, nor any drop to drink",
    )
    private val tongue = listOf(
        "She sells seashells by the seashore",
        "Peter Piper picked a peck of pickled peppers",
        "Red lorry yellow lorry red lorry yellow lorry",
        "How much wood would a woodchuck chuck",
        "Fresh fried fish, fish fresh fried",
        "Six slippery snails slid slowly seaward",
        "Unique New York, you know you need unique New York",
        "Betty Botter bought some butter but the butter was bitter",
        "A proper copper coffee pot",
        "Lesser leather never weathered wetter weather better",
        "Truly rural, truly rural, truly rural",
        "Fuzzy Wuzzy was a bear, Fuzzy Wuzzy had no hair",
    )
    private val awakeBases = listOf(
        "I am awake", "I am up", "Wake up now", "Good morning", "Eyes open",
        "I am getting up", "Rise and shine", "Out of bed",
    )

    /** The ready-made lines of one list, for browsing; empty for the user's own list. */
    fun builtIn(id: String): List<String> = when (id) {
        MOTIVATIONAL -> motivational
        SHORT -> short
        POEM -> poem
        TONGUE -> tongue
        AWAKE -> awakeBases
        else -> emptyList()
    }

    /** "i Am Awake, I aM awAke…": the same words in a different case each time. */
    fun repeated(rnd: Random, level: Int): String {
        val base = awakeBases.random(rnd)
        val reps = if (level <= 2) 2 else 3
        // Easier levels flip fewer letters.
        val flip = when (level) { 1 -> 0.2f; 2 -> 0.3f; 3 -> 0.4f; else -> 0.5f }
        return (1..reps).joinToString(", ") {
            base.map { ch -> if (ch.isLetter() && rnd.nextFloat() < flip) (if (ch.isUpperCase()) ch.lowercaseChar() else ch.uppercaseChar()) else ch }
                .joinToString("")
        }
    }

    fun pick(lists: List<String>, level: Int, custom: List<String>, avoid: String = "", rnd: Random = Random.Default): Phrase {
        val chosen = lists.filter { it in ALL }.ifEmpty { listOf(SHORT) }
        // Pick the list first so a long list doesn't drown out a short one.
        repeat(6) {
            val pool = when (chosen.random(rnd)) {
                AWAKE -> return Phrase(repeated(rnd, level), exactCase = true)
                MOTIVATIONAL -> motivational
                POEM -> poem
                TONGUE -> tongue
                CUSTOM -> custom.filter { it.isNotBlank() }
                else -> short
            }
            val candidates = byLength(pool, level).filter { it != avoid }
            if (candidates.isNotEmpty()) return Phrase(candidates.random(rnd))
        }
        return Phrase(short.random(rnd))
    }

    /** Low difficulty favours the shorter half of a list, high difficulty the longer half. */
    private fun byLength(pool: List<String>, level: Int): List<String> {
        if (pool.size < 4) return pool
        val sorted = pool.sortedBy { it.length }
        val half = sorted.size / 2
        return when (level) { 1, 2 -> sorted.take(half + 1); 3 -> sorted; else -> sorted.drop(half - 1) }
    }
}
