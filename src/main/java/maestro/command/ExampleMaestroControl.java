package maestro.command;

import static maestro.api.command.IMaestroChatControl.FORCE_COMMAND_PREFIX;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.MaestroAPI;
import maestro.api.Settings;
import maestro.api.command.argument.ICommandArgument;
import maestro.api.command.exception.CommandNotEnoughArgumentsException;
import maestro.api.command.exception.CommandNotFoundException;
import maestro.api.command.helpers.TabCompleteHelper;
import maestro.api.command.manager.ICommandManager;
import maestro.api.event.events.ChatEvent;
import maestro.api.event.events.TabCompleteEvent;
import maestro.api.utils.Helper;
import maestro.api.utils.SettingsUtil;
import maestro.behavior.Behavior;
import maestro.command.argument.ArgConsumer;
import maestro.command.argument.CommandArguments;
import maestro.command.manager.CommandManager;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.*;
import net.minecraft.util.Tuple;

public class ExampleMaestroControl extends Behavior implements Helper {

    private static final Settings settings = MaestroAPI.getSettings();
    private final ICommandManager manager;

    public ExampleMaestroControl(Agent maestro) {
        super(maestro);
        this.manager = maestro.getCommandManager();
    }

    @Override
    public void onSendChatMessage(ChatEvent event) {
        String msg = event.getMessage();
        String prefix = settings.prefix.value;
        boolean forceRun = msg.startsWith(FORCE_COMMAND_PREFIX);
        if ((settings.prefixControl.value && msg.startsWith(prefix)) || forceRun) {
            event.cancel();
            String commandStr =
                    msg.substring(forceRun ? FORCE_COMMAND_PREFIX.length() : prefix.length());
            if (!runCommand(commandStr) && !commandStr.trim().isEmpty()) {
                new CommandNotFoundException(CommandManager.expand(commandStr).getA())
                        .handle(null, null);
            }
        } else if ((settings.chatControl.value || settings.chatControlAnyway.value)
                && runCommand(msg)) {
            event.cancel();
        }
    }

    private void logRanCommand(String command, String rest) {
        if (settings.echoCommands.value) {
            String msg = command + rest;
            String toDisplay = settings.censorRanCommands.value ? command + " ..." : msg;
            MutableComponent component = Component.literal(String.format("> %s", toDisplay));
            component.setStyle(
                    component
                            .getStyle()
                            .withColor(ChatFormatting.WHITE)
                            .withHoverEvent(
                                    new HoverEvent(
                                            HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Click to rerun command")))
                            .withClickEvent(
                                    new ClickEvent(
                                            ClickEvent.Action.RUN_COMMAND,
                                            FORCE_COMMAND_PREFIX + msg)));
            logDirect(component);
        }
    }

    public boolean runCommand(String msg) {
        if (msg.trim().equalsIgnoreCase("damn")) {
            logDirect("daniel");
            return false;
        } else if (msg.trim().equalsIgnoreCase("orderpizza")) {
            try {
                Util.getPlatform().openUri("https://www.dominos.com/en/pages/order/");
            } catch (Exception ignored) {
            }
            return false;
        }
        if (msg.isEmpty()) {
            return this.runCommand("help");
        }
        Tuple<String, List<ICommandArgument>> pair = CommandManager.expand(msg);
        String command = pair.getA();
        String rest = msg.substring(pair.getA().length());
        ArgConsumer argc = new ArgConsumer(this.manager, pair.getB());
        if (!argc.hasAny()) {
            Settings.Setting<?> setting = settings.byLowerName.get(command.toLowerCase(Locale.US));
            if (setting != null) {
                logRanCommand(command, rest);
                if (setting.getValueClass() == Boolean.class) {
                    this.manager.execute(String.format("set toggle %s", setting.getName()));
                } else {
                    this.manager.execute(String.format("set %s", setting.getName()));
                }
                return true;
            }
        } else if (argc.hasExactlyOne()) {
            for (Settings.Setting setting : settings.allSettings) {
                if (setting.isJavaOnly()) {
                    continue;
                }
                if (setting.getName().equalsIgnoreCase(pair.getA())) {
                    logRanCommand(command, rest);
                    try {
                        this.manager.execute(
                                String.format("set %s %s", setting.getName(), argc.getString()));
                    } catch (CommandNotEnoughArgumentsException ignored) {
                    } // The operation is safe
                    return true;
                }
            }
        }

        // If the command exists, then handle echoing the input
        if (this.manager.getCommand(pair.getA()) != null) {
            logRanCommand(command, rest);
        }

        return this.manager.execute(pair);
    }

    @Override
    public void onPreTabComplete(TabCompleteEvent event) {
        if (!settings.prefixControl.value) {
            return;
        }
        String prefix = event.prefix;
        String commandPrefix = settings.prefix.value;
        if (!prefix.startsWith(commandPrefix)) {
            return;
        }
        String msg = prefix.substring(commandPrefix.length());
        List<ICommandArgument> args = CommandArguments.from(msg, true);
        Stream<String> stream = tabComplete(msg);
        if (args.size() == 1) {
            stream = stream.map(x -> commandPrefix + x);
        }
        event.completions = stream.toArray(String[]::new);
    }

    public Stream<String> tabComplete(String msg) {
        try {
            List<ICommandArgument> args = CommandArguments.from(msg, true);
            ArgConsumer argc = new ArgConsumer(this.manager, args);
            if (argc.hasAtMost(2)) {
                if (argc.hasExactly(1)) {
                    return new TabCompleteHelper()
                                    .addCommands(this.manager)
                                    .addSettings()
                                    .filterPrefix(argc.getString())
                                    .stream();
                }
                Settings.Setting<?> setting =
                        settings.byLowerName.get(argc.getString().toLowerCase(Locale.US));
                if (setting != null && !setting.isJavaOnly()) {
                    if (setting.getValueClass() == Boolean.class) {
                        TabCompleteHelper helper = new TabCompleteHelper();
                        if ((Boolean) setting.value) {
                            helper.append("true", "false");
                        } else {
                            helper.append("false", "true");
                        }
                        return helper.filterPrefix(argc.getString()).stream();
                    } else {
                        return Stream.of(SettingsUtil.settingValueToString(setting));
                    }
                }
            }
            return this.manager.tabComplete(msg);
        } catch (
                CommandNotEnoughArgumentsException
                        ignored) { // Shouldn't happen, the operation is safe
            return Stream.empty();
        }
    }
}
