package com.github.lunatrius.core.util.math;

import net.minecraft.core.BlockPos;

public class MBlockPos extends BlockPos {

    MBlockPos() {
        super(6, 6, 6);
    }

    @Override
    public int getX() {
        throw new LinkageError("Unsupported operation");
    }

    @Override
    public int getY() {
        throw new LinkageError("Unsupported operation");
    }

    @Override
    public int getZ() {
        throw new LinkageError("Unsupported operation");
    }
}
