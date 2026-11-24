$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
if (Test-Path build/classes) { Remove-Item -Recurse -Force build/classes }
New-Item -ItemType Directory -Force -Path build/classes, build/input | Out-Null
$sources = @(Get-ChildItem src/main/java/quallo -Filter *.java | ForEach-Object FullName)
& javac --release 21 -encoding UTF-8 -d build/classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }
Copy-Item src/main/resources/* build/classes -Recurse -Force
& jar --create --file build/input/quallo.jar --main-class quallo.Main -C build/classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed ($LASTEXITCODE)" }
