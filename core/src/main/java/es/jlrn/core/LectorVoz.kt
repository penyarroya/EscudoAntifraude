package es.jlrn.core

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

class LectorVoz(context: Context, @Suppress("UNUSED_PARAMETER") apiKey: String = "") {

    private var tts: TextToSpeech? = null
    private var estaListo = false
    private var textoPendiente: String? = null

    private val prefs = context.getSharedPreferences("escudo_antifraude_prefs", Context.MODE_PRIVATE)

    fun estaSilenciado(): Boolean {
        return prefs.getBoolean("voz_silenciada", false)
    }

    fun alternarSilencio(): Boolean {
        val nuevoEstado = !estaSilenciado()
        prefs.edit().putBoolean("voz_silenciada", nuevoEstado).apply()
        if (nuevoEstado) {
            detener()
        }
        return nuevoEstado
    }

    init {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                val espanol = Locale("es", "ES")
                val resultado = tts?.setLanguage(espanol)
                if (resultado != TextToSpeech.LANG_MISSING_DATA && resultado != TextToSpeech.LANG_NOT_SUPPORTED) {
                    seleccionarMejorVozEspanola()
                    tts?.setSpeechRate(0.85f)
                    tts?.setPitch(1.0f)
                    estaListo = true

                    textoPendiente?.let {
                        leer(it)
                        textoPendiente = null
                    }
                }
            }
        }

        // Forzamos el motor de Google Speech Services si está disponible en el dispositivo
        tts = try {
            TextToSpeech(context.applicationContext, listener, "com.google.android.tts")
        } catch (e: Exception) {
            TextToSpeech(context.applicationContext, listener)
        }
    }

    private fun seleccionarMejorVozEspanola() {
        try {
            val voces = tts?.voices ?: return
            // Priorizamos voces de alta calidad (network/neural) en español de España
            val mejorVoz = voces
                .filter { it.locale.language == "es" && it.locale.country == "ES" }
                .maxByOrNull { it.quality }
            if (mejorVoz != null && mejorVoz.quality >= Voice.QUALITY_NORMAL) {
                tts?.voice = mejorVoz
            }
        } catch (_: Exception) {
        }
    }

    fun leer(texto: String?) {
        // Si el usuario ha pulsado el botón discreto de silencio, no reproducimos nada
        if (estaSilenciado() || texto.isNullOrBlank()) return

        if (!estaListo) {
            textoPendiente = texto
            return
        }

        // Limpiamos emojis y caracteres XML conflictivos
        val textoLimpio = texto
            .replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{Z}]"), "")
            .replace("&", "y")
            .replace("<", "")
            .replace(">", "")
            .lowercase(Locale("es", "ES"))
            .trim()

        // Construimos SSML nativo de Android: fija ritmo constante al 85% y pausas de 500ms tras cada punto
        val textoConPausas = textoLimpio.replace(". ", ". <break time=\"500ms\"/> ")
        val ssml = "<speak><prosody rate=\"85%\">$textoConPausas</prosody></speak>"

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(ssml, TextToSpeech.QUEUE_FLUSH, params, "LOCUCION_SSML")
    }

    fun detener() {
        textoPendiente = null
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
    }

    fun liberarRecursos() {
        detener()
        tts?.shutdown()
    }
}
