param(
    [string]$Sdk = 'E:\Gao_Ji\Ni_Xiang\android-sdk',
    [string]$DispatcherLib = 'E:\Gao_Ji\ThinkReality_VRX\openxr-dispatcher-open\build-android-vrx\libopenxr_loader.so',
    [string]$GenericLoader,
    [string]$Keystore = 'E:\Gao_Ji\ThinkReality_VRX\VRX_Keys\VirtualDesktop.Android.keystore',
    [string]$Output = 'E:\Gao_Ji\ThinkReality_VRX\VRX_Output\VrApiSmoke.apk'
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = (Resolve-Path (Join-Path $here '..\..\..')).Path
$ndk = Join-Path $Sdk 'ndk\27.3.13750724'
$jdk = Join-Path $Sdk 'jdk17tmp\jdk-17.0.20.1+1'
$buildTools = Join-Path $Sdk 'build-tools\35.0.0'
$androidJar = Join-Path $Sdk 'platforms\android-32\android.jar'
$build = Join-Path $repo 'build\vrapi-smoke'
$nativeDir = Join-Path $build 'lib\arm64-v8a'

if (-not $GenericLoader) {
    throw 'Pass -GenericLoader (libopenxr_loader_generic.so extracted from an OVRPort library bundle or converted APK).'
}
foreach ($path in @($DispatcherLib, $GenericLoader, $Keystore, $androidJar)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required file is missing: $path"
    }
}

$payload = Join-Path $repo 'overportcli\build\generated\vrapiResources\main\vrapi\arm64-v8a\libvrapi.bin'
if (-not (Test-Path -LiteralPath $payload -PathType Leaf)) {
    throw 'Build the OVRPort CLI with -PwithVrApi=true before building the smoke APK.'
}

New-Item -ItemType Directory -Path $nativeDir -Force | Out-Null
Copy-Item -LiteralPath $payload -Destination (Join-Path $nativeDir 'libvrapi.so') -Force
Copy-Item -LiteralPath $DispatcherLib -Destination (Join-Path $nativeDir 'libopenxr_loader.so') -Force
Copy-Item -LiteralPath $GenericLoader -Destination (Join-Path $nativeDir 'libopenxr_loader_generic.so') -Force

$compiler = Join-Path $ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe'
$sysroot = Join-Path $ndk 'toolchains\llvm\prebuilt\windows-x86_64\sysroot'
& $compiler '--target=aarch64-linux-android29' "--sysroot=$sysroot" `
    '-std=c++17' '-O2' '-fPIC' '-shared' '-static-libstdc++' '-fvisibility=hidden' `
    "-I$repo\native\vrapi" "-I$repo\native\vrapi\build\include" `
    (Join-Path $here 'vrx_smoke.cpp') "-L$nativeDir" `
    '-lvrapi' '-lvulkan' '-llog' '-landroid' '-ldl' `
    '-Wl,--no-undefined' '-Wl,-z,max-page-size=16384' `
    '-o' (Join-Path $nativeDir 'libvrx_smoke.so')
if ($LASTEXITCODE -ne 0) { throw 'Native smoke compilation failed.' }

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
$classes = Join-Path $build 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& (Join-Path $jdk 'bin\javac.exe') -classpath $androidJar -d $classes (Join-Path $here 'MainActivity.java')
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
& (Join-Path $buildTools 'd8.bat') --min-api 29 --output $build `
    (Join-Path $classes 'moe\crx\overport\vrapismoke\MainActivity.class')
if ($LASTEXITCODE -ne 0) { throw 'DEX compilation failed.' }

$base = Join-Path $build 'base.apk'
$aligned = Join-Path $build 'aligned.apk'
& (Join-Path $buildTools 'aapt2.exe') link `
    --manifest (Join-Path $here 'AndroidManifest.xml') -I $androidJar `
    --min-sdk-version 29 --target-sdk-version 32 --version-code 1 --version-name 1.0 -o $base
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed.' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open($base, [IO.Compression.ZipArchiveMode]::Update)
try {
    $entry = $zip.CreateEntry('classes.dex', [IO.Compression.CompressionLevel]::Optimal)
    $out = $entry.Open()
    $input = [IO.File]::OpenRead((Join-Path $build 'classes.dex'))
    try { $input.CopyTo($out) } finally { $input.Dispose(); $out.Dispose() }
    Get-ChildItem -LiteralPath (Join-Path $build 'lib') -Recurse -File | ForEach-Object {
        $name = $_.FullName.Substring($build.Length + 1).Replace('\', '/')
        $entry = $zip.CreateEntry($name, [IO.Compression.CompressionLevel]::Optimal)
        $out = $entry.Open()
        $input = [IO.File]::OpenRead($_.FullName)
        try { $input.CopyTo($out) } finally { $input.Dispose(); $out.Dispose() }
    }
} finally {
    $zip.Dispose()
}

& (Join-Path $buildTools 'zipalign.exe') -f -P 16 4 $base $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed.' }
New-Item -ItemType Directory -Path (Split-Path -Parent $Output) -Force | Out-Null
& (Join-Path $buildTools 'apksigner.bat') sign --ks $Keystore `
    --ks-pass pass:password --key-pass pass:password --ks-key-alias key `
    --out $Output $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }

Write-Host "VrApi smoke APK: $Output"
