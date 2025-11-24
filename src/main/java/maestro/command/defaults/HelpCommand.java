package maestro.command.defaults;

import static maestro.api.command.IMaestroChatControl.FORCE_COMMAND_PREFIX;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import maestro.api.IMaestro;
import maestro.api.command.Command;
import maestro.api.command.ICommand;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.command.exception.CommandNotFoundException;
import maestro.api.command.helpers.Paginator;
import maestro.api.command.helpers.TabCompleteHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class HelpCommand extends Command {

    public HelpCommand(IMaestro maestro) {
        super(maestro, "help", "?");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        if (!args.hasAny() || args.is(Integer.class)) {
            Paginator.paginate(
                    args,
                    new Paginator<>(
                            this.maestro
                                    .getCommandManager()
                                    .getRegistry()
                                    .descendingStream()
                                    .filter(command -> !command.hiddenFromHelp())
                                    .collect(Collectors.toList())),
                    () -> logDirect("All Maestro commands (clickable):"),
                    command -> {
                        String names = String.join("/", command.getNames());
                        String name = command.getNames().getFirst();
                        MutableComponent shortDescComponent =
                                Component.literal(" - " + command.getShortDesc());
                        shortDescComponent.setStyle(
                                shortDescComponent.getStyle().withColor(ChatFormatting.DARK_GRAY));
                        MutableComponent namesComponent = Component.literal(names);
                        namesComponent.setStyle(
                                namesComponent.getStyle().withColor(ChatFormatting.WHITE));
                        MutableComponent hoverComponent = Component.literal("");
                        hoverComponent.setStyle(
                                hoverComponent.getStyle().withColor(ChatFormatting.GRAY));
                        hoverComponent.append(namesComponent);
                        hoverComponent.append("\n" + command.getShortDesc());
                        hoverComponent.append("\n\nClick to view full help");
                        String clickCommand =
                                FORCE_COMMAND_PREFIX
                                        + String.format(
                                                "%s %s", label, command.getNames().getFirst());
                        MutableComponent component = Component.literal(name);
                        component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
                        component.append(shortDescComponent);
                        component.setStyle(
                                component
                                        .getStyle()
                                        .withHoverEvent(
                                                new HoverEvent(
                                                        HoverEvent.Action.SHOW_TEXT,
                                                        hoverComponent))
                                        .withClickEvent(
                                                new ClickEvent(
                                                        ClickEvent.Action.RUN_COMMAND,
                                                        clickCommand)));
                        return component;
                    },
                    FORCE_COMMAND_PREFIX + label);
        } else {
            String commandName = args.getString().toLowerCase();
            ICommand command = this.maestro.getCommandManager().getCommand(commandName);
            if (command == null) {
                throw new CommandNotFoundException(commandName);
            }
            logDirect(
                    String.format(
                            "%s - %s",
                            String.join(" / ", command.getNames()), command.getShortDesc()));
            logDirect("");
            command.getLongDesc().forEach(this::logDirect);
            logDirect("");
            MutableComponent returnComponent =
                    Component.literal("Click to return to the help menu");
            returnComponent.setStyle(
                    returnComponent
                            .getStyle()
                            .withClickEvent(
                                    new ClickEvent(
                                            ClickEvent.Action.RUN_COMMAND,
                                            FORCE_COMMAND_PREFIX + label)));
            logDirect(returnComponent);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                            .addCommands(this.maestro.getCommandManager())
                            .filterPrefix(args.getString())
                            .stream();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View all commands or help on specific ones";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Using this command, you can view detailed help information on how to use certain"
                        + " commands of Maestro.",
                "",
                "Usage:",
                "> help - Lists all commands and their short descriptions.",
                "> help <command> - Displays help information on a specific command.");
    }
}
