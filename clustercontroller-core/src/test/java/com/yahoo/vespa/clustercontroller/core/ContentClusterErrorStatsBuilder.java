// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builder used for testing only.
 */
public class ContentClusterErrorStatsBuilder {

    private final Map<Integer, ContentNodeErrorStats> stats;

    public ContentClusterErrorStatsBuilder(Set<Integer> contentNodeIndexes) {
        stats = contentNodeIndexes.stream().collect(Collectors.toMap(i -> i, ContentNodeErrorStats::new));
    }

    public ContentClusterErrorStatsBuilder add(ContentNodeErrorStatsBuilder builder) {
        stats.put(builder.contentNodeIndex(), builder.build());
        return this;
    }

    public ContentClusterErrorStats build() {
        return new ContentClusterErrorStats(stats);
    }

}
