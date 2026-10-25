package moe.crx.overport.targets

import java.io.File

enum class CompatibilitySeverity {
    INFO,
    WARNING,
    BLOCKER,
}

data class CompatibilityIssue(
    val severity: CompatibilitySeverity,
    val message: String,
)

data class TargetCompatibilityReport(
    val target: TargetProfile,
    val issues: List<CompatibilityIssue>,
    val needsVrApiAdapter: Boolean = false,
) {
    val hasBlockers: Boolean
        get() = issues.any { it.severity == CompatibilitySeverity.BLOCKER }

    fun printTo(output: (String) -> Unit = ::println) {
        output("Target compatibility preflight: ${target.displayName} (${target.id})")
        if (issues.isEmpty()) {
            output("[INFO] No target-specific compatibility risks were detected.")
            return
        }
        issues.forEach { issue ->
            val label = when (issue.severity) {
                CompatibilitySeverity.INFO -> "INFO"
                CompatibilitySeverity.WARNING -> "WARN"
                CompatibilitySeverity.BLOCKER -> "UNSUPPORTED"
            }
            output("[$label] ${issue.message}")
        }
    }
}

object TargetCompatibility {
    private val strongServiceMarkers = listOf(
        "entitlement",
        "licensing",
        "authservice",
        "accountservice",
        "playbilling",
        "drm",
    )

    private val firstPassFeatures = listOf(
        "android.permission.HAND_TRACKING",
        "android.permission.EYE_TRACKING_COARSE",
        "android.permission.EYE_TRACKING_FINE",
        "android.permission.SCENE_UNDERSTANDING_COARSE",
        "android.permission.SCENE_UNDERSTANDING_FINE",
    )

    fun analyze(workspace: File, target: TargetProfile): TargetCompatibilityReport {
        if (target.id == TargetProfiles.GENERIC_ID) {
            return TargetCompatibilityReport(target, emptyList())
        }

        val issues = mutableListOf<CompatibilityIssue>()
        val nativeRoot = workspace.resolve("root/lib")
        val abiDirectories = nativeRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        val abis = abiDirectories.map { it.name }.toSet()
        val supported = abis.intersect(target.supportedAbis)

        if (supported.isEmpty()) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.BLOCKER,
                "${target.displayName} requires one of ${target.supportedAbis.joinToString()}; " +
                    "the APK contains ${abis.ifEmpty { setOf("no native ABIs") }.joinToString()}.",
            )
        }

        val unsupportedVrApi = abiDirectories
            .filter { it.name !in target.supportedAbis && it.resolve("libvrapi.so").isFile }
            .map { it.name }
            .sorted()
        if (unsupportedVrApi.isNotEmpty()) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.BLOCKER,
                "Contains VrApi for unsupported ABIs: ${unsupportedVrApi.joinToString()}. " +
                    "The VRX compatibility adapter is ARM64-only.",
            )
        }

        val arm64 = nativeRoot.resolve("arm64-v8a")
        val needsVrApiAdapter = arm64.resolve("libvrapi.so").isFile
        if (!needsVrApiAdapter) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.INFO,
                "No VrApi dependency found; this APK will use its native OpenXR path. " +
                    "The VRX VrApi adapter will not be injected.",
            )
        }

        if (arm64.resolve("libovrplatformloader.so").isFile) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.INFO,
                "Meta Platform SDK loader detected. OVRPort adds message-type compatibility, but " +
                    "account authentication, entitlement checks and service-authoritative content are not bypassed.",
            )
        }

        val matchingServiceMarkers = findServiceMarkers(workspace)
        if (matchingServiceMarkers.isNotEmpty()) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.WARNING,
                "Potential strong DRM/account checks detected (${matchingServiceMarkers.joinToString()}). " +
                    "The app may refuse to run without a live Meta account, entitlement or service.",
            )
        }

        val manifest = workspace.resolve("AndroidManifest.xml.json")
        if (manifest.isFile) {
            val text = manifest.readText()
            val unsupportedFeatures = firstPassFeatures.filter { text.contains(it) }
            if (unsupportedFeatures.isNotEmpty()) {
                issues += CompatibilityIssue(
                    CompatibilitySeverity.WARNING,
                    "Requests features outside the first VRX target (${unsupportedFeatures.joinToString()}); " +
                        "they may be ignored or fail at runtime.",
                )
            }
        }

        issues += CompatibilityIssue(
            CompatibilitySeverity.INFO,
            "Expected runtime packages: ${target.runtimePackages.joinToString()}. " +
                "These are system components and must not be bundled into the APK.",
        )
        issues += CompatibilityIssue(
            CompatibilitySeverity.INFO,
            "Controller profile: ${target.interactionProfiles.joinToString()}; tracking and interaction still " +
                "require an on-device smoke test.",
        )
        return TargetCompatibilityReport(target, issues, needsVrApiAdapter)
    }

    private fun findServiceMarkers(workspace: File): List<String> {
        val matches = linkedSetOf<String>()
        val smali = workspace.resolve("smali")
        if (smali.isDirectory) {
            smali.walkTopDown()
                .onEnter { directory -> matches.size < 8 && directory.name != ".cache" }
                .filter { it.isFile && it.name.endsWith(".smali") }
                .forEach { file ->
                    val normalized = file.invariantSeparatorsPath.lowercase()
                    strongServiceMarkers.firstOrNull(normalized::contains)?.let(matches::add)
                }
        }
        workspace.resolve("root/lib/arm64-v8a").listFiles()?.forEach { file ->
            val normalized = file.name.lowercase()
            strongServiceMarkers.firstOrNull(normalized::contains)?.let(matches::add)
        }
        return matches.sorted()
    }
}
