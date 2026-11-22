package es.jlrn.escudoantifraude

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import es.jlrn.analyzer.AnalizadorFraude
import es.jlrn.core.ResultadoSemaforo
import es.jlrn.core.ResultadoSemaforo.NivelRiesgo

class EscuchaNotificacionesService : NotificationListenerService() {

    // PEGA AQUÍ TU MISMA API KEY GRATUITA DE GOOGLE AI STUDIO
    private val apiKeyGemini: String by lazy { getString(R.string.gemini_api_key) }

    private lateinit var analizadorFraude: AnalizadorFraude

    private val appsVigiladas = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.google.android.gm",
        "com.microsoft.office.outlook"
    )

    private val palabrasSospechosas = listOf(
        "http", "www.", ".com", ".es", ".net", ".org", ".info", ".xyz", "bit.ly",
        "multa", "dgt", "correos", "paquete", "aduana", "envio", "envío",
        "banco", "bbva", "santander", "caixabank", "sabadell", "ing", "bankinter",
        "seguridad social", "hacienda", "aeat", "bizum", "urgente", "bloqueo",
        "bloqueada", "suspendida", "verificar", "clave", "codigo", "código", "premio"
    )

    private var ultimoTextoAnalizado = ""
    private var tiempoUltimoAnalisis = 0L

    override fun onCreate() {
        super.onCreate()
        analizadorFraude = AnalizadorFraude(apiKeyGemini)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val paquete = sbn?.packageName ?: return
        if (paquete !in appsVigiladas) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = sbn.notification.extras ?: return
        val remitente = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Desconocido"
        val textoMensaje = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        val mensajeCompleto = "Remitente: $remitente. Mensaje: $textoMensaje"

        val ahora = System.currentTimeMillis()
        if (mensajeCompleto == ultimoTextoAnalizado && (ahora - tiempoUltimoAnalisis) < 10_000) {
            return
        }

        val textoMinusculas = mensajeCompleto.lowercase()
        val pareceSospechoso = palabrasSospechosas.any { palabra -> textoMinusculas.contains(palabra) }
        if (!pareceSospechoso) return

        ultimoTextoAnalizado = mensajeCompleto
        tiempoUltimoAnalisis = ahora

        analizadorFraude.analizar(mensajeCompleto, null, object : AnalizadorFraude.ResultadoCallback {
            override fun onExito(resultado: ResultadoSemaforo) {
                if (resultado.nivel == NivelRiesgo.ROJO || resultado.nivel == NivelRiesgo.AMARILLO) {
                    abrirAlertaSeguraEnPantalla(textoMensaje, resultado)
                }
            }

            override fun onError(mensajeError: String) {
            }
        })
    }

    private fun abrirAlertaSeguraEnPantalla(textoOriginal: String, resultado: ResultadoSemaforo) {
        val textoSinEnlacesPulsables = neutralizarEnlaces(textoOriginal)

        val intentAlerta = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_AUTOMATICO", true)
            putExtra("EXTRA_TEXTO_SEGURO", textoSinEnlacesPulsables)
            putExtra("EXTRA_NIVEL", resultado.nivel.name)
            putExtra("EXTRA_TITULO", resultado.tituloCorto)
            putExtra("EXTRA_EXPLICACION", resultado.explicacionSencilla)
            putExtra("EXTRA_CONSEJO", resultado.consejoAccion)
        }
        startActivity(intentAlerta)
    }

    companion object {
        fun neutralizarEnlaces(texto: String): String {
            return texto.replace(
                Regex("(https?://|www\\.)\\S+", RegexOption.IGNORE_CASE),
                " [ENLACE BLOQUEADO POR SEGURIDAD] "
            )
        }
    }
}
