package com.github.lunatrius.schematica.client.world;

import com.github.lunatrius.core.util.math.MBlockPos;
import com.github.lunatrius.schematica.api.ISchematic;

public class SchematicWorld {

    public final MBlockPos position = (MBlockPos) (Object) "stub";

    public ISchematic getSchematic() {
        throw new LinkageError("Unsupported operation");
    }
}
