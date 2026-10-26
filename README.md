# GymWP-Free (Clean-Room 100% Pro Unlocked)

Implementación nativa Android (**Kotlin + Jetpack Compose + Room SQLite**) de **GymWP (Workout & Fitness Planner)**, 100% libre de anuncios, sin suscripciones ni bloqueos Pro, y completamente funcional offline.

## Características Incluidas

1. **Catálogo Completo de 353 Ejercicios en Español** (`app/src/main/assets/exercises_es.json` + 353 animaciones `.gif` en `app/src/main/assets/gifs/`):
   - Instrucciones paso a paso en español.
   - Vector de reclutamiento de los **17 grupos musculares** (`0` a `3`: Primario, Secundario, Sinergista).
   - Filtros por músculo, 10 tipos de equipamiento y favoritos.
2. **57 Planes de Entrenamiento Pre-diseñados (100% Pro Desbloqueados)** (`app/src/main/assets/prebuilt_plans_es.json`):
   - Importación en 1 toque a tus rutinas personales.
3. **Generador Inteligente de 3 Fases Periodizadas y Entrenamiento Libre (Freestyle)**:
   - Crea automáticamente tu plan de **Fase 1 (Adaptación)**, **Fase 2 (Hipertrofia)** y **Fase 3 (Fuerza)** según tu sexo, edad, peso, altura, objetivo, nivel, lugar de entrenamiento y días por semana.
   - Generador **Freestyle** que arma una sesión al instante según tu tiempo disponible (`20–90 min`) priorizando músculos con `< 40%` de fatiga.
4. **Asistente de Entrenamiento en Vivo**:
   - Cronómetro global de sesión, edición en línea de repeticiones y cargas (`kg`), soporte de **Bi-Series / Superseries**, **Cronómetro de Descanso** flotante automático (`+15s`, `-15s`, `Saltar`, alerta sonora) y resumen post-entrenamiento con calorías quemadas e impacto de fatiga muscular.
5. **Mapa Anatómico 2D de Fatiga Muscular** (`26` capas `.png` en `app/src/main/res/drawable-nodpi/`):
   - Vista frontal y dorsal coloreada dinámicamente en 4 bandas (`Recuperado`, `Fatiga Leve`, `Fatiga Moderada`, `Fatiga Alta`).
   - Curva de recuperación horaria exacta (`25%/24h` para Pecho, Cuádriceps y Dorsales; `30%/24h` para los 14 músculos restantes) y ajuste manual por deslizador.
6. **Estadísticas y Mi Cuerpo (Antropometría)**:
   - Gráficos de progresión por ejercicio (Carga Máxima, Volumen Total, Repeticiones) y distribución comparativa por grupo muscular.
   - Calculadora de **IMC (OMS)**, **Rango de Peso Ideal**, **% de Grasa Corporal (Fórmula de Deurenberg)**, **12 Circunferencias Corporales (`cm`)** y **9 Pliegues Cutáneos (`mm`)**.

## Cómo Ejecutar en Android Studio

1. Abre **Android Studio**.
2. Selecciona **Open** y elige la carpeta raíz del repositorio:
   `c:\Users\Laptop de Ignacio\Documents\Repositorios\GymWP-Free`
3. Espera a que termine el **Gradle Sync** (Android Studio descargará automáticamente las dependencias de Jetpack Compose, Room y Coil).
4. Conecta tu teléfono Android (con *Depuración por USB* activa) o inicia un Emulador en **Device Manager** y presiona **Run (`▶`)**.
