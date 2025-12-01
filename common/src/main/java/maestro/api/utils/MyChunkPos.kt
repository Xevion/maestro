package maestro.api.utils

import com.google.gson.annotations.SerializedName

/**
 * A non-obfuscated chunk position class for GSON serialization.
 *
 * This simple data class holds chunk coordinates (x, z) and can be serialized/deserialized
 * using GSON without obfuscation issues.
 */
data class MyChunkPos(
    @SerializedName("x")
    @JvmField var x: Int = 0,
    @SerializedName("z")
    @JvmField var z: Int = 0,
) {
    override fun toString(): String = "$x, $z"
}
