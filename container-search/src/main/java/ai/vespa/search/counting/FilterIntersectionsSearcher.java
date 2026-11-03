// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.counting;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingQueryParser;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.searchchain.AsyncExecution;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.FutureResult;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.search.yql.MinimalQueryInserter;
import com.yahoo.search.yql.YqlParser;
import com.yahoo.data.access.simple.Value;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Counts the documents matching the main query intersected with every combination of
 * up to {@code dimensions} named filters, and attaches the counts as a top-level
 * {@code filterIntersections} field.
 *
 * Each cell is run as a concurrent fork of the main query which asks for zero hits and
 * keeps the main query's rank profile, so rank profile inputs such as nearestNeighbor
 * query tensors stay valid. Filters are parsed on their own and combined with the main
 * query tree using AND.
 *
 * @author sebasabe
 */
@Provides("FilterIntersections")
@After({ MinimalQueryInserter.EXTERNAL_YQL, GroupingQueryParser.SELECT_PARAMETER_PARSING })
@Before(PhaseNames.TRANSFORMED_QUERY)
public class FilterIntersectionsSearcher extends Searcher {

    /** JSON array of named YQL filters: [{"name": ..., "where": ...}, ...] */
    public static final CompoundName PARAM_FILTERS = CompoundName.from("filterIntersections.filters");

    /** Separator joining filter names into bucket keys. Default {@code &}. */
    public static final CompoundName PARAM_SEPARATOR = CompoundName.from("filterIntersections.separator");

    /** Maximum number of filters combined per cell (the intersection depth). Default 2. */
    public static final CompoundName PARAM_DIMENSIONS = CompoundName.from("filterIntersections.dimensions");

    /** Maximum number of cells, and hence forked queries, allowed per request. Default {@value #DEFAULT_MAX_CELLS}. */
    public static final CompoundName PARAM_MAX_CELLS = CompoundName.from("filterIntersections.maxCells");

    public static final int DEFAULT_MAX_CELLS = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Result search(Query query, Execution execution) {
        String raw = query.properties().getString(PARAM_FILTERS);
        if (raw == null || raw.isBlank()) return execution.search(query);

        String separator = query.properties().getString(PARAM_SEPARATOR, "&");
        int dimensions = query.properties().getInteger(PARAM_DIMENSIONS, 2);
        Integer configuredMaxCells = query.properties().getInteger(PARAM_MAX_CELLS);
        int maxCells = configuredMaxCells != null ? configuredMaxCells : DEFAULT_MAX_CELLS;

        List<Filter> filters;
        List<Cell> cells;
        List<Query> forks;
        try {
            if (dimensions < 1)
                throw new IllegalArgumentException(PARAM_DIMENSIONS + " must be at least 1, got " + dimensions);
            if (maxCells < 1)
                throw new IllegalArgumentException(PARAM_MAX_CELLS + " must be at least 1, got " + maxCells);
            filters = readFilters(raw);
            if (filters.isEmpty()) return execution.search(query);
            long cellCount = countCells(filters.size(), dimensions);
            if (cellCount > maxCells)
                throw new IllegalArgumentException(filters.size() + " filters at " + dimensions + " dimensions give "
                                                   + (cellCount == Long.MAX_VALUE ? "too many cells to count" : cellCount + " cells")
                                                   + ", more than the "
                                                   + (configuredMaxCells == null
                                                      ? "default limit of " + maxCells + " (raise it with " + PARAM_MAX_CELLS + ")"
                                                      : "limit of " + maxCells + " set by " + PARAM_MAX_CELLS));
            cells = enumerateCells(filters, dimensions);
            YqlParser parser = new YqlParser(ParserEnvironment.fromExecutionContext(execution.context()));
            parser.setQueryParser(false);
            Map<Filter, Item> parsedFilters = parseFilters(filters, query, parser);
            forks = new ArrayList<>(cells.size());
            for (Cell cell : cells)
                forks.add(fork(query, cell, parsedFilters));
        } catch (IllegalArgumentException e) {
            Result error = new Result(query, ErrorMessage.createInvalidQueryParameter(e.getMessage()));
            attachIntersections(error, new Value.ArrayValue());
            return error;
        }
        query.trace("Computing filter intersections over " + filters.size() + " filters, "
                    + dimensions + " dimensions (" + cells.size() + " cells).", true, 3);

        List<FutureResult> futures = new ArrayList<>(forks.size());
        for (Query fork : forks)
            futures.add(new AsyncExecution(execution).search(fork));

        Result result = execution.search(query);

        Value.ArrayValue buckets = new Value.ArrayValue();
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            String key = String.join(separator, cell.names);
            FutureResult future = futures.get(i);
            Result forkResult = future.get(Math.max(0, query.getTimeLeft()), TimeUnit.MILLISECONDS);
            if ( ! future.isDone()) future.cancel(true);

            ErrorMessage forkError = forkResult.hits().getError();
            if (forkError != null) {
                String detail = forkError.getDetailedMessage() != null ? forkError.getDetailedMessage()
                                                                       : forkError.getMessage();
                result.hits().addError(new ErrorMessage(forkError.getCode(), forkError.getMessage(),
                                                        "Intersection cell '" + key + "': " + detail));
                continue;
            }

            Coverage coverage = forkResult.getCoverage(false);
            if (coverage != null && (coverage.isDegraded() || !coverage.getFull())) {
                String message = "Intersection cell '" + key + "': degraded coverage ("
                                 + coverage.getResultPercentage() + "%), count not exact";
                boolean timedOut = coverage.isDegradedByTimeout() || coverage.isDegradedByAdapativeTimeout()
                                   || coverage.isDegradedByAnnTimeout();
                result.hits().addError(timedOut ? ErrorMessage.createTimeout(message)
                                                : ErrorMessage.createBackendCommunicationError(message));
                continue;
            }

            Value.ArrayValue names = new Value.ArrayValue();
            cell.names.forEach(names::add);
            buckets.add(new Value.ObjectValue()
                    .put("key", key)
                    .put("names", names)
                    .put("totalCount", forkResult.getTotalHitCount()));
        }

