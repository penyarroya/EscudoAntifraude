# ⚙️ Especificación Técnica y Arquitectura de Software: Escudo Antifraude (v2.5)

**Clasificación:** Documento de Arquitectura y Seguridad de Software  
**Plataforma Objetivo:** Android OS (API 24 – API 35 / Compile SDK 37 / JVM Kotlin)  
**Patrón Arquitectónico:** Modulith (Monolito Modular Desacoplado)  
**Modelo de Privacidad:** *Zero-Retention* / Procesamiento Efímero en Memoria (RAM)  
**Coste de Infraestructura (TCO):** 0,00 € (Arquitectura *Serverless-Client* directa)

---

## 1. Resumen Ejecutivo y Problema de Ingeniería
**Escudo Antifraude** es una solución nativa de ciberseguridad defensiva orientada a mitigar vectores de ingeniería social (*smishing*, *phishing*, *vishing* y fraude documental) dirigidos a colectivos vulnerables o con brecha digital en España.

Desde el punto de vista de la ingeniería de sistemas, el diseño resuelve **cinco restricciones críticas**:
1. **Eliminación de infraestructura intermedia:** Comunicación directa TLS entre el cliente móvil y la API de inferencia multimodal, eliminando costes de servidor, latencia adicional y vectores de fuga de datos en bases de datos centralizadas.
2. **Compatibilidad universal sin fricción (*Sideloading-Safe*):** Diseño bajo el principio de menor privilegio (`INTERNET` y consulta opcional de agenda `READ_CONTACTS`, evitando `READ_CALL_LOG` o `NotificationListenerService`) para superar los bloqueos heurísticos de *Google Play Protect (Enhanced Fraud Protection)* en Android 14/15/16 durante la distribución directa por APK.
3. **Resiliencia ante errores de usuario (*Zero-Click Hardening*):** Sanitización activa de hipervínculos tanto en entrada como en salida, limpieza automática del portapapeles en ciclo de ventana y desactivación de renderizado de enlaces en UI.
4. **Endurecimiento interno y protección de integridad (v2.5):** Defensa multicapa frente a *Prompt Injection*, cargas de archivos maliciosos (*Magic Bytes* y límites de descompresión), depuración en producción y reempaquetado no autorizado del APK.
5. **Accesibilidad cognitiva y sensorial:** Síntesis de voz parametrizada mediante SSML nativo y UI de alto contraste con baja carga cognitiva.

---

## 2. Arquitectura del Sistema: Modulith (Monolito Modular)
El proyecto abandona el enfoque monolítico tradicional (`app`-único) en favor de un **Modulith compuesto por 5 módulos Gradle con grafo de dependencias acíclico y unidireccional** hacia el núcleo `:core`:

```text
                  ┌──────────────────────────────────────┐
                  │                :app                  │
                  │ (UI, Orquestación, Agenda, SHA-256)  │
                  └──────┬───────────┬───────────┬───────┘
                         │           │           │
                         ▼           ▼           ▼
                  ┌────────────┐ ┌───────────┐ ┌─────────┐
                  │  :scanner  │ │ :analyzer │ │ :alert  │
                  │ (Imágenes) │ │  (IA/Red) │ │ (Apoyo) │
                  └──────┬─────┘ └─────┬─────┘ └────┬────┘
                         │             │            │
                         └─────────────┼────────────┘
                                       ▼
                                ┌─────────────┐
                                │    :core    │
                                │ (Dominio/TTS)│
                                └─────────────┘
```

### 2.1. Desglose de Responsabilidades por Módulo
* **`:core` (Núcleo de Dominio y Motor Acústico):**
  * **Aislamiento:** Cero dependencias de módulos internos ni librerías de red/UI externas.
  * **Modelo de Dominio (`ResultadoSemaforo`):** *Data class* inmutable que encapsula `NivelRiesgo` (`ROJO`, `AMARILLO`, `VERDE`), `tituloCorto`, `explicacionSencilla` y `consejoAccion`, exponiendo métodos puros para serialización de locución (`getTextoParaVoz()`).
  * **Motor TTS (`LectorVoz`):** Gestiona el ciclo de vida de `android.speech.tts.TextToSpeech` forzando el motor neuronal de Google (`com.google.android.tts`) cuando está disponible. Implementa cola de espera previa a la inicialización (`textoPendiente`), filtrado de caracteres XML/emojis, construcción de etiquetas **SSML** (`<speak><prosody rate="85%">...<break time="500ms"/>...</prosody></speak>`) y persistencia de estado de silencio en `SharedPreferences`.
