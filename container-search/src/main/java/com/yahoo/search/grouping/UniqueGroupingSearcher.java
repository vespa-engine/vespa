// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import java.util.logging.Level;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.request.AllOperation;
import com.yahoo.search.grouping.request.AttributeValue;
import com.yahoo.search.grouping.request.CountAggregator;
import com.yahoo.search.grouping.request.EachOperation;
import com.yahoo.search.grouping.request.GroupingExpression;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.request.MaxAggregator;
import com.yahoo.search.grouping.request.MinAggregator;
import com.yahoo.search.grouping.request.NegFunction;
import com.yahoo.search.grouping.request.SummaryValue;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitOrderer;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Implements 'unique' using a grouping expression.
 *
 * It doesn't work for multi-level sorting.
 *
 * @author andreer
 */
@After(PhaseNames.RAW_QUERY)
@Before(PhaseNames.TRANSFORMED_QUERY)
public class UniqueGroupingSearcher extends Searcher {

    public static final CompoundName PARAM_UNIQUE = CompoundName.from("unique");
    private static final Logger log = Logger.getLogger(UniqueGroupingSearcher.class.getName());
    private static final HitOrderer NOP_ORDERER = new HitOrderer() {

        @Override
        public void order(List<Hit> hits) {
            // The order of hits is given by the grouping framework, and should not be re-ordered when we copy the hits
            // from the groups to the base HitGroup in the result.
        }
    };
    static final String LABEL_COUNT = "uniqueCount";
    static final String LABEL_GROUPS = "uniqueGroups";
    static final String LABEL_HITS = "uniqueHits";

    /**
     * Implements the deprecated "unique" api for deduplication by using grouping. We create a grouping expression on
     * the field we wish to dedup on (which must be an attribute).
     * Total hits is calculated using the new count unique groups functionality.
     */
    @Override
    public Result search(Query query, Execution execution) {
        // Determine if we should remove duplicates
        String unique = query.properties().getString(PARAM_UNIQUE);
        if (unique == null || unique.trim().isEmpty()) {
            return execution.search(query);
        }
        query.trace("Performing deduping by attribute '" + unique + "'.", true, 3);
        return dedupe(query, execution, unique);
    }

    /**
     * Until we can use the grouping pagination features in 5.1, we'll have to support offset
     * by simply requesting and discarding hit #0 up to hit #offset.
     */
    private static Result dedupe(Query query, Execution execution, String dedupField) {
        Sorting sorting = query.getRanking().getSorting();
        if (sorting != null && sorting.fieldOrders().size() > 1) {
            query.trace("Can not use grouping for deduping with multi-level sorting.", 3);
            // To support this we'd have to generate a grouping expression with as many levels
            // as there are levels in the sort spec. This is probably too slow and costly that
            // we'd ever want to actually use it (and a bit harder to implement as well).
            return execution.search(query);
        }

        int hits = query.getHits();
        int offset = query.getOffset();
        int groupingHits = hits + offset;

        GroupingRequest groupingRequest = GroupingRequest.newInstance(query);
        groupingRequest.setRootOperation(
                buildGroupingExpression(
                        dedupField,
                        groupingHits,
                        query.getPresentation().getSummary(),
                        sorting));

        query.setHits(0);
        query.setOffset(0);
        Result result = execution.search(query);

        query = result.getQuery(); // query could have changed further down in the chain
        query.setHits(hits);
        query.setOffset(offset);

        Group root = groupingRequest.getResultGroup(result);
        if (null == root) {
            String msg = "Result group not found for deduping grouping request, returning empty result.";
            query.trace(msg, 3);
            log.log(Level.WARNING, msg);
            throw new IllegalStateException("Failed to produce deduped result set.");
        }
        result.hits().remove(root.getId().toString()); // hide our tracks

        GroupList resultGroups = root.getGroupList(dedupField);
        if (resultGroups == null) {
            query.trace("Deduping grouping request returned no hits, returning empty result.", 3);
            return result;
        }

        // Make sure that .addAll() doesn't re-order the hits we copy from the grouping
        // framework. The groups are already in the order they should be.
        result.hits().setOrderer(NOP_ORDERER);
        result.hits().addAll(getRequestedHits(resultGroups, offset, hits));

        Long countField = (Long) root.getField(LABEL_COUNT);
        long count = countField != null ? countField : 0;
        result.setTotalHitCount(count);

        return result;
    }

    /**
     * Create a hit ordering clause based on the sorting spec.
     *
     * @param sortingSpec A (single level!) sorting specification
     * @return a grouping expression which produces a sortable value
     */
    private static List<GroupingExpression> createHitOrderingClause(Sorting sortingSpec) {
        List<GroupingExpression> orderingClause = new ArrayList<>();
        for (Sorting.FieldOrder fieldOrder : sortingSpec.fieldOrders()) {
            Sorting.Order sortOrder = fieldOrder.getSortOrder();
            switch (sortOrder) {
                case ASCENDING, UNDEFINED ->
                    // When we want ascending order, the hit with the smallest value should come first (and be surfaced).
                        orderingClause.add(new MinAggregator(new AttributeValue(fieldOrder.getFieldName())));
                case DESCENDING ->
                    // When we sort in descending order, the hit with the largest value should come first (and be surfaced).
                        orderingClause.add(new NegFunction(new MaxAggregator(new AttributeValue(fieldOrder.getFieldName()))));
                default -> throw new UnsupportedOperationException("Can not handle sort order " + sortOrder + ".");
            }
        }
        return orderingClause;
    }

