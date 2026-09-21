// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.counting;

import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.container.protect.Error;
import com.yahoo.data.access.Inspector;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingQueryParser;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.yql.MinimalQueryInserter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author sebasabe
 */
public class FilterIntersectionsSearcherTest {

    private static final List<Set<String>> CORPUS = List.of(
            Set.of("type:product", "instock:true",  "tier:premium"),
            Set.of("type:product", "instock:true",  "tier:basic"),
            Set.of("type:product", "instock:false", "tier:premium"),
            Set.of("type:product", "instock:false", "tier:premium", "brand:acme"),
            Set.of("type:service", "instock:true",  "tier:premium"));

    private static final String BASE_YQL = "select * from sources * where type contains \"product\""; // 4 out of 5

    private static final String FILTERS = """
            [{"name": "instock", "where": "instock contains \\"true\\""},
             {"name": "premium", "where": "tier contains \\"premium\\""},
             {"name": "acme",    "where": "brand contains \\"acme\\""}]""";

    // ---- counting -----------------------------------------------------------

    @Test
    void countsEveryCombinationUpToTwoDimensionsInSortedOrder() {
        Result result = search("filterIntersections.filters", FILTERS);

        assertNull(result.hits().getError());
        assertEquals(4, result.getTotalHitCount(), "main result is untouched");
        assertEquals(1, result.hits().size(), "main result still has its hits");
        assertEquals(List.of("acme", "instock", "premium", "acme&instock", "acme&premium", "instock&premium"),
                     List.copyOf(counts(result).keySet()));
        assertEquals(Map.of("acme", 1L, "instock", 2L, "premium", 3L,
                            "acme&instock", 0L, "acme&premium", 1L, "instock&premium", 1L),
                     counts(result));
    }

    @Test
    void separatorAndDimensionsAreConfigurable() {
        assertTrue(counts(search("filterIntersections.filters", FILTERS,
                                 "filterIntersections.separator", "|")).containsKey("instock|premium"));
        assertEquals(Set.of("acme", "instock", "premium"),
                     counts(search("filterIntersections.filters", FILTERS, "filterIntersections.dimensions", "1")).keySet());
       }

    @Test
    void dimensionClampsWhenNotEnoughFilters() {
        Map<String, Long> all = counts(search("filterIntersections.filters", FILTERS,
                                              "filterIntersections.dimensions", "100")); // clamps to 3 dimensions cause filter size
        assertEquals(7, all.size());
    }

    @Test
    void isANoOpWithoutFilters() {
        assertIsANoOp();
        assertIsANoOp("filterIntersections.filters", "[]");
    }

    /** Asserts that the searcher adds nothing to the result and sends nothing extra to the backend. */
    private static void assertIsANoOp(String... params) {
        var backend = new Backend();
        Result result = search(backend, params);
        assertNull(result.hits().getField("filterIntersections"), "no filterIntersections field on the result");
        assertTrue(backend.countQueries().isEmpty(), "no count queries were sent to the backend");
    }

    // ---- rejected input -----------------------------------------------------

    /** Runs once per case in {@link #rejectedRequests()}: the given query parameters must fail with the given error. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedRequests")
    void rejectsBeforeAnyBackendCall(String expectedError, List<String> queryParameters) {
        var backend = new Backend();
        Result result = search(backend, queryParameters.toArray(String[]::new));

        assertError(result, Error.INVALID_QUERY_PARAMETER, expectedError);
        assertTrue(counts(result).isEmpty(), "no counts on a rejected request");
        assertTrue(backend.queries.isEmpty(), "nothing reached the backend");
    }

    static Stream<Arguments> rejectedRequests() {
        return Stream.of(
                invalidFilters("Invalid filterIntersections.filters JSON",
                               "not json"),
                invalidFilters("must be a JSON array",
                               "{\"name\": \"a\", \"where\": \"type contains \\\"product\\\"\"}"),
                invalidFilters("must have a name",
                               "[{\"where\": \"type contains \\\"product\\\"\"}]"),
                invalidFilters("Filter 'a': empty filter expression",
                               "[{\"name\": \"a\", \"where\": \"\"}]"),
                invalidFilters("Filter 'a': duplicate filter name",
                               "[{\"name\": \"a\", \"where\": \"type contains \\\"product\\\"\"},"
                               + " {\"name\": \"a\", \"where\": \"type contains \\\"service\\\"\"}]"),
                invalidFilters("intersection cell 'broken': invalid YQL",
                               "[{\"name\": \"broken\", \"where\": \"this is not yql\"}]"),
                rejected("filterIntersections.dimensions must be at least 1, got 0",
                         "filterIntersections.filters", FILTERS, "filterIntersections.dimensions", "0"),
                rejected("filterIntersections.maxCells must be at least 1, got 0",
                         "filterIntersections.filters", FILTERS, "filterIntersections.maxCells", "0"),
                rejected("10 filters at 10 dimensions give 1023 cells, more than the default limit of 1000 (raise it with filterIntersections.maxCells)",
                         "filterIntersections.filters", filters(10), "filterIntersections.dimensions", "10"));
    }

    /** A request whose only parameter is the given filterIntersections.filters JSON, expected to fail with the given error. */
    private static Arguments invalidFilters(String expectedError, String filtersJson) {
        return rejected(expectedError, "filterIntersections.filters", filtersJson);
    }

