# Despliegue de TesisTrack: Supabase + Render + Vercel

Guía paso a paso para poner TesisTrack en la nube. Se despliega desde la rama **`main`**.

| Pieza | Dónde | Carpeta del repo |
|---|---|---|
| Base de datos (PostgreSQL) | **Supabase** | — (las tablas las crea el backend al arrancar) |
| Backend (Spring Boot, Java 17) | **Render**, con el `Dockerfile` que ya está | `tesistrack-app/` |
| Frontend (React + Vite) | **Vercel** | `tesistrack-web/` |

> **Por qué el backend no va en Vercel:** Vercel solo corre funciones cortas (Node, Python, Go). El backend es un servidor Java que tiene que estar siempre encendido. Render lo construye directo desde el `Dockerfile`. Cualquier servicio que corra un contenedor Docker sirve igual (Railway, Fly.io, Cloud Run): las variables son las mismas.

**Orden:** Supabase → Render → Vercel → volver a Render para el CORS → cargar la demo. Son unos 30 minutos.

---

## 1. Supabase (base de datos)

1. Crear un proyecto nuevo y **anotar la contraseña** de la base que se elige al crearlo.
2. Botón **Connect** → pestaña de cadenas de conexión → elegir **Session pooler**. Anotar:
   - **Host**: algo como `aws-0-<region>.pooler.supabase.com`
   - **Puerto**: `5432`
   - **Usuario**: `postgres.<id-del-proyecto>` (con el punto y el id, no solo `postgres`)
   - **Base**: `postgres`

> ⚠️ **Tiene que ser el *Session pooler*.**
> - La conexión *Direct* del plan gratis es solo IPv6, y Render no llega: da timeout.
> - El *Transaction pooler* (puerto `6543`) rompe Hibernate, con errores tipo `prepared statement "S_1" already exists`.

No hay que crear tablas a mano: **la base tiene que estar vacía**. Flyway crea todo la primera vez que arranca el backend.

## 2. Render (backend)

1. **New → Web Service** → conectar el repo `kepler04/GESTION-DE-TESIS`.
2. Configurar:
   - **Branch:** `main`
   - **Root Directory:** `tesistrack-app`
   - **Runtime / Language:** Docker (detecta el `Dockerfile` solo)
   - **Instance type:** Free alcanza
   - **Health Check Path:** `/api/health`
3. **Environment Variables:**

| Variable | Valor |
|---|---|
| `JWT_SECRET` | Una clave aleatoria de **al menos 32 caracteres**. Para generarla: `openssl rand -base64 48`, o cualquier generador de contraseñas largas. **Sin esta variable el backend no arranca, a propósito.** |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host-del-pooler>:5432/postgres?sslmode=require` |
| `DB_USER` | `postgres.<id-del-proyecto>` |
| `DB_PASSWORD` | La contraseña de Supabase |
| `CORS_ALLOWED_ORIGINS` | Por ahora `http://localhost:5173`. Se cambia en el paso 4, cuando exista la dirección de Vercel |
| `PORT` | `8080` (le indica a Render a qué puerto mandar el tráfico) |

4. **Create Web Service.** El primer build tarda entre 5 y 10 minutos, porque Maven descarga todo.
5. **Verificar:** abrir `https://<tu-backend>.onrender.com/api/health`. Tiene que responder `{"status":"ok","service":"tesistrack-api"}`.

## 3. Vercel (frontend)

1. **Add New → Project** → importar el mismo repo.
2. Configurar:
   - **Root Directory:** `tesistrack-web`. Es lo más importante: si queda la raíz, el build falla.
   - El framework (Vite), el comando de build y la carpeta `dist` salen solos de `tesistrack-web/vercel.json`.
3. **Environment Variables:**

| Variable | Valor |
|---|---|
| `VITE_API_URL` | `https://<tu-backend>.onrender.com`, **sin** `/api` y **sin** barra al final |

4. **Deploy.** Anotar la dirección de producción, por ejemplo `https://tesistrack.vercel.app`.

