#### 🛡️ Escudo Antifraude (v2.2)

Aplicación móvil nativa para Android diseñada para proteger a **personas mayores o con brecha digital** frente a estafas en **SMS, WhatsApp, correos electrónicos, documentos y llamadas telefónicas (*vishing*)** de forma sencilla, visual y por voz. 

Desarrollada bajo una **arquitectura Modulith (Monolito Modular)** en Kotlin con **coste operativo y de infraestructura de 0 €** y **100 % compatible con la instalación directa mediante APK** (sin bloqueos de permisos restringidos en Google Play Protect). 

#### ✨ Características Principales (v2.2)

* **Barra de 3 Acciones Rápidas (Diseño Limpio):** 

    * **📷 FOTO:** Abre el selector de imágenes de Android (Google Fotos, Galería y explorador de carpetas/discos locales) para analizar capturas de pantalla, cartas o décimos de lotería.
    * **📋 PEGAR:** Lee el mensaje copiado en el portapapeles con un solo toque, neutraliza cualquier enlace automáticamente y lo analiza al instante.
    * **📞 LLAMADA:** Escudo preventivo contra estafas telefónicas (*vishing*). Muestra la **Regla de Oro** (*"Cuelga siempre si te piden dinero o claves"*), incluye pegado rápido del número y analiza tanto el prefijo internacional/tarificación especial como el relato de la llamada.
* **Neutralización Total de Enlaces (*Zero-Click Protection*):** Convierte automáticamente cualquier URL (http://, https://, www.) en texto inofensivo ([ENLACE BLOQUEADO POR SEGURIDAD]) y desactiva los hipervínculos (autoLink="none"), impidiendo pulsaciones accidentales.
* **Control de Contexto Temporal Dinámico:** Inyecta automáticamente la fecha real del dispositivo en cada consulta a la IA para validar correctamente fechas de sorteos, recibos o documentos del año en curso.
* **Accesibilidad Visual y Auditiva:** 

    * **Semáforo de Alto Contraste:** Clasifica el riesgo en **ROJO** (Estafa), **AMARILLO** (Precaución) o **VERDE** (Seguro) con botón rápido de cierre (✕) y borrado (🗑️ BORRAR).
    * **Voz Pausada (SSML) con Control Discreto de Silencio (🔊 / 🔇):** Lee el diagnóstico en español de España (es-ES) a velocidad pausada (85 %) con pausas entre frases, e incluye botón de silencio persistente para lugares públicos.
* **Red de Ayuda en un Toque:** Envío de resumen pre-redactado por **WhatsApp** a una persona de confianza y llamada gratuita directa al **017 (INCIBE)** sin requerir permisos intrusivos.
* **Privacidad Total (*Zero-Retention*):** Sin servidores propios ni bases de datos remotas. Todo el procesamiento ocurre en la memoria RAM del dispositivo.

#### 🏗️ Arquitectura del Sistema (Modulith)

Mostrar código

Ocultar código

Descargar SVG del diagrama

ASCII

EscudoAntifraude/
├── :app        # UI accesible, Escudo de Llamadas, Portapapeles, Selector de Fotos e Intent Filters
├── :core       # Modelos de dominio (ResultadoSemaforo) y motor de voz SSML con silencio (LectorVoz)
├── :scanner    # Compresión segura de imágenes en RAM (inSampleSize) y codificación Base64
├── :analyzer   # Cliente HTTP asíncrono (OkHttp), contexto temporal dinámico e integración Gemini API
└── :alert      # Persistencia local de contacto de confianza, Deep Links WhatsApp y 017

Usa el código con precaución.

Copiar texto del código en el portapapeles.

#### 🛠️ Stack Tecnológico

* **Lenguaje:** Kotlin (100 % interoperable con Java sobre JVM)
* **SDK Android:** Min SDK 24 (Android 7.0 Nougat) | Target SDK 35 (Android 15)
* **IA Multimodal:** Google Gemini API (gemini-3.5-flash-lite en capa gratuita de Google AI Studio)
* **Red y Serialización:** OkHttp 4.12 + org.json
* **Audio:** android.speech.tts.TextToSpeech con SSML nativo (<prosody> y <break>)

#### 🚀 Instalación y Actualización (Usuarios Finales)

1. Descarga el archivo **EscudoAntifraude_v2.2.apk** desde la sección **Releases** de este repositorio.
2. Ábrelo en tu teléfono Android (si aparece el aviso preventivo de Google Play Protect por ser una app independiente, pulsa en **Más detalles > Instalar de todos modos**). Si ya tenías instalada una versión anterior (v2.0 o v2.1), el sistema actualizará encima manteniendo tu contacto de confianza guardado.
3. Pulsa en **Familiar** (arriba a la derecha) para guardar el nombre y teléfono de tu persona de confianza.

#### ⚙️ Configuración para Desarrolladores

1. Obtén una clave de API gratuita en **Google AI Studio**.
2. Abre app/src/main/java/es/jlrn/escudoantifraude/MainActivity.kt.
3. Sustituye "TU_API_KEY_DE_GEMINI_AQUI" por tu clave personal antes de generar el APK firmado (Build > Generate Signed App Bundle / APK...).
