# ⚙️ Especificación Técnica y Arquitectura de Software: Escudo Antifraude (v2.2)

**Clasificación:** Documento de Arquitectura y Seguridad de Software  
**Plataforma Objetivo:** Android OS (API 24 – API 35 / JVM Kotlin)  
**Patrón Arquitectónico:** Modulith (Monolito Modular Desacoplado)  
**Modelo de Privacidad:** *Zero-Retention* / Procesamiento Efímero en Memoria (RAM)  
**Coste de Infraestructura (TCO):** 0,00 € (Arquitectura *Serverless-Client* directa)

---

## 1. Resumen Ejecutivo y Problema de Ingeniería
**Escudo Antifraude** es una solución nativa de ciberseguridad defensiva orientada a mitigar vectores de ingeniería social (*smishing*, *phishing*, *vishing* y fraude documental) dirigidos a colectivos vulnerables o con brecha digital en España.

Desde el punto de vista de la ingeniería de sistemas, el diseño resuelve **cuatro restricciones críticas**:
1. **Eliminación de infraestructura intermedia:** Comunicación directa TLS entre el cliente móvil y la API de inferencia multimodal, eliminando costes de servidor, latencia adicional y vectores de fuga de datos en bases de datos centralizadas.
2. **Compatibilidad universal sin fricción (*Sideloading-Safe*):** Diseño bajo el principio de menor privilegio (`INTERNET` como único permiso en manifiesto) para superar los bloqueos heurísticos de *Google Play Protect (Enhanced Fraud Protection)* en Android 14/15/16 durante la distribución directa por APK.
3. **Resiliencia ante errores de usuario (*Zero-Click Hardening*):** Sanitización activa de hipervínculos tanto a nivel léxico como de renderizado en interfaz.
4. **Accesibilidad cognitiva y sensorial:** Síntesis de voz parametrizada mediante SSML nativo y UI de alto contraste con baja carga cognitiva.

---

## 2. Arquitectura del Sistema: Modulith (Monolito Modular)
El proyecto abandona el enfoque monolítico tradicional (`app`-único) en favor de un **Modulith compuesto por 5 módulos Gradle con grafo de dependencias acíclico y unidireccional** hacia el núcleo `:core`:

```text
                  ┌──────────────────────────────────────┐
                  │                :app                  │
                  │  (UI, Orquestación, Intents, Regex)  │
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
* **`:scanner` (Subsistema de Visión y Gestión de Memoria):**
    * **Prevención de `OutOfMemoryError`:** Implementa decodificación en dos pasadas mediante `ContentResolver` y `BitmapFactory.Options`. La primera pasada (`inJustDecodeBounds = true`) inspecciona dimensiones sin alojar píxeles en heap; la segunda calcula dinámicamente el factor de submuestreo `inSampleSize` (potencia de 2) para acotar la dimensión máxima a `1024px`.
    * **Optimización de Payload:** Compresión JPEG en memoria (`ByteArrayOutputStream`, calidad `75 %`, payload medio de `~150–220 KB`) y codificación `Base64.NO_WRAP` ejecutada íntegramente en hilos trabajadores (`Thread`) fuera del *Main UI Thread*.
* **`:analyzer` (Motor de Inferencia Multimodal y Red):**
    * **Cliente HTTP (`OkHttpClient`):** Configurado con *timeouts* explícitos de conexión y lectura (`30s`) para tolerar redes móviles rurales o de alta latencia.
    * **Inyección de Contexto Temporal Dinámico:** Resuelve la limitación de corte de conocimiento de los LLM inyectando en tiempo de ejecución la fecha local del dispositivo (`SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))`), evitando falsos positivos al auditar sorteos, recibos o documentos del año en curso.
    * **Determinismo de Salida:** *System prompt* estructurado que fuerza una respuesta JSON estricta de 4 campos con limpieza defensiva de bloques Markdown (` ```json `) y *fallback* seguro (`NivelRiesgo.AMARILLO`) ante respuestas malformadas.
