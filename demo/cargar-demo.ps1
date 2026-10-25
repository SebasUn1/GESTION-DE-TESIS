<#
.SYNOPSIS
    Carga los datos de la demo de TesisTrack: un profesor (asesor), una estudiante
    y una tesis con hitos en todos los estados del ciclo.

.DESCRIPTION
    Todo pasa por la API, igual que si lo hiciera alguien desde la web: las
    contraseñas se hashean, los estados de los hitos los mueve el backend y cada
    permiso se valida por pertenencia. Nada se inserta a mano en la base.

    Las fechas son relativas al día en que se corre, así el semáforo da lo mismo
    el martes que en la Competencia Final.

    Requiere el sistema levantado (docker compose up) y una base sin la demo. Para
    empezar de cero: docker compose down -v ; docker compose up -d

.EXAMPLE
    ./demo/cargar-demo.ps1
    ./demo/cargar-demo.ps1 -BaseUrl http://localhost:5173
#>
param(
    # El frontend reenvía /api al backend: en Docker es el 3000, con Vite el 5173.
    [string]$BaseUrl = 'http://localhost:3000'
)

$ErrorActionPreference = 'Stop'
$Api = "$BaseUrl/api"

$Clave = 'Demo2026!'
$AsesorEmail = 'asesor@tesistrack.demo'
$EstudianteEmail = 'estudiante@tesistrack.demo'

$hoy = (Get-Date).Date
function Dia([int]$desplazamiento) { $hoy.AddDays($desplazamiento).ToString('yyyy-MM-dd') }
# Hora de Lima (UTC-5, sin horario de verano) expresada en UTC, que es lo que espera un Instant.
function Momento([int]$desplazamiento, [int]$horaLima) {
    $hoy.AddDays($desplazamiento).AddHours($horaLima + 5).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
}

# --- Cliente HTTP ---------------------------------------------------------------

# Windows PowerShell 5.1 lee como ISO-8859-1 toda respuesta que no declare charset
# (y la API no lo declara): "Rúbrica" llegaría como "RÃºbrica". Por eso se baja el
# cuerpo crudo y se decodifica UTF-8 a mano, en las dos versiones por igual.
function Enviar([hashtable]$params) {
    try {
        $respuesta = Invoke-WebRequest @params -UseBasicParsing
    } catch {
        $detalle = $_.ErrorDetails.Message
        # 5.1 no llena ErrorDetails: el {"error": ...} de la API hay que leerlo del stream.
        if (-not $detalle -and $_.Exception.Response -and $_.Exception.Response.GetType().GetMethod('GetResponseStream')) {
            $lector = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream(), [Text.Encoding]::UTF8)
            $detalle = $lector.ReadToEnd()
        }
        if (-not $detalle) { $detalle = $_.Exception.Message }
        throw "$($params.Method) $($params.Uri) falló: $detalle"
    }
    $texto = [Text.Encoding]::UTF8.GetString($respuesta.RawContentStream.ToArray())
    if ($texto) { $texto | ConvertFrom-Json }
}

function Llamar([string]$metodo, [string]$ruta, $cuerpo = $null, [string]$token = $null) {
    $headers = @{}
    if ($token) { $headers.Authorization = "Bearer $token" }
    $params = @{ Method = $metodo; Uri = "$Api$ruta"; Headers = $headers }
    if ($null -ne $cuerpo) {
        # En bytes: Windows PowerShell 5.1 manda los string como ISO-8859-1 y rompe las tildes.
        $params.Body = [Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Depth 5))
        $params.ContentType = 'application/json; charset=utf-8'
    }
    Enviar $params
}

