package maestro.api.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import maestro.Agent;
import maestro.api.Setting;
import maestro.api.Settings;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

public class SettingsUtil {
    private static final Logger log = Loggers.get("api");

    public static final String SETTINGS_DEFAULT_NAME = "settings.txt";
    private static final Pattern SETTING_PATTERN =
            Pattern.compile(
                    "^(?<setting>[^ ]+) +(?<value>.+)"); // key and value split by the first space

    private static boolean isComment(String line) {
        return line.startsWith("#") || line.startsWith("//");
    }

    private static void forEachLine(Path file, Consumer<String> consumer) throws IOException {
        try (BufferedReader scan = Files.newBufferedReader(file)) {
            String line;
            while ((line = scan.readLine()) != null) {
                if (line.isEmpty() || isComment(line)) {
                    continue;
                }
                consumer.accept(line);
            }
        }
    }

    public static void readAndApply(Settings settings, String settingsName) {
        try {
            forEachLine(
                    settingsByName(settingsName),
                    line -> {
                        Matcher matcher = SETTING_PATTERN.matcher(line);
                        if (!matcher.matches()) {
                            log.atInfo()
                                    .addKeyValue("line", line)
                                    .log("Invalid syntax in setting file");
                            return;
                        }

                        String settingName =
                                matcher.group("setting").toLowerCase(java.util.Locale.ROOT);
                        String settingValue = matcher.group("value");
                        try {
                            parseAndApply(settings, settingName, settingValue);
                        } catch (Exception ex) {
                            log.atWarn()
                                    .setCause(ex)
                                    .addKeyValue("line", line)
                                    .log("Unable to parse setting line");
                        }
                    });
        } catch (NoSuchFileException ignored) {
            log.atInfo()
                    .addKeyValue("settings_file", settingsName)
                    .log("Settings file not found, using defaults");
        } catch (Exception ex) {
            log.atWarn()
                    .setCause(ex)
                    .log("Exception while reading settings, some may be reset to defaults");
        }
    }

    public static synchronized void save(Settings settings) {
        try (BufferedWriter out = Files.newBufferedWriter(settingsByName(SETTINGS_DEFAULT_NAME))) {
            for (Setting setting : modifiedSettings(settings)) {
                out.write(settingToString(setting) + "\n");
            }
        } catch (Exception ex) {
            log.atError().setCause(ex).log("Exception while saving settings");
        }
    }

    private static Path settingsByName(String name) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("maestro").resolve(name);
    }

    public static List<Setting> modifiedSettings(Settings settings) {
        List<Setting> modified = new ArrayList<>();
        for (Setting setting : settings.allSettings) {
            if (setting.value == null) {
                System.out.println("NULL SETTING?" + setting.getName());
                continue;
            }
            if (setting.isJavaOnly()) {
                continue; // NO
            }
            if (setting.value == setting.defaultValue) {
                continue;
            }
            modified.add(setting);
        }
        return modified;
    }

    /**
     * Gets the type of setting and returns it as a string, with package names stripped.
     *
     * <p>For example, if the setting type is {@code java.util.List<java.lang.String>}, this
     * function returns {@code List<String>}.
     *
     * @param setting The setting
     * @return The type
     */
    public static String settingTypeToString(Setting setting) {
        return setting.getType().getTypeName().replaceAll("(?:\\w+\\.)+(\\w+)", "$1");
    }

    public static <T> String settingValueToString(Setting<T> setting, T value)
            throws IllegalArgumentException {
        SettingParser.Parser io = SettingParser.Parser.getParser(setting.getType());

        if (io == null) {
            throw new IllegalStateException(
                    "Missing " + setting.getValueClass() + " " + setting.getName());
        }

        return io.toString(setting.getType(), value);
    }

    public static String settingValueToString(Setting setting) throws IllegalArgumentException {
        //noinspection unchecked
        return settingValueToString(setting, setting.value);
    }

    public static String settingDefaultToString(Setting setting) throws IllegalArgumentException {
        //noinspection unchecked
        return settingValueToString(setting, setting.defaultValue);
    }

    public static String maybeCensor(int coord) {
        if (Agent.getPrimaryAgent().getSettings().censorCoordinates.value) {
            return "<censored>";
        }

        return Integer.toString(coord);
    }

    public static String settingToString(Setting setting) throws IllegalStateException {
        if (setting.isJavaOnly()) {
            return setting.getName();
        }

        return setting.getName() + " " + settingValueToString(setting);
    }

    public static void parseAndApply(Settings settings, String settingName, String settingValue)
            throws IllegalStateException, NumberFormatException {
        Setting<?> setting = settings.byLowerName.get(settingName);
        if (setting == null) {
            throw new IllegalStateException("No setting by that name");
        }
        Class intendedType = setting.getValueClass();
        SettingParser ioMethod = SettingParser.Parser.getParser(setting.getType());
        Object parsed = ioMethod.parse(setting.getType(), settingValue);
        if (!intendedType.isInstance(parsed)) {
            throw new IllegalStateException(
                    ioMethod
                            + " parser returned incorrect type, expected "
                            + intendedType
                            + " got "
                            + parsed
                            + " which is "
                            + parsed.getClass());
        }
        setSettingValue(setting, parsed);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setSettingValue(Setting<T> setting, Object value) {
        setting.value = (T) value;
    }
}
