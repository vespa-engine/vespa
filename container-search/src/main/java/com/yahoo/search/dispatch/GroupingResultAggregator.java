// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.search.Query;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.searchlib.aggregation.Hit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Incrementally merges underlying {@link Grouping} instances from {@link GroupingListHit} hits.
 *
 * @author bjorncs
 */
class GroupingResultAggregator {
    private static final Logger log = Logger.getLogger(GroupingResultAggregator.class.getName());

    private final Map<Integer, Grouping> groupings = new LinkedHashMap<>();
    private DocsumDefinitionSet docsumDefinitions = null;
    private Query query = null;
    private int groupingHitsMerged = 0;

    void mergeWith(GroupingListHit result) {
        ++groupingHitsMerged;
        if (docsumDefinitions == null) docsumDefinitions = result.getDocsumDefinitionSet();
        if (query == null) query = result.getQuery();
        log.log(Level.FINE, () ->
                String.format("Merging hit #%d having %d groupings",
                        groupingHitsMerged, result.getGroupingList().size()));
        for (Grouping grouping : result.getGroupingList()) {
            groupings.merge(grouping.getId(), grouping, (existingGrouping, newGrouping) -> {
                existingGrouping.merge(newGrouping);
                return existingGrouping;
            });
        }
    }

    Optional<GroupingListHit> toAggregatedHit() {
        if (groupingHitsMerged == 0) return Optional.empty();
        log.log(Level.FINE, () ->
                String.format("Creating aggregated hit containing %d groupings from %d hits with docsums '%s' and %s",
                        groupings.size(), groupingHitsMerged, docsumDefinitions, query));
        GroupingListHit groupingHit = new GroupingListHit(List.copyOf(groupings.values()), docsumDefinitions);
        groupingHit.setQuery(query);
        groupingHit.getGroupingList().forEach(g -> {
            g.select(o -> o instanceof Hit, o -> ((Hit)o).setContext(groupingHit));
            g.postMerge();
        });
        return Optional.of(groupingHit);
    }

}
