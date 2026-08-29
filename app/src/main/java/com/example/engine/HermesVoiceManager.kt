package com.example.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class HermesVoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText = _recognizedText.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel = _soundLevel.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId = _speakingMessageId.asStateFlow()

    init {
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {}
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.CHINESE
            ttsReady = true
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }
                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _speakingMessageId.value = null
                }
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _speakingMessageId.value = null
                }
            })
        }
    }

    fun speak(text: String, messageId: String? = null) {
        if (!ttsReady || textToSpeech == null) {
            Toast.makeText(context, "TTS 语音引擎初始化中...", Toast.LENGTH_SHORT).show()
            return
        }
        if (_isSpeaking.value && _speakingMessageId.value == messageId) {
            stopSpeaking()
            return
        }
        stopSpeaking()
        _speakingMessageId.value = messageId
        _isSpeaking.value = true
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, messageId ?: "hermes_tts_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
        _speakingMessageId.value = null
    }

    fun startListening(onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "当前设备不支持语音识别", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _soundLevel.value = 0f
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        _soundLevel.value = rmsdB.coerceIn(0f, 12f)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _soundLevel.value = 0f
                    }
                    override fun onError(error: Int) {
                        _isListening.value = false
                        _soundLevel.value = 0f
                    }
                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _soundLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            _recognizedText.value = text
                            onResult(text)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _recognizedText.value = matches[0]
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "正在倾听 Hermes 智能体指令...")
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            _soundLevel.value = 0f
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
        _soundLevel.value = 0f
    }

    fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        _isListening.value = false
        _isSpeaking.value = false
    }
}
