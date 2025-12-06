package maestro.selector

/**
 * Base class for selectors that cache their resolution results.
 *
 * Resolving selectors (especially wildcards and categories) requires iterating
 * the Minecraft registry, which is expensive. This base class ensures that
 * resolution is computed only once per selector instance.
 *
 * ## Design Decision: Sealed Classes
 *
 * [BlockSelector] and [EntitySelector] remain sealed classes despite requiring
 * `instanceof` checks in Java. This decision prioritizes:
 * 1. Exhaustiveness checking in Kotlin `when` expressions
 * 2. Kotlin-first development direction for this codebase
 * 3. Java code only needing `resolve()` and `matches()`, not pattern matching
 *
 * @param T The type of registry entry this selector matches (e.g., Block, EntityType)
 */
abstract class CachingSelector<T>(
    override val rawInput: String,
    override val type: SelectorType,
) : TargetSelector<T> {
    private val cachedResolution: Set<T> by lazy { computeResolve() }

    /**
     * Computes the set of matching entries.
     *
     * This is called lazily on the first [resolve] invocation and cached thereafter.
     * Implementations should not cache internally - the base class handles caching.
     */
    protected abstract fun computeResolve(): Set<T>

    /**
     * Returns the cached set of matching entries.
     *
     * This method is safe to call multiple times - the computation happens only once.
     */
    final override fun resolve(): Set<T> = cachedResolution
}
