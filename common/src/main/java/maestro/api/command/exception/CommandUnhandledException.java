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

public class CommandUnhandledException extends RuntimeException implements ICommandException {

    private static final Logger log = MaestroLogger.get("cmd");

    public CommandUnhandledException(String message) {
        super(message);
    }

    public CommandUnhandledException(Throwable cause) {
        super(cause);
    }

    @Override
    public void handle(ICommand command, List<ICommandArgument> args) {
        // Log error with exception
        log.atError().setCause(this).log("Unhandled exception occurred");

        // Send to chat
        MutableComponent errorMsg = Component.literal("[cmd] ");
        errorMsg.setStyle(errorMsg.getStyle().withColor(ChatFormatting.AQUA));

        MutableComponent messageComponent =
                Component.literal(
                        "An unhandled exception occurred. The error is in your game's log, please"
                                + " report this at https://github.com/cabaletta/baritone/issues");
        messageComponent.setStyle(messageComponent.getStyle().withColor(ChatFormatting.RED));
        errorMsg.append(messageComponent);

        Minecraft.getInstance()
                .execute(() -> MaestroAPI.getSettings().logger.value.accept(errorMsg));

        // Print stack trace to console
        this.printStackTrace();
    }
}
