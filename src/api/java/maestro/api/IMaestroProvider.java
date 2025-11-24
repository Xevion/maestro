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
 * Provides the present {@link IMaestro} instances, as well as non-maestro instance related APIs.
 */
public interface IMaestroProvider {

    /**
     * Returns the primary {@link IMaestro} instance. This instance is persistent, and is
     * represented by the local player that is created by the game itself, not a "bot" player
     * through Maestro.
     *
     * @return The primary {@link IMaestro} instance.
     */
    IMaestro getPrimaryMaestro();

    /**
     * Returns all of the active {@link IMaestro} instances. This includes the local one returned by
     * {@link #getPrimaryMaestro()}.
     *
     * @return All active {@link IMaestro} instances.
     * @see #getMaestroForPlayer(LocalPlayer)
     */
    List<IMaestro> getAllMaestros();

    /**
     * Provides the {@link IMaestro} instance for a given {@link LocalPlayer}.
     *
     * @param player The player
     * @return The {@link IMaestro} instance.
     */
    default IMaestro getMaestroForPlayer(LocalPlayer player) {
        for (IMaestro maestro : this.getAllMaestros()) {
            if (Objects.equals(player, maestro.getPlayerContext().player())) {
                return maestro;
            }
        }
        return null;
    }

    /**
     * Provides the {@link IMaestro} instance for a given {@link Minecraft}.
     *
     * @param minecraft The minecraft
     * @return The {@link IMaestro} instance.
     */
    default IMaestro getMaestroForMinecraft(Minecraft minecraft) {
        for (IMaestro maestro : this.getAllMaestros()) {
            if (Objects.equals(minecraft, maestro.getPlayerContext().minecraft())) {
                return maestro;
            }
        }
        return null;
    }

    /**
     * Provides the {@link IMaestro} instance for the player with the specified connection.
     *
     * @param connection The connection
     * @return The {@link IMaestro} instance.
     */
    default IMaestro getMaestroForConnection(ClientPacketListener connection) {
        for (IMaestro maestro : this.getAllMaestros()) {
            final LocalPlayer player = maestro.getPlayerContext().player();
            if (player != null && player.connection == connection) {
                return maestro;
            }
        }
        return null;
    }

    /**
     * Creates and registers a new {@link IMaestro} instance using the specified {@link Minecraft}.
     * The existing instance is returned if already registered.
     *
     * @param minecraft The minecraft
     * @return The {@link IMaestro} instance
     */
    IMaestro createMaestro(Minecraft minecraft);

    /**
     * Destroys and removes the specified {@link IMaestro} instance. If the specified instance is
     * the {@link #getPrimaryMaestro() primary maestro}, this operation has no effect and will
     * return {@code false}.
     *
     * @param maestro The maestro instance to remove
     * @return Whether the maestro instance was removed
     */
    boolean destroyMaestro(IMaestro maestro);

    /**
     * Returns the {@link IWorldScanner} instance. This is not a type returned by {@link IMaestro}
     * implementation, because it is not linked with {@link IMaestro}.
     *
     * @return The {@link IWorldScanner} instance.
     */
    IWorldScanner getWorldScanner();

    /**
     * Returns the {@link ICommandSystem} instance. This is not bound to a specific {@link IMaestro}
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
