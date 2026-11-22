package es.jlrn.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ProcesadorImagen(private val context: Context) {

    /**
     * Convierte la Uri de una captura de pantalla en una cadena Base64 comprimida,
     * optimizada para leerse con IA sin consumir apenas datos móviles.
     */
    fun convertirUriABase64(uriImagen: Uri): String? {
        return try {
            // 1. Leemos primero solo las dimensiones de la imagen para no saturar la memoria
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uriImagen)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // 2. Calculamos el factor de reducción para que no supere los 1024px de ancho/alto
            // (Suficiente para leer cualquier texto de SMS o WhatsApp con total nitidez)
            options.inSampleSize = calcularFactorReduccion(options, 1024, 1024)
            options.inJustDecodeBounds = false

            // 3. Decodificamos la imagen ya reducida
            val bitmapReducido: Bitmap? = context.contentResolver.openInputStream(uriImagen)?.use { stream: InputStream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (bitmapReducido == null) return null

            // 4. Comprimimos a JPEG (calidad 80%) y convertimos a Base64 sin saltos de línea
            val outputStream = ByteArrayOutputStream()
            bitmapReducido.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytesImagen = outputStream.toByteArray()

            // Liberamos el bitmap de la memoria RAM inmediatamente
            bitmapReducido.recycle()

            Base64.encodeToString(bytesImagen, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calcularFactorReduccion(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
