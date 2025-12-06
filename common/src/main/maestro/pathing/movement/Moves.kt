package maestro.pathing.movement

import maestro.pathing.MutableMoveResult
import maestro.pathing.movement.movements.MovementAscend
import maestro.pathing.movement.movements.MovementDescend
import maestro.pathing.movement.movements.MovementDownward
import maestro.pathing.movement.movements.MovementTraverse
import maestro.utils.PackedBlockPos

// TODO: Convert to Kotlin
// import maestro.pathing.movement.movements.MovementDiagonal
// import maestro.pathing.movement.movements.MovementFall
// import maestro.pathing.movement.movements.MovementParkour
// import maestro.pathing.movement.movements.MovementPillar

/** An enum of all possible movements attached to all possible directions they could be taken in */
enum class Moves(
    val xOffset: Int,
    val yOffset: Int,
    val zOffset: Int,
    val dynamicXZ: Boolean = false,
    val dynamicY: Boolean = false,
) {
    DOWNWARD(0, -1, 0) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement = MovementDownward(context.agent, src, src.below())

        override fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
        ): Double = MovementDownward.cost(context, x, y, z)
    },

    // TODO: Convert MovementPillar to Kotlin
    // PILLAR(0, +1, 0) {
    //     override fun apply0(
    //         context: CalculationContext,
    //         src: PackedBlockPos,
    //     ): Movement = MovementPillar(context.maestro, src, src.above())
    //
    //     override fun cost(
    //         context: CalculationContext,
    //         x: Int,
    //         y: Int,
    //         z: Int,
    //     ): Double = MovementPillar.cost(context, x, y, z)
    // },

    TRAVERSE_NORTH(0, 0, -1) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement = MovementTraverse(context.agent, src, src.north())

        override fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
        ): Double = MovementTraverse.cost(context, x, y, z, x, z - 1)
    },

    TRAVERSE_SOUTH(0, 0, +1) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement = MovementTraverse(context.agent, src, src.south())

        override fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
        ): Double = MovementTraverse.cost(context, x, y, z, x, z + 1)
    },

    TRAVERSE_EAST(+1, 0, 0) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement = MovementTraverse(context.agent, src, src.east())

        override fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
        ): Double = MovementTraverse.cost(context, x, y, z, x + 1, z)
    },

    TRAVERSE_WEST(-1, 0, 0) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement = MovementTraverse(context.agent, src, src.west())

        override fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
        ): Double = MovementTraverse.cost(context, x, y, z, x - 1, z)
    },

    ASCEND_NORTH(0, +1, -1) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement =
            MovementAscend(
                context.agent,
                src,
                PackedBlockPos(src.x, src.y + 1, src.z - 1),
            )

        override fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
        ): Double = MovementAscend.cost(context, x, y, z, x, z - 1)
    },

    ASCEND_SOUTH(0, +1, +1) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement =
            MovementAscend(
                context.agent,
                src,
                PackedBlockPos(src.x, src.y + 1, src.z + 1),
            )

        override fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
        ): Double = MovementAscend.cost(context, x, y, z, x, z + 1)
    },

    ASCEND_EAST(+1, +1, 0) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement =
            MovementAscend(
                context.agent,
                src,
                PackedBlockPos(src.x + 1, src.y + 1, src.z),
            )

        override fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
        ): Double = MovementAscend.cost(context, x, y, z, x + 1, z)
    },

    ASCEND_WEST(-1, +1, 0) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement =
            MovementAscend(
                context.agent,
                src,
                PackedBlockPos(src.x - 1, src.y + 1, src.z),
            )

        override fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
        ): Double = MovementAscend.cost(context, x, y, z, x - 1, z)
    },

    // NOTE: Only single-block descend enabled. Multi-block falls require MovementFall.kt conversion.
    DESCEND_EAST(+1, -1, 0, dynamicXZ = false, dynamicY = true) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement {
            val res = MutableMoveResult()
            apply(context, src.x, src.y, src.z, res)
            // Only support single-block descend for now (y == src.y - 1)
            if (res.y != src.y - 1) {
                return MovementDescend(context.agent, src, src) // Invalid movement
            }
            return MovementDescend(context.agent, src, PackedBlockPos(res.x, res.y, res.z))
        }

        override fun apply(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
            result: MutableMoveResult,
        ) {
            MovementDescend.cost(context, x, y, z, x + 1, z, result)
        }
    },

    DESCEND_WEST(-1, -1, 0, dynamicXZ = false, dynamicY = true) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement {
            val res = MutableMoveResult()
            apply(context, src.x, src.y, src.z, res)
            if (res.y != src.y - 1) {
                return MovementDescend(context.agent, src, src)
            }
            return MovementDescend(context.agent, src, PackedBlockPos(res.x, res.y, res.z))
        }

        override fun apply(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
            result: MutableMoveResult,
        ) {
            MovementDescend.cost(context, x, y, z, x - 1, z, result)
        }
    },

    DESCEND_NORTH(0, -1, -1, dynamicXZ = false, dynamicY = true) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement {
            val res = MutableMoveResult()
            apply(context, src.x, src.y, src.z, res)
            if (res.y != src.y - 1) {
                return MovementDescend(context.agent, src, src)
            }
            return MovementDescend(context.agent, src, PackedBlockPos(res.x, res.y, res.z))
        }

        override fun apply(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
            result: MutableMoveResult,
        ) {
            MovementDescend.cost(context, x, y, z, x, z - 1, result)
        }
    },

    DESCEND_SOUTH(0, -1, +1, dynamicXZ = false, dynamicY = true) {
        override fun apply0(
            context: CalculationContext,
            src: PackedBlockPos,
        ): Movement {
            val res = MutableMoveResult()
            apply(context, src.x, src.y, src.z, res)
            if (res.y != src.y - 1) {
                return MovementDescend(context.agent, src, src)
            }
            return MovementDescend(context.agent, src, PackedBlockPos(res.x, res.y, res.z))
        }

        override fun apply(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
            result: MutableMoveResult,
        ) {
            MovementDescend.cost(context, x, y, z, x, z + 1, result)
        }
    },

    // TODO: Convert MovementDiagonal to Kotlin
    // DIAGONAL_NORTHEAST(+1, 0, -1, dynamicXZ = false, dynamicY = true) {
    //     override fun apply0(
    //         context: CalculationContext,
    //         src: PackedBlockPos,
    //     ): Movement {
    //         val res = MutableMoveResult()
    //         apply(context, src.x, src.y, src.z, res)
    //         return MovementDiagonal(
    //             context.maestro,
    //             src,
    //             Direction.NORTH,
    //             Direction.EAST,
    //             res.y - src.y,
    //         )
    //     }
    //
    //     override fun apply(
    //         context: CalculationContext,
    //         x: Int,
    //         y: Int,
    //         z: Int,
    //         result: MutableMoveResult,
    //         ) {
    //         MovementDiagonal.cost(context, x, y, z, x + 1, z - 1, result)
    //     }
    // },
    //
    // DIAGONAL_NORTHWEST(-1, 0, -1, dynamicXZ = false, dynamicY = true) {
    //     override fun apply0(
    //         context: CalculationContext,
    //         src: PackedBlockPos,
    //     ): Movement {
    //         val res = MutableMoveResult()
    //         apply(context, src.x, src.y, src.z, res)
    //         return MovementDiagonal(
    //             context.maestro,
    //             src,
    //             Direction.NORTH,
    //             Direction.WEST,
    //             res.y - src.y,
    //         )
    //     }
    //
    //     override fun apply(
    //         context: CalculationContext,
    //         x: Int,
    //         y: Int,
    //         z: Int,
    //         result: MutableMoveResult,
    //     ) {
    //         MovementDiagonal.cost(context, x, y, z, x - 1, z - 1, result)
    //     }
    // },
    //
    // DIAGONAL_SOUTHEAST(+1, 0, +1, dynamicXZ = false, dynamicY = true) {
    //     override fun apply0(
    //         context: CalculationContext,
    //         src: PackedBlockPos,
    //     ): Movement {
    //         val res = MutableMoveResult()
    //         apply(context, src.x, src.y, src.z, res)
    //         return MovementDiagonal(
    //             context.maestro,
    //             src,
    //             Direction.SOUTH,
    //             Direction.EAST,
    //             res.y - src.y,
    //         )
    //     }
    //
    //     override fun apply(
    //         context: CalculationContext,
    //         x: Int,
    //         y: Int,
    //         z: Int,
    //         result: MutableMoveResult,
    //     ) {
    //         MovementDiagonal.cost(context, x, y, z, x + 1, z + 1, result)
    //     }
    // },
    //
    // DIAGONAL_SOUTHWEST(-1, 0, +1, dynamicXZ = false, dynamicY = true) {
    //     override fun apply0(
    //         context: CalculationContext,
    //         src: PackedBlockPos,
    //     ): Movement {
    //         val res = MutableMoveResult()
    //         apply(context, src.x, src.y, src.z, res)
    //         return MovementDiagonal(
    //             context.maestro,
    //             src,
    //             Direction.SOUTH,
    //             Direction.WEST,
    //             res.y - src.y,
    //         )
    //     }
    //
    //     override fun apply(
    //         context: CalculationContext,
    //         x: Int,
    //         y: Int,
    //         z: Int,
    //         result: MutableMoveResult,
    //     ) {
    //         MovementDiagonal.cost(context, x, y, z, x - 1, z + 1, result)
    //     }
    // },

    // TODO: Convert MovementParkour to Kotlin
    // PARKOUR_NORTH(0, 0, -4, dynamicXZ = true, dynamicY = true) {
    //     override fun apply0(
    //         context: CalculationContext,
    //         src: PackedBlockPos,
    //     ): Movement = MovementParkour.cost(context, src, Direction.NORTH)
    //
    //     override fun apply(
    //         context: CalculationContext,
    //         x: Int,
    //         y: Int,
    //         z: Int,
    //         result: MutableMoveResult,
    //     ) {
    //         MovementParkour.cost(context, x, y, z, Direction.NORTH, result)
    //     }
    // },
    //
    // PARKOUR_SOUTH(0, 0, +4, dynamicXZ = true, dynamicY = true) {
    //     override fun apply0(
    //         context: CalculationContext,
    //         src: PackedBlockPos,
    //     ): Movement = MovementParkour.cost(context, src, Direction.SOUTH)
    //
    //     override fun apply(
    //         context: CalculationContext,
    //         x: Int,
    //         y: Int,
    //         z: Int,
    //         result: MutableMoveResult,
    //     ) {
    //         MovementParkour.cost(context, x, y, z, Direction.SOUTH, result)
    //     }
    // },
    //
    // PARKOUR_EAST(+4, 0, 0, dynamicXZ = true, dynamicY = true) {
    //     override fun apply0(
    //         context: CalculationContext,
    //         src: PackedBlockPos,
    //     ): Movement = MovementParkour.cost(context, src, Direction.EAST)
    //
    //     override fun apply(
    //         context: CalculationContext,
    //         x: Int,
    //         y: Int,
    //         z: Int,
    //         result: MutableMoveResult,
    //     ) {
    //         MovementParkour.cost(context, x, y, z, Direction.EAST, result)
    //     }
    // },
    //
    // PARKOUR_WEST(-4, 0, 0, dynamicXZ = true, dynamicY = true) {
    //     override fun apply0(
    //         context: CalculationContext,
    //         src: PackedBlockPos,
    //     ): Movement = MovementParkour.cost(context, src, Direction.WEST)
    //
    //     override fun apply(
    //         context: CalculationContext,
    //         x: Int,
    //         y: Int,
    //         z: Int,
    //         result: MutableMoveResult,
    //     ) {
    //         MovementParkour.cost(context, x, y, z, Direction.WEST, result)
    //     }
    // },
    ;

    abstract fun apply0(
        context: CalculationContext,
        src: PackedBlockPos,
    ): Movement

    open fun apply(
        context: CalculationContext,
        x: Int,
        y: Int,
        z: Int,
        result: MutableMoveResult,
    ) {
        require(!dynamicXZ && !dynamicY) {
            "Movements with dynamic offset must override `apply`"
        }
        result.x = x + xOffset
        result.y = y + yOffset
        result.z = z + zOffset
        result.cost = cost(context, x, y, z)
    }

    open fun cost(
        context: CalculationContext,
        x: Int,
        y: Int,
        z: Int,
    ): Double = throw UnsupportedOperationException("Movements must override `cost` or `apply`")
}
