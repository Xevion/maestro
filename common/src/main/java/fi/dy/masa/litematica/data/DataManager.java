package fi.dy.masa.litematica.data;

import com.google.errorprone.annotations.DoNotCall;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

public class DataManager {

    @DoNotCall("Always throws java.lang.LinkageError")
    public static SchematicPlacementManager getSchematicPlacementManager() {
        throw new LinkageError();
    }
}
