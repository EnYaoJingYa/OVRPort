package moe.crx.overport.patches

import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import moe.crx.overport.patching.Patch
import moe.crx.overport.utils.elem
import moe.crx.overport.utils.elemEach
import moe.crx.overport.utils.nameAttribute
import moe.crx.overport.utils.named
import moe.crx.overport.utils.takeNodes
import moe.crx.overport.utils.takeNodesEach

private const val VRX_RUNTIME_BROKER = "com.qualcomm.qti.openxrruntime_broker"

private fun createBrokerQuery(): JSONObject {
    return JSONObject().put("node_type", "element").put("name", "provider").put(
        "attributes", JSONArray().put(
            JSONObject().put("name", "authorities").put("id", 16842776)
                .put("uri", "http://schemas.android.com/apk/res/android").put("prefix", "android")
                .put("value_type", "STRING").put("data", VRX_RUNTIME_BROKER)
        )
    )
}

private fun ensureVrxBrokerQuery(manifest: JSONObject?) {
    val manifestNode = manifest
        ?.elemEach<JSONObject> { named("manifest") }
        ?.firstOrNull()
        ?: return
    val manifestNodes = manifestNode.elem<JSONArray>("nodes")
        ?: JSONArray().also { manifestNode.put("nodes", it) }
    val queries = manifestNodes.elemEach<JSONObject> { named("queries") }
        .firstOrNull()
        ?: JSONObject().put("node_type", "element").put("name", "queries").put("nodes", JSONArray())
            .also(manifestNodes::put)
    val queryNodes = queries.elem<JSONArray>("nodes")
        ?: JSONArray().also { queries.put("nodes", it) }
    val alreadyPresent = queryNodes.elemEach<JSONObject> {
        named("provider") && toString().contains(VRX_RUNTIME_BROKER)
    }.isNotEmpty()
    if (!alreadyPresent) {
        queryNodes.put(createBrokerQuery())
    }
}

val PATCH_VRX_METADATA = Patch("patch_vrx_metadata", false) {
    selectManifestJson {
        ensureVrxBrokerQuery(this)
        takeNodesEach({ named("manifest") }) {
            takeNodesEach({ named("uses-feature") }) {
                if (nameAttribute() in setOf(
                        "android.software.xr.api.openxr",
                        "android.software.xr.api.spatial",
                        "android.software.xr.input.controller",
                        "android.hardware.xr.input.hand_tracking",
                    )
                ) {
                    null
                } else {
                    this
                }
            }
            takeNodesEach({ named("uses-permission") }) {
                if (nameAttribute() in setOf(
                        "org.khronos.openxr.permission.OPENXR",
                        "org.khronos.openxr.permission.OPENXR_SYSTEM",
                        "android.permission.VIBRATE",
                    )
                ) {
                    null
                } else {
                    this
                }
            }
            takeNodes {
                this?.put(createUsesFeature("android.software.xr.api.openxr", false))
                    ?.put(createUsesFeature("android.software.xr.api.spatial", false))
                    ?.put(createUsesFeature("android.software.xr.input.controller", false))
                    ?.put(createUsesFeature("android.hardware.xr.input.hand_tracking", false))
                    ?.put(createUsesPermission("org.khronos.openxr.permission.OPENXR"))
                    ?.put(createUsesPermission("org.khronos.openxr.permission.OPENXR_SYSTEM"))
                    ?.put(createUsesPermission("android.permission.VIBRATE"))
            }
            takeNodesEach({ named("application") }) {
                takeNodesEach({ named("meta-data") }) {
                    if (nameAttribute() == "com.oculus.supportedDevices") null else this
                }
                takeNodesEach({ named("activity") }) {
                    takeNodes {
                        this?.put(
                            createProperty(
                                "android.window.PROPERTY_XR_ACTIVITY_START_MODE",
                                "XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED",
                            )
                        )?.put(
                            createProperty(
                                "android.window.PROPERTY_XR_BOUNDARY_TYPE_RECOMMENDED",
                                "XR_BOUNDARY_TYPE_LARGE",
                            )
                        )
                    }
                }
            }
        }
    }
}
