package maestro.utils;

import java.awt.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Provides parsing and serialization for different setting value types. Supports primitives,
 * collections, Minecraft types (blocks, items, rotations), and custom types.
 */
public interface SettingParser<T> {

    T parse(Type type, String raw);

    String toString(Type type, T value);

    boolean accepts(Type type);

    @SuppressWarnings("ImmutableEnumChecker")
    enum Parser implements SettingParser {
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
