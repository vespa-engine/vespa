// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import java.util.List;

/**
 * @author bratseth
 */
public class NodeRepoStats {

    private final double totalCost;
    private final double totalAllocatedCost;
    private final Load load;
    private final Load activeLoad;
    private final List<ApplicationStats> applicationStats;

    public NodeRepoStats(double totalCost, double totalAllocatedCost,
                         Load load, Load activeLoad, List<ApplicationStats> applicationStats) {
        this.totalCost = totalCost;
        this.totalAllocatedCost = totalAllocatedCost;
        this.load = load;
        this.activeLoad = activeLoad;
        this.applicationStats = List.copyOf(applicationStats);
    }

    public double totalCost() { return totalCost; }
    public double totalAllocatedCost() { return totalAllocatedCost; }
    public Load load() { return load; }
    public Load activeLoad() { return activeLoad; }
    public List<ApplicationStats> applicationStats() { return applicationStats; }

}
