package moe.crx.overport.targets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TargetProfilesTest {
    @Test
    fun `VRX aliases resolve to the independent target`() {
        for (value in listOf("thinkreality-vrx", "vrx", "Lenovo-VRX")) {
            assertEquals(TargetProfiles.THINKREALITY_VRX_ID, TargetProfiles.resolve(value).id)
        }
    }

    @Test
    fun `generic remains the default target`() {
        assertEquals(TargetProfiles.GENERIC_ID, TargetProfiles.resolve(null).id)
        assertEquals(TargetProfiles.GENERIC_ID, TargetProfiles.resolve("").id)
    }

    @Test
    fun `VRX profile has the runtime and controller assumptions used by the patcher`() {
        val target = TargetProfiles.resolve("vrx")

        assertEquals(setOf("arm64-v8a"), target.supportedAbis)
        assertTrue(target.runtimePackages.contains("com.qualcomm.qti.spaces.services"))
        assertTrue(target.interactionProfiles.contains("/interaction_profiles/oculus/touch_controller"))
        assertTrue(target.compatibilityPatches.containsAll(
            listOf("patch_vrx_metadata", "patch_vrapi_openxr", "patch_vrx_openxr")
        ))
    }

    @Test
    fun `unknown target is rejected with available choices`() {
        val error = assertFailsWith<IllegalArgumentException> {
            TargetProfiles.resolve("pico")
        }

        assertTrue(error.message.orEmpty().contains("thinkreality-vrx"))
        assertTrue(error.message.orEmpty().contains("generic"))
    }
}
