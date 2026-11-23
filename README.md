# 🛡️ Escudo Antifraude (v2.5)

Aplicación móvil nativa para Android diseñada para proteger a **personas mayores o con brecha digital** frente a estafas en **SMS, WhatsApp, correos electrónicos, documentos y llamadas telefónicas (*vishing*)** de forma sencilla, visual y por voz.

Desarrollada bajo una **arquitectura Modulith (Monolito Modular)** en Kotlin con **coste operativo y de infraestructura de 0 €** y **100 % compatible con la instalación directa mediante APK** (sin permisos restringidos).

---

## ✨ Características Principales (v2.5)

* **Barra de 3 Acciones Rápidas:**
  * **📷 FOTO:** Analiza capturas de pantalla, cartas o décimos de lotería mediante el selector de imágenes y carpetas de Android, con **validación binaria de seguridad** (*Magic Bytes* `JPG/PNG/WEBP/HEIC`, límite de peso y protección contra bombas de descompresión).
  * **📋 PEGAR:** Lee y analiza el mensaje del portapapeles neutralizando enlaces automáticamente, con **limpieza automática del portapapeles** al iniciar la aplicación y al finalizar cada consulta.
  * **📞 LLAMADA:** Escudo preventivo contra *vishing* con la **Regla de Oro**, acceso directo a **📲 Ver mis últimas llamadas**, relleno automático inteligente al regresar, **verificación automática en tu agenda de contactos** y análisis universal de prefijos nacionales, internacionales y de tarificación especial.
* **Blindaje Multicapa de Seguridad Interna (v2.5):**
  * **Protección *Anti-Prompt Injection*:** Aislamiento XML (`<mensaje_sospechoso>`), filtrado de caracteres de control invisibles y neutralización de URLs tanto en entrada como en salida.
  * **Protección *Anti-Reempaquetado* y Ofuscación R8:** Verificación criptográfica de firma SHA-256 en tiempo de ejecución, bloqueo de depuradores en producción y ofuscación de código con R8.
* **Neutralización Total de Enlaces (*Zero-Click Protection*):** Convierte las URL en texto inofensivo (`[ENLACE BLOQUEADO POR SEGURIDAD]`) y desactiva hipervínculos (`autoLink="none"`).
* **Control de Contexto Temporal Dinámico:** Inyecta la fecha real del dispositivo en cada consulta a la IA para validar fechas vigentes.
* **Accesibilidad Visual y Auditiva:**
  * **Semáforo de Alto Contraste:** Clasifica el riesgo en **ROJO** (Estafa), **AMARILLO** (Precaución) o **VERDE** (Seguro) con botón rápido de cierre (`✕`).
  * **Voz Pausada (`SSML`) con Control Discreto de Silencio (`🔊 / 🔇`):** Lectura diagnóstica en español (`es-ES`) a velocidad pausada (85 %) con botón de silencio persistente.
* **Red de Ayuda en un Toque:** Resumen pre-redactado por **WhatsApp** a un contacto de confianza y llamada gratuita directa al **017 (INCIBE)**.
* **Privacidad Total (*Zero-Retention*):** Procesamiento íntegro en la memoria RAM sin servidores ni bases de datos remotas.
## 🏗️ Arquitectura del Sistema (Modulith)

```text
EscudoAntifraude/
├── :app        # UI accesible, Escudo de Llamadas, Agenda, Portapapeles, Integridad SHA-256 y R8
├── :core       # Modelos de dominio (ResultadoSemaforo) y motor de voz SSML con silencio (LectorVoz)
├── :scanner    # Validación Magic Bytes, compresión segura en RAM (inSampleSize) y Base64
├── :analyzer   # Cliente HTTP OkHttp, aislamiento XML anti-inyección, contexto temporal y Gemini API
└── :alert      # Persistencia local de contacto de confianza, Deep Links WhatsApp y 017
```

---

## 🛠️ Stack Tecnológico

* **Lenguaje:** Kotlin (100 % interoperable con Java sobre JVM)
* **SDK Android:** Min SDK 24 (Android 7.0 Nougat) | Target SDK 35 (Android 15) | Compile SDK 37 (AGP 9.5)
* **IA Multimodal:** Google Gemini API (`gemini-3.5-flash-lite` en Google AI Studio)
* **Red y Serialización:** OkHttp 4.12 + `org.json`
* **Audio:** `android.speech.tts.TextToSpeech` con SSML nativo (`<prosody>` y `<break>`)
* **Seguridad de Compilación:** Ofuscación R8 + `proguard-rules.pro` + verificación SHA-256 en tiempo de ejecución

---

## 🚀 Instalación y Actualización (Usuarios Finales)

1. Descarga **`EscudoAntifraude_v2.5.apk`** desde la sección **Releases** del repositorio.
2. Ábrelo en tu dispositivo Android (si aparece el aviso preventivo de Google Play Protect por ser una app independiente, selecciona **Más detalles > Instalar de todos modos**). Las actualizaciones conservan los datos guardados de versiones anteriores (`v2.0` a `v2.4`).
3. Pulsa en **Familiar** (arriba a la derecha) para registrar el nombre y teléfono de tu contacto de confianza.

---

## ⚙️ Configuración para Desarrolladores

1. Obtén una clave de API gratuita en **Google AI Studio**.
2. Crea o edita el archivo **`local.properties`** en la raíz del proyecto (excluido en `.gitignore`):
   ```properties
   GEMINI_API_KEY=TU_CLAVE_REAL_DE_GEMINI
   ```
3. Configura la huella SHA-256 de tu certificado de firma (`firmaOficialSha256`) en `MainActivity.kt`.
4. Haz clic en **Sync Now** en Android Studio y genera tu APK firmado (**Build > Generate Signed App Bundle / APK...**).

