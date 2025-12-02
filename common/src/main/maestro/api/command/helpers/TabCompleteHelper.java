package maestro.api.command.helpers;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import maestro.api.MaestroAPI;
import maestro.api.Setting;
import maestro.api.Settings;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.IDatatype;
import maestro.api.command.manager.ICommandManager;
import maestro.api.event.events.TabCompleteEvent;
import maestro.api.utils.SettingsUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * The {@link TabCompleteHelper} is a <b>single-use</b> object that helps you handle tab completion.
 * It includes helper methods for appending and prepending streams, sorting, filtering by prefix,
 * and so on.
 *
 * <p>The recommended way to use this class is:
 *
 * <ul>
 *   <li>Create a new instance with the empty constructor
 *   <li>Use {@code append}, {@code prepend} or {@code add<something>} methods to add completions
 *   <li>Sort using {@link #sort(Comparator)} or {@link #sortAlphabetically()} and then filter by
 *       prefix using {@link #filterPrefix(String)}
 *   <li>Get the stream using {@link #stream()}
 *   <li>Pass it up to whatever's calling your tab complete function (i.e. {@link
 *       ICommandManager#tabComplete(String)} or {@link
 *       IArgConsumer#tabCompleteDatatype(IDatatype)})
 * </ul>
 *
 * <p>For advanced users: if you're intercepting {@link TabCompleteEvent}s directly, use {@link
 * #build()} instead for an array.
 */
public class TabCompleteHelper {

    private Stream<String> stream;

    public TabCompleteHelper(String[] base) {
        stream = Stream.of(base);
    }

    public TabCompleteHelper(List<String> base) {
        stream = base.stream();
    }

    public TabCompleteHelper() {
        stream = Stream.empty();
    }

    /**
     * Appends the specified stream to this {@link TabCompleteHelper} and returns it for chaining
     *
     * @param source The stream to append
     * @return This {@link TabCompleteHelper} after having appended the stream
     * @see #append(String...)
     * @see #append(Class)
     */
    public TabCompleteHelper append(Stream<String> source) {
        stream = Stream.concat(stream, source);
        return this;
    }

    /**
     * Appends the specified strings to this {@link TabCompleteHelper} and returns it for chaining
     *
     * @param source The stream to append
     * @return This {@link TabCompleteHelper} after having appended the strings
     * @see #append(Stream)
     * @see #append(Class)
     */
    public TabCompleteHelper append(String... source) {
        return append(Stream.of(source));
    }

    /**
     * Appends all values of the specified enum to this {@link TabCompleteHelper} and returns it for
     * chaining
     *
     * @param num The enum to append the values of
     * @return This {@link TabCompleteHelper} after having appended the values
     * @see #append(Stream)
     * @see #append(String...)
     */
    public TabCompleteHelper append(Class<? extends Enum<?>> num) {
        return append(Stream.of(num.getEnumConstants()).map(Enum::name).map(String::toLowerCase));
    }

    /**
     * Prepends the specified stream to this {@link TabCompleteHelper} and returns it for chaining
     *
     * @param source The stream to prepend
     * @return This {@link TabCompleteHelper} after having prepended the stream
     * @see #prepend(String...)
     * @see #prepend(Class)
     */
    public TabCompleteHelper prepend(Stream<String> source) {
        stream = Stream.concat(source, stream);
        return this;
    }

    /**
     * Prepends the specified strings to this {@link TabCompleteHelper} and returns it for chaining
     *
     * @param source The stream to prepend
     * @return This {@link TabCompleteHelper} after having prepended the strings
     * @see #prepend(Stream)
     * @see #prepend(Class)
     */
    public TabCompleteHelper prepend(String... source) {
        return prepend(Stream.of(source));
    }

    /**
     * Prepends all values of the specified enum to this {@link TabCompleteHelper} and returns it
     * for chaining
     *
     * @param num The enum to prepend the values of
     * @return This {@link TabCompleteHelper} after having prepended the values
     * @see #prepend(Stream)
     * @see #prepend(String...)
     */
    public TabCompleteHelper prepend(Class<? extends Enum<?>> num) {
        return prepend(Stream.of(num.getEnumConstants()).map(Enum::name).map(String::toLowerCase));
    }

    /**
     * Apply the specified {@code transform} to every element <b>currently</b> in this {@link
     * TabCompleteHelper} and return this object for chaining
     *
     * @param transform The transform to apply
     * @return This {@link TabCompleteHelper}
     */
    public TabCompleteHelper map(Function<String, String> transform) {
        stream = stream.map(transform);
        return this;
    }

    /**
     * Apply the specified {@code filter} to every element <b>currently</b> in this {@link
     * TabCompleteHelper} and return this object for chaining
     *
     * @param filter The filter to apply
     * @return This {@link TabCompleteHelper}
     */
    public TabCompleteHelper filter(Predicate<String> filter) {
        stream = stream.filter(filter);
        return this;
    }

    /**
     * Apply the specified {@code sort} to every element <b>currently</b> in this {@link
     * TabCompleteHelper} and return this object for chaining
     *
     * @param comparator The comparator to use
     * @return This {@link TabCompleteHelper}
     */
    public TabCompleteHelper sort(Comparator<String> comparator) {
        stream = stream.sorted(comparator);
        return this;
    }

    /**
     * Sort every element <b>currently</b> in this {@link TabCompleteHelper} alphabetically and
     * return this object for chaining
     *
     * @return This {@link TabCompleteHelper}
     */
    public TabCompleteHelper sortAlphabetically() {
        return sort(String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Filter out any element that doesn't start with {@code prefix} and return this object for
     * chaining
     *
     * <p>This method now uses fuzzy search with a stricter threshold (70) to maintain prefix-like
     * behavior while adding typo tolerance.
     *
     * @param prefix The prefix to filter for
     * @return This {@link TabCompleteHelper}
     * @see #filterFuzzy(String)
     */
    public TabCompleteHelper filterPrefix(String prefix) {
        return filterFuzzy(prefix, 70, FuzzySearchHelper.DEFAULT_LIMIT);
    }

    /**
     * Filter out any element that doesn't start with {@code prefix} and return this object for
     * chaining
     *
     * <p>Assumes every element in this {@link TabCompleteHelper} is a {@link ResourceLocation}
     *
     * @param prefix The prefix to filter for
     * @return This {@link TabCompleteHelper}
     */
    public TabCompleteHelper filterPrefixNamespaced(String prefix) {
        ResourceLocation loc = ResourceLocation.tryParse(prefix);
        if (loc == null) {
            stream = Stream.empty();
            return this;
        }
        return filterPrefix(loc.toString());
    }

    /**
     * Filter using fuzzy search with default threshold ({@value
     * FuzzySearchHelper#DEFAULT_THRESHOLD}) and limit ({@value FuzzySearchHelper#DEFAULT_LIMIT}).
     *
     * <p>Uses Levenshtein distance-based matching with multi-stage algorithm: exact → prefix →
     * fuzzy.
     *
     * @param query The search query (supports typos)
     * @return This {@link TabCompleteHelper}
     * @see FuzzySearchHelper
     */
    public TabCompleteHelper filterFuzzy(String query) {
        return filterFuzzy(
                query, FuzzySearchHelper.DEFAULT_THRESHOLD, FuzzySearchHelper.DEFAULT_LIMIT);
    }

    /**
     * Filter using fuzzy search with custom threshold and limit.
     *
     * <p>Uses Levenshtein distance-based matching with multi-stage algorithm: exact → prefix →
     * fuzzy.
     *
     * @param query The search query (supports typos)
     * @param threshold Minimum fuzzy match score (0-100, higher = stricter)
     * @param limit Maximum number of results to return
     * @return This {@link TabCompleteHelper}
     * @see FuzzySearchHelper
     */
    public TabCompleteHelper filterFuzzy(String query, int threshold, int limit) {
        stream = FuzzySearchHelper.searchStream(query, stream, threshold, limit);
        return this;
    }

    /**
     * @return An array containing every element in this {@link TabCompleteHelper}
     * @see #stream()
     */
    public String[] build() {
        return stream.toArray(String[]::new);
    }

    /**
     * @return A stream containing every element in this {@link TabCompleteHelper}
     * @see #build()
     */
    public Stream<String> stream() {
        return stream;
    }

    /**
     * Appends every command in the specified {@link ICommandManager} to this {@link
     * TabCompleteHelper}
     *
     * @param manager A command manager
     * @return This {@link TabCompleteHelper}
     */
    public TabCompleteHelper addCommands(ICommandManager manager) {
        return append(
                manager.getRegistry()
                        .descendingStream()
                        .flatMap(command -> command.getNames().stream())
                        .distinct());
    }

    /**
     * Appends every setting in the {@link Settings} to this {@link TabCompleteHelper}
     *
     * @return This {@link TabCompleteHelper}
     */
    public TabCompleteHelper addSettings() {
        return append(
                MaestroAPI.getSettings().allSettings.stream()
                        .filter(s -> !s.isJavaOnly())
                        .map(Setting::getName)
                        .sorted(String.CASE_INSENSITIVE_ORDER));
    }

    /**
     * Appends every modified setting in the {@link Settings} to this {@link TabCompleteHelper}
     *
     * @return This {@link TabCompleteHelper}
     */
    public TabCompleteHelper addModifiedSettings() {
        return append(
                SettingsUtil.modifiedSettings(MaestroAPI.getSettings()).stream()
                        .map(Setting::getName)
                        .sorted(String.CASE_INSENSITIVE_ORDER));
    }

    /**
     * Appends every {@link Boolean} setting in the {@link Settings} to this {@link
     * TabCompleteHelper}
     *
     * @return This {@link TabCompleteHelper}
     */
    public TabCompleteHelper addToggleableSettings() {
        return append(
                MaestroAPI.getSettings().getAllValuesByType(Boolean.class).stream()
                        .map(Setting::getName)
                        .sorted(String.CASE_INSENSITIVE_ORDER));
    }
}
