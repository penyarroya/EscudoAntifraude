# 📓 Bitácora de Desarrollo: Proyecto Escudo Antifraude

**Autor:** JLRN  
**Ubicación:** Cornellà de Llobregat, España  
**Plataforma:** Android Nativo (Kotlin / JVM)  
**Arquitectura:** Modulith (Monolito Modular)  
**Presupuesto de Infraestructura:** 0,00 €

---

## Fase 1: Concepción y Diseño de Arquitectura (v1.0)
* **Problema detectado:** Crecimiento de campañas de *smishing*, *phishing* y *vishing* dirigidas a personas mayores o con brecha digital en España.
* **Decisión de Arquitectura:** Se adopta un **Modulith (Monolito Modular)** en Android Studio dividido en **5 módulos Gradle desacoplados** (`:core`, `:scanner`, `:analyzer`, `:alert`, `:app`) con principio **Zero-Retention** (cero servidores propios, cero bases de datos remotas).

---

## Fase 2: Implementación Base y Accesibilidad Auditiva (v1.0)
* **Procesamiento de Imágenes (`:scanner`):** Decodificación en dos pasadas (`BitmapFactory.Options` + `inSampleSize`) para evitar `OutOfMemoryError` y reducir capturas a ~180 KB en `Base64`.
* **Motor de Voz Pausada (`:core`):** Implementación de `LectorVoz` mediante **SSML nativo de Android** (`<prosody rate="85%">` + `<break time="500ms"/>`) priorizando voces neuronales de *Google Speech Services* en español de España (`es-ES`).
* **Red de Apoyo (`:alert`):** Integración con WhatsApp (*Deep Links* con normalización `+34`) y llamada gratuita al **017 (INCIBE)** mediante `Intent.ACTION_DIAL`.
---

## Fase 3: Evolución a Versión 2.0 — Seguridad Proactiva y Compatibilidad Universal
Durante las pruebas de campo de la evolución v2.0 se tomaron decisiones técnicas clave tras validar el comportamiento real de Google Play Protect en Android 15/16:
1. **Decisión sobre Permisos Restringidos (`Play Protect`):** Se evaluó inicialmente un `NotificationListenerService` para lectura automática de notificaciones en segundo plano, pero Google Play Protect (*Enhanced Fraud Protection*) bloqueaba la instalación directa por APK fuera de tienda. Para garantizar un **instalador universal apto para todos los usuarios**, se retiró dicho servicio restringido y se sustituyó por un flujo seguro de **pegado en un toque desde el portapapeles (`📋 PEGAR`)** + recepción nativa mediante **`ACTION_SEND` (Compartir)**, manteniendo exclusivamente el permiso `android.permission.INTERNET`.
2. **Aislamiento de Enlaces (*Zero-Click Protection*):** Implementación de `neutralizarEnlaces()` mediante expresiones regulares y desactivación de `autoLink` en las vistas para bloquear cualquier URL maliciosa en pantalla.
3. **Botón Discreto de Silencio (`🔊 / 🔇`):** Persistencia en `SharedPreferences` dentro de `:core` para silenciar la locución en entornos públicos.

---

## Fase 4: Refinamiento UX, Escudo de Llamadas y Contexto Temporal (v2.1 – v2.2)
* **Rediseño de Interfaz (Barra Única de 3 Acciones):** Se compactaron los controles principales en una sola fila equilibrada (**`📷 FOTO`**, **`📋 PEGAR`**, **`📞 LLAMADA`**), alineando los botones de acción (`2. COMPROBAR` y `🗑️ BORRAR`) y añadiendo un botón **`✕`** para cerrar el bloque de resultado.
* **Selector Universal de Imágenes (`<queries>`):** Incorporación de visibilidad explícita de `GET_CONTENT` y `OPEN_DOCUMENT` en `AndroidManifest.xml` para garantizar el acceso tanto a Google Fotos/Galería como a carpetas y discos locales en Android 11+.
* **Escudo contra Estafas Telefónicas (*Vishing*):** Implementación de diálogo preventivo con la **Regla de Oro** (*colgar siempre*), botón de pegado rápido de teléfono (evitando conflictos de teclado numérico en diálogos) y análisis combinado de prefijos (internacionales de riesgo, tarificación especial `807/902/118`) y relato de la llamada.
* **Inyección de Fecha Real del Dispositivo:** Corrección de falsos positivos temporales (detectados al analizar un décimo de Lotería de Navidad de 2026) inyectando `SimpleDateFormat` con la fecha actual del sistema en cada petición a Gemini.
* **Estabilización de SDK:** Ajuste de `targetSdk = 35` (Android 15 estable) y `versionCode = 8` (`versionName = "2.2"`).

---

## Fase 5: Empaquetado, Firma y Despliegue (v2.2)
* **Firma Digital:** Generación de certificado X.509 propio (`llave-antifraude.jks`) con soporte de actualizaciones directas (*in-place updates*) entre versiones.
* **Seguridad del Repositorio:** Exclusión del certificado de firma y sustitución de la clave de API por marcador de posición antes de la publicación del código fuente en GitHub.

---

## Fase 6: Unificación Visual de Diálogos y Acceso Seguro a Últimas Llamadas (v2.3)
* **Acceso al Historial sin Permisos Restringidos:** Para evitar que el usuario mayor tenga que memorizar o teclear números largos sin activar los bloqueos de Google Play Protect asociados al permiso `READ_CALL_LOG`, se implementó un botón de acceso directo al registro nativo del teléfono (`Intent.ACTION_VIEW` sobre `CallLog.Calls.CONTENT_URI`).
* **Relleno Automático Inteligente y Limpieza de Estado:** Al regresar desde el historial de llamadas con un número copiado, el diálogo lo rellena automáticamente en el campo de teléfono. Además, se incorporó control de estado (`ultimoNumeroAnalizado`) y un botón de borrado rápido (`✕`) para garantizar que el formulario se abra siempre limpio en cada nueva consulta y no repita números anteriores del portapapeles.
* **Rediseño Visual de Diálogos:** Se unificó la estética de las ventanas emergentes (**Escudo de Llamadas** y **Persona de Confianza**) mediante tarjetas redondeadas (`GradientDrawable`), paleta de colores coherente con la pantalla principal (`#102A43`, `#0052CC`, `#F5F7FA`) y botones táctiles inferiores de gran tamaño (`CERRAR / ANALIZAR` y `CANCELAR / GUARDAR`).
* **Versionado:** Actualización del paquete a `versionCode = 9` (`versionName = "2.3"`).

---

## Fase 7: Verificación en Agenda de Contactos y Limpieza de Portapapeles (v2.4)
* **Detección de Contactos Conocidos (`READ_CONTACTS`):** Integración de búsqueda automática en la agenda del dispositivo mediante `ContactsContract.PhoneLookup`. Si el número llamante está guardado, se informa al usuario por su nombre (manteniendo advertencias preventivas frente a suplantación de identidad o robo de teléfono); si no existe, se analiza como desconocido.
* **Limpieza Automática del Portapapeles:** Implementación de `limpiarPortapapeles()` sincronizada con el ciclo de foco de ventana (`onWindowFocusChanged`) y al finalizar cada análisis para evitar arrastrar textos o números de consultas anteriores.
* **Versionado:** Actualización del paquete a `versionCode = 10` (`versionName = "2.4"`).