    /**
     * Create a hit ordering clause based on the sorting spec.
     *
     * @param sortingSpec A (single level!) sorting specification
     * @return a grouping expression which produces a sortable value
     */
    private static GroupingExpression createGroupOrderingClause(Sorting sortingSpec) {
        GroupingExpression groupingClause = null;
        for (Sorting.FieldOrder fieldOrder : sortingSpec.fieldOrders()) {
            Sorting.Order sortOrder = fieldOrder.getSortOrder();
            groupingClause = switch (sortOrder) {
                case ASCENDING, UNDEFINED -> new AttributeValue(fieldOrder.getFieldName());
                case DESCENDING ->
                    // To sort descending, just take the negative. This is the most common case
                        new NegFunction(new AttributeValue(fieldOrder.getFieldName()));
                default -> throw new UnsupportedOperationException("Can not handle sort order " + sortOrder + ".");
            };
        }
        return groupingClause;
    }

    /**
     * Retrieve the actually unique hits from the grouping results.
     *
     * @param resultGroups the results of the dedup grouping expression.
     * @param offset       the requested offset. Hits before this are discarded.
     * @param hits         the requested number of hits. Hits in excess of this are discarded.
     * @return A list of the actually requested hits, sorted as by the grouping expression.
     */
    private static List<Hit> getRequestedHits(GroupList resultGroups, int offset, int hits) {
        List<Hit> receivedHits = getAllHitsFromGroupingResult(resultGroups);
        if (receivedHits.size() <= offset) {
            return Collections.emptyList(); // There weren't any hits as far out as requested.
        }
        int lastRequestedHit = Math.min(offset + hits, receivedHits.size());
        return receivedHits.subList(offset, lastRequestedHit);
    }

    /**
     * Get all the hits returned by the grouping request. This might be more or less than the user requested.
     * This method handles the results from two different types of grouping expression, depending on whether
     * sorting was used for the query or not.
     *
     * @param resultGroups The result group of the dedup grouping request
     * @return A (correctly sorted) list of all the hits returned by the grouping expression.
     */
    private static List<Hit> getAllHitsFromGroupingResult(GroupList resultGroups) {
        List<Hit> hits = new ArrayList<>(resultGroups.size());
        for (Hit groupHit : resultGroups) {
            Group group = (Group)groupHit;
            GroupList sorted = group.getGroupList(LABEL_GROUPS);
            if (sorted != null) {
                group = (Group)sorted.iterator().next();
            }
            for (Hit hit : group.getHitList(LABEL_HITS)) {
                hits.add(hit);
            }
        }
        return hits;
    }

    static GroupingOperation buildGroupingExpression(String dedupField, int groupingHits, String summaryClass,
                                                     Sorting sortSpec) {
        if (sortSpec != null) {
            return buildGroupingExpressionWithSorting(dedupField, groupingHits, summaryClass, sortSpec);
        } else {
            return buildGroupingExpressionWithRanking(dedupField, groupingHits, summaryClass);
        }
    }

    /**
     * Create the grouping expression when ranking is used for ordering
     * (which is the default for grouping expressions, so ranking is not explicitly mentioned).
     * See unit test for examples
     */
    private static GroupingOperation buildGroupingExpressionWithRanking(String dedupField, int groupingHits,
                                                                        String summaryClass) {
        return new AllOperation()
            .setGroupBy(new AttributeValue(dedupField))
            .addOutput(new CountAggregator().setLabel(LABEL_COUNT))
            .setMax(groupingHits)
            .addChild(new EachOperation()
                .setMax(1)
                .addChild(new EachOperation()
                    .setLabel(LABEL_HITS)
                    .addOutput(summaryClass == null ? new SummaryValue() : new SummaryValue(summaryClass))));
    }

    /**
     * Create the grouping expression when sorting is used for ordering
     * This grouping expression is more complicated and probably quite a bit heavier to execute.
     * See unit test for examples
     */
    private static GroupingOperation buildGroupingExpressionWithSorting(String dedupField, int groupingHits,
                                                                        String summaryClass, Sorting sortSpec) {
        return new AllOperation()
            .setGroupBy(new AttributeValue(dedupField))
            .addOutput(new CountAggregator().setLabel(LABEL_COUNT))
            .setMax(groupingHits)
            .addOrderBy(createHitOrderingClause(sortSpec))
            .addChild(new EachOperation()
                .addChild(new AllOperation()
                    .setGroupBy(createGroupOrderingClause(sortSpec))
                    .addOrderBy(createHitOrderingClause(sortSpec))
                    .setMax(1)
                    .addChild(new EachOperation()
                        .setLabel(LABEL_GROUPS)
                        .addChild(new EachOperation()
                            .setLabel(LABEL_HITS)
                            .addOutput(summaryClass == null ? new SummaryValue() : new SummaryValue(summaryClass))))));
    }

}
