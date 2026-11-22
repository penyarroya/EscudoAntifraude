package es.jlrn.analyzer

import es.jlrn.core.ResultadoSemaforo
import es.jlrn.core.ResultadoSemaforo.NivelRiesgo
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AnalizadorFraude(private val apiKey: String) {

    // Cliente HTTP con margen de espera amplio por si el móvil tiene conexión lenta
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Prompt estricto adaptado a estafas reales en España y lenguaje claro
    private val promptSistema = """
        Eres un experto en ciberseguridad que ayuda a personas mayores en España a detectar estafas (SMS, WhatsApp, correos, llamadas o documentos).
        Analiza el contenido que te envío y responde ÚNICAMENTE con un objeto JSON válido con estas 4 claves exactas, sin texto adicional ni marcas markdown:
        {
          "nivel": "ROJO" o "AMARILLO" o "VERDE",
          "tituloCorto": "Máximo 5 palabras en mayúsculas (ej. PELIGRO: ES UNA ESTAFA)",
          "explicacionSencilla": "Explica en 1 o 2 frases muy sencillas por qué es seguro o por qué es una trampa, sin tecnicismos",
          "consejoAccion": "Una orden clara de qué hacer ahora (ej. Borra este mensaje y no pulses ningún enlace)"
        }
        Reglas: Si piden dinero urgente, datos bancarios, códigos SMS o usan enlaces raros (suplantando a Correos, DGT, Seguridad Social, bancos o familiares), marca siempre ROJO.
    """.trimIndent()

    interface ResultadoCallback {
        fun onExito(resultado: ResultadoSemaforo)
        fun onError(mensajeError: String)
    }

    /**
     * Analiza un texto sospechoso o una imagen en Base64 (o ambos a la vez).
     */
    fun analizar(textoSospechoso: String?, imagenBase64: String?, callback: ResultadoCallback) {
        val host = "generativelanguage.googleapis.com"
        val modelo = "gemini-3.5-flash-lite"
        val ruta = "/v1beta/models/$modelo:generateContent?key=$apiKey"
        val url = "https://$host$ruta"

        // Obtenemos la fecha real del teléfono para que la IA nunca falle con el año en curso
        val fechaActual = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "ES")).format(Date())
        val contextoTemporal = "FECHA ACTUAL REAL DE HOY: $fechaActual. Tenla en cuenta al validar fechas de sorteos (como Lotería de Navidad del 22 de diciembre), recibos, multas o documentos del año actual."

        try {
            val partsArray = JSONArray()

            // 1. Añadimos las instrucciones del sistema + la fecha real + el texto si lo hay
            val textoFinal = if (!textoSospechoso.isNullOrBlank()) {
                "$promptSistema\n$contextoTemporal\n\nMensaje a analizar:\n\"$textoSospechoso\""
            } else {
                "$promptSistema\n$contextoTemporal\n\nAnaliza la captura de pantalla o foto adjunta:"
            }
            partsArray.put(JSONObject().put("text", textoFinal))

            // 2. Si hay captura de pantalla en Base64, la adjuntamos
            if (!imagenBase64.isNullOrBlank()) {
                val inlineData = JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", imagenBase64)
                partsArray.put(JSONObject().put("inline_data", inlineData))
            }

            val contentObj = JSONObject().put("parts", partsArray)
            val bodyJson = JSONObject().put("contents", JSONArray().put(contentObj))

            val requestBody = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback.onError("No hay conexión a internet. Revisa tu Wi-Fi o datos móviles.")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val respuestaCruda = it.body?.string() ?: ""
                        if (!it.isSuccessful) {
                            callback.onError("Error del servidor de análisis (Código ${it.code}). Inténtalo de nuevo.")
                            return
                        }
                        parsearRespuestaGemini(respuestaCruda, callback)
                    }
                }
            })
        } catch (e: Exception) {
            callback.onError("Error al preparar el análisis: ${e.message}")
        }
    }

    private fun parsearRespuestaGemini(respuestaCruda: String, callback: ResultadoCallback) {
        try {
            val jsonRaiz = JSONObject(respuestaCruda)
            val textoGenerado = jsonRaiz
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            // Limpiamos posibles bloques ```json ... ``` que a veces añade la IA
            val jsonLimpio = textoGenerado
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val datos = JSONObject(jsonLimpio)
            val nivelTexto = datos.optString("nivel", "AMARILLO").uppercase()
            val nivel = try {
                NivelRiesgo.valueOf(nivelTexto)
            } catch (e: Exception) {
                NivelRiesgo.AMARILLO
            }

            val resultado = ResultadoSemaforo(
                nivel = nivel,
                tituloCorto = datos.optString("tituloCorto", "PRECAUCIÓN"),
                explicacionSencilla = datos.optString("explicacionSencilla", "No se ha podido verificar con seguridad."),
                consejoAccion = datos.optString("consejoAccion", "Consulta con un familiar antes de hacer nada.")
            )
            callback.onExito(resultado)

        } catch (e: Exception) {
            callback.onError("No se pudo interpretar el resultado. Por precaución, no pulses ningún enlace.")
        }
    }
}
