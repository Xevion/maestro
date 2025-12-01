package maestro.api.command.exception;

import java.util.List;
import maestro.api.MaestroAPI;
import maestro.api.command.ICommand;
import maestro.api.command.argument.ICommandArgument;
import maestro.api.utils.MaestroLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

/**
 * The base for a Maestro Command Exception, checked or unchecked. Provides a {@link
 * #handle(ICommand, List)} method that is used to provide useful output to the user for diagnosing
 * issues that may have occurred during execution.
 *
 * <p>Anything implementing this interface should be assignable to {@link Exception}.
 */
public interface ICommandException {

    Logger log = MaestroLogger.get("cmd");

    /**
     * @return The exception details
     * @see Exception#getMessage()
     */
    String getMessage();

    /**
     * Called when this exception is thrown, to handle the exception.
     *
     * @param command The command that threw it.
     * @param args The arguments the command was called with.
     */
    default void handle(ICommand command, List<ICommandArgument> args) {
        // Log error
        log.atError().log(this.getMessage());

        // Send to chat directly (bypasses appender to ensure it shows)
        MutableComponent errorMsg = Component.literal("[cmd] ");
        errorMsg.setStyle(errorMsg.getStyle().withColor(ChatFormatting.AQUA));

        MutableComponent messageComponent = Component.literal(this.getMessage());
        messageComponent.setStyle(messageComponent.getStyle().withColor(ChatFormatting.RED));
        errorMsg.append(messageComponent);

        Minecraft.getInstance()
                .execute(() -> MaestroAPI.getSettings().logger.value.accept(errorMsg));
    }
}
