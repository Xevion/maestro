package maestro.cache

import maestro.utils.PackedBlockPos
import java.util.Collections
import java.util.Date

/** A marker for a position in the world. */
data class Waypoint
    @JvmOverloads
    constructor(
        private val name: String,
        private val tag: Tag,
        private val location: PackedBlockPos,
        private val creationTimestamp: Long = System.currentTimeMillis(),
    ) {
        fun getName(): String = name

        fun getTag(): Tag = tag

        fun getCreationTimestamp(): Long = creationTimestamp

        fun getLocation(): PackedBlockPos = location

        override fun toString(): String =
            String.format(
                "%s %s %s",
                name,
                PackedBlockPos.from(location.toBlockPos()).toString(),
                Date(creationTimestamp),
            )

        override fun equals(other: Any?): Boolean {
            if (other == null) return false
            if (other !is Waypoint) return false
            return name == other.name && tag == other.tag && location == other.location
        }

        override fun hashCode(): Int = name.hashCode() xor tag.hashCode() xor location.hashCode() xor creationTimestamp.hashCode()

        @Suppress("ImmutableEnumChecker")
        enum class Tag(
            vararg names: String,
        ) {
            /** Tag indicating a position explictly marked as a home base */
            HOME("home", "base"),

            /** Tag indicating a position that the local player has died at */
            DEATH("death"),

            /** Tag indicating a bed position */
            BED("bed", "spawn"),

            /** Tag indicating that the waypoint was user-created */
            USER("user"),
            ;

            /** The names for the tag, anything that the tag can be referred to as. */
            val names: List<String> = names.toList()

            /**
             * @return A name that can be passed to [getByName] to retrieve this tag
             */
            fun getName(): String = names[0]

            companion object {
                /** A list of all of the */
                private val TAG_LIST = Collections.unmodifiableList(entries.toList())

                /**
                 * Gets a tag by one of its names.
                 *
                 * @param name The name to search for.
                 * @return The tag, if found, or null.
                 */
                @JvmStatic
                fun getByName(name: String): Tag? {
                    for (action in entries) {
                        for (alias in action.names) {
                            if (alias.equals(name, ignoreCase = true)) {
                                return action
                            }
                        }
                    }
                    return null
                }

                /**
                 * @return All tag names.
                 */
                @JvmStatic
                fun getAllNames(): Array<String> {
                    val names = mutableSetOf<String>()
                    for (tag in entries) {
                        names.addAll(tag.names)
                    }
                    return names.toTypedArray()
                }
            }
        }
    }
