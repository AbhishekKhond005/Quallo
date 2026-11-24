param([string]$Version = "1.0.1")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
& "$PSScriptRoot/build.ps1"
New-Item -ItemType Directory -Force -Path dist | Out-Null
if (Test-Path build/app-image) { Remove-Item -Recurse -Force build/app-image }
if ($env:WIX -and !(Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    $env:Path = (Join-Path $env:WIX "bin") + ";" + $env:Path
}
& jpackage --type app-image --dest build/app-image --input build/input --main-jar quallo.jar `
  --name Quallo --app-version $Version --vendor Quallo --description "Offline game production planner" `
  --icon packaging/quallo.ico --add-modules "java.desktop,java.xml,jdk.localedata"
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed ($LASTEXITCODE)" }
& jpackage --type msi --dest dist --app-image build/app-image/Quallo --name Quallo --app-version $Version `
  --vendor Quallo --description "Offline game production planner" --win-menu --win-menu-group Quallo `
  --win-shortcut --win-per-user-install --win-upgrade-uuid "771e545c-3217-4e88-bc2a-fc7c845492d8"
if ($LASTEXITCODE -ne 0) { throw "jpackage MSI failed ($LASTEXITCODE)" }
$archive = "dist/quallo-$Version-windows-x64.zip"
Compress-Archive -Path build/app-image/Quallo -DestinationPath $archive -CompressionLevel Optimal -Force
