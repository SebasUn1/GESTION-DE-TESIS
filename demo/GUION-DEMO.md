# Guion de la demo: TesisTrack

Demo de unos 12 minutos con dos perfiles: **la estudiante**, que lleva su tesis, y **la profesora (asesora)**, que la revisa y la aprueba. La historia es una tesis real en curso: un hito ya cerrado, uno con correcciones pendientes, uno esperando revisión y dos por venir. Durante la demo se cierra un ciclo de corrección completo en vivo.

---

## 1. Preparación (el día anterior, y otra vez 15 minutos antes)

Desde la carpeta del repo, en la rama `mejoras/calidad` (o `main` cuando se mergee):

```powershell
docker compose down -v               # borra la base: arranca vacía
docker compose up -d --build         # la primera vez tarda unos minutos
./demo/cargar-demo.ps1               # carga perfiles, tesis, entregas, etc.
```

Si el script dice *"La demo ya está cargada"*, no duplica nada: sale sin tocar la base. Para empezar de cero, se repiten los tres comandos.

**Dos navegadores.** La sesión se guarda en el navegador, así que dos pestañas de la misma ventana comparten usuario. Abre:

- **Chrome normal** → profesora
- **Chrome incógnito** (o Edge) → estudiante

Las dos en `http://localhost:3000/login`, ya con la sesión iniciada antes de empezar.

**Archivo a mano:** `demo/marco-teorico-v2.pdf` (lo genera el script). Es la corrección que la estudiante sube en vivo.

## 2. Los perfiles

| | Profesora (asesora) | Estudiante |
|---|---|---|
| Nombre | Carmen Salazar Ríos | Lucía Fernández Paredes |
| Correo | `asesor@tesistrack.demo` | `estudiante@tesistrack.demo` |
| Contraseña | `Demo2026!` | `Demo2026!` |
| Rol en el sistema | `ASESOR` (en pantalla dice "Profesor") | `ESTUDIANTE` |
| Qué hace | Arma su clase, reparte actividades, revisa versiones, observa, aprueba | Entra con el código, sube versiones, corrige, agenda asesorías |

**La tesis:** *"Detección temprana de deficiencias nutricionales en lechuga hidropónica mediante visión por computadora"*, en la clase **Seminario de Tesis II**.

### Cómo arranca

| # | Hito | Estado | Qué pasó |
|---|---|---|---|
| 1 | Planteamiento del problema | ✅ Completado | v1 observada (2 observaciones) → v2 corregida y aprobada |
| 2 | Marco teórico | 🟠 Observado | v1 con 2 observaciones sin resolver. **La estudiante corrige en vivo** |
| 3 | Metodología | 🔵 Entregado | v1 esperando revisión. **La profesora la evalúa en vivo** |
| 4 | Resultados preliminares | ⚪ Pendiente | vence en ~5 semanas |
| 5 | Informe final | ⚪ Pendiente | vence en ~7 semanas |

Además: una asesoría realizada con 2 acuerdos, una tarea hecha y otra pendiente, una asesoría programada para dentro de 3 días con enlace de Meet, una sesión de clase, un aviso, la rúbrica en PDF y un mensaje sin leer para la profesora.

> Las fechas se calculan desde el día en que se corre el script, así que el semáforo se ve igual el martes que en la Competencia Final.

---

## 3. Recorrido: estudiante (navegador incógnito, ~4 min)

| Paso | Dónde | Qué mostrar | Qué decir |
|---|---|---|---|
| 1 | **Dashboard** | Semáforo amarillo, próximo hito, tareas pendientes, próximas reuniones | "Lucía entra y en una pantalla sabe qué le toca: su tesis está *en riesgo* porque tiene correcciones pendientes en el marco teórico." |
| 2 | **Mi tesis** | Título, profesora, clase | "Entró a la clase con un código de invitación que le pasó su profesora, y eso le asignó asesora y todos los hitos de una vez." |
| 3 | **Mi clase** | Aviso, próxima sesión con enlace, *Trabajo de clase* → actividades y la carpeta **Rúbrica** | "Como un aula virtual: avisos, sesiones con enlace y materiales." |
| 4 | **Hitos** | Los 5 hitos con fecha y estado | "Cada actividad de la clase se volvió un hito de su tesis." |
| 5 | **Observaciones** → Marco teórico | v1 con sus 2 observaciones pendientes | "Las observaciones cuelgan de la **versión** concreta, no del hito: queda el historial de qué se pidió en cada versión." |
| 6 | **Entregas** → Marco teórico → **Subir nueva versión** → `marco-teorico-v2.pdf` → **Registrar entrega** | Aparece la v2 y el hito pasa a *Entregado* | **En vivo.** "La v2 no pisa a la v1: el sistema numera las versiones solo." |
| 7 | **Mensajes** | Conversación con la profesora | "Solo se pueden escribir quienes comparten una tesis." |