        attachIntersections(result, buckets);
        return result;
    }

    private static Map<Filter, Item> parseFilters(List<Filter> filters, Query query, YqlParser parser) {
        Map<Filter, Item> parsed = new HashMap<>();
        for (Filter filter : filters) {
            try {
                parser.setUserQuery(query); // the parser clears this after each parse
                QueryTree tree = parser.parse(Parsable.fromQueryModel(query.getModel())
                                                      .setQuery("select * from sources * where " + filter.where));
                parsed.put(filter, tree.getRoot());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Filter '" + filter.name + "': invalid YQL: " + e.getMessage(), e);
            }
        }
        return parsed;
    }

    private static Query fork(Query query, Cell cell, Map<Filter, Item> parsedFilters) {
        Query fork = query.clone();
        fork.setHits(0);
        fork.setOffset(0);
        fork.getRanking().getSoftTimeout().setEnable(false);
        fork.getPresentation().setSummary(null);
        // Grouping is already parsed at this point
        fork.getSelect().getGrouping().clear();
        fork.getSelect().setGroupingExpressionString(null);

        AndItem intersection = new AndItem();
        intersection.addItem(fork.getModel().getQueryTree().getRoot());
        for (Filter filter : cell.filters)
            intersection.addItem(parsedFilters.get(filter).clone());
        fork.getModel().getQueryTree().setRoot(intersection);
        return fork;
    }

    /** The number of non-empty subsets of at most {@code dimensions} filters, saturating at {@link Long#MAX_VALUE}. */
    static long countCells(int filters, int dimensions) {
        long total = 0;
        long combinations = 1; // C(filters, k)
        int maxSize = Math.min(dimensions, filters);
        for (int k = 1; k <= maxSize; k++) {
            if (combinations > Long.MAX_VALUE / (filters - k + 1)) return Long.MAX_VALUE;
            combinations = combinations * (filters - k + 1) / k;
            if (total > Long.MAX_VALUE - combinations) return Long.MAX_VALUE;
            total += combinations;
        }
        return total;
    }

    private static List<Cell> enumerateCells(List<Filter> filters, int dimensions) {
        List<Cell> cells = new ArrayList<>();
        int maxSize = Math.min(dimensions, filters.size());
        for (int size = 1; size <= maxSize; size++)
            addCombinations(filters, size, 0, new ArrayList<>(), cells);
        return cells;
    }

    private static void addCombinations(List<Filter> filters, int size, int from,
                                        List<Filter> current, List<Cell> cells) {
        if (current.size() == size) {
            cells.add(Cell.of(current));
            return;
        }
        int remaining = size - current.size();
        for (int i = from; i + remaining <= filters.size(); i++) {
            current.add(filters.get(i));
            addCombinations(filters, size, i + 1, current, cells);
            current.remove(current.size() - 1);
        }
    }

    private static void attachIntersections(Result result, Value.ArrayValue buckets) {
        result.hits().setField("filterIntersections", new Value.ObjectValue().put("buckets", buckets));
    }

    // ---- input model ------------------------------------------------------

    private record Filter(String name, String where) {}

    private record Cell(List<String> names, List<Filter> filters) {
        static Cell of(List<Filter> filters) {
            return new Cell(filters.stream().map(Filter::name).toList(), List.copyOf(filters));
        }
    }

    private static List<Filter> readFilters(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + PARAM_FILTERS + " JSON: " + e.getMessage(), e);
        }
        if (!root.isArray())
            throw new IllegalArgumentException(PARAM_FILTERS + " must be a JSON array of {name, where} objects");
        List<Filter> filters = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonNode f : root) {
            String name = f.path("name").asText();
            String where = f.path("where").asText();
            if (name.isBlank())
                throw new IllegalArgumentException("Every filter in " + PARAM_FILTERS + " must have a name");
            if (where.isBlank())
                throw new IllegalArgumentException("Filter '" + name + "': empty filter expression");
            if (!names.add(name))
                throw new IllegalArgumentException("Filter '" + name + "': duplicate filter name");
            filters.add(new Filter(name, where));
        }
        filters.sort(Comparator.comparing(Filter::name, String.CASE_INSENSITIVE_ORDER));
        return filters;
    }

}
