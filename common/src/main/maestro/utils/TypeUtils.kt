package maestro.utils

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Utility functions for working with Java reflection types.
 */
object TypeUtils {
    /**
     * Resolves the "base type" for the specified type.
     *
     * For example, if the specified type is `List<String>`, then `List.class` will be returned.
     * If the specified type is already a class, then it is directly returned.
     *
     * @param type The type to resolve
     * @return The base class, or null if the type cannot be resolved
     */
    @JvmStatic
    fun resolveBaseClass(type: Type): Class<*>? =
        when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as? Class<*>
            else -> null
        }
}