> ⚠️ `VITE_API_URL` se usa **al construir**, no al abrir la página. Si se cambia después, hay que hacer **Redeploy** en Vercel. Si falta, el frontend intenta hablarle a `localhost:8080` y nada anda.

## 4. Conectar los dos (CORS)

Volver a Render → Environment → cambiar `CORS_ALLOWED_ORIGINS` por la dirección de Vercel del paso 3:

```
https://tesistrack.vercel.app
```

Para permitir más de una, se separan con coma (`https://a.vercel.app,https://b.vercel.app`). Render redespliega solo al guardar.

> Las *preview* de Vercel (una por cada push a otra rama) tienen otra dirección: el backend las rechaza salvo que se agreguen acá. Para la presentación alcanza con la de producción.

## 5. Cargar las cuentas de la demo

La base de la nube arranca vacía. Desde la raíz del repo, en una PC con PowerShell, **una sola vez**:

```powershell
./demo/cargar-demo.ps1 -BaseUrl https://<tu-backend>.onrender.com -WebUrl https://tesistrack.vercel.app
```

Crea una profesora (`asesor@tesistrack.demo`), una estudiante (`estudiante@tesistrack.demo`), ambas con la contraseña `Demo2026!`, y una tesis con hitos en todos los estados. El guion de la presentación está en [`demo/GUION-DEMO.md`](demo/GUION-DEMO.md).

## 6. Verificación final

- [ ] `https://<tu-backend>.onrender.com/api/health` responde `ok`
- [ ] El login de Vercel entra con `asesor@tesistrack.demo`
- [ ] Recargar el navegador estando en `/panel` no da 404
- [ ] En el dashboard de la profesora aparece **Para revisar** con *Metodología (v1)*
- [ ] **Ver entrega** abre el PDF dentro de la página

---

## Si algo falla

| Síntoma | Causa | Solución |
|---|---|---|
| Render: el backend se cae al arrancar con `Falta la variable de entorno JWT_SECRET` | Falta `JWT_SECRET` o tiene menos de 32 caracteres | Agregarla en Render (paso 2) |
| Render: timeout o `Connection refused` contra Supabase | Se usó la conexión *Direct* (IPv6) | Usar el host del **Session pooler** |
| Render: `prepared statement "S_1" already exists` | Se usó el *Transaction pooler* (6543) | Usar el **Session pooler** (5432) |
| Render: `password authentication failed` | Usuario sin el id del proyecto | `DB_USER` es `postgres.<id-del-proyecto>` |
| Render: errores de Flyway o `relation ... does not exist` | La base no estaba vacía la primera vez | Usar un proyecto de Supabase nuevo, o vaciar el esquema `public` y redesplegar |
| Vercel: el login dice que no se pudo conectar, y la consola del navegador habla de **CORS** | `CORS_ALLOWED_ORIGINS` no coincide con la dirección de Vercel | Copiarla exacta desde la barra del navegador (paso 4) |
| Vercel: las llamadas van a `localhost:8080` | Faltaba `VITE_API_URL` al construir | Agregarla y hacer **Redeploy** |
| Vercel: 404 al recargar una página que no es la inicial | El Root Directory no es `tesistrack-web`, así que no se leyó `vercel.json` | Corregir el Root Directory |
| La primera visita tarda casi un minuto | El plan gratis de Render se duerme tras 15 minutos sin uso | Normal. Antes de presentar, abrir `/api/health` y esperar a que responda |

## Para tener en cuenta

- **Archivos:** los documentos de las entregas se guardan dentro de PostgreSQL (máximo 15 MB cada uno). El plan gratis de Supabase tiene 500 MB, de sobra para la demo.
- **Volver a empezar la demo en la nube:** vaciar el esquema `public` en Supabase, redesplegar el backend (Flyway recrea las tablas) y volver a correr el script del paso 5.
- **Imágenes Docker:** cada push a `main` con los tests en verde publica imágenes en `ghcr.io/kepler04/gestion-de-tesis-backend` y `-frontend` (ver `.github/workflows/`). Sirven si en algún momento prefieren desplegar la imagen ya construida en vez de construir desde el repo.
