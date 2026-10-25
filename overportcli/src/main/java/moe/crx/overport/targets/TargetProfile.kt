package moe.crx.overport.targets

import moe.crx.overport.patches.PATCH_VR_METADATA
import moe.crx.overport.patches.PATCH_VRAPI_OPENXR
import moe.crx.overport.patches.PATCH_VRX_METADATA
import moe.crx.overport.patches.PATCH_VRX_OPENXR

data class TargetProfile(
    val id: String,
    val displayName: String,
    val description: String,
    val supportedAbis: Set<String>,
    val excludedRecommendedPatches: Set<String> = emptySet(),
    val compatibilityPatches: List<String> = emptyList(),
    val vrApiCompatibilityPatch: String? = null,
    val runtimePackages: List<String> = emptyList(),
    val interactionProfiles: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

object TargetProfiles {
    const val GENERIC_ID = "generic"
    const val THINKREALITY_VRX_ID = "thinkreality-vrx"

    private val GENERIC = TargetProfile(
        id = GENERIC_ID,
        displayName = "Generic OpenXR",
        description = "The existing OVRPort patch set for OpenXR-compatible headsets.",
        supportedAbis = setOf("arm64-v8a", "armeabi-v7a"),
    )

    private val THINKREALITY_VRX = TargetProfile(
        id = THINKREALITY_VRX_ID,
        displayName = "Lenovo ThinkReality VRX",
        description = "Snapdragon Spaces OpenXR target for Lenovo ThinkReality VRX on Android 12/API 32.",
        supportedAbis = setOf("arm64-v8a"),
        excludedRecommendedPatches = setOf(PATCH_VR_METADATA.name),
        compatibilityPatches = listOf(
            PATCH_VRX_METADATA.name,
            PATCH_VRAPI_OPENXR.name,
            PATCH_VRX_OPENXR.name,
        ),
        vrApiCompatibilityPatch = PATCH_VRAPI_OPENXR.name,
        runtimePackages = listOf(
            "com.qualcomm.qti.spaces.services",
            "com.qualcomm.qti.openxr.overlaysession",
            "com.qualcomm.qti.xrvd.service",
            "com.lenovo.xr.vrxhome",
            "com.lenovo.xr.vrxboundary",
        ),
        interactionProfiles = listOf(
            "/interaction_profiles/oculus/touch_controller",
        ),
        notes = listOf(
            "Uses the system Snapdragon Spaces OpenXR runtime; it does not install or emulate Quest services.",
            "The runtime exposes the Oculus Touch interaction profile for the two 6DoF controllers.",
            "Passthrough, hand tracking, spatial anchors and mixed reality are outside the first compatibility target.",
        ),
    )

    val all: List<TargetProfile> = listOf(GENERIC, THINKREALITY_VRX)

    fun default(): TargetProfile = GENERIC

    fun resolve(value: String?): TargetProfile {
        val normalized = value?.trim()?.lowercase().orEmpty().ifEmpty { GENERIC_ID }
        return when (normalized) {
            GENERIC_ID, "openxr" -> GENERIC
            THINKREALITY_VRX_ID, "vrx", "lenovo-vrx" -> THINKREALITY_VRX
            else -> throw IllegalArgumentException(
                "Unknown target '$normalized'. Supported targets: ${all.joinToString { it.id }}"
            )
        }
    }
}
