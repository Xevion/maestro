package maestro.pathing.movement;

import maestro.api.utils.PackedBlockPos;
import maestro.pathing.movement.movements.*;
import maestro.utils.pathing.MutableMoveResult;
import net.minecraft.core.Direction;

/** An enum of all possible movements attached to all possible directions they could be taken in */
public enum Moves {
    DOWNWARD(0, -1, 0) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return new MovementDownward(context.getMaestro(), src, src.below());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementDownward.cost(context, x, y, z);
        }
    },

    PILLAR(0, +1, 0) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return new MovementPillar(context.getMaestro(), src, src.above());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementPillar.cost(context, x, y, z);
        }
    },

    TRAVERSE_NORTH(0, 0, -1) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return new MovementTraverse(context.getMaestro(), src, src.north());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementTraverse.cost(context, x, y, z, x, z - 1);
        }
    },

    TRAVERSE_SOUTH(0, 0, +1) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return new MovementTraverse(context.getMaestro(), src, src.south());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementTraverse.cost(context, x, y, z, x, z + 1);
        }
    },

    TRAVERSE_EAST(+1, 0, 0) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return new MovementTraverse(context.getMaestro(), src, src.east());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementTraverse.cost(context, x, y, z, x + 1, z);
        }
    },

    TRAVERSE_WEST(-1, 0, 0) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return new MovementTraverse(context.getMaestro(), src, src.west());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementTraverse.cost(context, x, y, z, x - 1, z);
        }
    },

    ASCEND_NORTH(0, +1, -1) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return new MovementAscend(
                    context.getMaestro(),
                    src,
                    new PackedBlockPos(src.getX(), src.getY() + 1, src.getZ() - 1));
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementAscend.cost(context, x, y, z, x, z - 1);
        }
    },

    ASCEND_SOUTH(0, +1, +1) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return new MovementAscend(
                    context.getMaestro(),
                    src,
                    new PackedBlockPos(src.getX(), src.getY() + 1, src.getZ() + 1));
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementAscend.cost(context, x, y, z, x, z + 1);
        }
    },

    ASCEND_EAST(+1, +1, 0) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return new MovementAscend(
                    context.getMaestro(),
                    src,
                    new PackedBlockPos(src.getX() + 1, src.getY() + 1, src.getZ()));
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementAscend.cost(context, x, y, z, x + 1, z);
        }
    },

    ASCEND_WEST(-1, +1, 0) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return new MovementAscend(
                    context.getMaestro(),
                    src,
                    new PackedBlockPos(src.getX() - 1, src.getY() + 1, src.getZ()));
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementAscend.cost(context, x, y, z, x - 1, z);
        }
    },

    DESCEND_EAST(+1, -1, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.getX(), src.getY(), src.getZ(), res);
            if (res.y == src.getY() - 1) {
                return new MovementDescend(
                        context.getMaestro(), src, new PackedBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(
                        context.getMaestro(), src, new PackedBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x + 1, z, result);
        }
    },

    DESCEND_WEST(-1, -1, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.getX(), src.getY(), src.getZ(), res);
            if (res.y == src.getY() - 1) {
                return new MovementDescend(
                        context.getMaestro(), src, new PackedBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(
                        context.getMaestro(), src, new PackedBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x - 1, z, result);
        }
    },

    DESCEND_NORTH(0, -1, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.getX(), src.getY(), src.getZ(), res);
            if (res.y == src.getY() - 1) {
                return new MovementDescend(
                        context.getMaestro(), src, new PackedBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(
                        context.getMaestro(), src, new PackedBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x, z - 1, result);
        }
    },

    DESCEND_SOUTH(0, -1, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.getX(), src.getY(), src.getZ(), res);
            if (res.y == src.getY() - 1) {
                return new MovementDescend(
                        context.getMaestro(), src, new PackedBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(
                        context.getMaestro(), src, new PackedBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x, z + 1, result);
        }
    },

    DIAGONAL_NORTHEAST(+1, 0, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.getX(), src.getY(), src.getZ(), res);
            return new MovementDiagonal(
                    context.getMaestro(), src, Direction.NORTH, Direction.EAST, res.y - src.getY());
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x + 1, z - 1, result);
        }
    },

    DIAGONAL_NORTHWEST(-1, 0, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.getX(), src.getY(), src.getZ(), res);
            return new MovementDiagonal(
                    context.getMaestro(), src, Direction.NORTH, Direction.WEST, res.y - src.getY());
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x - 1, z - 1, result);
        }
    },

    DIAGONAL_SOUTHEAST(+1, 0, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.getX(), src.getY(), src.getZ(), res);
            return new MovementDiagonal(
                    context.getMaestro(), src, Direction.SOUTH, Direction.EAST, res.y - src.getY());
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x + 1, z + 1, result);
        }
    },

    DIAGONAL_SOUTHWEST(-1, 0, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.getX(), src.getY(), src.getZ(), res);
            return new MovementDiagonal(
                    context.getMaestro(), src, Direction.SOUTH, Direction.WEST, res.y - src.getY());
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x - 1, z + 1, result);
        }
    },

    PARKOUR_NORTH(0, 0, -4, true, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return MovementParkour.cost(context, src, Direction.NORTH);
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, Direction.NORTH, result);
        }
    },

    PARKOUR_SOUTH(0, 0, +4, true, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return MovementParkour.cost(context, src, Direction.SOUTH);
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, Direction.SOUTH, result);
        }
    },

    PARKOUR_EAST(+4, 0, 0, true, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return MovementParkour.cost(context, src, Direction.EAST);
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, Direction.EAST, result);
        }
    },

    PARKOUR_WEST(-4, 0, 0, true, true) {
        @Override
        public Movement apply0(CalculationContext context, PackedBlockPos src) {
            return MovementParkour.cost(context, src, Direction.WEST);
        }

        @Override
        public void apply(
                CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, Direction.WEST, result);
        }
    };

    public final boolean dynamicXZ;
    public final boolean dynamicY;

    public final int xOffset;
    public final int yOffset;
    public final int zOffset;

    Moves(int x, int y, int z, boolean dynamicXZ, boolean dynamicY) {
        this.xOffset = x;
        this.yOffset = y;
        this.zOffset = z;
        this.dynamicXZ = dynamicXZ;
        this.dynamicY = dynamicY;
    }

    Moves(int x, int y, int z) {
        this(x, y, z, false, false);
    }

    public abstract Movement apply0(CalculationContext context, PackedBlockPos src);

    public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
        if (dynamicXZ || dynamicY) {
            throw new UnsupportedOperationException(
                    "Movements with dynamic offset must override `apply`");
        }
        result.x = x + xOffset;
        result.y = y + yOffset;
        result.z = z + zOffset;
        result.cost = cost(context, x, y, z);
    }

    public double cost(CalculationContext context, int x, int y, int z) {
        throw new UnsupportedOperationException("Movements must override `cost` or `apply`");
    }
}