* **`:scanner` (Subsistema de Visión, Validación Binaria y Gestión de Memoria):**
  * **Validación Previa de Seguridad (`esImagenSegura`):** Inspección de tamaño físico máximo (`15 MB`) y verificación binaria de cabeceras reales (*Magic Bytes* para `JPEG`, `PNG`, `WEBP` y `HEIC/HEIF`), bloqueando archivos ejecutables, scripts o documentos renombrados antes de invocar el decodificador gráfico.
  * **Prevención de `OutOfMemoryError` y *Decompression Bombs*:** Decodificación en dos pasadas mediante `ContentResolver` y `BitmapFactory.Options`. La primera pasada (`inJustDecodeBounds = true`) rechaza dimensiones corruptas o superiores a `8192 px` sin alojar píxeles en *heap*; la segunda calcula dinámicamente el factor de submuestreo `inSampleSize` (potencia de 2) para acotar la dimensión máxima a `1024 px`.
  * **Optimización de Payload:** Compresión JPEG en memoria (`ByteArrayOutputStream`, calidad `80 %`) y codificación `Base64.NO_WRAP` con liberación explícita del `Bitmap` (`recycle()`) tras la conversión.
* **`:analyzer` (Motor de Inferencia Multimodal, Red y Sanitización):**
  * **Cliente HTTP (`OkHttpClient`):** Configurado con *timeouts* explícitos y reintentos ante microcortes de red móvil, enviando cabeceras de identidad de aplicación Android (`X-Android-Package` y `X-Android-Cert`).
  * **Defensa *Anti-Prompt Injection* (`sanitizarEntrada` / `neutralizarSalida`):** Filtrado de caracteres de control invisibles (`\p{Cc}`), acotación de longitud (`3000` caracteres), encapsulado estructural de la entrada no confiable mediante delimitadores XML (`<mensaje_sospechoso>`) con bloqueo de cierres de etiqueta inyectados, y sanitización de URLs en las respuestas generadas por el modelo.
  * **Inyección de Contexto Temporal Dinámico:** Inyecta en tiempo de ejecución la fecha local del dispositivo (`SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))`), evitando falsos positivos temporales en sorteos, recibos o notificaciones oficiales.
  * **Matriz Universal de Evaluación Telefónica:** Reglas deterministas para tarificación especial (`803/806/807/902/905/118`), prefijos internacionales extranjeros, numeración nacional española (`+34` / fijos provinciales / móviles) y teléfonos oficiales de emergencia (`017`, `112`, `091`, etc.).
* **`:alert` (Subsistema de Escalado y Ayuda Humana):**
  * **Persistencia Local (`GestorAlertas`):** Almacenamiento privado (`Context.MODE_PRIVATE`) del contacto de confianza con normalización automática de prefijo internacional español (`+34`).
  * **Integración Externa sin Permisos:** Invocación de WhatsApp mediante *Deep Link* HTTPS (`https://wa.me...`) y apertura del marcador telefónico oficial hacia el **017 (Línea de Ayuda en Ciberseguridad de INCIBE)** mediante `Intent.ACTION_DIAL`.
* **`:app` (Capa de Presentación, Orquestación y Verificación de Integridad):**
  * Implementa `MainActivity`, verificación criptográfica en arranque (`esApkIntegro()`), receptores `Intent.ACTION_SEND`, gestión segura del portapapeles con limpieza automática en `onWindowFocusChanged`, diálogo preventivo de auditoría de llamadas (*vishing*) con consulta de contactos (`ContactsContract.PhoneLookup`) y ofuscación **R8** en compilación `release`.

---

## 3. Flujos de Datos y Secuencia de Ejecución

### 3.1. Pipeline de Análisis Multimodal (Texto / Imagen / Llamada)
```text
[Arranque App] ──> (0) Verificación Firma SHA-256 + Anti-Debugger
                              │
                              ▼
[Entrada Usuario] ──> (1) Sanitización XML / Validación Magic Bytes + RAM
                              │
                              ▼
                      (2) Inyección Prompt Blindado + Fecha Sistema (es-ES)
                              │
                              ▼
                      (3) Petición HTTPS/TLS POST -> Gemini API
                              │
                              ▼
                      (4) Parser JSON + Neutralización Salida + Fallback
                              │
                              ▼
[Salida Dual] ◄───────┴──> UI Semáforo (Alto Contraste) + Locución SSML
```

### 3.2. Flujo de Auditoría Preventiva de Llamadas (*Vishing*)
1. El usuario acciona **`📞 LLAMADA`** y visualiza en primer plano la **Regla de Oro** de interrupción de urgencia psicológica (*"Cuelga siempre si dicen ser tu banco, luz/teléfono o un familiar pidiendo dinero"*).
2. Dispone de acceso directo al historial nativo del sistema (`Intent.ACTION_VIEW` sobre `CallLog.Calls.CONTENT_URI`) sin requerir el permiso restringido `READ_CALL_LOG`, con relleno automático inteligente al regresar.
3. El sistema verifica automáticamente si el teléfono existe en la agenda local (`ContactsContract.PhoneLookup`), informando por su nombre si es un contacto guardado (con advertencia preventiva de suplantación o robo) o marcándolo como desconocido.
4. El motor evalúa simultáneamente:
  * **Análisis de numeración:** Prefijos internacionales de riesgo (*wangiri*), numeración de tarificación especial (`803`, `806`, `807`, `902`, `905`, `118xx`) o líneas nacionales.
  * **Análisis semántico del relato:** Patrones conocidos en España (falsa operadora energética/telecomunicaciones, falso soporte técnico, falso gestor bancario solicitando códigos OTP o estafa del "hijo en apuros").