* **`:alert` (Subsistema de Escalado y Ayuda Humana):**
    * **Persistencia Local (`GestorAlertas`):** Almacenamiento privado (`Context.MODE_PRIVATE`) del contacto de confianza con normalización automática de prefijo internacional español (`+34`).
    * **Integración Externa sin Permisos:** Invocación de WhatsApp mediante *Deep Link* HTTPS (`https://whatsapp.com...`) y apertura del marcador telefónico oficial hacia el **017 (Línea de Ayuda en Ciberseguridad de INCIBE)** mediante `Intent.ACTION_DIAL` (evitando `CALL_PHONE` para no requerir permisos intrusivos).
* **`:app` (Capa de Presentación y Orquestación):**
    * Implementa `MainActivity`, gestión del ciclo de vida, receptores `Intent.ACTION_SEND` (`text/plain` e `image/*`), lectura segura del portapapeles (`ClipboardManager`), diálogo preventivo de auditoría de llamadas (*vishing*) y sanitización de enlaces.
---

## 3. Flujos de Datos y Secuencia de Ejecución

### 3.1. Pipeline de Análisis Multimodal (Texto / Imagen / Llamada)
```text
[Entrada Usuario] ──> (1) Sanitización URLs / Escalado Bitmap RAM
                              │
                              ▼
                      (2) Inyección Prompt + Fecha Sistema (es-ES)
                              │
                              ▼
                      (3) Petición HTTPS/TLS POST -> Gemini API
                              │
                              ▼
                      (4) Parser JSON Estricto + Fallback Seguro
                              │
                              ▼
[Salida Dual] ◄───────┴──> UI Semáforo (Alto Contraste) + Locución SSML
```

### 3.2. Flujo de Auditoría Preventiva de Llamadas (*Vishing*)
1. El usuario acciona **`📞 LLAMADA`** y visualiza en primer plano la **Regla de Oro** de interrupción de urgencia psicológica (*"Cuelga siempre si dicen ser tu banco, luz/teléfono o un familiar pidiendo dinero"*).
2. Introduce o pega mediante botón dedicado (`📋 Pegar`) el número llamante y, opcionalmente, el relato verbal recibido.
3. El motor evalúa simultáneamente:
    * **Análisis de numeración:** Prefijos internacionales asociados a fraude *wangiri* (ej. `+212`, `+225`, `+355`), numeración de tarificación especial (`807`, `902`, `118xx`) o suplantación de identidad (*caller ID spoofing*).
    * **Análisis semántico del relato:** Patrones conocidos en España (falsa operadora energética/telecomunicaciones, falso soporte técnico, falso gestor bancario solicitando códigos OTP o estafa del "hijo en apuros").

---

## 4. Modelo de Amenazas, Seguridad y Privacidad

| Vector de Amenaza / Riesgo | Mitigación Implementada | Capa / Módulo |
| :--- | :--- | :--- |
| **Pulsación accidental de enlaces maliciosos (*Phishing/Smishing*)** | **Doble barrera *Zero-Click*:** Reemplazo léxico mediante regex `(https?://\|www\.)\S+` por `[ENLACE BLOQUEADO POR SEGURIDAD]` antes de renderizar + `android:autoLink="none"` y `android:linksClickable="false"` en vistas XML. | `:app` (`EscuchaNotificacionesService`, `activity_main.xml`) |
| **Fuga o retención de datos personales sensibles** | **Arquitectura *Zero-Retention*:** Ausencia total de *backend* propio, logs remotos o analíticas de terceros. Los datos viven exclusivamente en variables locales de memoria volátil durante el ciclo de petición HTTP. | Global (`:scanner`, `:analyzer`) |
| **Bloqueo de instalación por Google Play Protect** | **Principio de Menor Privilegio:** Único permiso declarado: `android.permission.INTERNET`. Eliminación deliberada de `BIND_NOTIFICATION_LISTENER_SERVICE`, `READ_CALL_LOG` y `READ_MEDIA_IMAGES`, sustituidos por `ClipboardManager`, `ACTION_SEND` y `GetContent`. | `:app` (`AndroidManifest.xml`) |
| **Falsos positivos por desfase temporal del LLM** | **Anclaje Temporal Dinámico:** Inyección de la fecha real del reloj del dispositivo en formato español (`dd 'de' MMMM 'de' yyyy`) dentro del contexto de cada inferencia. | `:analyzer` (`AnalizadorFraude.kt`) |
| **Agotamiento de memoria (`OOM`) con fotos de alta resolución** | **Submuestreo en dos pasadas:** Cálculo de `inSampleSize` antes de decodificar el mapa de bits y compresión JPEG acotada a `1024px`. | `:scanner` (`ProcesadorImagen.kt`) |
| **Alucinación o respuesta no estructurada de la IA** | **Validación Defensiva JSON:** Limpieza de delimitadores Markdown y captura de excepciones con degradación controlada a estado `AMARILLO` (*Precaución*). | `:analyzer` (`AnalizadorFraude.kt`) |
---

