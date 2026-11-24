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
//    fun convertirUriABase64(uriImagen: Uri): String? {
//        return try {
//            // 0. Escudo de seguridad: tamaño físico (máx 10 MB) y Magic Bytes reales
//            if (!esImagenSegura(uriImagen)) return null
//
//            // 1. Leemos primero solo las dimensiones de la imagen para no saturar la memoria
//            val options = BitmapFactory.Options().apply {
//                inJustDecodeBounds = true
//
//            }
//
//            context.contentResolver.openInputStream(uriImagen)?.use { stream ->
//                BitmapFactory.decodeStream(stream, null, options)
//            }
//
//            // Bloqueo de imágenes corruptas o bombas de descompresión (> 8192 px)
//            if (options.outWidth <= 0 || options.outHeight <= 0 ||
//                options.outWidth > 8192 || options.outHeight > 8192) {
//                return null
//            }
//
//            // 2. Calculamos el factor de reducción para que no supere los 1024px de ancho/alto
//            options.inSampleSize = calcularFactorReduccion(options, 1024, 1024)
//            options.inJustDecodeBounds = false
//
//            // 3. Decodificamos la imagen ya reducida
//            val bitmapReducido: Bitmap? = context.contentResolver.openInputStream(uriImagen)?.use { stream: InputStream ->
//                BitmapFactory.decodeStream(stream, null, options)
//            }
//
//            if (bitmapReducido == null) return null
//
//            // 4. Comprimimos a JPEG (calidad 80%) y convertimos a Base64 sin saltos de línea
//            val outputStream = ByteArrayOutputStream()
//            bitmapReducido.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
//            val bytesImagen = outputStream.toByteArray()
//
//            // Liberamos el bitmap de la memoria RAM inmediatamente
//            bitmapReducido.recycle()
//
//            Base64.encodeToString(bytesImagen, Base64.NO_WRAP)
//        } catch (e: Exception) {
//            e.printStackTrace()
//            null
//        }
//    }

    fun convertirUriABase64(uriImagen: Uri): String? {
        var bitmapReducido: Bitmap? = null
        return try {
            // 0. Escudo de seguridad: tamaño físico (máx 10 MB) y Magic Bytes reales
            if (!esImagenSegura(uriImagen)) return null

            // 1. Leemos primero solo las dimensiones de la imagen para no saturar la memoria
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(uriImagen)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Bloqueo de imágenes corruptas o bombas de descompresión (> 8192 px)
            if (options.outWidth <= 0 || options.outHeight <= 0 ||
                options.outWidth > 8192 || options.outHeight > 8192) {
                return null
            }

            // 2. Calculamos el factor de reducción para que no supere los 1024px de ancho/alto
            options.inSampleSize = calcularFactorReduccion(options, 1024, 1024)
            options.inJustDecodeBounds = false

            // 3. Decodificamos la imagen ya reducida
            bitmapReducido = context.contentResolver.openInputStream(uriImagen)?.use { stream: InputStream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val bitmapSeguro = bitmapReducido ?: return null

            // 4. Comprimimos a JPEG (calidad 80%) y convertimos a Base64 sin saltos de línea
            val outputStream = ByteArrayOutputStream()
            bitmapSeguro.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytesImagen = outputStream.toByteArray()

            Base64.encodeToString(bytesImagen, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        } finally {
            // Liberamos el bitmap de la memoria RAM siempre, incluso si ocurriera un fallo
            bitmapReducido?.recycle()
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
    private fun esImagenSegura(uri: Uri): Boolean {
        return try {
            val maxBytes = 15L * 1024L * 1024L // Límite seguro de 15 MB
            val tamano = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (tamano > maxBytes) return false

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(12)
                val leidos = stream.read(header)
                if (leidos < 4) return@use false

                val esJpeg = header.get(0) == 0xFF.toByte() &&
                        header.get(1) == 0xD8.toByte() &&
                        header.get(2) == 0xFF.toByte()

                val esPng = header.get(0) == 0x89.toByte() &&
                        header.get(1) == 0x50.toByte() &&
                        header.get(2) == 0x4E.toByte() &&
                        header.get(3) == 0x47.toByte()

                val esWebp = leidos >= 12 &&
                        header.get(0) == 0x52.toByte() && header.get(1) == 0x49.toByte() &&
                        header.get(2) == 0x46.toByte() && header.get(3) == 0x46.toByte() &&
                        header.get(8) == 0x57.toByte() && header.get(9) == 0x45.toByte() &&
                        header.get(10) == 0x42.toByte() && header.get(11) == 0x50.toByte()

                // Soporta también fotos HEIC/HEIF nativas de cámaras Android modernas ("ftyp" en bytes 4..7)
                val esHeic = leidos >= 8 &&
                        header.get(4) == 0x66.toByte() && header.get(5) == 0x74.toByte() &&
                        header.get(6) == 0x79.toByte() && header.get(7) == 0x70.toByte()

                esJpeg || esPng || esWebp || esHeic
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

}
