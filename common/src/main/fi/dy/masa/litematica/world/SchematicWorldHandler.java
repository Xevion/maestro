package fi.dy.masa.litematica.world;

import com.google.errorprone.annotations.DoNotCall;

public class SchematicWorldHandler {

    @DoNotCall("Always throws java.lang.LinkageError")
    public static WorldSchematic getSchematicWorld() {
        throw new LinkageError();
    }
}
