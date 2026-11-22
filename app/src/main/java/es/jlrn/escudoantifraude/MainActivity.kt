package es.jlrn.escudoantifraude

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import es.jlrn.alert.GestorAlertas
import es.jlrn.analyzer.AnalizadorFraude
import es.jlrn.core.LectorVoz
import es.jlrn.core.ResultadoSemaforo
import es.jlrn.core.ResultadoSemaforo.NivelRiesgo
import es.jlrn.scanner.ProcesadorImagen

class MainActivity : AppCompatActivity() {

    // PEGA AQUÍ TU API KEY GRATUITA DE GOOGLE AI STUDIO
    private val apiKeyGemini: String by lazy { getString(R.string.gemini_api_key) }
    private lateinit var lectorVoz: LectorVoz
    private lateinit var procesadorImagen: ProcesadorImagen
    private lateinit var analizadorFraude: AnalizadorFraude
    private lateinit var gestorAlertas: GestorAlertas

    private lateinit var layoutCargando: LinearLayout
    private lateinit var cardResultado: MaterialCardView
    private lateinit var layoutColorSemaforo: LinearLayout
    private lateinit var tvTituloSemaforo: TextView
    private lateinit var tvExplicacion: TextView
    private lateinit var tvConsejo: TextView
    private lateinit var etTextoSospechoso: EditText
    private lateinit var btnAvisarFamiliar: Button
    private lateinit var btnLlamar017: Button
    private lateinit var btnPegarYComprobar: Button

    private var ultimoResultado: ResultadoSemaforo? = null

