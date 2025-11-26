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

public class CommandNotFoundException extends CommandException {

    private static final Logger log = MaestroLogger.get("cmd");
    public final String command;

    public CommandNotFoundException(String command) {
        super(String.format("Command not found: %s", command));
        this.command = command;
    }

    @Override
    public void handle(ICommand command, List<ICommandArgument> args) {
        // Log as warning
        log.atWarn().addKeyValue("command", this.command).log("Command not found");

        // Send to chat
        MutableComponent errorMsg = Component.literal("[cmd] ");
        errorMsg.setStyle(errorMsg.getStyle().withColor(ChatFormatting.AQUA));

        MutableComponent messageComponent = Component.literal(getMessage());
        messageComponent.setStyle(messageComponent.getStyle().withColor(ChatFormatting.GRAY));
        errorMsg.append(messageComponent);

        Minecraft.getInstance()
                .execute(() -> MaestroAPI.getSettings().logger.value.accept(errorMsg));
    }
}