## 4. Recorrido: profesora (navegador normal, ~6 min)

| Paso | Dónde | Qué mostrar | Qué decir |
|---|---|---|---|
| 1 | **Dashboard** | Su clase con el semáforo, **Para revisar** (Metodología v1 **y la v2 que Lucía acaba de subir**), **Necesitan atención** | "La profesora no busca tesis por tesis: el sistema le junta lo que está esperando su revisión." |
| 2 | Para revisar → Marco teórico v2 → **Ver entrega** | Se abre el PDF en el visor, con el panel de observaciones | **En vivo.** "Revisa sin descargar nada." → **Aprobar y completar hito** → Cerrar |
| 3 | **Observaciones** → Marco teórico | La v1 con sus dos observaciones, ya **resueltas** | "Al completar el hito, lo que había pedido en la v1 queda atendido: queda el historial de qué se pidió y con qué versión se corrigió." |
| 4 | **Hitos** | Marco teórico ya está verde | "Aprobar una versión y cerrar el hito son dos decisiones (puede aprobar y aun así pedir más), pero lo común es que vayan juntas, así que el visor ofrece las dos." |
| 5 | Dashboard → Metodología v1 → **Ver entrega** → **Observar entrega** | Escribe, por ejemplo: *"Justifica el tamaño de la muestra: ¿por qué 400 imágenes por clase?"* → **Enviar observación** | **En vivo.** "Al observar, el hito vuelve a *Observado* y Lucía lo ve en su dashboard." |
| 6 | **Mis clases** → Seminario de Tesis II → **Seguimiento** | La grilla alumnos × actividades con el semáforo | "Con 30 alumnos, esta es la vista que le dice a quién llamar primero." |
| 7 | **Trabajo de clase** | Actividades y materiales | "Crea una actividad una vez y le llega como hito a cada tesis de la clase, también a quien entre después." |
| 8 | **Asesorías** | La reunión realizada con 2 acuerdos, la programada con enlace de Meet | "De la reunión salen acuerdos, y de los acuerdos, tareas." |
| 9 | **Tareas** | Una completada (de un acuerdo) y una pendiente con fecha | "Todo queda trazado: qué se dijo, qué se acordó, qué se hizo." |
| 10 | **Mensajes** | El mensaje sin leer de Lucía → responder | Cierre de la parte de la profesora. |

## 5. Cierre (volver al incógnito, ~30 s)

En el **Dashboard** de Lucía: Marco teórico ya está completado y Metodología aparece observada con la nueva observación.

> "Ese es el ciclo completo: entrega → observación → corrección → aprobación, con cada versión y cada comentario guardados. Esa es la pregunta que responde TesisTrack: **centralizar el seguimiento de una tesis en un solo lugar**."

---

## Si algo falla

| Problema | Solución |
|---|---|
| La página no carga | `docker compose ps`: los tres contenedores tienen que estar `running`. Si no, `docker compose up -d` |
| Login rechazado | La base no tiene la demo: `./demo/cargar-demo.ps1` |
| Los datos quedaron a medio mover después de un ensayo | `docker compose down -v ; docker compose up -d ; ./demo/cargar-demo.ps1` (menos de un minuto con las imágenes ya construidas) |
| Las dos ventanas muestran el mismo usuario | Están en el mismo navegador: usa incógnito u otro navegador para el segundo perfil |
| El script no corre ("ejecución de scripts deshabilitada") | `powershell -ExecutionPolicy Bypass -File .\demo\cargar-demo.ps1` |

## Si el jurado pregunta

- **¿Y el coordinador?** Existe un tercer rol, de solo lectura: ve todas las tesis y su avance, pero no crea ni modifica nada. No se puede elegir al registrarse (si no, cualquiera leería tesis ajenas).
- **¿Un profesor puede ver tesis de otro?** No. El acceso se resuelve por pertenencia: hay que ser *el* asesor de esa tesis, no basta con tener el rol.
- **¿Dónde quedan los archivos?** En PostgreSQL, hasta 15 MB por archivo. El tipo se verifica por el contenido, no por la extensión: solo se previsualizan PDF e imágenes reales.
- **¿Tesis grupales?** Sí: cualquier integrante puede sumar compañeros por correo, y todos tienen los mismos permisos.
- **¿Cómo saben que funciona?** 36 tests de backend contra un PostgreSQL real en Docker (ciclo de corrección, permisos, login) y 16 de frontend. Corren en GitHub Actions en cada pull request, y las imágenes Docker solo se publican si pasan.
- **¿Qué pasa si alguien prueba contraseñas?** Queda frenado a los 10 intentos por minuto contra una cuenta, y la dueña de la cuenta sigue entrando desde su máquina: se frena al que prueba, no a la cuenta.
