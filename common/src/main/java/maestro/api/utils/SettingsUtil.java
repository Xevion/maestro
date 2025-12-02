package maestro.api.utils;

import java.awt.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import maestro.api.MaestroAPI;
import maestro.api.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;

public class SettingsUtil {
    private static final Logger log = MaestroLogger.get("api");

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
                        // TODO remove soonish
                        if ("allowjumpat256".equals(settingName)) {
                            settingName = "allowjumpatbuildlimit";
                        }
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
            for (Settings.Setting setting : modifiedSettings(settings)) {
                out.write(settingToString(setting) + "\n");
            }
        } catch (Exception ex) {
            log.atError().setCause(ex).log("Exception while saving settings");
        }
    }

    private static Path settingsByName(String name) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("maestro").resolve(name);
    }

    public static List<Settings.Setting> modifiedSettings(Settings settings) {
        List<Settings.Setting> modified = new ArrayList<>();
        for (Settings.Setting setting : settings.allSettings) {
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
    public static String settingTypeToString(Settings.Setting setting) {
        return setting.getType().getTypeName().replaceAll("(?:\\w+\\.)+(\\w+)", "$1");
    }

    public static <T> String settingValueToString(Settings.Setting<T> setting, T value)
            throws IllegalArgumentException {
        Parser io = Parser.getParser(setting.getType());

        if (io == null) {
            throw new IllegalStateException(
                    "Missing " + setting.getValueClass() + " " + setting.getName());
        }

        return io.toString(setting.getType(), value);
    }

    public static String settingValueToString(Settings.Setting setting)
            throws IllegalArgumentException {
        //noinspection unchecked
        return settingValueToString(setting, setting.value);
    }

    public static String settingDefaultToString(Settings.Setting setting)
            throws IllegalArgumentException {
        //noinspection unchecked
        return settingValueToString(setting, setting.defaultValue);
    }

    public static String maybeCensor(int coord) {
        if (MaestroAPI.getSettings().censorCoordinates.value) {
            return "<censored>";
        }

        return Integer.toString(coord);
    }

    public static String settingToString(Settings.Setting setting) throws IllegalStateException {
        if (setting.isJavaOnly()) {
            return setting.getName();
        }

        return setting.getName() + " " + settingValueToString(setting);
    }

    /**
     * Deprecated. Use {@link Settings.Setting#isJavaOnly()} instead.
     *
     * @param setting The Setting
     * @return true if the setting can not be set or read by the user
     */
    @Deprecated
    @com.google.errorprone.annotations.InlineMe(replacement = "setting.isJavaOnly()")
    public static final boolean javaOnlySetting(Settings.Setting setting) {
        return setting.isJavaOnly();
    }

    public static void parseAndApply(Settings settings, String settingName, String settingValue)
            throws IllegalStateException, NumberFormatException {
        Settings.Setting<?> setting = settings.byLowerName.get(settingName);
        if (setting == null) {
            throw new IllegalStateException("No setting by that name");
        }
        Class intendedType = setting.getValueClass();
        ISettingParser ioMethod = Parser.getParser(setting.getType());
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
    private static <T> void setSettingValue(Settings.Setting<T> setting, Object value) {
        setting.value = (T) value;
    }

    private interface ISettingParser<T> {

        T parse(Type type, String raw);

        String toString(Type type, T value);

        boolean accepts(Type type);
    }

    @SuppressWarnings("ImmutableEnumChecker")
    private enum Parser implements ISettingParser {
        DOUBLE(Double.class, Double::parseDouble),
        BOOLEAN(Boolean.class, Boolean::parseBoolean),
        INTEGER(Integer.class, Integer::parseInt),
        FLOAT(Float.class, Float::parseFloat),
        LONG(Long.class, Long::parseLong),
        STRING(String.class, String::new),
        MIRROR(Mirror.class, Mirror::valueOf, Mirror::name),
        ROTATION(Rotation.class, Rotation::valueOf, Rotation::name),
        COLOR(
                Color.class,
                str -> {
                    Iterator<String> it =
                            com.google.common.base.Splitter.on(',').split(str).iterator();
                    return new Color(
                            Integer.parseInt(it.next()),
                            Integer.parseInt(it.next()),
                            Integer.parseInt(it.next()));
                },
                color -> color.getRed() + "," + color.getGreen() + "," + color.getBlue()),
        VEC3I(
                Vec3i.class,
                str -> {
                    Iterator<String> it =
                            com.google.common.base.Splitter.on(',').split(str).iterator();
                    return new Vec3i(
                            Integer.parseInt(it.next()),
                            Integer.parseInt(it.next()),
                            Integer.parseInt(it.next()));
                },
                vec -> vec.getX() + "," + vec.getY() + "," + vec.getZ()),
        BLOCK(
                Block.class,
                str -> BlockUtils.stringToBlockRequired(str.trim()),
                BlockUtils::blockToString),
        ITEM(
                Item.class,
                str ->
                        BuiltInRegistries.ITEM
                                .get(ResourceLocation.parse(str.trim()))
                                .map(Holder.Reference::value)
                                .orElse(null),
                item -> BuiltInRegistries.ITEM.getKey(item).toString()),
        LIST() {
            @Override
            public Object parse(Type type, String raw) {
                Type elementType = ((ParameterizedType) type).getActualTypeArguments()[0];
                Parser parser = Parser.getParser(elementType);
                return Stream.of(raw.split(","))
                        .map(s -> parser.parse(elementType, s))
                        .collect(Collectors.toList());
            }

            @Override
            public String toString(Type type, Object value) {
                Type elementType = ((ParameterizedType) type).getActualTypeArguments()[0];
                Parser parser = Parser.getParser(elementType);

                return ((Collection<?>) value)
                        .stream()
                                .map(o -> parser.toString(elementType, o))
                                .collect(Collectors.joining(","));
            }

            @Override
            public boolean accepts(Type type) {
                return List.class.isAssignableFrom(TypeUtils.resolveBaseClass(type));
            }
        },
        MAPPING() {
            @Override
            public Object parse(Type type, String raw) {
                Type keyType = ((ParameterizedType) type).getActualTypeArguments()[0];
                Type valueType = ((ParameterizedType) type).getActualTypeArguments()[1];
                Parser keyParser = Parser.getParser(keyType);
                Parser valueParser = Parser.getParser(valueType);

                return Stream.of(raw.split(",(?=[^,]*->)"))
                        .map(s -> s.split("->"))
                        .collect(
                                Collectors.toMap(
                                        s -> keyParser.parse(keyType, s[0]),
                                        s -> valueParser.parse(valueType, s[1])));
            }

            @Override
            public String toString(Type type, Object value) {
                Type keyType = ((ParameterizedType) type).getActualTypeArguments()[0];
                Type valueType = ((ParameterizedType) type).getActualTypeArguments()[1];
                Parser keyParser = Parser.getParser(keyType);
                Parser valueParser = Parser.getParser(valueType);

                return ((Map<?, ?>) value)
                        .entrySet().stream()
                                .map(
                                        o ->
                                                keyParser.toString(keyType, o.getKey())
                                                        + "->"
                                                        + valueParser.toString(
                                                                valueType, o.getValue()))
                                .collect(Collectors.joining(","));
            }

            @Override
            public boolean accepts(Type type) {
                return Map.class.isAssignableFrom(TypeUtils.resolveBaseClass(type));
            }
        };

        private final Class<?> cla$$;
        private final Function<String, Object> parser;
        private final Function<Object, String> toString;

        Parser() {
            this.cla$$ = null;
            this.parser = null;
            this.toString = null;
        }

        <T> Parser(Class<T> cla$$, Function<String, T> parser) {
            this(cla$$, parser, Object::toString);
        }

        <T> Parser(Class<T> cla$$, Function<String, T> parser, Function<T, String> toString) {
            this.cla$$ = cla$$;
            this.parser = parser::apply;
            this.toString = x -> toString.apply((T) x);
        }

        @Override
        public Object parse(Type type, String raw) {
            Object parsed = this.parser.apply(raw);
            Objects.requireNonNull(parsed);
            return parsed;
        }

        @Override
        public String toString(Type type, Object value) {
            return this.toString.apply(value);
        }

        @Override
        public boolean accepts(Type type) {
            return type instanceof Class && this.cla$$.isAssignableFrom((Class) type);
        }

        public static Parser getParser(Type type) {
            return Stream.of(values())
                    .filter(parser -> parser.accepts(type))
                    .findFirst()
                    .orElse(null);
        }
    }
}
