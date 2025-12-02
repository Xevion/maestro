package maestro.api.cache

import maestro.api.cache.IWaypoint.Tag
import maestro.api.utils.PackedBlockPos
import java.util.Date

/** Basic implementation of [IWaypoint] */
data class Waypoint
    @JvmOverloads
    constructor(
        private val name: String,
        private val tag: Tag,
        private val location: PackedBlockPos,
        private val creationTimestamp: Long = System.currentTimeMillis(),
    ) : IWaypoint {
        override fun getName(): String = name

        override fun getTag(): Tag = tag

        override fun getCreationTimestamp(): Long = creationTimestamp

        override fun getLocation(): PackedBlockPos = location

        override fun toString(): String =
            String.format(
                "%s %s %s",
                name,
                PackedBlockPos.from(location.toBlockPos()).toString(),
                Date(creationTimestamp),
            )

        override fun equals(other: Any?): Boolean {
            if (other == null) return false
            if (other !is IWaypoint) return false
            return name == other.name && tag == other.tag && location == other.location
        }

        override fun hashCode(): Int = name.hashCode() xor tag.hashCode() xor location.hashCode() xor creationTimestamp.hashCode()
    }
