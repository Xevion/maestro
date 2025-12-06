package maestro.api.command.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult;

/**
 * Fuzzy search utility using Levenshtein distance for intelligent command and setting suggestions.
 *
 * <p>This helper implements a multi-stage search strategy to balance accuracy and permissiveness:
 *
 * <ol>
 *   <li><b>Exact match</b>: Case-insensitive exact match (highest confidence)
 *   <li><b>Prefix match</b>: Traditional startsWith matching (familiar UX)
 *   <li><b>Fuzzy match</b>: Levenshtein distance-based matching (typo tolerance)
 * </ol>
 *
 * <p><b>Usage Examples:</b>
 *
 * <pre>{@code
 * // Simple string search
 * List<String> commands = List.of("mine", "help", "goto", "build");
 * List<String> results = FuzzySearchHelper.search("mien", commands);
 * // Returns: ["mine"] (catches typo)
 *
 * // Object search with custom extractor
 * List<Setting> settings = Agent.getPrimaryAgent().getSettings().allSettings;
 * List<Setting> matches = FuzzySearchHelper.search(
 *     "phat",
 *     settings,
 *     Setting::getName
 * );
 * // Returns settings with names like "pathfinding..." (typo tolerance)
 *
 * // Stream-based API
 * Stream<String> suggestions = FuzzySearchHelper.searchStream("hlp", commandStream);
 * // Returns: Stream containing "help"
 * }</pre>
 *
 * <p><b>Configuration:</b>
 *
 * <ul>
 *   <li>{@link #DEFAULT_THRESHOLD} = 60: Catches common typos, rejects nonsense
 *   <li>{@link #DEFAULT_LIMIT} = 15: Manageable result count
 *   <li>Short queries (1-2 chars): Prefix-only (fuzzy too noisy)
 * </ul>
 *
 * <p><b>Performance:</b> O(n*m) complexity per fuzzy comparison. Typical use: 10-50 candidates,
 * 5-15 char queries. Expected latency: <10ms.
 *
 * @see TabCompleteHelper
 */
public class FuzzySearchHelper {

    /**
     * Default fuzzy match threshold (0-100 scale).
     *
     * <p>60 is a sweet spot that:
     *
     * <ul>
     *   <li>Catches common typos: "mien" → "mine", "hlp" → "help"
     *   <li>Rejects nonsense: "xyz" won't match "mine"
     *   <li>Balances precision and recall
     * </ul>
     */
    public static final int DEFAULT_THRESHOLD = 60;

    /**
     * Default maximum number of results to return.
     *
     * <p>15 provides a manageable list without overwhelming the user. Minecraft chat displays ~10
     * lines comfortably, so 15 allows scrolling without cognitive overload.
     */
    public static final int DEFAULT_LIMIT = 15;

    /**
     * Search with default threshold ({@value DEFAULT_THRESHOLD}) and limit ({@value
     * DEFAULT_LIMIT}).
     *
     * @param query The search query (user input)
     * @param candidates List of candidate strings to search
     * @return Top matching strings, ranked by relevance
     */
    public static List<String> search(String query, List<String> candidates) {
        return search(query, candidates, Function.identity(), DEFAULT_THRESHOLD, DEFAULT_LIMIT);
    }

    /**
     * Search with custom threshold and limit.
     *
     * @param query The search query (user input)
     * @param candidates List of candidate strings to search
     * @param threshold Minimum fuzzy match score (0-100, higher = stricter)
     * @param limit Maximum number of results to return
     * @return Top matching strings, ranked by relevance
     */
    public static List<String> search(
            String query, List<String> candidates, int threshold, int limit) {
        return search(query, candidates, Function.identity(), threshold, limit);
    }

    /**
     * Search objects with custom extractor function and default threshold/limit.
     *
     * @param <T> Type of objects being searched
     * @param query The search query (user input)
     * @param candidates List of candidate objects to search
     * @param extractor Function to extract searchable string from each object
     * @return Top matching objects, ranked by relevance
     */
    public static <T> List<T> search(
            String query, List<T> candidates, Function<T, String> extractor) {
        return search(query, candidates, extractor, DEFAULT_THRESHOLD, DEFAULT_LIMIT);
    }

