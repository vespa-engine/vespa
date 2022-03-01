// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.searchlib.aggregation.Grouping;

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
    private DocsumDefinitionSet documentDefinitions = null;
    private int groupingHitsMerged = 0;

    void mergeWith(GroupingListHit result) {
        if (groupingHitsMerged == 0) documentDefinitions = result.getDocsumDefinitionSet();
        ++groupingHitsMerged;
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
                String.format("Creating aggregated hit containing %d groupings from %d hits",
                        groupings.size(), groupingHitsMerged));
        groupings.values().forEach(Grouping::postMerge);
        return Optional.of(new GroupingListHit(List.copyOf(groupings.values()), documentDefinitions));
    }

}
