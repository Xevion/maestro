package maestro.api;

import java.util.List;
import java.util.Objects;
import maestro.api.cache.IWorldScanner;
import maestro.api.command.ICommand;
import maestro.api.command.ICommandSystem;
import maestro.api.schematic.ISchematicSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

/**
 * Provides the present {@link IAgent} instances, as well as non-maestro instance related APIs.
 */
public interface IMaestroProvider {

    /**
     * Returns the primary {@link IAgent} instance. This instance is persistent, and is
     * represented by the local player that is created by the game itself, not a "bot" player
     * through Maestro.
     *
     * @return The primary {@link IAgent} instance.
     */
    IAgent getPrimaryAgent();

    /**
     * Returns all of the active {@link IAgent} instances. This includes the local one returned by
     * {@link #getPrimaryAgent()}.
     *
     * @return All active {@link IAgent} instances.
     * @see #getMaestroForPlayer(LocalPlayer)
     */
    List<IAgent> getAllMaestros();

    /**
     * Provides the {@link IAgent} instance for a given {@link LocalPlayer}.
     *
     * @param player The player
     * @return The {@link IAgent} instance.
     */
    default IAgent getMaestroForPlayer(LocalPlayer player) {
        for (IAgent maestro : this.getAllMaestros()) {
            if (Objects.equals(player, maestro.getPlayerContext().player())) {
                return maestro;
            }
        }
        return null;
    }

    /**
     * Provides the {@link IAgent} instance for a given {@link Minecraft}.
     *
     * @param minecraft The minecraft
     * @return The {@link IAgent} instance.
     */
    default IAgent getMaestroForMinecraft(Minecraft minecraft) {
        for (IAgent maestro : this.getAllMaestros()) {
            if (Objects.equals(minecraft, maestro.getPlayerContext().minecraft())) {
                return maestro;
            }
        }
        return null;
    }

    /**
     * Provides the {@link IAgent} instance for the player with the specified connection.
     *
     * @param connection The connection
     * @return The {@link IAgent} instance.
     */
    default IAgent getMaestroForConnection(ClientPacketListener connection) {
        for (IAgent maestro : this.getAllMaestros()) {
            final LocalPlayer player = maestro.getPlayerContext().player();
            if (player != null && player.connection == connection) {
                return maestro;
            }
        }
        return null;
    }

    /**
     * Creates and registers a new {@link IAgent} instance using the specified {@link Minecraft}.
     * The existing instance is returned if already registered.
     *
     * @param minecraft The minecraft
     * @return The {@link IAgent} instance
     */
    IAgent createMaestro(Minecraft minecraft);

    /**
     * Destroys and removes the specified {@link IAgent} instance. If the specified instance is
     * the {@link #getPrimaryAgent() primary maestro}, this operation has no effect and will
     * return {@code false}.
     *
     * @param maestro The maestro instance to remove
     * @return Whether the maestro instance was removed
     */
    boolean destroyMaestro(IAgent maestro);

    /**
     * Returns the {@link IWorldScanner} instance. This is not a type returned by {@link IAgent}
     * implementation, because it is not linked with {@link IAgent}.
     *
     * @return The {@link IWorldScanner} instance.
     */
    IWorldScanner getWorldScanner();

    /**
     * Returns the {@link ICommandSystem} instance. This is not bound to a specific {@link IAgent}
     * instance because {@link ICommandSystem} itself controls global behavior for {@link
     * ICommand}s.
     *
     * @return The {@link ICommandSystem} instance.
     */
    ICommandSystem getCommandSystem();

    /**
     * @return The {@link ISchematicSystem} instance.
     */
    ISchematicSystem getSchematicSystem();
}
