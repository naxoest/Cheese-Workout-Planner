# Cheese WP 🧀🏋️

Aplicación nativa Android (**Kotlin + Jetpack Compose + Room SQLite**) de planificación de entrenamiento, ejecución interactiva de rutinas, mapa de fatiga muscular 2D y seguimiento antropométrico multi-usuario con sincronización directa a **Google Drive** y actualizaciones OTA desde GitHub.

## 📲 Descarga Directa (APK)
- **[Descargar v1.apk](./v1.apk)** *(Entra a `v1.apk` desde GitHub en tu celular y toca **Download raw file** / **View raw** para instalarla directamente).*

## Características Principales

1. **Soporte Multi-Usuario Independiente + 5 Temas Visuales**:
   - Múltiples perfiles de usuario en el mismo dispositivo (cada uno conserva sus propios planes, rutinas, cargas, fatiga muscular, historial, favoritos, tema visual y mediciones corporales).
   - **5 Temas Oficiales**: *Naranjo con Rojo (Por defecto)*, *Rosado con Negro*, *Rosado con Blanco*, *Amarillo con Verde* y *Naranjo con Blanco*.
2. **Sincronización Automática con Google Drive (Auto-Sync) y Actualizaciones OTA**:
   - Vincula un archivo de respaldo en tu **Google Drive** (`Cheese_WP_Backup.json`) mediante permisos persistentes de Android (`Storage Access Framework`).
   - Notificación e instalador automático de nuevas versiones desde GitHub conservando el 100% de tus datos locales.
3. **Catálogo de 353 Ejercicios + Creación de Ejercicios / Máquinas Personalizadas**:
   - Crea y edita tus propios ejercicios o máquinas específicas de tu gimnasio tanto desde la pestaña **Ejercicios** como directamente dentro de cualquier rutina existente o en ejecución, asignando equipamiento, unidad y músculos primarios/secundarios.
4. **Asistente de Entrenamiento en Vivo Amigable**:
   - Interfaz moderna por tarjetas enfocadas o vista completa con controles táctiles rápidos (`- / valor / +` y edición exacta), botón **`Replicar S1`** para copiar repeticiones y carga de la primera serie al resto de series, reemplazo de ejercicios sin eliminarlos (**`Reemplazar`**) y edición completa de la rutina en plena ejecución.
   - Cronómetro general y cronómetro de descanso exactos incluso con la **pantalla bloqueada**, con alerta sonora, vibración y notificación en pantalla de bloqueo.
5. **57 Planes Especializados, Plan Inteligente de 3 Fases y Modo Libre (Freestyle)**:
   - Generación automática de **Fase 1 (Adaptación)**, **Fase 2 (Hipertrofia)** y **Fase 3 (Fuerza)** o sesiones instantáneas según tiempo disponible y recuperación muscular.
6. **Mapa Anatómico 2D de Fatiga Muscular Auditado**:
   - Vista frontal y dorsal coloreada dinámicamente en 4 bandas con correspondencia anatómica verificada para los 17 grupos musculares y curva de recuperación horaria automática.
7. **Estadísticas y Mi Cuerpo (Antropometría)**:
   - Gráficos de progresión por ejercicio, distribución muscular comparativa, **IMC (OMS)**, **Peso Ideal**, **% de Grasa Corporal (Deurenberg)**, **12 Circunferencias (`cm`)** y **9 Pliegues Cutáneos (`mm`)**.
