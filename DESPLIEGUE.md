# Despliegue de TesisTrack: Render + Vercel + PostgreSQL (Neon o Supabase)

| Pieza | Dónde | Carpeta del repo |
|---|---|---|
| Base de datos (PostgreSQL) | **Neon** o **Supabase** | — (las tablas las crea el backend al arrancar, con Flyway) |
| Backend (Spring Boot, Java 17) | **Render**, con el `Dockerfile` que ya está | `tesistrack-app/` |
| Frontend (React + Vite) | **Vercel** | `tesistrack-web/` |

El navegador solo habla con Vercel: `tesistrack-web/vercel.json` reenvía `/api/*` al backend en Render. Por eso **no hace falta CORS ni `VITE_API_URL`**: el frontend llama a `/api` relativo.

---

## ⚠️ Si ya estaba desplegado: qué cambia al actualizar

Esta versión trae la Fase 1 en adelante (clases, materiales, sesiones, avisos, dashboard del profesor, mensajes), tests y correcciones de seguridad. Antes de desplegarla:

1. **En Render, `JWT_SECRET` es obligatorio y debe tener al menos 32 caracteres.** Antes, sin la variable, el backend arrancaba con una clave de prueba publicada en GitHub. Ahora **no arranca**. Revisar que esté definida y sea larga (`openssl rand -base64 48`), y recién ahí desplegar.
2. **La base se actualiza sola.** Al arrancar, Flyway aplica las migraciones nuevas (V2 a V4: materiales, sesiones, avisos, archivos verificados, mensajes) sobre la base existente. Son aditivas: **no se pierden datos**.
3. **Cambiar `JWT_SECRET` cierra las sesiones abiertas.** La gente tiene que volver a iniciar sesión. Es normal.
4. Si la URL del backend no es `https://gestion-de-tesis.onrender.com`, actualizarla en `tesistrack-web/vercel.json`.

---

## Despliegue desde cero

**Orden:** base de datos → Render → Vercel → cargar la demo. Son unos 30 minutos.

### 1. Base de datos

Crear un proyecto en **Neon** o **Supabase** y anotar host, puerto, base, usuario y contraseña. **La base tiene que estar vacía**: Flyway crea todo la primera vez que arranca el backend.

- **Neon:** usar la cadena de conexión que da el panel. La conexión directa y la *pooled* funcionan las dos.
- **Supabase:** usar el **Session pooler** (botón *Connect*). El usuario es `postgres.<id-del-proyecto>`. No usar la conexión *Direct*, que es solo IPv6 y Render no llega, ni el *Transaction pooler* (puerto 6543), que rompe Hibernate.

### 2. Render (backend)

1. **New → Web Service** → conectar el repo.
2. **Branch:** `main` · **Root Directory:** `tesistrack-app` · **Runtime:** Docker · **Health Check Path:** `/api/health` (o `/actuator/health`).
3. **Environment Variables:**

| Variable | Valor |
|---|---|
| `JWT_SECRET` | Clave aleatoria de **al menos 32 caracteres** (`openssl rand -base64 48`). **Sin esto no arranca.** |
| `DB_HOST` | Host de la base |
| `DB_PORT` | `5432` |
| `DB_NAME` | Nombre de la base (`neondb` en Neon, `postgres` en Supabase) |
| `DB_USER` | Usuario de la base |
| `DB_PASSWORD` | Contraseña de la base |
| `DB_PARAMS` | `?sslmode=require` |

El puerto lo pone Render solo (`PORT`). El backend lo lee.

4. **Verificar:** `https://<tu-backend>.onrender.com/api/health` responde `{"status":"ok","service":"tesistrack-api"}`. La documentación de la API queda en `/swagger-ui/index.html`.

### 3. Vercel (frontend)

1. **Add New → Project** → importar el repo.
2. **Root Directory:** `tesistrack-web`. Es lo más importante: si queda la raíz, el build falla. El resto (Vite, `npm run build`, `dist`) sale de `vercel.json`.
3. Revisar que la URL del backend en `tesistrack-web/vercel.json` sea la de Render.
4. **Deploy.**

### 4. Cargar las cuentas de la demo

Desde la raíz del repo, en una PC con PowerShell, **una sola vez**:

```powershell
./demo/cargar-demo.ps1 -BaseUrl https://<tu-backend>.onrender.com -WebUrl https://<tu-frontend>.vercel.app
```

Crea `asesor@tesistrack.demo` y `estudiante@tesistrack.demo`, ambas con la contraseña `Demo2026!`, y una tesis con hitos en todos los estados. El guion está en [`demo/GUION-DEMO.md`](demo/GUION-DEMO.md). Si ya existen, el script avisa y no duplica nada.

### 5. Verificación final

- [ ] `/api/health` del backend responde `ok`
- [ ] El login de Vercel entra con `asesor@tesistrack.demo`
- [ ] Recargar estando en `/panel` no da 404
- [ ] El dashboard de la profesora muestra **Para revisar** con *Metodología (v1)*
- [ ] **Ver entrega** abre el PDF dentro de la página

---

## Si algo falla

| Síntoma | Causa | Solución |
|---|---|---|
| Render: se cae al arrancar con `Falta la variable de entorno JWT_SECRET` | Falta `JWT_SECRET` o tiene menos de 32 caracteres | Definirla en Render |
| Render: `Connection refused`, timeout o error de SSL contra la base | Host o puerto equivocado, falta `DB_PARAMS=?sslmode=require`, o se usó la conexión directa de Supabase (IPv6) | Revisar las variables del paso 2 |
| Render: `prepared statement "S_1" already exists` | Supabase con el *Transaction pooler* (6543) | Usar el **Session pooler** (5432) |
| Render: errores de Flyway en un despliegue nuevo | La base no estaba vacía | Usar una base nueva o vaciar el esquema `public` |
| Vercel: el login no conecta o da 404 en `/api/...` | La URL de Render en `vercel.json` no es la correcta | Corregirla y redesplegar |
| Vercel: 404 al recargar una página interna | El Root Directory no es `tesistrack-web` | Corregir el Root Directory |
| La primera visita tarda casi un minuto | El plan gratis de Render se duerme tras 15 minutos sin uso | Normal. Antes de presentar, abrir `/api/health` y esperar a que responda |

## Para tener en cuenta

- **Límites de frecuencia detrás de Vercel.** Como Vercel reenvía `/api`, el backend ve la IP de Vercel y no la de cada usuario. Los límites por IP (10 consultas por minuto de "¿existe este correo?" en el registro, 60 logins por minuto) quedan **compartidos** entre quien entre por el mismo nodo de Vercel. Para la demo no importa. Si una clase entera se registrara a la vez, alguien podría ver *"Demasiadas consultas"* en el primer paso del registro y tendría que esperar un minuto. La alternativa es que el frontend llame directo al backend (`VITE_API_URL` con la URL de Render, `CORS_ALLOWED_ORIGINS` con la de Vercel, y quitar el reenvío de `/api` en `vercel.json`).
- **Archivos:** los documentos de las entregas se guardan dentro de PostgreSQL (máximo 15 MB cada uno). Hay que mirar el límite de almacenamiento del plan gratis de la base.
- **Volver a empezar la demo:** vaciar el esquema `public`, redesplegar el backend (Flyway recrea las tablas) y volver a correr el script del paso 4.
