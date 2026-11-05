# Cheese WP

Aplicación nativa Android (Kotlin + Jetpack Compose + Room SQLite) de planificación de entrenamiento, ejecución interactiva de rutinas, mapa de fatiga muscular 2D y seguimiento antropométrico multi-usuario con sincronización directa a Google Drive y actualizaciones OTA desde GitHub.

## Descarga Directa (APK)
- **[Descargar v1.apk](./v1.apk)** *(Entra a `v1.apk` desde GitHub en tu celular y toca **Download raw file** / **View raw** para instalarla directamente).*

## Características Principales

1. **Soporte Multi-Usuario Independiente y 5 Temas Visuales**:
   - Múltiples perfiles de usuario en el mismo dispositivo. Cada perfil conserva sus propios planes, rutinas, cargas, fatiga muscular, historial, favoritos, tema visual y mediciones corporales de manera aislada y privada.
   - Perfil genérico predeterminado ("Usuario 1") con personalización completa.
   - 5 temas visuales: Naranjo con Rojo (Por defecto), Rosado con Negro, Rosado con Blanco, Amarillo con Verde y Naranjo con Blanco.

2. **Programación Semanal y Estructura de Días**:
   - Asignación preliminar de días de la semana (Lun, Mar, Mié, Jue, Vie, Sáb, Dom) para cada sesión de entrenamiento.
   - Indicador visual automático cuando un día de rutina corresponde a la fecha actual ("Hoy").
   - Interfaz sobria y limpia con acentos de color reservados exclusivamente para las acciones principales.

3. **Ejercicios Parche y Alternativos Dinámicos**:
   - Configuración de ejercicios secundarios, terciarios u opcionales por si una máquina del gimnasio está ocupada.
   - Conmutación instantánea en el asistente en vivo entre el ejercicio principal y sus alternativas con conservación de series y cargas.

4. **Búsqueda y Creación Insensible a Tildes**:
   - Normalización de acentos y caracteres diacríticos para búsqueda y creación de ejercicios (por ejemplo, buscar "press frances" encuentra "Press Francés").
   - Creación de ejercicios y máquinas personalizadas con foto desde cámara o galería, tanto en el catálogo como directamente dentro de una rutina.

5. **Asistente de Entrenamiento en Vivo**:
   - Vista moderna por tarjetas enfocadas y vista completa de sesión.
   - Replicación rápida de carga y repeticiones de la primera serie a las series restantes.
   - Reemplazo y reordenamiento de ejercicios sobre la marcha.
   - Sugerencias de sobrecarga progresiva basadas en el desempeño de sesiones anteriores.
   - Cronómetro general y cronómetro de descanso con ejecución en segundo plano y pantalla bloqueada, con alerta sonora, vibración y notificación persistente.

6. **Historial y Estadísticas de Entrenamiento**:
   - Historial detallado de sesiones completadas en modo solo lectura con desglose exacto de series, cargas y tiempos.
   - Demostración gráfica semanal de días completados, volumen y series realizadas.
   - Comparación automática con marcas anteriores para guiar aumentos de carga.

7. **Mapa Anatómico 2D de Fatiga Muscular**:
   - Vista frontal y dorsal coloreada dinámicamente con correspondencia anatómica para 17 grupos musculares y curva de recuperación horaria.

8. **Mi Cuerpo (Biometría y Antropometría)**:
   - Seguimiento antropométrico con cálculo de IMC, peso ideal y porcentaje de grasa corporal según fórmula de Deurenberg.
   - Registro de 12 circunferencias corporales y 9 pliegues cutáneos con historial cronológico.

9. **Sincronización y Actualizaciones Seguras**:
   - Copia de seguridad y restauración automática en Google Drive mediante Storage Access Framework sin servidores intermediarios.
   - Actualización en un toque desde GitHub manteniendo íntegros los datos locales.
   - Cuadros de confirmación previa antes de eliminar rutinas, ejercicios, días o perfiles.