    private val seleccionarImagenLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            procesarYAnalizarImagen(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lectorVoz = LectorVoz(this, apiKeyGemini)
        procesadorImagen = ProcesadorImagen(this)
        analizadorFraude = AnalizadorFraude(apiKeyGemini)
        gestorAlertas = GestorAlertas(this)

        enlazarVistas()
        configurarBotones()
        gestionarIntentCompartido(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        gestionarIntentCompartido(intent)
    }

    private fun enlazarVistas() {
        layoutCargando = findViewById(R.id.layoutCargando)
        cardResultado = findViewById(R.id.cardResultado)
        layoutColorSemaforo = findViewById(R.id.layoutColorSemaforo)
        tvTituloSemaforo = findViewById(R.id.tvTituloSemaforo)
        tvExplicacion = findViewById(R.id.tvExplicacion)
        tvConsejo = findViewById(R.id.tvConsejo)
        etTextoSospechoso = findViewById(R.id.etTextoSospechoso)
        btnAvisarFamiliar = findViewById(R.id.btnAvisarFamiliar)
        btnLlamar017 = findViewById(R.id.btnLlamar017)
        btnPegarYComprobar = findViewById(R.id.btnPegarYComprobar)

    }
    private fun configurarBotones() {
        btnPegarYComprobar.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val textoPegado = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: ""

            if (textoPegado.isEmpty()) {
                Toast.makeText(this, "No hay ningún texto copiado todavía", Toast.LENGTH_LONG).show()
                lectorVoz.leer("No tienes ningún mensaje copiado. Copia primero el mensaje sospechoso.")
                return@setOnClickListener
            }

            // Bloqueamos los enlaces visualmente en el cuadro de texto
            val textoSeguro = EscuchaNotificacionesService.neutralizarEnlaces(textoPegado)
            etTextoSospechoso.setText(textoSeguro)

            // Enviamos el texto original a Gemini para comprobar si es una estafa
            analizarContenido(textoSospechoso = textoPegado, imagenBase64 = null)
        }

        findViewById<Button>(R.id.btnElegirCaptura).setOnClickListener {
            lectorVoz.detener()
            seleccionarImagenLauncher.launch("image/*")
        }

        findViewById<Button>(R.id.btnAnalizarTexto).setOnClickListener {
            val textoOriginal = etTextoSospechoso.text.toString().trim()
            if (textoOriginal.isEmpty()) {
                Toast.makeText(this, "Pega primero un mensaje para comprobarlo", Toast.LENGTH_LONG).show()
                lectorVoz.leer("Por favor, pega primero un mensaje en el recuadro blanco.")
                return@setOnClickListener
            }
            val textoSeguro = EscuchaNotificacionesService.neutralizarEnlaces(textoOriginal)
            etTextoSospechoso.setText(textoSeguro)
            analizarContenido(textoSospechoso = textoOriginal, imagenBase64 = null)
        }

        findViewById<Button>(R.id.btnBorrarTexto).setOnClickListener {
            lectorVoz.detener()
            etTextoSospechoso.setText("")
            cardResultado.visibility = View.GONE
            ultimoResultado = null
            Toast.makeText(this, "Texto borrado", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnCerrarResultado).setOnClickListener {
            lectorVoz.detener()
            cardResultado.visibility = View.GONE
            ultimoResultado = null
        }

        findViewById<Button>(R.id.btnConfigurarFamiliar).setOnClickListener {
            mostrarDialogoFamiliar()
        }

        findViewById<Button>(R.id.btnRepetirVoz).setOnClickListener {
            ultimoResultado?.let { lectorVoz.leer(it.getTextoParaVoz()) }
        }

        findViewById<Button>(R.id.btnVerificarLlamada).setOnClickListener {
            lectorVoz.detener()
            mostrarDialogoComprobarLlamada()
        }

        btnAvisarFamiliar.setOnClickListener {
            ultimoResultado?.let { gestorAlertas.enviarAvisoWhatsApp(it) }
        }

        btnLlamar017.setOnClickListener {
            lectorVoz.detener()
            gestorAlertas.llamarAl017()
        }

        val btnSilenciarVoz = findViewById<Button>(R.id.btnSilenciarVoz)
        btnSilenciarVoz.text = if (lectorVoz.estaSilenciado()) "🔇" else "🔊"

        btnSilenciarVoz.setOnClickListener {
            val silenciado = lectorVoz.alternarSilencio()
            btnSilenciarVoz.text = if (silenciado) "🔇" else "🔊"
            val aviso = if (silenciado) "Voz desactivada" else "Voz activada"
            Toast.makeText(this, aviso, Toast.LENGTH_SHORT).show()
        }
    }

    private fun gestionarIntentCompartido(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra("EXTRA_AUTOMATICO", false)) {
            val textoSeguro = intent.getStringExtra("EXTRA_TEXTO_SEGURO") ?: ""
            val nivelStr = intent.getStringExtra("EXTRA_NIVEL") ?: "ROJO"
            val titulo = intent.getStringExtra("EXTRA_TITULO") ?: "PELIGRO DETECTADO"
            val explicacion = intent.getStringExtra("EXTRA_EXPLICACION") ?: ""
            val consejo = intent.getStringExtra("EXTRA_CONSEJO") ?: ""

            etTextoSospechoso.setText(textoSeguro)
            val nivel = try {
                NivelRiesgo.valueOf(nivelStr)
            } catch (_: Exception) {
                NivelRiesgo.ROJO
            }

            mostrarResultadoSemaforo(ResultadoSemaforo(nivel, titulo, explicacion, consejo))
            return
        }

        if (intent.action == Intent.ACTION_SEND) {
            val tipo = intent.type ?: return
            if (tipo.startsWith("image/")) {
                val imageUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (imageUri != null) {
                    procesarYAnalizarImagen(imageUri)
                }
            } else if (tipo == "text/plain") {
                val textoCompartido = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!textoCompartido.isNullOrBlank()) {
                    etTextoSospechoso.setText(EscuchaNotificacionesService.neutralizarEnlaces(textoCompartido))
                    analizarContenido(textoSospechoso = textoCompartido, imagenBase64 = null)
                }
            }
        }
    }

    private fun procesarYAnalizarImagen(uri: Uri) {
        mostrarEstadoCargando(true)
        Thread {
            val base64 = procesadorImagen.convertirUriABase64(uri)
            runOnUiThread {
                if (base64 != null) {
                    analizarContenido(textoSospechoso = null, imagenBase64 = base64)
                } else {
                    mostrarEstadoCargando(false)
                    Toast.makeText(this, "No se pudo leer esa imagen", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun analizarContenido(textoSospechoso: String?, imagenBase64: String?) {
        mostrarEstadoCargando(true)

        analizadorFraude.analizar(textoSospechoso, imagenBase64, object : AnalizadorFraude.ResultadoCallback {
            override fun onExito(resultado: ResultadoSemaforo) {
                runOnUiThread {
                    mostrarEstadoCargando(false)
                    mostrarResultadoSemaforo(resultado)
                }
            }

            override fun onError(mensajeError: String) {
                runOnUiThread {
                    mostrarEstadoCargando(false)
                    Toast.makeText(this@MainActivity, mensajeError, Toast.LENGTH_LONG).show()
                    lectorVoz.leer(mensajeError)
                }
            }
        })
    }

    private fun mostrarResultadoSemaforo(resultado: ResultadoSemaforo) {
        ultimoResultado = resultado
        cardResultado.visibility = View.VISIBLE

        tvTituloSemaforo.text = resultado.tituloCorto
        tvExplicacion.text = EscuchaNotificacionesService.neutralizarEnlaces(resultado.explicacionSencilla)
        tvConsejo.text = "QUÉ HACER: ${resultado.consejoAccion}"

        val nombreFamiliar = gestorAlertas.obtenerNombreConfianza()
        btnAvisarFamiliar.text = if (nombreFamiliar.isNotBlank()) {
            "AVISAR A ${nombreFamiliar.uppercase()}"
        } else {
            "AVISAR A UN FAMILIAR"
        }

        when (resultado.nivel) {
            NivelRiesgo.ROJO -> {
                layoutColorSemaforo.setBackgroundColor(Color.parseColor("#C62828"))
                btnAvisarFamiliar.visibility = View.VISIBLE
                btnLlamar017.visibility = View.VISIBLE
            }
            NivelRiesgo.AMARILLO -> {
                layoutColorSemaforo.setBackgroundColor(Color.parseColor("#EF6C00"))
                btnAvisarFamiliar.visibility = View.VISIBLE
                btnLlamar017.visibility = View.VISIBLE
            }
            NivelRiesgo.VERDE -> {
                layoutColorSemaforo.setBackgroundColor(Color.parseColor("#2E7D32"))
                btnAvisarFamiliar.visibility = View.GONE
                btnLlamar017.visibility = View.GONE
            }
        }

        lectorVoz.leer(resultado.getTextoParaVoz())
    }

    private fun mostrarEstadoCargando(cargando: Boolean) {
        layoutCargando.visibility = if (cargando) View.VISIBLE else View.GONE
        if (cargando) {
            cardResultado.visibility = View.GONE
        }
    }

    private fun mostrarDialogoComprobarLlamada() {
        val fondoCaja = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#F8FAFC"))
            setStroke(3, Color.parseColor("#CBD5E1"))
            cornerRadius = 24f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 28, 48, 12)
        }

        val tvReglaOro = TextView(this).apply {
            text = "⚠️ REGLA DE ORO: Si dicen ser tu banco, luz/teléfono o un familiar pidiendo dinero urgente: CUELGA SIEMPRE."
            textSize = 15f
            setTextColor(Color.parseColor("#991B1B"))
            setBackgroundColor(Color.parseColor("#FEE2E2"))
            setPadding(24, 20, 24, 20)
        }

        val filaNumero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 28, 0, 16)
        }

        val inputTelefono = EditText(this).apply {
            hint = "Número (ej: +212600...)"
            textSize = 17f
            background = fondoCaja
            setPadding(28, 24, 28, 24)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnPegarNum = Button(this).apply {
            text = "📋 Pegar"
            textSize = 14f
            setOnClickListener {
                val clip = (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.trim() ?: ""
                if (clip.isNotEmpty()) {
                    inputTelefono.setText(clip)
                } else {
                    Toast.makeText(this@MainActivity, "No hay ningún número copiado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        filaNumero.addView(inputTelefono)
        filaNumero.addView(btnPegarNum)

        val inputMotivo = EditText(this).apply {
            hint = "¿Qué te han dicho? (Opcional: ej. subida de luz, falso banco...)"
            textSize = 16f
            background = fondoCaja.constantState?.newDrawable()?.mutate()
            setPadding(28, 24, 28, 24)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }

        layout.addView(tvReglaOro)
        layout.addView(filaNumero)
        layout.addView(inputMotivo)

        AlertDialog.Builder(this)
            .setTitle("📞 Comprobar Llamada")
            .setView(layout)
            .setPositiveButton("Analizar") { _, _ ->
                val numero = inputTelefono.text.toString().trim()
                val motivo = inputMotivo.text.toString().trim()

                if (numero.isEmpty() && motivo.isEmpty()) {
                    Toast.makeText(this, "Escribe el número o lo que te han dicho", Toast.LENGTH_SHORT).show()
                } else {
                    val resumenPantalla = buildString {
                        if (numero.isNotEmpty()) append("Llamada del número: $numero\n")
                        if (motivo.isNotEmpty()) append("Motivo: $motivo")
                    }.trim()

                    etTextoSospechoso.setText(resumenPantalla)

                    val consultaLlamada = """
                        El usuario en España ha recibido una llamada telefónica sospechosa.
                        Número llamante: "${numero.ifEmpty { "No indicado" }}".
                        Relato de la llamada: "${motivo.ifEmpty { "No indicado" }}".
                        Analiza el prefijo (internacional de riesgo, tarificación especial 807/902/118 o estándar) y el motivo (falsa operadora, falso banco, falso soporte técnico o familiar en apuros).
                    """.trimIndent()

                    analizarContenido(textoSospechoso = consultaLlamada, imagenBase64 = null)
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun mostrarDialogoFamiliar() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        val inputNombre = EditText(this).apply {
            hint = "Nombre (ej. Hijo, Marta)"
            setText(gestorAlertas.obtenerNombreConfianza())
            textSize = 18f
        }
        val inputTelefono = EditText(this).apply {
            hint = "Teléfono móvil (ej. 612345678)"
            setText(gestorAlertas.obtenerTelefonoConfianza().removePrefix("34"))
            textSize = 18f
        }

        layout.addView(inputNombre)
        layout.addView(inputTelefono)

        AlertDialog.Builder(this)
            .setTitle("Persona de confianza")
            .setMessage("Si recibes una estafa, podrás avisarle por WhatsApp con un solo toque.")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                gestorAlertas.guardarContactoConfianza(
                    inputNombre.text.toString(),
                    inputTelefono.text.toString()
                )
                Toast.makeText(this, "Contacto guardado correctamente", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroy() {
        lectorVoz.liberarRecursos()
        super.onDestroy()
    }
}
