package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.ICommand;
import maestro.command.argument.IArgConsumer;
import maestro.command.exception.CommandException;
import maestro.command.helpers.Paginator;
import maestro.command.helpers.TabCompleteHelper;
import maestro.gui.chat.ChatMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class HelpCommand extends Command {

    public HelpCommand(Agent maestro) {
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
                    () -> log.atInfo().log("All Maestro commands (clickable):"),
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
                                Agent.getPrimaryAgent().getSettings().prefix.value
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

                        MutableComponent prefixed = Component.literal("");
                        prefixed.append(ChatMessage.createCategoryPrefix("cmd"));
                        prefixed.append(" ");
                        prefixed.append(component);

                        Minecraft.getInstance()
                                .execute(
                                        () ->
                                                Agent.getPrimaryAgent()
                                                        .getSettings()
                                                        .logger
                                                        .value
                                                        .accept(prefixed));

                        return component;
                    },
                    Agent.getPrimaryAgent().getSettings().prefix.value + label);
        } else {
            String commandName = args.getString().toLowerCase(java.util.Locale.ROOT);
            ICommand command = this.maestro.getCommandManager().getCommand(commandName);
            if (command == null) {
                throw new CommandException.NotFound(commandName);
            }
            log.atInfo().log(
                    String.format(
                            "%s - %s",
                            String.join(" / ", command.getNames()), command.getShortDesc()));
            log.atInfo().log("");
            command.getLongDesc().forEach(line -> log.atInfo().log(line));
            log.atInfo().log("");

            MutableComponent returnComponent =
                    Component.literal("Click to return to the help menu");
            returnComponent.setStyle(
                    returnComponent
                            .getStyle()
                            .withClickEvent(
                                    new ClickEvent(
                                            ClickEvent.Action.RUN_COMMAND,
                                            Agent.getPrimaryAgent().getSettings().prefix.value
                                                    + label)));

            MutableComponent prefixed = Component.literal("");
            prefixed.append(ChatMessage.createCategoryPrefix("cmd"));
            prefixed.append(" ");
            prefixed.append(returnComponent);

            Minecraft.getInstance()
                    .execute(
                            () ->
                                    Agent.getPrimaryAgent()
                                            .getSettings()
                                            .logger
                                            .value
                                            .accept(prefixed));
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
