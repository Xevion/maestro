package maestro;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import maestro.api.IAgent;
import maestro.api.IMaestroProvider;
import maestro.api.cache.IWorldScanner;
import maestro.api.command.ICommandSystem;
import maestro.api.schematic.ISchematicSystem;
import maestro.cache.FasterWorldScanner;
import maestro.command.CommandSystem;
import maestro.command.ExampleMaestroControl;
import maestro.process.schematic.SchematicSystem;
import net.minecraft.client.Minecraft;

public final class MaestroProvider implements IMaestroProvider {

    private final List<IAgent> all;
    private final List<IAgent> allView;

    public MaestroProvider() {
        this.all = new CopyOnWriteArrayList<>();
        this.allView = Collections.unmodifiableList(this.all);

        // Setup chat control, just for the primary instance
        final Agent primary = (Agent) this.createMaestro(Minecraft.getInstance());
        primary.registerBehavior(ExampleMaestroControl::new);
    }

    @Override
    public IAgent getPrimaryAgent() {
        return this.all.getFirst();
    }

    @Override
    public List<IAgent> getAllMaestros() {
        return this.allView;
    }

    @Override
    public synchronized IAgent createMaestro(Minecraft minecraft) {
        IAgent maestro = this.getMaestroForMinecraft(minecraft);
        if (maestro == null) {
            this.all.add(maestro = new Agent(minecraft));
        }
        return maestro;
    }

    @Override
    public synchronized boolean destroyMaestro(IAgent maestro) {
        return maestro != this.getPrimaryAgent() && this.all.remove(maestro);
    }

    @Override
    public IWorldScanner getWorldScanner() {
        return FasterWorldScanner.INSTANCE;
    }

    @Override
    public ICommandSystem getCommandSystem() {
        return CommandSystem.INSTANCE;
    }

    @Override
    public ISchematicSystem getSchematicSystem() {
        return SchematicSystem.INSTANCE;
    }
}
