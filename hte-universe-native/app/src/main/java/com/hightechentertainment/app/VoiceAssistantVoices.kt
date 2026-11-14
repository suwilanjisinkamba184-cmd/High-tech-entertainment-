package com.hightechentertainment.app

internal data class VoiceAssistantVoice(
    val id: String,
    val label: String,
    val description: String
)

internal object VoiceAssistantVoices {
    const val DEFAULT_ID = "cjVigY5qzO86Huf0OWal"

    val all = listOf(
        VoiceAssistantVoice(DEFAULT_ID, "Eric — Smooth & Trustworthy", "Male · American · Conversational"),
        VoiceAssistantVoice("EXAVITQu4vr4xnSDxMaL", "Sarah — Warm & Professional", "Female · American · Clear and reassuring"),
        VoiceAssistantVoice("CwhRBWXzGAHq8TQ4Fs17", "Roger — Relaxed & Casual", "Male · American · Easy-going")
    )

    val default: VoiceAssistantVoice get() = all.first()
    fun find(id: String): VoiceAssistantVoice? = all.firstOrNull { it.id == id }
}
