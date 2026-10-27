# TesisTrack

Proyecto grupal desarrollado para el curso de Desarrollo Web Full Stack.

TesisTrack centraliza el seguimiento de una tesis: el profesor arma su clase, reparte actividades que llegan como hitos a cada tesis, revisa las versiones que suben los estudiantes, observa y aprueba. Todo queda en un solo lugar: asesorías, acuerdos, tareas, entregas y observaciones.

| Parte | Tecnología | Carpeta |
|---|---|---|
| Backend | Java 17, Spring Boot 4, Spring Security (JWT), JPA, Flyway | [`tesistrack-app/`](tesistrack-app/) |
| Frontend | React 18, Vite, React Router | [`tesistrack-web/`](tesistrack-web/) |
| Base de datos | PostgreSQL 16 | (Docker) |
| Entrega | Docker Compose, GitHub Actions, imágenes en ghcr.io | [`.github/workflows/`](.github/workflows/) |

## Levantarlo

Con Docker Desktop abierto, desde esta carpeta:

```bash
docker compose up -d --build
```

Abre http://localhost:3000. La base arranca vacía. Para cargar datos de demostración (una profesora, una estudiante y una tesis con hitos en todos los estados):

```powershell
./demo/cargar-demo.ps1
```

Las cuentas, el guion de la demo y cómo volver a empezar de cero están en [`demo/GUION-DEMO.md`](demo/GUION-DEMO.md).

## Desarrollo local (sin Docker para la app)

El backend necesita un PostgreSQL 16. Una forma rápida:

```bash
docker run -d --name tesistrack-db -p 5433:5432 \
  -e POSTGRES_DB=tesistrack -e POSTGRES_USER=tesistrack -e POSTGRES_PASSWORD=tesistrack postgres:16
```

```powershell
# Backend en :8080, con el perfil dev (clave JWT de desarrollo y SQL en el log)
cd tesistrack-app
$env:DB_PORT="5433"; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# Frontend en :5173 (reenvía /api al 8080)
cd tesistrack-web
npm install
npm run dev
```

Sin el perfil `dev`, el backend **no arranca** si falta `JWT_SECRET`: es a propósito, para que nunca llegue a producción con una clave conocida.

## Tests

| Qué | Comando | Qué cubre |
|---|---|---|
| Backend | `cd tesistrack-app && ./mvnw verify` | 36 tests. Los de integración levantan su propio PostgreSQL con Testcontainers (hace falta Docker): ciclo de corrección completo, permisos por pertenencia, autenticación y límite de login, reparto de actividades, tipos de archivo, mensajes |
| Frontend | `cd tesistrack-web && npm test` | 16 tests de las funciones puras: fechas en la zona de Lima, archivos previsualizables, medidor de contraseñas, formato |
| Lint | `cd tesistrack-web && npm run lint` | oxlint |

El workflow [CI](.github/workflows/ci.yml) corre todo esto en cada pull request y en cada push a `main`. Las imágenes Docker se publican en ghcr.io **solo si el CI pasa** en `main`.

## Producción

**Supabase + Render + Vercel:** paso a paso en [`DESPLIEGUE.md`](DESPLIEGUE.md).

**En un servidor propio con Docker:** copiar [`.env.example`](.env.example) como `.env` junto al `docker-compose.yml` y completar:

| Variable | Para qué |
|---|---|
| `JWT_SECRET` | Firma de los tokens. Obligatoria, mínimo 32 caracteres (`openssl rand -base64 48`) |
| `DB_PASSWORD` | Clave de PostgreSQL |
| `CORS_ALLOWED_ORIGINS` | La dirección pública del frontend |

El frontend (nginx) es el único puerto expuesto y reenvía `/api` al backend con la IP real del usuario en `X-Forwarded-For` (nginx la reemplaza, no confía en la que mande el cliente). Los límites de frecuencia (login, consulta de correo, mensajes) viven en memoria: alcanzan para un servidor único.

## Documentación del diseño

Las decisiones de diseño, con las alternativas descartadas, están en [`TesisTrack/`](TesisTrack/), la copia versionada del vault de Obsidian del proyecto. Empezar por [`TesisTrack.md`](TesisTrack/TesisTrack.md).

## Taller 2 – Desarrollo Backend

Para la revisión del Taller 2, se ha preparado un apartado específico que organiza las evidencias correspondientes a los 7 criterios solicitados:

➡️ **[Revisar evidencias del Taller 2](TALLER-02/README.md)**

La implementación funcional se encuentra principalmente en `tesistrack-app/`.
