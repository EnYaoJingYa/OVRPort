package moe.crx.overport.patches

import moe.crx.overport.patching.Patch
import java.io.File

internal const val VRX_OPENXR_DISPATCHER_RESOURCE =
    "/dispatcher/arm64-v8a/libopenxr_loader.bin"

private object VrxOpenXrDispatcherResource

internal fun installVrxOpenXrDispatcher(
    workspace: File,
    loadPayload: () -> ByteArray?,
) {
    val libraries = workspace.resolve("root/lib/arm64-v8a")
    require(libraries.isDirectory) {
        "Cannot apply patch_vrx_openxr: no arm64-v8a native library directory was found."
    }
    val loader = libraries.resolve("libopenxr_loader.so")
    val genericLoader = libraries.resolve("libopenxr_loader_generic.so")
    require(genericLoader.isFile) {
        "Cannot apply patch_vrx_openxr: libopenxr_loader_generic.so is missing. " +
            "patch_copy_libraries must run first and provide the OVRPort OpenXR libraries."
    }
    val payload = loadPayload()?.takeIf { it.isNotEmpty() }
    require(payload != null) {
        "Cannot apply patch_vrx_openxr: $VRX_OPENXR_DISPATCHER_RESOURCE is unavailable."
    }
    loader.writeBytes(payload)
}

val PATCH_VRX_OPENXR = Patch("patch_vrx_openxr", false) {
    selectWorkspace {
        installVrxOpenXrDispatcher(file) {
            VrxOpenXrDispatcherResource::class.java
                .getResourceAsStream(VRX_OPENXR_DISPATCHER_RESOURCE)
                ?.use { it.readBytes() }
        }
    }
}
