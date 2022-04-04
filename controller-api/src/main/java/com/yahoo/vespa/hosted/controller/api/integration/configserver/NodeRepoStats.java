// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import java.util.List;

/**
 * @author bratseth
 */
public class NodeRepoStats {

    private final Load load;
    private final Load activeLoad;
    private final List<ApplicationStats> applicationStats;

    public NodeRepoStats(Load load, Load activeLoad, List<ApplicationStats> applicationStats) {
        this.load = load;
        this.activeLoad = activeLoad;
        this.applicationStats = List.copyOf(applicationStats);
    }

    public Load load() { return load; }
    public Load activeLoad() { return activeLoad; }
    public List<ApplicationStats> applicationStats() { return  applicationStats; }

}
