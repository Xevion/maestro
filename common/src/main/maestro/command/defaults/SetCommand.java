package maestro.command.defaults;

import static maestro.api.command.IMaestroChatControl.FORCE_COMMAND_PREFIX;
import static maestro.api.utils.SettingsUtil.*;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.MaestroAPI;
import maestro.api.Setting;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.RelativeFile;
import maestro.api.command.exception.CommandException;
import maestro.api.command.helpers.FuzzySearchHelper;
import maestro.api.command.helpers.Paginator;
import maestro.api.command.helpers.TabCompleteHelper;
import maestro.api.utils.SettingsUtil;
import maestro.utils.chat.ChatMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class SetCommand extends Command {

    public SetCommand(IAgent maestro) {
        super(maestro, "set", "setting", "settings");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String arg = args.hasAny() ? args.getString().toLowerCase(Locale.US) : "list";
        if (Arrays.asList("s", "save").contains(arg)) {
            SettingsUtil.save(Agent.settings());
            log.atInfo().log("Settings saved");
            return;
        }
        if (Arrays.asList("load", "ld").contains(arg)) {
            String file = SETTINGS_DEFAULT_NAME;
            if (args.hasAny()) {
                file = args.getString();
            }
            // reset to defaults
            SettingsUtil.modifiedSettings(Agent.settings()).forEach(Setting::reset);
            // then load from disk
            SettingsUtil.readAndApply(Agent.settings(), file);
            log.atInfo().addKeyValue("file", file).log("Settings reloaded");
            return;
        }
        boolean viewModified = Arrays.asList("m", "mod", "modified").contains(arg);
        boolean viewAll = Arrays.asList("all", "l", "list").contains(arg);
        boolean paginate = viewModified || viewAll;
        if (paginate) {
            String search =
                    args.hasAny() && args.peekAsOrNull(Integer.class) == null
                            ? args.getString()
                            : "";
            args.requireMax(1);

            // Collect all candidates (filtered for Java-only)
            List<Setting> allCandidates =
                    (viewModified
                                    ? SettingsUtil.modifiedSettings(Agent.settings())
                                    : Agent.settings().allSettings)
                            .stream().filter(s -> !s.isJavaOnly()).collect(Collectors.toList());

            // Use fuzzy search for non-empty queries, alphabetical sort for empty
            List<? extends Setting> toPaginate =
                    search.isEmpty()
                            ? allCandidates.stream()
                                    .sorted(
                                            (s1, s2) ->
                                                    String.CASE_INSENSITIVE_ORDER.compare(
                                                            s1.getName(), s2.getName()))
                                    .collect(Collectors.toList())
                            : FuzzySearchHelper.search(
                                    search, allCandidates, Setting::getName, 60, Integer.MAX_VALUE);
            Paginator.paginate(
                    args,
                    new Paginator<>(toPaginate),
                    () ->
                            log.atInfo().log(
                                    !search.isEmpty()
                                            ? String.format(
                                                    "All %ssettings containing the string '%s':",
                                                    viewModified ? "modified " : "", search)
                                            : String.format(
                                                    "All %ssettings:",
                                                    viewModified ? "modified " : "")),
                    setting -> {
                        MutableComponent typeComponent =
                                Component.literal(
                                        String.format(" (%s)", settingTypeToString(setting)));
                        typeComponent.setStyle(
                                typeComponent.getStyle().withColor(ChatFormatting.DARK_GRAY));
                        MutableComponent hoverComponent = Component.literal("");
                        hoverComponent.setStyle(
                                hoverComponent.getStyle().withColor(ChatFormatting.GRAY));
                        hoverComponent.append(setting.getName());
                        hoverComponent.append(
                                String.format("\nType: %s", settingTypeToString(setting)));
                        hoverComponent.append(
                                String.format("\n\nValue:\n%s", settingValueToString(setting)));
                        hoverComponent.append(
                                String.format(
                                        "\n\nDefault Value:\n%s", settingDefaultToString(setting)));
                        String commandSuggestion =
                                Agent.settings().prefix.value
                                        + String.format("set %s ", setting.getName());
                        MutableComponent component = Component.literal(setting.getName());
                        component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
                        component.append(typeComponent);
                        component.setStyle(
                                component
                                        .getStyle()
                                        .withHoverEvent(
                                                new HoverEvent(
                                                        HoverEvent.Action.SHOW_TEXT,
                                                        hoverComponent))
                                        .withClickEvent(
                                                new ClickEvent(
                                                        ClickEvent.Action.SUGGEST_COMMAND,
                                                        commandSuggestion)));

                        MutableComponent prefixed = Component.literal("");
                        prefixed.append(ChatMessage.createCategoryPrefix("cmd"));
                        prefixed.append(" ");
                        prefixed.append(component);

                        Minecraft.getInstance()
                                .execute(
                                        () ->
                                                MaestroAPI.getSettings()
                                                        .logger
                                                        .value
                                                        .accept(prefixed));

                        return component;
                    },
                    FORCE_COMMAND_PREFIX + "set " + arg + " " + search);
            return;
        }
        args.requireMax(1);
        boolean resetting = arg.equalsIgnoreCase("reset");
        boolean toggling = arg.equalsIgnoreCase("toggle");
        boolean doingSomething = resetting || toggling;
        if (resetting) {
            if (!args.hasAny()) {
                log.atInfo().log(
                        "Please specify 'all' as an argument to reset to confirm you'd really like"
                                + " to do this");
                log.atInfo().log(
                        "ALL settings will be reset. Use the 'set modified' or 'modified' commands"
                                + " to see what will be reset.");
                log.atInfo().log(
                        "Specify a setting name instead of 'all' to only reset one setting");
            } else if (args.peekString().equalsIgnoreCase("all")) {
                SettingsUtil.modifiedSettings(Agent.settings()).forEach(Setting::reset);
                log.atInfo().log("All settings have been reset to their default values");
                SettingsUtil.save(Agent.settings());
                return;
            }
        }
        if (toggling) {
            args.requireMin(1);
        }
        String settingName = doingSomething ? args.getString() : arg;
        Setting<?> setting =
                Agent.settings().allSettings.stream()
                        .filter(s -> s.getName().equalsIgnoreCase(settingName))
                        .findFirst()
                        .orElse(null);
        if (setting == null) {
            throw new CommandException.InvalidArgument.InvalidType(
                    args.consumed(), "a valid setting");
        }
        if (setting.isJavaOnly()) {
            // ideally it would act as if the setting didn't exist
            // but users will see it in Settings.java or its javadoc
            // so at some point we have to tell them or they will see it as a bug
            throw new CommandException.InvalidState(
                    String.format("Setting %s can only be used via the api.", setting.getName()));
        }
        if (!doingSomething && !args.hasAny()) {
            log.atInfo().log(String.format("Value of setting %s:", setting.getName()));
            log.atInfo().log(settingValueToString(setting));
        } else {
            String oldValue = settingValueToString(setting);
            if (resetting) {
                setting.reset();
            } else if (toggling) {
                if (setting.getValueClass() != Boolean.class) {
                    throw new CommandException.InvalidArgument.InvalidType(
                            args.consumed(), "a toggleable setting", "some other setting");
                }
                //noinspection unchecked
                Setting<Boolean> asBoolSetting = (Setting<Boolean>) setting;
                asBoolSetting.value ^= true;
                log.atInfo().log(
                        String.format(
                                "Toggled setting %s to %s", setting.getName(), setting.value));
            } else {
                String newValue = args.getString();
                try {
                    SettingsUtil.parseAndApply(Agent.settings(), arg, newValue);
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new CommandException.InvalidArgument.InvalidType(
                            args.consumed(), "a valid value", t);
                }
            }
            if (!toggling) {
                log.atInfo().log(
                        String.format(
                                "Successfully %s %s to %s",
                                resetting ? "reset" : "set",
                                setting.getName(),
                                settingValueToString(setting)));
            }

            MutableComponent oldValueComponent =
                    Component.literal(String.format("Old value: %s", oldValue));
            oldValueComponent.setStyle(
                    oldValueComponent
                            .getStyle()
                            .withColor(ChatFormatting.GRAY)
                            .withHoverEvent(
                                    new HoverEvent(
                                            HoverEvent.Action.SHOW_TEXT,
                                            Component.literal(
                                                    "Click to set the setting back to this value")))
                            .withClickEvent(
                                    new ClickEvent(
                                            ClickEvent.Action.RUN_COMMAND,
                                            FORCE_COMMAND_PREFIX
                                                    + String.format(
                                                            "set %s %s",
                                                            setting.getName(), oldValue))));

            MutableComponent prefixed = Component.literal("");
            prefixed.append(ChatMessage.createCategoryPrefix("cmd"));
            prefixed.append(" ");
            prefixed.append(oldValueComponent);

            Minecraft.getInstance()
                    .execute(() -> MaestroAPI.getSettings().logger.value.accept(prefixed));

            if (((setting.getName().equals("chatControl")
                            && !(Boolean) setting.value
                            && !Agent.settings().chatControlAnyway.value)
                    || (setting.getName().equals("chatControlAnyway")
                            && !(Boolean) setting.value
                            && !Agent.settings().chatControl.value))) {
                log.atWarn()
                        .log(
                                "Warning: Chat commands will no longer work. If you want to revert"
                                    + " this change, use prefix control (if enabled) or click the"
                                    + " old value listed above.");
            } else if (setting.getName().equals("prefixControl") && !(Boolean) setting.value) {
                log.atWarn()
                        .log(
                                "Warning: Prefixed commands will no longer work. If you want to"
                                    + " revert this change, use chat control (if enabled) or click"
                                    + " the old value listed above.");
            }
        }
        SettingsUtil.save(Agent.settings());
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            String arg = args.getString();
            if (args.hasExactlyOne()
                    && !Arrays.asList("s", "save")
                            .contains(args.peekString().toLowerCase(Locale.US))) {
                if (arg.equalsIgnoreCase("reset")) {
                    return new TabCompleteHelper()
                                    .addModifiedSettings()
                                    .prepend("all")
                                    .filterPrefix(args.getString())
                                    .stream();
                } else if (arg.equalsIgnoreCase("toggle")) {
                    return new TabCompleteHelper()
                            .addToggleableSettings().filterPrefix(args.getString()).stream();
                } else if (Arrays.asList("ld", "load").contains(arg.toLowerCase(Locale.US))) {
                    // settings always use the directory of the main Minecraft instance
                    return RelativeFile.tabComplete(
                            args,
                            Minecraft.getInstance()
                                    .gameDirectory
                                    .toPath()
                                    .resolve("maestro")
                                    .toFile());
                }
                Setting<?> setting = Agent.settings().byLowerName.get(arg.toLowerCase(Locale.US));
                if (setting != null) {
                    if (setting.getType() == Boolean.class) {
                        TabCompleteHelper helper = new TabCompleteHelper();
                        if ((Boolean) setting.value) {
                            helper.append("true", "false");
                        } else {
                            helper.append("false", "true");
                        }
                        return helper.filterPrefix(args.getString()).stream();
                    } else {
                        return Stream.of(settingValueToString(setting));
                    }
                }
            } else if (!args.hasAny()) {
                return new TabCompleteHelper()
                                .addSettings()
                                .sortAlphabetically()
                                .prepend("list", "modified", "reset", "toggle", "save", "load")
                                .filterPrefix(arg)
                                .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View or change settings";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Using the set command, you can manage all of Maestro's settings. Almost every"
                        + " aspect is controlled by these settings - go wild!",
                "",
                "Usage:",
                "> set - Same as `set list`",
                "> set list [page] - View all settings",
                "> set modified [page] - View modified settings",
                "> set <setting> - View the current value of a setting",
                "> set <setting> <value> - Set the value of a setting",
                "> set reset all - Reset ALL SETTINGS to their defaults",
                "> set reset <setting> - Reset a setting to its default",
                "> set toggle <setting> - Toggle a boolean setting",
                "> set save - Save all settings (this is automatic tho)",
                "> set load - Load settings from settings.txt",
                "> set load [filename] - Load settings from another file in your"
                        + " minecraft/maestro");
    }
}
