$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectDir

[xml]$Pom = Get-Content -Raw (Join-Path $ProjectDir "pom.xml")
$Version = $Pom.project.version.Trim()
if ([string]::IsNullOrWhiteSpace($Version)) {
    throw "Could not read the project version from pom.xml."
}
if ($Version.EndsWith("-SNAPSHOT")) {
    throw "Release packaging requires a non-SNAPSHOT Maven version (found $Version)."
}

$InputDir = Join-Path $ProjectDir "target\package-input"
$DistDir = Join-Path $ProjectDir "target\dist"
Remove-Item $InputDir, $DistDir -Recurse -Force -ErrorAction SilentlyContinue

& .\mvnw.cmd -q clean package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
New-Item (Join-Path $InputDir "lib") -ItemType Directory -Force | Out-Null
New-Item $DistDir -ItemType Directory -Force | Out-Null
& .\mvnw.cmd -q dependency:copy-dependencies -DincludeScope=runtime "-DoutputDirectory=$InputDir\lib"
if ($LASTEXITCODE -ne 0) { throw "Dependency staging failed." }
Copy-Item (Join-Path $ProjectDir "target\remote-manager-$Version.jar") $InputDir

$Common = @(
    "--name", "Remote Manager",
    "--app-version", $Version,
    "--vendor", "Remote Manager",
    "--description", "SSH connection manager backed by a KeePass vault",
    "--input", $InputDir,
    "--main-jar", "remote-manager-$Version.jar",
    "--main-class", "com.example.remotemanager.app.Main",
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--icon", (Join-Path $ProjectDir "src\main\resources\icons\app\remote-manager.ico"),
    "--dest", $DistDir
)
if ($env:JPACKAGE_RUNTIME_IMAGE) {
    $Common += @("--runtime-image", $env:JPACKAGE_RUNTIME_IMAGE)
}

& jpackage @Common --type app-image
if ($LASTEXITCODE -ne 0) { throw "Portable app image creation failed." }
$Portable = Join-Path $DistDir "Remote-Manager-$Version-windows-x64.zip"
Compress-Archive -Path (Join-Path $DistDir "Remote Manager") -DestinationPath $Portable
Remove-Item (Join-Path $DistDir "Remote Manager") -Recurse -Force

& jpackage @Common --type exe `
    --win-per-user-install `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut `
    --win-upgrade-uuid "BA8F2B07-47E3-4B9E-88D1-75BE765347BD"
if ($LASTEXITCODE -ne 0) { throw "Windows installer creation failed." }

$Checksum = Join-Path $DistDir "SHA256SUMS-windows-x64.txt"
Get-ChildItem $DistDir -File | Where-Object { $_.Extension -in ".exe", ".zip" } | Sort-Object Name | ForEach-Object {
    $Hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$Hash  $($_.Name)"
} | Set-Content $Checksum -Encoding ascii
