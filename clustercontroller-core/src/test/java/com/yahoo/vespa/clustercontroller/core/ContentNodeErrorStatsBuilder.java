// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder used for testing only.
 */
public class ContentNodeErrorStatsBuilder {

    private final int contentNodeIndex;
    private final Map<Integer, ContentNodeErrorStats.DistributorErrorStats> statsFromDistributors = new HashMap<>();

    public ContentNodeErrorStatsBuilder(int contentNodeIndex) {
        this.contentNodeIndex = contentNodeIndex;
    }

    public int contentNodeIndex() { return this.contentNodeIndex; }

    public ContentNodeErrorStatsBuilder addNetworkErrors(int fromDistributor, int responsesTotal, int networkErrors) {
        var stats = new ContentNodeErrorStats.DistributorErrorStats(responsesTotal, networkErrors);
        statsFromDistributors.put(fromDistributor, stats);
        return this;
    }

    public ContentNodeErrorStats build() {
        return new ContentNodeErrorStats(contentNodeIndex, statsFromDistributors);
    }

}