    /** A request with the given query parameters, expected to fail with the given error. */
    private static Arguments rejected(String expectedError, String... queryParameters) {
        return Arguments.of(expectedError, List.of(queryParameters));
    }

    // ---- cell limit ---------------------------------------------------------

    @Test
    void cellLimitIsInclusive() {
        // FILTERS gives 6 cells at the default 2 dimensions
        Result atLimit = search("filterIntersections.filters", FILTERS, "filterIntersections.maxCells", "6");
        assertNull(atLimit.hits().getError());
        assertEquals(6, counts(atLimit).size());

        Result overLimit = search("filterIntersections.filters", FILTERS, "filterIntersections.maxCells", "5");
        assertError(overLimit, Error.INVALID_QUERY_PARAMETER,
                    "3 filters at 2 dimensions give 6 cells, more than the limit of 5 set by filterIntersections.maxCells");
    }

    @Test
    void countCellsIsExactAndNeverOverflows() {
        assertEquals(6, FilterIntersectionsSearcher.countCells(3, 2));
        assertEquals(1023, FilterIntersectionsSearcher.countCells(10, 10));
        assertEquals(Long.MAX_VALUE, FilterIntersectionsSearcher.countCells(500, 500), "saturates instead of overflowing");
    }

    // ---- failing cells ------------------------------------------------------

    @Test
    void failedCellKeepsTheBackendErrorCodeAndIsOmitted() {
        var backend = new Backend().failOn("brand:acme");
        Result result = search(backend, "filterIntersections.filters", FILTERS);

        assertError(result, Error.BACKEND_COMMUNICATION_ERROR, "Intersection cell 'acme': stub failure");
        assertEquals(Set.of("instock", "premium", "instock&premium"), counts(result).keySet());
        assertEquals(3, result.hits().getErrorHit().errors().size(), "one error per acme cell");
    }

    /** Runs once per case in {@link #degradedCoverage()}: a cell degraded for the given reason is reported with the given error code. */
    @ParameterizedTest(name = "degraded reason {0} reports {1}")
    @MethodSource("degradedCoverage")
    void degradedCellIsReportedAndOmitted(int degradedReason, Error expectedCode) {
        var backend = new Backend().degradeOn("instock:true", degradedReason);
        Result result = search(backend, "filterIntersections.filters", FILTERS);

        assertError(result, expectedCode, "Intersection cell 'instock': degraded coverage");
        assertEquals(Set.of("acme", "premium", "acme&premium"), counts(result).keySet());
    }

    static Stream<Arguments> degradedCoverage() {
        return Stream.of(
                Arguments.of(Coverage.DEGRADED_BY_TIMEOUT, Error.TIMEOUT),
                Arguments.of(Coverage.DEGRADED_BY_MATCH_PHASE, Error.BACKEND_COMMUNICATION_ERROR),
                Arguments.of(0 /* fewer docs than active, no stated reason */, Error.BACKEND_COMMUNICATION_ERROR));
    }

