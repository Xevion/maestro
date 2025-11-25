package maestro.api.combat

import net.minecraft.world.phys.Vec3

/**
 * Result of arrow trajectory simulation.
 *
 * @property points List of positions along the trajectory path
 * @property endPoint Final position (either hit location or last simulated point)
 * @property hitBlock True if trajectory ended by hitting a block
 */
data class TrajectoryResult(
    val points: List<Vec3>,
    val endPoint: Vec3,
    val hitBlock: Boolean,
)
