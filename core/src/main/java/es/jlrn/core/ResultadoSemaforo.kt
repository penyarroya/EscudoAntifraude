package es.jlrn.core

data class ResultadoSemaforo(
    val nivel: NivelRiesgo,
    val tituloCorto: String,
    val explicacionSencilla: String,
    val consejoAccion: String
) {
    enum class NivelRiesgo {
        ROJO,      // Estafa confirmada o altísimo riesgo
        AMARILLO,  // Sospechoso, requiere precaución
        VERDE      // Parece seguro
    }

    /**
     * Genera el texto completo optimizado para ser leído en voz alta.
     */
    fun getTextoParaVoz(): String {
        return "$tituloCorto. $explicacionSencilla. $consejoAccion"
    }
}
