package maestro.api

import maestro.api.utils.SettingsUtil
import maestro.api.utils.TypeUtils
import java.lang.reflect.Type

/**
 * A setting with a typed value, default value, and optional category.
 *
 * Settings can be created using the builder pattern:
 * ```kotlin
 * val mySetting = setting(true) {
 *     category = SettingCategory.MOVEMENT
 *     description = "Allow sprinting"
 * }
 * ```
 *
 * @param T The type of value this setting holds
 */
class Setting<T : Any> private constructor(
    initialValue: T,
    val category: SettingCategory?,
    val description: String?,
    private val javaOnly: Boolean,
) {
    @JvmField
    var value: T = initialValue

    @JvmField
    val defaultValue: T = initialValue

    /**
     * The name of this setting (set via reflection by Settings container).
     */
    @JvmField
    var name: String = ""

    /**
     * The type of this setting (set via reflection by Settings container).
     */
    internal var type: Type? = null

    /**
     * Gets the class of the value this setting holds.
     */
    @Suppress("UNCHECKED_CAST")
    fun getValueClass(): Class<T> = TypeUtils.resolveBaseClass(getType()) as Class<T>

    /**
     * Gets the type of this setting.
     */
    fun getType(): Type = type ?: error("Setting type not initialized")

    /**
     * Gets the name of this setting.
     */
    fun getName(): String = name

    /**
     * Returns true if this setting is Java-only (cannot be serialized/deserialized).
     */
    fun isJavaOnly(): Boolean = javaOnly

    /**
     * Resets this setting to its default value.
     */
    fun reset() {
        value = defaultValue
    }

    override fun toString(): String = SettingsUtil.settingToString(this)

    /**
     * Builder for creating settings with optional metadata.
     */
    class Builder<T : Any>(
        private val defaultValue: T,
    ) {
        var category: SettingCategory? = null
        var description: String? = null
        var javaOnly: Boolean = false

        fun build(): Setting<T> = Setting(defaultValue, category, description, javaOnly)
    }

    companion object {
        /**
         * Creates a setting with the given default value and optional configuration.
         *
         * @param defaultValue The default value for this setting
         * @param block Optional configuration block
         * @return A new setting instance
         */
        operator fun <T : Any> invoke(
            defaultValue: T,
            block: Builder<T>.() -> Unit = {},
        ): Setting<T> = Builder(defaultValue).apply(block).build()
    }
}
