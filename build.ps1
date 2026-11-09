# Builds AURA OS with the plain Android SDK tools (no Gradle).
#
#   .\build.ps1                       debug APK, signed with a local debug key (created on first run)
#   .\build.ps1 -Release              release APK, signed with the key from the environment (see docs/BUILD.md)
#   .\build.ps1 -Install -Device 192.168.1.20:5555   also install it on a TV over ADB
#
# Needs JDK 17 (JAVA_HOME or javac on PATH) and an Android SDK with build-tools 34.0.0 and platforms;android-34
# (ANDROID_HOME, ANDROID_SDK_ROOT, or Android Studio's default location).
param([switch]$Release, [switch]$Install, [string]$Device = $env:AURA_DEVICE)

$ErrorActionPreference = "Continue"
$Root = $PSScriptRoot
$App = Join-Path $Root "app"
$Out = Join-Path $Root "build"
$Dist = Join-Path $Root "dist"
$BuildTools = "34.0.0"
$Platform = "android-34"
$MinSdk = 26
$TargetSdk = 30

function Fail($msg) { Write-Error $msg; exit 1 }
function Check($what) { if ($LASTEXITCODE -ne 0) { Fail "$what failed" } }

$sdk = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk")) |
    Where-Object { $_ -and (Test-Path (Join-Path $_ "build-tools\$BuildTools")) } | Select-Object -First 1
if (-not $sdk) { Fail "Android SDK with build-tools $BuildTools not found. Set ANDROID_HOME." }
$bt = Join-Path $sdk "build-tools\$BuildTools"
$jar = Join-Path $sdk "platforms\$Platform\android.jar"
if (-not (Test-Path $jar)) { Fail "Missing $Platform. Run: sdkmanager `"platforms;$Platform`"" }
$javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\javac.exe" } else { "javac" }
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\keytool.exe" } else { "keytool" }

$version = ([xml](Get-Content (Join-Path $App "AndroidManifest.xml"))).manifest.versionName
Write-Output "AURA OS $version ($(if ($Release) { 'release' } else { 'debug' }))"

if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force "$Out\gen", "$Out\classes", "$Out\dex", $Dist | Out-Null

& "$bt\aapt2.exe" compile --dir (Join-Path $App "res") -o "$Out\res.zip"; Check "aapt2 compile"
& "$bt\aapt2.exe" link -o "$Out\base.apk" -I $jar --manifest (Join-Path $App "AndroidManifest.xml") --java "$Out\gen" `
    --min-sdk-version $MinSdk --target-sdk-version $TargetSdk "$Out\res.zip"; Check "aapt2 link"

$src = Get-ChildItem -Recurse -Filter *.java (Join-Path $App "src"), "$Out\gen" | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -nowarn "-Xlint:-options" --release 11 -classpath $jar -d "$Out\classes" @src; Check "javac"
$cls = Get-ChildItem -Recurse -Filter *.class "$Out\classes" | ForEach-Object { $_.FullName }
& "$bt\d8.bat" --release --min-api $MinSdk --lib $jar --output "$Out\dex" @cls; Check "d8"

# Assets go in by hand: aapt2 -A on Windows stores "assets/wallpapers\01.jpg", which Android cannot open.
# Fixed entry timestamps keep the unsigned APK identical between builds of the same source.
Copy-Item "$Out\base.apk" "$Out\unsigned.apk"
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$stamp = [DateTimeOffset]::new(2020, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
$zip = [System.IO.Compression.ZipFile]::Open("$Out\unsigned.apk", 'Update')
function Add-Entry($name, $file, $level) {
    $e = $zip.CreateEntry($name, $level)
    $e.LastWriteTime = $stamp
    $s = $e.Open(); $b = [System.IO.File]::ReadAllBytes($file); $s.Write($b, 0, $b.Length); $s.Close()
}
Add-Entry "classes.dex" "$Out\dex\classes.dex" ([System.IO.Compression.CompressionLevel]::Optimal)
Get-ChildItem -Recurse -File (Join-Path $App "assets") | Sort-Object FullName | ForEach-Object {
    Add-Entry ($_.FullName.Substring($App.Length + 1).Replace('\', '/')) $_.FullName ([System.IO.Compression.CompressionLevel]::NoCompression)
}
$zip.Dispose()
& "$bt\zipalign.exe" -f -p 4 "$Out\unsigned.apk" "$Out\aligned.apk"; Check "zipalign"

if ($Release) {
    $ks = $env:AURA_RELEASE_KEYSTORE; $alias = $env:AURA_RELEASE_KEY_ALIAS; $pass = $env:AURA_RELEASE_STORE_PASS
    if (-not $ks -or -not $alias -or -not $pass) { Fail "Release signing needs AURA_RELEASE_KEYSTORE, AURA_RELEASE_KEY_ALIAS and AURA_RELEASE_STORE_PASS." }
    $apk = Join-Path $Dist "AURA-OS-v$version.apk"
    $env:AURA_SIGN_PASS = $pass
    & "$bt\apksigner.bat" sign --ks $ks --ks-key-alias $alias --ks-pass env:AURA_SIGN_PASS --key-pass env:AURA_SIGN_PASS --out $apk "$Out\aligned.apk"
    Remove-Item Env:AURA_SIGN_PASS; Check "apksigner"
} else {
    $ks = Join-Path $Root "keys\debug.keystore"
    if (-not (Test-Path $ks)) {
        New-Item -ItemType Directory -Force (Split-Path $ks) | Out-Null
        & $keytool -genkeypair -keystore $ks -storepass android -keypass android -alias aura-debug -keyalg RSA -keysize 2048 `
            -validity 10000 -dname "CN=AURA OS debug" | Out-Null; Check "keytool"
    }
    $apk = Join-Path $Dist "AURA-OS-v$version-debug.apk"
    & "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --out $apk "$Out\aligned.apk"; Check "apksigner"
}
& "$bt\apksigner.bat" verify $apk; Check "apksigner verify"
$hash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
"$hash  $(Split-Path $apk -Leaf)" | Set-Content -Encoding ASCII "$apk.sha256"
Write-Output "Built $apk ($((Get-Item $apk).Length) bytes)"
Write-Output "SHA-256 $hash"

if ($Install) {
    if (-not $Device) { Fail "Give the TV's address with -Device <ip:port> or AURA_DEVICE." }
    $adb = if ($env:ANDROID_HOME -and (Test-Path "$sdk\platform-tools\adb.exe")) { "$sdk\platform-tools\adb.exe" } else { "adb" }
    & $adb connect $Device | Out-Null
    & $adb -s $Device install -r $apk; Check "adb install"
}
