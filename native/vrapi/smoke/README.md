# VrApi VRX smoke test

This is a small Android APK that exercises the project-built `libvrapi.so`
adapter directly on ThinkReality VRX. It does not contain or redistribute a
Quest game or Meta VrApi binary.

The test:

1. calls `vrapi_Initialize`;
2. creates a Vulkan device using the extensions returned by VrApi;
3. registers it with `vrapi_CreateSystemVulkan`;
4. enters VR with `vrapi_EnterVrMode`;
5. reads predicted head tracking;
6. submits empty frames and enumerates tracked-remote controllers.

Current VRX result:

- `vrapi_Initialize=0`;
- `vrapi_CreateSystemVulkan=0`;
- `vrapi_EnterVrMode` returns a valid `ovrMobile`;
- head tracking returns a finite orientation and valid 6DoF position;
- empty frame submission succeeds repeatedly;
- controller enumeration remains zero while no controller action becomes
  active. The same VRX session exposes two tracked controller spaces and real
  button/analog values through the OpenXR application smoke test, so this is
  still an open VrApi input-activation compatibility item.

The adapter was adjusted for VRX in two places during this test:

- initial recenter accepts poses with position and orientation `VALID`;
- `vrapi_EnterVrMode` synchronizes input before returning to clients that
  enumerate controllers immediately.

## Build

Requirements are the Android SDK, NDK r27d, JDK 17, and an experimental
OVRPort CLI build containing the VrApi resource. Build with:

```powershell
.\build_smoke.ps1 `
  -GenericLoader='E:\path\to\libopenxr_loader_generic.so' `
  -Keystore='E:\Gao_Ji\ThinkReality_VRX\VRX_Keys\VirtualDesktop.Android.keystore'
```

The default SDK and dispatcher paths match this workspace. Use
`adb install -r` on the generated `VrApiSmoke.apk`, launch
`moe.crx.overport.vrapismoke/.MainActivity`, and inspect:

```powershell
adb logcat -s VrApiVrxSmoke OVRPortVrApi OpenXRDispatch
```
