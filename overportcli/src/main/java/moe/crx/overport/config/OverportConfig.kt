package moe.crx.overport.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OverportConfigLanguage(
    @SerialName("english_name")
    var englishName: String = "",
    @SerialName("native_name")
    var nativeName: String = "",
    @SerialName("tag")
    var tag: String = "",
)

@Serializable
data class OverportConfigAsset(
    @SerialName("asset_id")
    var assetId: Long = 0,
    @SerialName("file_path")
    var filePath: String,
    @SerialName("asset_type")
    var assetType: String = "default",
    @SerialName("language")
    var language: OverportConfigLanguage = OverportConfigLanguage(),
    @SerialName("download_status")
    var downloadStatus: String = "installed",
    @SerialName("iap_status")
    var iapStatus: String = "entitled",
    @SerialName("metadata")
    var metadata: String = "",
)

@Serializable
data class OverportConfigApplication(
    @SerialName("app_id")
    var appId: String,
    @SerialName("products")
    var products: List<String>,
    @SerialName("assets")
    var assets: List<OverportConfigAsset>,
)

@Serializable
data class OverportPatchedInfo(
    @SerialName("by")
    var by: String,
    @SerialName("with")
    var with: String,
    @SerialName("patches")
    var patches: List<String>,
    @SerialName("target")
    var target: String? = null,
)

@Serializable
data class OverportConfig(
    @SerialName("version")
    var version: Int = 0,
    @SerialName("disable_controller_offset")
    var disableControllerOffset: Int = 0,
    @SerialName("force_passthrough")
    var forcePassthrough: Int = 0,
    @SerialName("disable_space_warp")
    var disableSpaceWarp: Int = 0,
    @SerialName("verbose_logging")
    var verboseLogging: Int = 0,
    @SerialName("suggested_texture_width")
    var suggestedTextureWidth: Int = 0,
    @SerialName("suggested_texture_height")
    var suggestedTextureHeight: Int = 0,
    @SerialName("suggested_cpu_level")
    var suggestedCpuLevel: Int = 0,
    @SerialName("suggested_gpu_level")
    var suggestedGpuLevel: Int = 0,
    @SerialName("vibration_multiplier")
    var vibrationMultiplier: Double = 1.0,
    @SerialName("vibration_frequency_multiplier")
    var vibrationFrequencyMultiplier: Double = 1.0,
    @SerialName("database")
    var database: List<OverportConfigApplication> = listOf(),
    @SerialName("patched")
    var patched: OverportPatchedInfo? = null,
)
