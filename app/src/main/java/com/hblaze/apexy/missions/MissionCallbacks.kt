package com.hblaze.apexy.missions

/** What every mission screen reports back to the runner. */
class MissionCallbacks(
    /** Any touch or sensor progress; keeps the idle timer from firing. */
    val onInteraction: () -> Unit,
    val onComplete: () -> Unit,
)
