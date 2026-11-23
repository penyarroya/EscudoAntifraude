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
    private var ultimoNumeroAnalizado: String = ""

    private var limpiezaInicialHecha = false


    private val seleccionarImagenLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            procesarYAnalizarImagen(uri)
        }
    }
    private val permisoContactosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Si lo concede, la búsqueda en agenda funcionará automáticamente */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lectorVoz = LectorVoz(this)
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !limpiezaInicialHecha) {
            limpiezaInicialHecha = true
            limpiarPortapapeles()
        }
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
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CONTACTS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permisoContactosLauncher.launch(android.Manifest.permission.READ_CONTACTS)
        }

        fun crearFondo(colorFondo: String, colorBorde: String, radio: Float = 28f) =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor(colorFondo))
                setStroke(3, Color.parseColor(colorBorde))
                cornerRadius = radio
            }

        val layoutPrincipal = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
            setPadding(44, 36, 44, 36)
        }

        // 1. Cabecera limpia
        val tvTitulo = TextView(this).apply {
            text = "📞 Escudo de Llamadas"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
            setPadding(0, 0, 0, 20)
        }

        // 2. Tarjeta de Regla de Oro
        val tvReglaOro = TextView(this).apply {
            text = "⚠️ REGLA DE ORO: Si dicen ser tu banco, luz/teléfono o un familiar pidiendo dinero urgente: CUELGA SIEMPRE."
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#991B1B"))
            background = crearFondo("#FEF2F2", "#FECACA", 24f)
            setPadding(28, 22, 28, 22)
        }

        var fueAlHistorial = false

        // 3. Botón estilizado para ir al historial nativo
        val btnVerHistorial = Button(this).apply {
            text = "📲 VER MIS ÚLTIMAS LLAMADAS"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#0052CC"))
            background = crearFondo("#EFF6FF", "#93C5FD", 24f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
                bottomMargin = 24
            }
            setOnClickListener {
                fueAlHistorial = true
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.provider.CallLog.Calls.CONTENT_URI))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_DIAL))
                }
            }
        }

        // 4. Fila del número con botones Limpiar (✕) y Pegar (📋) a juego
        val filaNumero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }

        val inputTelefono = EditText(this).apply {
            hint = "Número (ej: +212600...)"
            textSize = 17f
            setTextColor(Color.parseColor("#102A43"))
            setHintTextColor(Color.parseColor("#829AB1"))
            background = crearFondo("#FFFFFF", "#CBD5E1", 24f)
            setPadding(28, 26, 28, 26)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnLimpiarNum = Button(this).apply {
            text = "✕"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#C62828"))
            background = crearFondo("#FEE2E2", "#FCA5A5", 24f)
            layoutParams = LinearLayout.LayoutParams(115, 115).apply { leftMargin = 12 }
            setOnClickListener { inputTelefono.setText("") }
        }

        val btnPegarNum = Button(this).apply {
            text = "📋"
            textSize = 16f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = crearFondo("#0F766E", "#0F766E", 24f)
            layoutParams = LinearLayout.LayoutParams(115, 115).apply { leftMargin = 12 }
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
        filaNumero.addView(btnLimpiarNum)
        filaNumero.addView(btnPegarNum)

        // 5. Campo opcional para el motivo de la llamada
        val inputMotivo = EditText(this).apply {
            hint = "¿Qué te han dicho? (Opcional: ej. subida de luz, falso banco...)"
            textSize = 16f
            setTextColor(Color.parseColor("#102A43"))
            setHintTextColor(Color.parseColor("#829AB1"))
            background = crearFondo("#FFFFFF", "#CBD5E1", 24f)
            setPadding(28, 26, 28, 26)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 28 }
        }

        // 6. Fila inferior de botones grandes alineados (Cerrar y Analizar)
        val filaAcciones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val dialogo = AlertDialog.Builder(this)
            .setView(layoutPrincipal)
            .create()

        val btnCerrarDialogo = Button(this).apply {
            text = "CERRAR"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#334E68"))
            background = crearFondo("#E2E8F0", "#CBD5E1", 24f)
            layoutParams = LinearLayout.LayoutParams(0, 125, 1f).apply { rightMargin = 14 }
            setOnClickListener { dialogo.dismiss() }
        }

        val btnAnalizarLlamada = Button(this).apply {
            text = "ANALIZAR"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#FFFFFF"))
            background = crearFondo("#102A43", "#102A43", 24f)
            layoutParams = LinearLayout.LayoutParams(0, 125, 1.4f)
            setOnClickListener {
                val numero = inputTelefono.text.toString().trim()
                val motivo = inputMotivo.text.toString().trim()

                if (numero.isEmpty() && motivo.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Escribe el número o lo que te han dicho", Toast.LENGTH_SHORT).show()
                } else {
                    if (numero.isNotEmpty()) ultimoNumeroAnalizado = numero

                    val nombreContacto = if (numero.isNotEmpty()) buscarNombreEnAgenda(numero) else null
                    val infoAgenda = when {
                        numero.isEmpty() -> "No se ha facilitado número."
                        nombreContacto != null -> "SÍ ESTÁ EN SU AGENDA DE CONTACTOS guardado como: \"$nombreContacto\"."
                        else -> "NO ESTÁ EN SU AGENDA DE CONTACTOS (es un número desconocido para el usuario)."
                    }

                    val resumenPantalla = buildString {
                        if (numero.isNotEmpty()) {
                            append("Llamada del número: $numero")
                            if (nombreContacto != null) append(" (Contacto: $nombreContacto)")
                            append("\n")
                        }
                        if (motivo.isNotEmpty()) append("Motivo: $motivo")
                    }.trim()

                    etTextoSospechoso.setText(resumenPantalla)

                    val consultaLlamada = """
                        El usuario en España ha recibido una llamada telefónica.
                        Número llamante: "${numero.ifEmpty { "No indicado" }}".
                        Estado en agenda del teléfono: $infoAgenda
                        Relato de lo que le han dicho: "${motivo.ifEmpty { "No indicado" }}".
                        Instrucciones:
                        1. Indica claramente al principio si el número está guardado en su agenda (y con qué nombre) o si es un número desconocido.
                        2. Si está en su agenda pero el relato pide dinero urgente, claves o transferencias, advierte que puede ser una suplantación de identidad (spoofing) o teléfono robado.
                        3. Si es desconocido, analiza el prefijo (internacional de riesgo, tarificación especial 807/902/118 o estándar) y el relato.
                    """.trimIndent()

                    limpiarPortapapeles()
                    dialogo.dismiss()
                    analizarContenido(textoSospechoso = consultaLlamada, imagenBase64 = null)
                }
            }
        }

        filaAcciones.addView(btnCerrarDialogo)
        filaAcciones.addView(btnAnalizarLlamada)

        layoutPrincipal.addView(tvTitulo)
        layoutPrincipal.addView(tvReglaOro)
        layoutPrincipal.addView(btnVerHistorial)
        layoutPrincipal.addView(filaNumero)
        layoutPrincipal.addView(inputMotivo)
        layoutPrincipal.addView(filaAcciones)

        // Rellena automáticamente solo si el usuario vuelve de ver sus llamadas con un número nuevo
        dialogo.setOnShowListener {
            dialogo.window?.setBackgroundDrawable(crearFondo("#F5F7FA", "#CBD5E1", 36f))
            dialogo.window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener { hasFocus ->
                if (hasFocus && fueAlHistorial) {
                    fueAlHistorial = false
                    val clip = (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: ""
                    if (clip.matches(Regex("^[+0-9\\s()-]{5,20}$")) && clip != ultimoNumeroAnalizado) {
                        inputTelefono.setText(clip)
                    }
                }
            }
        }

        dialogo.show()
    }
    private fun mostrarDialogoFamiliar() {
        fun crearFondo(colorFondo: String, colorBorde: String, radio: Float = 28f) =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor(colorFondo))
                setStroke(3, Color.parseColor(colorBorde))
                cornerRadius = radio
            }

        val layoutPrincipal = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
            setPadding(44, 36, 44, 36)
        }

        // 1. Título limpio a juego con la app
        val tvTitulo = TextView(this).apply {
            text = "👤 Persona de confianza"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#102A43"))
            setPadding(0, 0, 0, 18)
        }

        // 2. Tarjeta explicativa en verde suave (a juego con WhatsApp)
        val tvInfo = TextView(this).apply {
            text = "💬 Si recibes una estafa, podrás pedirle ayuda por WhatsApp con un solo toque."
            textSize = 15f
            setTextColor(Color.parseColor("#065F46"))
            background = crearFondo("#ECFDF5", "#A7F3D0", 24f)
            setPadding(28, 22, 28, 22)
        }

        // 3. Etiqueta y campo Nombre
        val tvLabelNombre = TextView(this).apply {
            text = "Nombre del familiar o amigo:"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#334E68"))
            setPadding(4, 24, 0, 8)
        }

        val inputNombre = EditText(this).apply {
            hint = "Ej: Hijo, Marta..."
            setText(gestorAlertas.obtenerNombreConfianza())
            textSize = 18f
            setTextColor(Color.parseColor("#102A43"))
            setHintTextColor(Color.parseColor("#829AB1"))
            background = crearFondo("#FFFFFF", "#CBD5E1", 24f)
            setPadding(28, 24, 28, 24)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        // 4. Etiqueta y campo Teléfono con botón de borrado rápido (✕)
        val tvLabelTelefono = TextView(this).apply {
            text = "Teléfono móvil con WhatsApp:"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#334E68"))
            setPadding(4, 22, 0, 8)
        }

        val filaTelefono = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 30 }
        }

        val inputTelefono = EditText(this).apply {
            hint = "Ej: 612345678"
            setText(gestorAlertas.obtenerTelefonoConfianza().removePrefix("34"))
            textSize = 18f
            setTextColor(Color.parseColor("#102A43"))
            setHintTextColor(Color.parseColor("#829AB1"))
            background = crearFondo("#FFFFFF", "#CBD5E1", 24f)
            setPadding(28, 24, 28, 24)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnLimpiarTel = Button(this).apply {
            text = "✕"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#C62828"))
            background = crearFondo("#FEE2E2", "#FCA5A5", 24f)
            layoutParams = LinearLayout.LayoutParams(115, 115).apply { leftMargin = 12 }
            setOnClickListener { inputTelefono.setText("") }
        }

        filaTelefono.addView(inputTelefono)
        filaTelefono.addView(btnLimpiarTel)

        // 5. Botones inferiores grandes (Cancelar y Guardar)
        val filaBotones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val dialogo = AlertDialog.Builder(this)
            .setView(layoutPrincipal)
            .create()

        val btnCancelar = Button(this).apply {
            text = "CANCELAR"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#334E68"))
            background = crearFondo("#E2E8F0", "#CBD5E1", 24f)
            layoutParams = LinearLayout.LayoutParams(0, 125, 1f).apply { rightMargin = 14 }
            setOnClickListener { dialogo.dismiss() }
        }

        val btnGuardar = Button(this).apply {
            text = "GUARDAR"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#FFFFFF"))
            background = crearFondo("#102A43", "#102A43", 24f)
            layoutParams = LinearLayout.LayoutParams(0, 125, 1.4f)
            setOnClickListener {
                gestorAlertas.guardarContactoConfianza(
                    inputNombre.text.toString(),
                    inputTelefono.text.toString()
                )
                Toast.makeText(this@MainActivity, "Contacto guardado correctamente", Toast.LENGTH_SHORT).show()
                dialogo.dismiss()
            }
        }

        filaBotones.addView(btnCancelar)
        filaBotones.addView(btnGuardar)

        layoutPrincipal.addView(tvTitulo)
        layoutPrincipal.addView(tvInfo)
        layoutPrincipal.addView(tvLabelNombre)
        layoutPrincipal.addView(inputNombre)
        layoutPrincipal.addView(tvLabelTelefono)
        layoutPrincipal.addView(filaTelefono)
        layoutPrincipal.addView(filaBotones)

        dialogo.setOnShowListener {
            dialogo.window?.setBackgroundDrawable(crearFondo("#F5F7FA", "#CBD5E1", 36f))
        }

        dialogo.show()
    }

    private fun buscarNombreEnAgenda(numero: String): String? {
        if (numero.isBlank()) return null
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_CONTACTS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(numero)
            )
            contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
    private fun limpiarPortapapeles() {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            }
        } catch (_: Exception) {
            // Ignoramos si el sistema restringe el acceso cuando la ventana no tiene foco
        }
    }

    override fun onDestroy() {
        lectorVoz.liberarRecursos()
        super.onDestroy()
    }
}
