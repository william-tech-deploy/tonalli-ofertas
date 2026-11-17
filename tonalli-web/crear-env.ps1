# Crea el archivo .env con claves al azar para probar el sitio en tu computadora.
# Uso (dentro de la carpeta tonalli-web):
#   powershell -ExecutionPolicy Bypass -File .\crear-env.ps1
# No usar este .env en un servidor publico: activa los datos de ejemplo y apaga la verificacion de correo.

# Genera una clave con el generador criptografico de Windows (no con Get-Random, que es predecible).
# El alfabeto tiene 64 simbolos: asi cada byte al azar cae en un simbolo con la misma probabilidad.
function NuevaClave([int]$n) {
  $abc = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'
  $bytes = New-Object byte[] $n
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($bytes)
  $rng.Dispose()
  -join ($bytes | ForEach-Object { $abc[$_ % 64] })
}

$adm = "Tonalli-" + (NuevaClave 12) + "-local"
$demo = "Demo-" + (NuevaClave 12) + "-local"

# Cada linea va entre parentesis y SIN comas: en PowerShell la coma tiene prioridad sobre el "+",
# y con comas todo el archivo quedaba pegado en una sola linea.
$lineas = @(
  ("DB_PASSWORD=" + (NuevaClave 28))
  ("DB_ROOT_PASSWORD=" + (NuevaClave 28))
  "TONALLI_ADMIN_CORREO=admin@tonalli.local"
  ("TONALLI_ADMIN_PASSWORD=" + $adm)
  "TONALLI_DEMO=true"
  ("TONALLI_DEMO_PASSWORD=" + $demo)
  "BASE_URL=http://localhost:8080"
  "COOKIE_SECURE=false"
  "VERIFICACION_OBLIGATORIA=false"
  "MAIL_FROM=no-reply@tonalli.local"
  "SPRING_MAIL_HOST="
  "SPRING_MAIL_PORT=587"
  "SPRING_MAIL_USERNAME="
  "SPRING_MAIL_PASSWORD="
  "DOMINIO=localhost"
)

# ASCII: Docker Compose no entiende el UTF-16 que PowerShell 5 escribe por defecto.
Set-Content -Path (Join-Path $PSScriptRoot ".env") -Value $lineas -Encoding ascii

Write-Host ""
Write-Host "Listo. Archivo .env creado con $($lineas.Count) variables."
Write-Host "Administrador:        admin@tonalli.local  /  $adm"
Write-Host "Usuarios de ejemplo:  demo@tonalli.local (y 4 mas)  /  $demo"
Write-Host "Anota estas claves: no se vuelven a mostrar (estan en el archivo .env)."