## 4. Modelo de Amenazas, Seguridad y Privacidad

| Vector de Amenaza / Riesgo | Mitigación Implementada | Capa / Módulo |
| :--- | :--- | :--- |
| **Pulsación accidental de enlaces maliciosos (*Phishing/Smishing*)** | **Doble barrera sin clics (*Zero-Click*):** Reemplazo léxico de URLs tanto en entrada como en salida (`[ENLACE BLOQUEADO]`) y desactivación de hipervínculos en vistas (`autoLink="none"`). | `:app` / `:analyzer` |
| **Inyección de instrucciones maliciosas (*Prompt Injection*)** | Encapsulado de datos no confiables en etiquetas XML (`<mensaje_sospechoso>`), filtrado de caracteres de control invisibles (`\p{Cc}`), límite de 3.000 caracteres y directiva estricta de aislamiento en el prompt del sistema. | `:analyzer` |
| **Carga de archivos maliciosos o bombas de descompresión** | Inspección binaria de cabeceras reales (*Magic Bytes* `JPG/PNG/WEBP/HEIC`), límite físico de `15 MB` y bloqueo de resoluciones superiores a `8192 px` antes de asignar memoria *heap*. | `:scanner` |
| **Reempaquetado del APK e ingeniería inversa** | Verificación criptográfica en tiempo de ejecución de la huella **SHA-256** del certificado de firma (`esApkIntegro()`), bloqueo de depuradores activos (`Debug.isDebuggerConnected()`) y ofuscación de código con **R8** (`proguard-rules.pro` + `keep.xml`). | `:app` |
| **Fuga de datos sensibles en portapapeles** | Limpieza automática del portapapeles del sistema (`limpiarPortapapeles()`) al recuperar el foco de ventana (`onWindowFocusChanged`) y tras completar cada análisis. | `:app` |
| **Bloqueos heurísticos en instalación directa (*Play Protect*)** | Arquitectura de privilegios mínimos: eliminación de `READ_CALL_LOG` y `NotificationListenerService`, sustituidos por flujos nativos `ACTION_SEND`, portapapeles y visor externo de llamadas. | `:app` |
| **Exposición de credenciales de API en repositorio** | Inyección de `GEMINI_API_KEY` en tiempo de compilación desde `local.properties` (`resValue`) y exclusión del certificado `.jks` mediante `.gitignore`. | `:app` / Gradle |
---

## 5. Compilación, Firma Digital y Distribución Segura (v2.5)

### 5.1. Pipeline de Construcción y Ofuscación (`Release`)
* **Versionado del Artefacto:** `versionCode = 11` | `versionName = "2.5"` (`applicationId = "es.jlrn.escudoantifraude"`).
* **Endurecimiento del Bytecode (R8):** Activación de `isMinifyEnabled = true` e `isShrinkResources = true` en el bloque `buildTypes.release` de `:app`. R8 renombra clases, métodos y atributos internos (`es.jlrn.*`), elimina código muerto y suprime llamadas de diagnóstico (`android.util.Log.v/d/i`) mediante `proguard-rules.pro`.
* **Preservación de Recursos Críticos (`keep.xml`):** Declaración explícita mediante `tools:keep="@string/gemini_api_key"` en `res/values/keep.xml` para evitar que la compresión de recursos elimine la clave de API inyectada dinámicamente vía `resValue`.

### 5.2. Firma Criptográfica y Verificación de Integridad
* **Esquema de Firma:** Firma mediante certificado X.509 propio (`llave-antifraude.jks`, sujeto `CN=Nino`) bajo **APK Signature Scheme v2/v3**, garantizando actualizaciones directas (*in-place updates*) sin pérdida de preferencias locales (`SharedPreferences`).
* **Autoverificación en Tiempo de Ejecución (`esApkIntegro`):** Al invocar `MainActivity.onCreate()`, la aplicación consulta `PackageManager.GET_SIGNING_CERTIFICATES` (API 28+) y verifica que el resumen **SHA-256** del certificado firmante coincida exactamente con la huella oficial compilada, abortando la ejecución (`finishAffinity()`) ante cualquier intento de reempaquetado o depuración activa.

### 5.3. Checklist de Seguridad Pre-Publicación (GitHub / Releases)
1. **Aislamiento de Secretos:** Verificación mediante `.gitignore` de la exclusión de `local.properties` y almacenes de claves (`*.jks`, `*.keystore`).
2. **Sanitización de Código Público:** Sustitución del literal de huella SHA-256 en `MainActivity.kt` por un marcador genérico antes del *commit* en el repositorio público.
3. **Empaquetado Final:** Distribución del binario firmado **`EscudoAntifraude_v2.5.apk`** en la sección *Releases* con notas de compatibilidad para Android 7.0 – 16.
