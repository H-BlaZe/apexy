package com.apexy.alarm.missions

import org.json.JSONArray
import org.json.JSONObject

enum class MissionType(
    val title: String,
    val blurb: String,
    val countLabel: String,
    val defaultCount: Int,
    val countRange: IntRange,
    val countStep: Int = 1,
    val hasDifficulty: Boolean = true,
) {
    MATH("Math", "Solve equations", "Problems", 3, 1..10),
    MEMORY("Memory", "Repeat the lit tiles", "Rounds", 3, 1..6),
    TYPING("Typing", "Type phrases exactly", "Phrases", 2, 1..6),
    SHAKE("Shake", "Shake your phone", "Shakes", 30, 10..200, 10),
    WALK("Steps", "Walk around", "Steps", 30, 10..500, 10, hasDifficulty = false),
    SQUAT("Squats", "Squat with your phone", "Squats", 10, 5..50, 5),
    QR("QR code", "Scan your QR code", "", 1, 1..1, hasDifficulty = false),
    BARCODE("Barcode", "Scan your barcode", "", 1, 1..1, hasDifficulty = false),
    PHOTO("Photo", "Snap a set spot", "", 1, 1..1),
    NFC("NFC tag", "Tap your NFC tag", "", 1, 1..1, hasDifficulty = false),
    LIGHT("Light", "Find bright light", "Seconds", 5, 3..30),
    ODD("Odd one", "Spot the odd emoji", "Rounds", 3, 1..10),
    ORDER("In order", "Tap numbers in order", "Rounds", 2, 1..6),
    STEADY("Steady", "Hold the phone still", "Seconds", 10, 5..60, 5),
    ;

    val needsCamera get() = this == QR || this == BARCODE || this == PHOTO
    val needsActivityPermission get() = this == WALK
    val needsRegistration get() = needsCamera || this == NFC
    /** Something you have to get up and go to. */
    val isPhysical get() = this in setOf(QR, BARCODE, PHOTO, NFC, LIGHT, WALK, SQUAT, SHAKE)
}

val DIFFICULTY_NAMES = listOf("Very easy", "Easy", "Normal", "Hard", "Very hard")

data class Mission(
    val type: MissionType,
    /** 1..5. */
    val difficulty: Int = 3,
    val count: Int = type.defaultCount,
    /** Registered QR/barcode values. */
    val codes: List<String> = emptyList(),
    /** Absolute path of the registered reference photo. */
    val photo: String = "",
    /** Typing only: which phrase lists to draw from (see [Phrases]). */
    val phrases: List<String> = listOf(Phrases.SHORT, Phrases.MOTIVATIONAL),
) {
    val isReady get() = when (type) {
        MissionType.QR, MissionType.BARCODE, MissionType.NFC -> codes.isNotEmpty()
        MissionType.PHOTO -> photo.isNotBlank()
        else -> true
    }

    private fun unit() = type.countLabel.lowercase().let { if (count == 1) it.removeSuffix("s") else it }

    fun summary(): String = when (type) {
        MissionType.QR, MissionType.BARCODE ->
            if (codes.isEmpty()) "Not registered yet" else "${codes.size} code${if (codes.size > 1) "s" else ""} registered"
        MissionType.NFC -> if (codes.isEmpty()) "No tag yet" else "${codes.size} tag${if (codes.size > 1) "s" else ""} registered"
        MissionType.TYPING -> "$count ${unit()} · ${phrases.joinToString(", ") { Phrases.name(it) }}"
        MissionType.PHOTO -> if (photo.isBlank()) "No photo yet" else "Match: ${DIFFICULTY_NAMES[difficulty - 1].lowercase()}"
        MissionType.WALK -> "$count ${unit()}"
        else -> "$count ${unit()} · ${DIFFICULTY_NAMES[difficulty - 1]}"
    }
}

object MissionCodec {
    fun decode(raw: String): List<Mission> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val type = runCatching { MissionType.valueOf(o.getString("type")) }.getOrNull() ?: return@mapNotNull null
                val codes = o.optJSONArray("codes")?.let { c -> (0 until c.length()).map { c.getString(it) } } ?: emptyList()
                Mission(
                    type = type,
                    difficulty = o.optInt("difficulty", 3).coerceIn(1, 5),
                    count = o.optInt("count", type.defaultCount),
                    codes = codes,
                    photo = o.optString("photo", ""),
                    phrases = o.optJSONArray("phrases")?.let { p -> (0 until p.length()).map { p.getString(it) } }
                        ?.filter { it in Phrases.ALL }?.ifEmpty { null } ?: Mission(type).phrases,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encode(missions: List<Mission>): String {
        if (missions.isEmpty()) return ""
        val arr = JSONArray()
        missions.forEach { m ->
            arr.put(
                JSONObject()
                    .put("type", m.type.name)
                    .put("difficulty", m.difficulty)
                    .put("count", m.count)
                    .put("codes", JSONArray(m.codes))
                    .put("photo", m.photo)
                    .put("phrases", JSONArray(m.phrases)),
            )
        }
        return arr.toString()
    }
}