# Multipart armado a mano para no depender de -Form (solo existe en PowerShell 7).
function Subir([string]$metodo, [string]$ruta, [string]$token, [byte[]]$bytes, [string]$nombre, [hashtable]$campos = @{}) {
    $limite = [guid]::NewGuid().ToString('N')
    $utf8 = [Text.Encoding]::UTF8
    $ms = New-Object IO.MemoryStream
    $escribir = { param($texto) $b = $utf8.GetBytes($texto); $ms.Write($b, 0, $b.Length) }
    foreach ($campo in $campos.GetEnumerator()) {
        & $escribir "--$limite`r`nContent-Disposition: form-data; name=`"$($campo.Key)`"`r`n`r`n$($campo.Value)`r`n"
    }
    & $escribir "--$limite`r`nContent-Disposition: form-data; name=`"archivo`"; filename=`"$nombre`"`r`nContent-Type: application/pdf`r`n`r`n"
    $ms.Write($bytes, 0, $bytes.Length)
    & $escribir "`r`n--$limite--`r`n"
    Enviar @{
        Method = $metodo; Uri = "$Api$ruta"; Headers = @{ Authorization = "Bearer $token" }
        ContentType = "multipart/form-data; boundary=$limite"; Body = $ms.ToArray()
    }
}

# PDF de una página con texto, para que el visor de entregas muestre algo legible.
# Helvetica con WinAnsiEncoding cubre tildes y eñes; las rayas largas no, por eso no se usan.
function NuevoPdf([string]$titulo, [string[]]$lineas) {
    $escapar = { param($t) $t.Replace('\', '\\').Replace('(', '\(').Replace(')', '\)') }
    $texto = "BT /F1 18 Tf 72 720 Td 26 TL ($(& $escapar $titulo)) Tj T* /F1 11 Tf 16 TL"
    foreach ($linea in $lineas) { $texto += " ($(& $escapar $linea)) Tj T*" }
    $texto += ' ET'

    $objetos = @(
        '<< /Type /Catalog /Pages 2 0 R >>',
        '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
        "<< /Length $($texto.Length) >>`nstream`n$texto`nendstream",
        '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>'
    )
    $pdf = "%PDF-1.4`n"
    $offsets = @()
    for ($i = 0; $i -lt $objetos.Count; $i++) {
        $offsets += $pdf.Length
        $pdf += "$($i + 1) 0 obj`n$($objetos[$i])`nendobj`n"
    }
    $xref = $pdf.Length
    $pdf += "xref`n0 $($objetos.Count + 1)`n0000000000 65535 f `n"
    foreach ($o in $offsets) { $pdf += ('{0:D10} 00000 n ' -f $o) + "`n" }
    $pdf += "trailer`n<< /Size $($objetos.Count + 1) /Root 1 0 R >>`nstartxref`n$xref`n%%EOF`n"
    # Latin-1: un carácter, un byte, así los offsets del xref calculados sobre el string valen.
    [Text.Encoding]::GetEncoding(28591).GetBytes($pdf)
}

function Paso([string]$texto) { Write-Host "  - $texto" }

# --- Chequeos previos -----------------------------------------------------------

Write-Host "TesisTrack: cargando la demo en $BaseUrl" -ForegroundColor Cyan
try {
    Invoke-RestMethod "$Api/health" | Out-Null
} catch {
    throw "No responde $Api/health. ¿Está levantado el sistema? (docker compose up -d)"
}
if ((Llamar GET "/auth/existe?email=$AsesorEmail").existe) {
    Write-Host "La demo ya está cargada ($AsesorEmail existe)." -ForegroundColor Yellow
    Write-Host 'Para empezar de cero: docker compose down -v ; docker compose up -d ; y volver a correr este script.'
    exit 0
}

# --- 1. El profesor arma su clase -----------------------------------------------

Write-Host '1. Profesor' -ForegroundColor Cyan
$asesor = Llamar POST '/auth/register' @{
    name = 'Carmen Salazar Ríos'; email = $AsesorEmail; password = $Clave; role = 'ASESOR'
    carrera = 'Ingeniería de Sistemas'; organizacion = 'UTEC'; ubicacion = 'Lima, Perú'
    aceptaPolitica = $true
}
$tA = $asesor.token
Paso "cuenta $AsesorEmail"

$clase = Llamar POST '/areas' @{ nombre = 'Seminario de Tesis II' } $tA
Paso "clase '$($clase.nombre)' con código $($clase.codigo)"

# Las actividades se reparten como hito a cada tesis de la clase, también a las
# que entran después: por eso se crean antes de que llegue la estudiante.
$actividades = @(
    @{ nombre = 'Planteamiento del problema'; fechaLimite = Dia -37; orden = 1
       descripcion = 'Problema, preguntas de investigación, objetivos y justificación.' },
    @{ nombre = 'Marco teórico'; fechaLimite = Dia -9; orden = 2
       descripcion = 'Antecedentes (mínimo 10, de los últimos 5 años) y bases teóricas.' },
    @{ nombre = 'Metodología'; fechaLimite = Dia 5; orden = 3
       descripcion = 'Tipo y diseño de investigación, población, muestra e instrumentos.' },
    @{ nombre = 'Resultados preliminares'; fechaLimite = Dia 33; orden = 4
       descripcion = 'Primeros resultados del piloto con su análisis.' },
    @{ nombre = 'Informe final'; fechaLimite = Dia 47; orden = 5
       descripcion = 'Documento completo listo para sustentación.' }
)
foreach ($a in $actividades) { Llamar POST "/areas/$($clase.id)/actividades" $a $tA | Out-Null }
Paso "$($actividades.Count) actividades (se vuelven los hitos de cada tesis)"

Llamar POST "/areas/$($clase.id)/avisos" @{
    texto = "¡Bienvenidos al Seminario de Tesis II! En 'Trabajo de clase' están las actividades con sus fechas y la rúbrica. Las entregas se suben en PDF."
} $tA | Out-Null
Llamar POST "/areas/$($clase.id)/sesiones" @{
    titulo = 'Clase: diseño metodológico'; fechaHora = Momento 4 19; enlace = 'https://meet.google.com/tes-demo-clase'
} $tA | Out-Null
Paso 'aviso y sesión de clase'

$carpetas = Llamar GET "/areas/$($clase.id)/carpetas" $null $tA
$rubrica = $carpetas | Where-Object { $_.nombre -eq 'Rúbrica' } | Select-Object -First 1
if (-not $rubrica) { $rubrica = Llamar POST "/areas/$($clase.id)/carpetas" @{ nombre = 'Rúbrica' } $tA }
$pdfRubrica = NuevoPdf 'Rúbrica de evaluación de tesis' @(
    'Seminario de Tesis II - Ingeniería de Sistemas',
    '',
    '1. Planteamiento del problema ............ 20 puntos',
    '2. Marco teórico .......................... 20 puntos',
    '3. Metodología ............................ 25 puntos',
    '4. Resultados y discusión ................. 25 puntos',
    '5. Redacción y formato APA 7 .............. 10 puntos'
)
Subir POST "/carpetas/$($rubrica.id)/materiales/archivo" $tA $pdfRubrica 'rubrica-seminario-tesis-ii.pdf' @{ titulo = 'Rúbrica de evaluación' } | Out-Null
Llamar POST "/carpetas/$($rubrica.id)/materiales" @{ titulo = 'Guía de estilo APA 7'; url = 'https://apastyle.apa.org/' } $tA | Out-Null
Paso 'materiales en la carpeta Rúbrica'

# --- 2. La estudiante entra con el código ---------------------------------------

Write-Host '2. Estudiante' -ForegroundColor Cyan
$estudiante = Llamar POST '/auth/register' @{
    name = 'Lucía Fernández Paredes'; email = $EstudianteEmail; password = $Clave; role = 'ESTUDIANTE'
    carrera = 'Ingeniería de Sistemas'; organizacion = 'UTEC'; ubicacion = 'Lima, Perú'
    aceptaPolitica = $true
}
$tE = $estudiante.token
Paso "cuenta $EstudianteEmail"

$tesis = Llamar POST '/proyectos' @{
    titulo = 'Detección temprana de deficiencias nutricionales en lechuga hidropónica mediante visión por computadora'
    descripcion = 'Modelo de clasificación de imágenes para identificar deficiencias de nitrógeno, fósforo y potasio en cultivos hidropónicos de pequeños productores de Lima.'
    codigoInvitacion = $clase.codigo
    fechaInicio = Dia -50
} $tE
Paso "tesis creada y unida a la clase con el código $($clase.codigo)"

$hitos = Llamar GET "/proyectos/$($tesis.id)/hitos" $null $tE
function Hito([int]$orden) { $hitos | Where-Object { $_.orden -eq $orden } | Select-Object -First 1 }
$h1 = Hito 1; $h2 = Hito 2; $h3 = Hito 3
if (-not ($h1 -and $h2 -and $h3)) { throw "La tesis no recibió los hitos de la clase (llegaron $($hitos.Count))" }
Paso "recibió $($hitos.Count) hitos de la clase"

function Entregar($hito, [int]$version, [string]$comentario, [string[]]$contenido) {
    $slug = @{ 1 = 'planteamiento-del-problema'; 2 = 'marco-teorico'; 3 = 'metodologia' }[[int]$hito.orden]
    $nombre = "$slug-v$version.pdf"
    $entrega = Llamar POST "/hitos/$($hito.id)/entregas" @{ archivoNombre = $nombre; comentario = $comentario } $tE
    $pdf = NuevoPdf "$($hito.nombre) - versión $version" (@('Lucía Fernández Paredes - Seminario de Tesis II', '') + $contenido)
    Subir PUT "/entregas/$($entrega.id)/archivo" $tE $pdf $nombre | Out-Null
    $entrega
}

# --- 3. Hito 1: el ciclo completo, ya cerrado -----------------------------------

Write-Host '3. Hito 1 - ciclo completo (COMPLETADO)' -ForegroundColor Cyan
$e1v1 = Entregar $h1 1 'Primera versión del planteamiento.' @(
    'Problema: los pequeños productores detectan tarde las deficiencias de nutrientes.',
    'Objetivo general: clasificar deficiencias de N, P y K a partir de fotografías.'
)
$o1 = Llamar POST "/entregas/$($e1v1.id)/observaciones" @{
    descripcion = 'La pregunta de investigación es demasiado amplia: delimita el cultivo, la zona y el período.'
} $tA
$o2 = Llamar POST "/entregas/$($e1v1.id)/observaciones" @{
    descripcion = 'Falta cuantificar el problema: agrega al menos un dato de pérdidas por deficiencias nutricionales.'
} $tA
Paso 'v1 entregada y observada (2 observaciones)'

$e1v2 = Entregar $h1 2 'Corregí la pregunta y agregué datos de pérdidas.' @(
    'Pregunta: ¿qué precisión alcanza un modelo CNN para detectar deficiencias de N, P y K',
    'en lechuga hidropónica de 3 invernaderos de Lurín entre agosto y noviembre de 2026?',
    'Las pérdidas por deficiencias no detectadas llegan al 18% de la producción.'
)
Llamar PATCH "/observaciones/$($o1.id)/estado" @{ estado = 'RESUELTA' } $tA | Out-Null
Llamar PATCH "/observaciones/$($o2.id)/estado" @{ estado = 'RESUELTA' } $tA | Out-Null
Llamar PATCH "/entregas/$($e1v2.id)/estado" @{ estado = 'APROBADA' } $tA | Out-Null
Llamar PATCH "/hitos/$($h1.id)/estado" @{ estado = 'COMPLETADO' } $tA | Out-Null
Paso 'v2 corregida: observaciones resueltas, entrega aprobada, hito completado'

# --- 4. Hito 2: observado, la estudiante tiene que corregir ---------------------

Write-Host '4. Hito 2 - observado (le toca a la estudiante)' -ForegroundColor Cyan
$e2v1 = Entregar $h2 1 'Marco teórico con 7 antecedentes.' @(
    'Antecedentes: 7 estudios sobre visión por computadora aplicada a cultivos.',
    'Bases teóricas: redes neuronales convolucionales, transferencia de aprendizaje.'
)
Llamar POST "/entregas/$($e2v1.id)/observaciones" @{
    descripcion = 'Tienes 7 antecedentes y la consigna pide 10, de los últimos 5 años. Suma al menos 3 internacionales.'
} $tA | Out-Null
Llamar POST "/entregas/$($e2v1.id)/observaciones" @{
    descripcion = 'Las citas no siguen APA 7: revisa el formato autor-año y la lista de referencias.'
} $tA | Out-Null
Paso 'v1 entregada con 2 observaciones pendientes'

# --- 5. Hito 3: entregado, esperando revisión del profesor ----------------------

Write-Host '5. Hito 3 - entregado (le toca al profesor)' -ForegroundColor Cyan
Entregar $h3 1 'Profesora, subo la metodología. Tengo dudas con el tamaño de la muestra.' @(
    'Tipo: aplicada, enfoque cuantitativo. Diseño: experimental.',
    'Población: plantas de lechuga de 3 invernaderos hidropónicos en Lurín.',
    'Muestra: 1 200 imágenes etiquetadas (400 por deficiencia).',
    'Instrumento: dataset propio validado por un agrónomo.'
) | Out-Null
Paso 'v1 entregada, en revisión'

# --- 6. Asesorías, acuerdos y tareas --------------------------------------------

Write-Host '6. Asesorías y tareas' -ForegroundColor Cyan
$reunion = Llamar POST "/proyectos/$($tesis.id)/asesorias" @{
    fecha = Momento -24 10; tema = 'Revisión del planteamiento del problema'
    resumen = 'Se revisó la v1 del planteamiento. Hay que acotar la pregunta y sustentar el problema con datos.'
} $tA
$acuerdo1 = Llamar POST "/asesorias/$($reunion.id)/acuerdos" @{ descripcion = 'Delimitar el estudio a 3 invernaderos de Lurín.' } $tA
$acuerdo2 = Llamar POST "/asesorias/$($reunion.id)/acuerdos" @{ descripcion = 'Ampliar los antecedentes con estudios internacionales.' } $tA
Paso 'asesoría realizada con 2 acuerdos'

$tareaHecha = Llamar POST "/proyectos/$($tesis.id)/tareas" @{
    descripcion = 'Contactar a los 3 invernaderos y confirmar el acceso para tomar fotos.'
    acuerdoId = $acuerdo1.id; responsableId = $estudiante.user.id; fechaLimite = Dia -17
} $tA
Llamar PATCH "/tareas/$($tareaHecha.id)/completar" $null $tE | Out-Null
Llamar POST "/proyectos/$($tesis.id)/tareas" @{
    descripcion = 'Buscar 3 antecedentes internacionales (2021-2026) sobre detección de deficiencias con CNN.'
    acuerdoId = $acuerdo2.id; responsableId = $estudiante.user.id; fechaLimite = Dia 3
} $tA | Out-Null
Paso 'una tarea completada y una pendiente'

Llamar POST "/proyectos/$($tesis.id)/asesorias" @{
    fecha = Momento 3 18; tema = 'Revisión de la metodología y el tamaño de muestra'
    estado = 'PROGRAMADA'; enlace = 'https://meet.google.com/tes-demo-asesoria'
} $tE | Out-Null
Paso 'asesoría programada por la estudiante, con enlace'

# --- 7. Mensajes ----------------------------------------------------------------

Write-Host '7. Mensajes' -ForegroundColor Cyan
Llamar POST "/mensajes/con/$($estudiante.user.id)" @{
    texto = 'Hola Lucía, ya dejé las observaciones del marco teórico. Si tienes dudas con APA lo vemos en la próxima asesoría.'
} $tA | Out-Null
Llamar PUT "/mensajes/con/$($asesor.user.id)/leidos" $null $tE | Out-Null
Llamar POST "/mensajes/con/$($asesor.user.id)" @{
    texto = 'Gracias, profesora. Ya subí la metodología; ¿la podría revisar antes de la asesoría?'
} $tE | Out-Null
Paso 'conversación con un mensaje sin leer para el profesor'

# --- PDF para subir en vivo -----------------------------------------------------

# La corrección del hito 2 se hace delante del público: se deja el archivo a mano.
$enVivo = Join-Path $PSScriptRoot 'marco-teorico-v2.pdf'
[IO.File]::WriteAllBytes($enVivo, (NuevoPdf 'Marco teórico - versión 2' @(
    'Lucía Fernández Paredes - Seminario de Tesis II', '',
    'Antecedentes: 10 estudios (2021-2026), 4 de ellos internacionales.',
    'Citas y referencias corregidas según APA 7.',
    'Bases teóricas: redes neuronales convolucionales, transferencia de aprendizaje,',
    'nutrición mineral de la lechuga en sistemas hidropónicos NFT.'
)))

# --- Resumen --------------------------------------------------------------------

Write-Host ''
Write-Host 'Demo cargada.' -ForegroundColor Green
Write-Host "  Web:         $BaseUrl/login"
Write-Host "  Profesor:    $AsesorEmail / $Clave"
Write-Host "  Estudiante:  $EstudianteEmail / $Clave"
Write-Host "  Código de la clase: $($clase.codigo)"
Write-Host "  PDF para subir en vivo: $enVivo"
