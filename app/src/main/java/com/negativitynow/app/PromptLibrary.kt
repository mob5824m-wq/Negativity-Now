package com.negativitynow.app

/** Preloaded prompts used for both scheduled reminders and the in-app opening verdict. */
object PromptLibrary {
    val presets: List<String> = listOf(
        "You said you would begin. The clock disagrees.",
        "Potential is cheap. Follow-through is not.",
        "The task is still there. Avoiding it only made it older.",
        "Stop treating effort like a personality trait. Do the next thing.",
        "Your future plans are not on today's calendar. You are.",
        "Nobody is coming to finish the work you keep postponing.",
        "Be less impressive in theory and more useful in practice.",
        "You do not need a better mood. You need a smaller first step.",
        "The version of you with momentum started before feeling ready.",
        "If it matters, give it ten honest minutes instead of another excuse."
    )

    fun messageFor(timestampMillis: Long = System.currentTimeMillis()): String {
        val index = ((timestampMillis / 60_000L) % presets.size).toInt()
        return presets[index]
    }

    fun formattedPresetList(): String = presets.mapIndexed { index, message ->
        "${index + 1}. $message"
    }.joinToString(separator = "\n\n")
}
