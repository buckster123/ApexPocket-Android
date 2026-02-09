package com.apexaurum.pocket.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Voice engine wrapping Android SpeechRecognizer (STT) and TextToSpeech (TTS).
 * Per-agent voice personalities via pitch/speed tuning.
 */
class SpeechService(private val context: Context) {

    // --- Agent voice personalities ---
    private data class VoiceProfile(val pitch: Float, val speed: Float)

    private val voiceProfiles = mapOf(
        "AZOTH" to VoiceProfile(pitch = 0.8f, speed = 0.9f),
        "ELYSIAN" to VoiceProfile(pitch = 1.3f, speed = 1.0f),
        "VAJRA" to VoiceProfile(pitch = 0.9f, speed = 1.1f),
        "KETHER" to VoiceProfile(pitch = 1.0f, speed = 0.85f),
        "CLAUDE" to VoiceProfile(pitch = 1.0f, speed = 1.0f),
    )

    // --- State flows ---
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _recognizedText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val recognizedText: SharedFlow<String> = _recognizedText.asSharedFlow()

    // --- STT ---
    private var speechRecognizer: SpeechRecognizer? = null

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _isListening.value = false
        }

        override fun onError(error: Int) {
            _isListening.value = false
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            if (!text.isNullOrBlank()) {
                _recognizedText.tryEmit(text)
            }
            _isListening.value = false
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // --- TTS ---
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
                ttsReady = true
            }
        }
    }

    /** Check if speech recognition is available on this device. */
    fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /** Start listening for speech input. */
    fun startListening() {
        if (_isListening.value) return
        if (!isRecognitionAvailable()) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(recognitionListener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            sr.startListening(intent)
            _isListening.value = true
        }
    }

    /** Stop listening. */
    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    /** Speak text with agent-specific voice personality. */
    fun speak(text: String, agentId: String = "CLAUDE") {
        if (!ttsReady) return

        val profile = voiceProfiles[agentId.uppercase()] ?: voiceProfiles["CLAUDE"]!!
        tts?.setPitch(profile.pitch)
        tts?.setSpeechRate(profile.speed)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "apex_${System.currentTimeMillis()}")
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "apex_${System.currentTimeMillis()}")
    }

    /** Stop any ongoing speech. */
    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /** Clean up resources. Call from ViewModel.onCleared(). */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
