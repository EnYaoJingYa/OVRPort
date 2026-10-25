# Open-source OpenXR dispatcher resource

`overportcli/src/main/resources/dispatcher/arm64-v8a/libopenxr_loader.bin` is
the ARM64 build of
[`Molier/openxr-dispatcher-open`](https://github.com/Molier/openxr-dispatcher-open).
The `.bin` suffix keeps ordinary Gradle resource processing from changing the
native library; `patch_vrx_openxr` writes the bytes to
`lib/arm64-v8a/libopenxr_loader.so`.

The resource is licensed under the MIT license in this directory. The
dispatcher loads `libopenxr_loader_generic.so` for the Lenovo VRX and then
forwards OpenXR calls to the Snapdragon Spaces runtime. It is not the closed
dispatcher distributed by the original OVRPort release service.

## Rebuild

Use Android NDK r27d and CMake/Ninja from the Android SDK:

```powershell
$sdk = "E:\Gao_Ji\Ni_Xiang\android-sdk"
$source = "E:\Gao_Ji\ThinkReality_VRX\openxr-dispatcher-open"
& "$sdk\cmake\3.22.1\bin\cmake.exe" -S $source -B "$source\build-android-vrx" -G Ninja `
  -DCMAKE_TOOLCHAIN_FILE="$sdk\ndk\27.3.13750724\build\cmake\android.toolchain.cmake" `
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DCMAKE_BUILD_TYPE=Release
& "$sdk\cmake\3.22.1\bin\cmake.exe" --build "$source\build-android-vrx"
Copy-Item "$source\build-android-vrx\libopenxr_loader.so" `
  "overportcli\src\main\resources\dispatcher\arm64-v8a\libopenxr_loader.bin" -Force
```

After replacing the binary, run `:overportcli:test`; `VrxOpenXrPatchTest`
checks that the resource exists and is an ELF shared object.
