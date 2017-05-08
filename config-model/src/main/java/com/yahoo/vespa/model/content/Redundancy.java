// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;

/**
 * Configuration of the redundancy of a content cluster.
 *
 * @author bratseth
 */
public class Redundancy implements StorDistributionConfig.Producer, ProtonConfig.Producer {

    private final int redundancy;
    private final int readyCopies;

    private int implicitGroups = 1;
    private int explicitGroups = 1;

    /** The total number of nodes available in this cluster (assigned when this becomes known) */
    private int totalNodes = 0;

    public Redundancy(int redundancy, int readyCopies) {
        this.redundancy = redundancy;
        this.readyCopies = readyCopies;
    }

    /**
     * Set the total number of nodes available in this cluster.
     * This impacts the effective redundancy in the case where there are fewer nodes available than
     * the requested redundancy.
     */
    public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }

    /**
     * Sets the number of groups resulting from implicit setup (groups attribute)
     * in this cluster. With implicit groups the redundancy settings are taken to be
     * <i>per group</i> and are multiplied by this number to get the effective <i>total</i>
     * values returned in the config.
     */
    public void setImplicitGroups(int implicitGroups) { this.implicitGroups = implicitGroups; }
    public void setExplicitGroups(int explicitGroups) { this.explicitGroups = explicitGroups; }

    public int redundancy() { return redundancy; }
    public int readyCopies() { return readyCopies; }
    public int totalNodes() {
        return totalNodes;
    }

    public int effectiveRedundancy() { return Math.min(totalNodes, redundancy * implicitGroups); }
    public int effectiveReadyCopies() { return Math.min(totalNodes, readyCopies * implicitGroups); }

    public boolean isEffectivelyGloballyDistributed() {
        return totalNodes == effectiveRedundancy();
    }

    @Override
    public void getConfig(StorDistributionConfig.Builder builder) {
        builder.initial_redundancy(effectiveRedundancy());
        builder.redundancy(effectiveRedundancy());
        builder.ready_copies(effectiveReadyCopies());
    }
    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        ProtonConfig.Distribution.Builder distBuilder = new ProtonConfig.Distribution.Builder();
        distBuilder.redundancy(redundancy / explicitGroups);
        distBuilder.searchablecopies(readyCopies / (explicitGroups * implicitGroups));
        builder.distribution(distBuilder);
    }
}