## 5. Cumplimiento de Políticas Android y Configuración de Build

### 5.1. Visibilidad de Paquetes (`Android 11+ / API 30+`)
Para cumplir con las restricciones de privacidad de visibilidad de aplicaciones manteniendo la interoperabilidad con herramientas del sistema, `AndroidManifest.xml` declara explícitamente en `<queries>`:
* `com.whatsapp`: Verificación de disponibilidad del cliente oficial para el escalado de alertas familiares.
* `android.intent.action.OPEN_DOCUMENT` y `android.intent.action.GET_CONTENT` (`image/*`): Habilitación del selector universal de medios del sistema (Google Fotos, Galería local, Descargas y unidades externas) sin permisos de lectura de almacenamiento.

### 5.2. Especificaciones de Compilación (`Gradle Kotlin DSL`)
* **Identificador de Aplicación:** `es.jlrn.escudoantifraude`
* **Rango de Compatibilidad:** `minSdk = 24` (Android 7.0 Nougat, cobertura > 97 % del parque activo) hasta `targetSdk = 35` (Android 15 estable).
* **Versionado de Producción:** `versionCode = 8` | `versionName = "2.2"`.
* **Java / Kotlin Bytecode Target:** JVM `VERSION_11`.
* **Gestión de Ofuscación (`R8`):** Desactivada explícitamente en `buildTypes.release` (`optimization { enable = false }`) para garantizar la integridad de reflexión en serialización JSON y llamadas `OkHttp` sin sobrecarga de mantenimiento de reglas ProGuard.
* **Política de Firma:** Certificado X.509 propio (`JKS`) persistente entre versiones, garantizando actualizaciones directas (*in-place updates*) con conservación íntegra de `SharedPreferences` (contacto de confianza y preferencia de silencio).

---

## 6. Matriz de Validación y Pruebas Realizadas

| Caso de Prueba | Entrada de Validación | Resultado Esperado | Estado |
| :--- | :--- | :--- | :--- |
| **Smishing bancario / DGT** | SMS con URL fraudulenta (`http://dgt-multas...`) | Semáforo **ROJO**, URL reemplazada por `[ENLACE BLOQUEADO POR SEGURIDAD]`, locución SSML pausada. | ✅ Superado |
| **Vishing (Llamada + Relato)** | Prefijo `+212...` o relato de falsa subida de tarifa eléctrica | Alerta preventiva **ROJO**, orden explícita de colgar y no facilitar datos. | ✅ Superado |
| **Validación Documental Temporal** | Fotografía de décimo de Lotería de Navidad (`22 de diciembre de 2026`) | Reconocimiento válido de fecha actual sin falsos positivos por año. | ✅ Superado |
| **Privacidad Auditiva** | Activación de conmutador `🔇` en cabecera | Silencio inmediato y persistente tras reiniciar la aplicación. | ✅ Superado |
| **Despliegue y Actualización** | Instalación de `APK v2.2` (`versionCode = 8`) sobre `v2.1` | Instalación sin bloqueo de Play Protect y conservación del familiar guardado. | ✅ Superado |
