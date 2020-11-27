// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;

/**
 * Configuration of the redundancy of a content cluster.
 *
 * @author bratseth
 */
public class Redundancy implements StorDistributionConfig.Producer, ProtonConfig.Producer {

    // This numbers are all per group as wanted numbers.
    private final int initialRedundancy ;
    private final int finalRedundancy;
    private final int readyCopies;

    private final int groups;

    /** The total number of nodes available in this cluster (assigned when this becomes known) */
    private final int totalNodes;

    public Redundancy(int initialRedundancy, int finalRedundancy, int readyCopies, int groups, int totalNodes) {
        this.initialRedundancy = initialRedundancy;
        this.finalRedundancy = finalRedundancy;
        this.readyCopies = readyCopies;
        this.groups = groups;
        this.totalNodes = totalNodes;
    }

    public int finalRedundancy() { return effectiveFinalRedundancy()/groups; }
    public int readyCopies() { return effectiveReadyCopies()/groups; }
    public int groups() { return groups; }
    public int totalNodes() { return totalNodes; }

    public int effectiveInitialRedundancy() { return Math.min(totalNodes, initialRedundancy * groups); }
    public int effectiveFinalRedundancy() { return Math.min(totalNodes, finalRedundancy * groups); }
    public int effectiveReadyCopies() { return Math.min(totalNodes, readyCopies * groups); }

    public boolean isEffectivelyGloballyDistributed() {
        return totalNodes == effectiveFinalRedundancy();
    }

    @Override
    public void getConfig(StorDistributionConfig.Builder builder) {
        builder.initial_redundancy(effectiveInitialRedundancy());
        builder.redundancy(effectiveFinalRedundancy());
        builder.ready_copies(effectiveReadyCopies());
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        ProtonConfig.Distribution.Builder distBuilder = new ProtonConfig.Distribution.Builder();
        distBuilder.redundancy(finalRedundancy());
        distBuilder.searchablecopies(readyCopies());
        builder.distribution(distBuilder);
    }

}
