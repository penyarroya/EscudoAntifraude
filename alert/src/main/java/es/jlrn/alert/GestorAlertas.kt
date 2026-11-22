package es.jlrn.alert

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import es.jlrn.core.ResultadoSemaforo
import java.net.URLEncoder

class GestorAlertas(private val context: Context) {

    private val prefs = context.getSharedPreferences("escudo_antifraude_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TELEFONO = "telefono_confianza"
        private const val KEY_NOMBRE = "nombre_confianza"
    }

    /**
     * Guarda localmente el nombre y teléfono del familiar (ej. "612345678").
     */
    fun guardarContactoConfianza(nombre: String, telefono: String) {
        // Limpiamos espacios o guiones y añadimos prefijo de España (+34) si no lo tiene
        var numeroLimpio = telefono.replace(Regex("[^0-9+]"), "")
        if (!numeroLimpio.startsWith("+") && !numeroLimpio.startsWith("34") && numeroLimpio.length == 9) {
            numeroLimpio = "34$numeroLimpio"
        } else if (numeroLimpio.startsWith("+")) {
            numeroLimpio = numeroLimpio.substring(1)
        }

        prefs.edit()
            .putString(KEY_NOMBRE, nombre.trim().ifEmpty { "Familiar" })
            .putString(KEY_TELEFONO, numeroLimpio)
            .apply()
    }

    fun obtenerNombreConfianza(): String {
        return prefs.getString(KEY_NOMBRE, "") ?: ""
    }

    fun obtenerTelefonoConfianza(): String {
        return prefs.getString(KEY_TELEFONO, "") ?: ""
    }

    fun tieneContactoConfigurado(): Boolean {
        return obtenerTelefonoConfianza().isNotBlank()
    }

    /**
     * Abre WhatsApp directamente en el chat del familiar con el resumen del posible fraude.
     */
    fun enviarAvisoWhatsApp(resultado: ResultadoSemaforo) {
        val telefono = obtenerTelefonoConfianza()
        val mensaje = construirMensajeAlerta(resultado)

        try {
            val textoCodificado = URLEncoder.encode(mensaje, "UTF-8")
            val uri = if (telefono.isNotBlank()) {
                // Abre directamente el chat con el familiar guardado
                Uri.parse("https://whatsapp.com")
            } else {
                // Si no hay teléfono guardado, abre WhatsApp para que elija a quién enviarlo
                Uri.parse("https://whatsapp.com")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No se pudo abrir WhatsApp. Llama por teléfono a tu familiar.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun construirMensajeAlerta(resultado: ResultadoSemaforo): String {
        return "⚠️ *AVISO DE POSIBLE ESTAFA* ⚠️\n\n" +
                "He analizado un mensaje sospechoso con mi app Escudo Antifraude y me dice:\n" +
                "• *Nivel:* ${resultado.nivel}\n" +
                "• *Diagnóstico:* ${resultado.tituloCorto}\n" +
                "• *Motivo:* ${resultado.explicacionSencilla}\n\n" +
                "¿Puedes echarle un vistazo y ayudarme antes de que haga nada?"
    }

    /**
     * Abre el marcador telefónico del móvil con el 017 (INCIBE - Línea gratuita de ciberseguridad en España)
     * listo para llamar con un solo toque, sin requerir permisos especiales.
     */
    fun llamarAl017() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:017")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Marca el 017 en tu teléfono para recibir ayuda gratuita.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

}