    @Test
    void slowCellIsBoundedByTheQueryTimeout() {
        var backend = new Backend().sleepOn("brand:acme", 3000);
        long start = System.nanoTime();
        Result result = search(backend, "timeout", "300ms",
                               "filterIntersections.filters", "[{\"name\": \"acme\", \"where\": \"brand contains \\\"acme\\\"\"}]");
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMillis < 2000, "returned after " + elapsedMillis + " ms");
        assertError(result, Error.TIMEOUT, "Intersection cell 'acme'");
        assertTrue(counts(result).isEmpty());
        assertEquals(4, result.getTotalHitCount(), "main result is still delivered");
    }

    // ---- fork shape ---------------------------------------------------------

    @Test
    void forksAreCountOnlyWhileTheMainQueryIsUntouched() {
        var backend = new Backend();
        search(backend, "filterIntersections.filters", FILTERS,
               "select", "all(group(tier) each(output(count())))",
               "summary", "short");

        Query main = backend.mainQuery();
        assertEquals(1, main.getSelect().getGrouping().size());
        assertEquals("short", main.getPresentation().getSummary());

        List<Query> forks = backend.countQueries();
        assertEquals(6, forks.size());
        for (Query fork : forks) {
            assertEquals(0, fork.getOffset());
            assertEquals("unranked", fork.getRanking().getProfile());
            assertFalse(fork.getRanking().getSoftTimeout().getEnable());
            assertTrue(fork.getSelect().getGrouping().isEmpty());
            assertNull(fork.getSelect().getGroupingExpressionString(), "raw 'select' must not survive on forks");
            assertNull(fork.getPresentation().getSummary());
        }
    }

    @Test
    void runsAfterYqlAndGroupParsing() {
        Set<String> after = Set.of(FilterIntersectionsSearcher.class.getAnnotation(After.class).value());
        assertTrue(after.contains(MinimalQueryInserter.EXTERNAL_YQL));
        assertTrue(after.contains(GroupingQueryParser.SELECT_PARAMETER_PARSING));
    }

    // ---- helpers ------------------------------------------------------------

    private static Result search(String... params) {
        return search(new Backend(), params);
    }

    private static Result search(Backend backend, String... params) {
        StringBuilder url = new StringBuilder("?yql=").append(URLEncoder.encode(BASE_YQL, StandardCharsets.UTF_8));
        for (int i = 0; i < params.length; i += 2)
            url.append('&').append(params[i]).append('=').append(URLEncoder.encode(params[i + 1], StandardCharsets.UTF_8));
        Chain<Searcher> chain = new Chain<>(new MinimalQueryInserter(), new GroupingQueryParser(),
                                            new FilterIntersectionsSearcher(), backend);
        return new Execution(chain, Execution.Context.createContextStub()).search(new Query(url.toString()));
    }

    private static Inspector intersections(Result result) {
        Object field = result.hits().getField("filterIntersections");
        assertNotNull(field, "no filterIntersections field on the result");
        return (Inspector) field;
    }

    private static Map<String, Long> counts(Result result) {
        Inspector buckets = intersections(result).field("buckets");
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < buckets.entryCount(); i++)
            counts.put(buckets.entry(i).field("key").asString(), buckets.entry(i).field("totalCount").asLong());
        return counts;
    }

    private static void assertError(Result result, Error expectedCode, String expectedMessage) {
        assertNotNull(result.hits().getErrorHit(), "expected an error");
        List<ErrorMessage> errors = result.hits().getErrorHit().errors().stream().toList();
        assertTrue(errors.stream().anyMatch(e -> e.getCode() == expectedCode.code
                                                 && String.valueOf(e.getDetailedMessage()).contains(expectedMessage)),
                   "no " + expectedCode + " error containing '" + expectedMessage + "' in " + errors);
    }

    /** n distinct valid filters, for cell count tests. */
    private static String filters(int n) {
        return IntStream.range(0, n)
                        .mapToObj(i -> "{\"name\": \"f" + i + "\", \"where\": \"brand contains \\\"v" + i + "\\\"\"}")
                        .reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }

    /**
     * Counts documents containing every term of the query, so intersections are computed.
     * Records each query it sees. Can fail, degrade or stall for queries containing a given term.
     */
    private static class Backend extends Searcher {

        final List<Query> queries = new ArrayList<>();
        private String failTerm, degradeTerm, sleepTerm;
        private int degradedReason;
        private long sleepMillis;

        Backend failOn(String term) { failTerm = term; return this; }
        Backend degradeOn(String term, int reason) { degradeTerm = term; degradedReason = reason; return this; }
        Backend sleepOn(String term, long millis) { sleepTerm = term; sleepMillis = millis; return this; }

        /** The user's query: the only one that asks for hits. */
        Query mainQuery() { return queries.stream().filter(q -> q.getHits() > 0).findFirst().orElseThrow(); }

        /** The forked intersection queries: they ask for a count only, so hits=0. */
        List<Query> countQueries() { return queries.stream().filter(q -> q.getHits() == 0).toList(); }

        @Override
        public Result search(Query query, Execution execution) {
            synchronized (queries) { queries.add(query); }
            Set<String> terms = new HashSet<>();
            collectTerms(query.getModel().getQueryTree().getRoot(), terms);

            if (terms.contains(sleepTerm) && query.getHits() == 0)
                try { Thread.sleep(sleepMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (terms.contains(failTerm))
                return new Result(query, ErrorMessage.createBackendCommunicationError("stub failure"));

            // simulate counting
            Result result = new Result(query);
            result.setTotalHitCount(CORPUS.stream().filter(doc -> doc.containsAll(terms)).count());
            result.setCoverage(terms.contains(degradeTerm)
                               ? new Coverage(CORPUS.size() - 2, CORPUS.size(), 1).setDegradedReason(degradedReason)
                               : new Coverage(CORPUS.size(), CORPUS.size(), 1));
            if (query.getHits() > 0) result.hits().add(new Hit("hit:1"));
            return result;
        }

        private static void collectTerms(Item item, Set<String> terms) {
            if (item instanceof WordItem word) terms.add(word.getIndexName() + ":" + word.getWord());
            else if (item instanceof CompositeItem composite) composite.items().forEach(child -> collectTerms(child, terms));
        }

    }

}
