package moe.crx.overport.patches

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VrxOpenXrPatchTest {
    @TempDir
    lateinit var directory: File

    private val dispatcherBytes by lazy {
        assertNotNull(javaClass.getResourceAsStream(VRX_OPENXR_DISPATCHER_RESOURCE))
            .use { it.readBytes() }
    }

    @Test
    fun `open dispatcher resource is packaged`() {
        assertTrue(dispatcherBytes.size > 16_384)
        assertContentEquals(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()),
            dispatcherBytes.copyOfRange(0, 4))
    }

    @Test
    fun `VRX patch replaces the OVRPort dispatcher while preserving the generic loader`() {
        val libraries = directory.resolve("root/lib/arm64-v8a").apply { mkdirs() }
        libraries.resolve("libopenxr_loader.so").writeText("closed-dispatcher")
        libraries.resolve("libopenxr_loader_generic.so").writeText("generic-loader")

        installVrxOpenXrDispatcher(directory) { dispatcherBytes }

        assertContentEquals(dispatcherBytes, libraries.resolve("libopenxr_loader.so").readBytes())
        assertContentEquals("generic-loader".toByteArray(), libraries.resolve("libopenxr_loader_generic.so").readBytes())
    }

    @Test
    fun `missing generic loader fails without silently producing a non-starting APK`() {
        directory.resolve("root/lib/arm64-v8a").mkdirs()

        val error = assertFailsWith<IllegalArgumentException> {
            installVrxOpenXrDispatcher(directory) { dispatcherBytes }
        }

        assertTrue(error.message.orEmpty().contains("libopenxr_loader_generic.so"))
    }
}