    /**
     * Search objects with custom extractor function, threshold, and limit.
     *
     * <p>This is the core search implementation. All other methods delegate to this.
     *
     * <p><b>Multi-stage algorithm:</b>
     *
     * <ol>
     *   <li>Empty query: Return all (up to limit)
     *   <li>Short query (1-2 chars): Prefix-only matching
     *   <li>Long query (3+ chars):
     *       <ol>
     *         <li>Exact match (case-insensitive): Return immediately
     *         <li>Prefix match: Return if enough results found
     *         <li>Fuzzy match: Use Levenshtein distance with threshold
     *       </ol>
     * </ol>
     *
     * @param <T> Type of objects being searched
     * @param query The search query (user input)
     * @param candidates List of candidate objects to search
     * @param extractor Function to extract searchable string from each object
     * @param threshold Minimum fuzzy match score (0-100, higher = stricter)
     * @param limit Maximum number of results to return
     * @return Top matching objects, ranked by relevance
     */
    public static <T> List<T> search(
            String query,
            List<T> candidates,
            Function<T, String> extractor,
            int threshold,
            int limit) {
        if (query == null) {
            query = "";
        }
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }

        final String queryLower = query.toLowerCase(Locale.US);
        final int queryLength = queryLower.length();

        // Stage 1: Empty query - return all (up to limit)
        if (queryLength == 0) {
            return candidates.stream()
                    .sorted(Comparator.comparing(extractor, String.CASE_INSENSITIVE_ORDER))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        // Stage 2: Short query (1-2 chars) - prefix only (fuzzy too noisy)
        if (queryLength <= 2) {
            return candidates.stream()
                    .filter(
                            candidate -> {
                                String value = extractor.apply(candidate);
                                return value != null
                                        && value.toLowerCase(Locale.US).startsWith(queryLower);
                            })
                    .sorted(Comparator.comparing(extractor, String.CASE_INSENSITIVE_ORDER))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        // Stage 3a: Exact match (case-insensitive)
        List<T> exactMatches =
                candidates.stream()
                        .filter(
                                candidate -> {
                                    String value = extractor.apply(candidate);
                                    return value != null && value.equalsIgnoreCase(queryLower);
                                })
                        .toList();
        if (!exactMatches.isEmpty()) {
            return exactMatches.stream().limit(limit).collect(Collectors.toList());
        }

        // Stage 3b: Prefix match
        List<T> prefixMatches =
                candidates.stream()
                        .filter(
                                candidate -> {
                                    String value = extractor.apply(candidate);
                                    return value != null
                                            && value.toLowerCase(Locale.US).startsWith(queryLower);
                                })
                        .sorted(Comparator.comparing(extractor, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        if (prefixMatches.size() >= limit) {
            return prefixMatches.stream().limit(limit).collect(Collectors.toList());
        }

        // Stage 3c: Fuzzy match using FuzzyWuzzy (Levenshtein distance)
        // Note: FuzzyWuzzy uses ToStringFunction, so we wrap the extractor in a lambda
        List<BoundExtractedResult<T>> fuzzyResults =
                FuzzySearch.extractTop(
                        query, candidates, t -> extractor.apply(t), limit * 2, threshold);

        return fuzzyResults.stream()
                .sorted(
                        Comparator.<BoundExtractedResult<T>>comparingInt(r -> r.getScore())
                                .reversed()
                                .thenComparing(
                                        result -> extractor.apply(result.getReferent()),
                                        String.CASE_INSENSITIVE_ORDER))
                .map(BoundExtractedResult::getReferent)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Stream-based search API for compatibility with {@link TabCompleteHelper}.
     *
     * <p>Converts stream to list, performs search, returns results as stream.
     *
     * @param query The search query (user input)
     * @param candidates Stream of candidate strings to search
     * @return Stream of top matching strings, ranked by relevance
     */
    public static Stream<String> searchStream(String query, Stream<String> candidates) {
        List<String> candidateList = candidates.collect(Collectors.toList());
        return search(query, candidateList).stream();
    }

    /**
     * Stream-based search with custom threshold and limit.
     *
     * @param query The search query (user input)
     * @param candidates Stream of candidate strings to search
     * @param threshold Minimum fuzzy match score (0-100, higher = stricter)
     * @param limit Maximum number of results to return
     * @return Stream of top matching strings, ranked by relevance
     */
    public static Stream<String> searchStream(
            String query, Stream<String> candidates, int threshold, int limit) {
        List<String> candidateList = candidates.collect(Collectors.toList());
        return search(query, candidateList, threshold, limit).stream();
    }
}
