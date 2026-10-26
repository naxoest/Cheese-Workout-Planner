# Chukaros's GymPlanner

Aplicación nativa Android (**Kotlin + Jetpack Compose + Room SQLite**) de planificación de entrenamiento, control de fatiga muscular 2D y seguimiento antropométrico multi-usuario con sincronización directa a **Google Drive**.

## Características Principales

1. **Soporte Multi-Usuario Independiente**:
   - Permite registrar múltiples perfiles de usuario (pareja, familiares o amigos) en el mismo dispositivo.
   - Cada usuario mantiene de forma independiente sus planes de 3 fases, rutinas, cargas, mapa de fatiga muscular, historial de entrenamientos, favoritos y mediciones corporales.
2. **Sincronización Automática con Google Drive (Auto-Sync)**:
   - Vincula un archivo de respaldo en tu unidad de **Google Drive** (`Chukaros_GymPlanner_Backup.json`) mediante permisos persistentes de Android (`Storage Access Framework`).
   - Cada vez que cualquier usuario finaliza un entrenamiento, edita una rutina o registra una medición corporal, la base de datos completa de todos los usuarios se sincroniza automáticamente en Google Drive.
3. **Catálogo de 353 Ejercicios en Español** (`app/src/main/assets/exercises_es.json` + 353 animaciones `.gif` en `app/src/main/assets/gifs/`):
   - Instrucciones paso a paso en español.
   - Vector de reclutamiento de los **17 grupos musculares** (`0` a `3`: Primario, Secundario, Sinergista).
   - Filtros por músculo, 10 tipos de equipamiento y favoritos por usuario.
4. **57 Planes de Entrenamiento Especializados** (`app/src/main/assets/prebuilt_plans_es.json`):
   - Importación en 1 toque al perfil del usuario activo.
5. **Generador Inteligente de 3 Fases Periodizadas y Entrenamiento Libre (Freestyle)**:
   - Crea automáticamente el plan de **Fase 1 (Adaptación)**, **Fase 2 (Hipertrofia)** y **Fase 3 (Fuerza)** según el sexo, edad, peso, altura, objetivo, nivel, lugar de entrenamiento y días por semana de cada usuario.
   - Generador **Freestyle** que arma una sesión al instante según el tiempo disponible (`20–90 min`) priorizando músculos con `< 40%` de fatiga.
6. **Asistente de Entrenamiento en Vivo**:
   - Cronómetro global de sesión, edición en línea de repeticiones y cargas (`kg`), soporte de **Bi-Series / Superseries**, **Cronómetro de Descanso** flotante automático (`+15s`, `-15s`, `Saltar`, alerta sonora) y resumen post-entrenamiento con calorías quemadas e impacto de fatiga muscular.
7. **Mapa Anatómico 2D de Fatiga Muscular** (`26` capas `.png` en `app/src/main/res/drawable-nodpi/`):
   - Vista frontal y dorsal coloreada dinámicamente en 4 bandas (`Recuperado`, `Fatiga Leve`, `Fatiga Moderada`, `Fatiga Alta`).
   - Curva de recuperación horaria automática (`25%/24h` para Pecho, Cuádriceps y Dorsales; `30%/24h` para los 14 músculos restantes) y ajuste manual por deslizador.
8. **Estadísticas y Mi Cuerpo (Antropometría)**:
   - Gráficos de progresión por ejercicio (Carga Máxima, Volumen Total, Repeticiones) y distribución comparativa por grupo muscular.
   - Calculadora de **IMC (OMS)**, **Rango de Peso Ideal**, **% de Grasa Corporal (Fórmula de Deurenberg)**, **12 Circunferencias Corporales (`cm`)** y **9 Pliegues Cutáneos (`mm`)**.
