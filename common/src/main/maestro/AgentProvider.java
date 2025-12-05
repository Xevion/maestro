package maestro;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import maestro.cache.WorldScanner;
import maestro.command.ChatCommandHandler;
import maestro.command.CommandSystem;
import maestro.task.schematic.SchematicSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

public final class AgentProvider {

    private final List<Agent> all;
    private final List<Agent> allView;

    public AgentProvider() {
        this.all = new CopyOnWriteArrayList<>();
        this.allView = Collections.unmodifiableList(this.all);

        // Setup chat control, just for the primary instance
        final Agent primary = (Agent) this.createMaestro(Minecraft.getInstance());
        primary.registerBehavior(ChatCommandHandler::new);
    }

    public Agent getPrimaryAgent() {
        return this.all.getFirst();
    }

    public List<Agent> getAllMaestros() {
        return this.allView;
    }

    public Agent getMaestroForPlayer(LocalPlayer player) {
        for (Agent maestro : this.getAllMaestros()) {
            if (Objects.equals(player, maestro.getPlayerContext().player())) {
                return maestro;
            }
        }
        return null;
    }

    public Agent getMaestroForMinecraft(Minecraft minecraft) {
        for (Agent maestro : this.getAllMaestros()) {
            if (Objects.equals(minecraft, maestro.getPlayerContext().minecraft())) {
                return maestro;
            }
        }
        return null;
    }

    public Agent getMaestroForConnection(ClientPacketListener connection) {
        for (Agent maestro : this.getAllMaestros()) {
            final LocalPlayer player = maestro.getPlayerContext().player();
            if (player != null && player.connection == connection) {
                return maestro;
            }
        }
        return null;
    }

    public synchronized Agent createMaestro(Minecraft minecraft) {
        Agent maestro = this.getMaestroForMinecraft(minecraft);
        if (maestro == null) {
            this.all.add(maestro = new Agent(minecraft));
        }
        return maestro;
    }

    public synchronized boolean destroyMaestro(Agent maestro) {
        return maestro != this.getPrimaryAgent() && this.all.remove(maestro);
    }

    public WorldScanner getWorldScanner() {
        return WorldScanner.INSTANCE;
    }

    public CommandSystem getCommandSystem() {
        return CommandSystem.INSTANCE;
    }

    public SchematicSystem getSchematicSystem() {
        return SchematicSystem.INSTANCE;
    }
}
