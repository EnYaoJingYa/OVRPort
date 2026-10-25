package moe.crx.overport.targets

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TargetCompatibilityTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `VRX rejects an APK without ARM64 native code`() {
        directory.resolve("root/lib/armeabi-v7a").mkdirs()

        val report = TargetCompatibility.analyze(directory, TargetProfiles.resolve("vrx"))

        assertTrue(report.hasBlockers)
        assertTrue(report.issues.any {
            it.severity == CompatibilitySeverity.BLOCKER && it.message.contains("arm64-v8a")
        })
    }

    @Test
    fun `VRX native OpenXR APK does not require an injected VrApi adapter`() {
        directory.resolve("root/lib/arm64-v8a").mkdirs()

        val report = TargetCompatibility.analyze(directory, TargetProfiles.resolve("vrx"))

        val nativeOpenXr = !report.needsVrApiAdapter
        assertTrue(nativeOpenXr)
        assertTrue(report.issues.any {
            it.severity == CompatibilitySeverity.INFO && it.message.contains("native OpenXR path")
        })
    }

    @Test
    fun `VRX reports persistent Meta account and entitlement risk without claiming bypass`() {
        val arm64 = directory.resolve("root/lib/arm64-v8a").apply { mkdirs() }
        arm64.resolve("libvrapi.so").writeText("vrapi")
        arm64.resolve("libovrplatformloader.so").writeText("platform")
        directory.resolve("smali/classes/com/example/AppEntitlement.smali").apply {
            parentFile.mkdirs()
            writeText(".class public Lcom/example/AppEntitlement;")
        }

        val report = TargetCompatibility.analyze(directory, TargetProfiles.resolve("vrx"))

        assertTrue(report.issues.any {
            it.severity == CompatibilitySeverity.UNSUPPORTED && it.message.contains("strong DRM/account")
        })
        assertTrue(report.issues.any {
            it.severity == CompatibilitySeverity.INFO && it.message.contains("not bypassed")
        })
    }
}
