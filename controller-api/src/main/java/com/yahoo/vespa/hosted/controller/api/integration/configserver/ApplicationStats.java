// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.ApplicationId;

/**
 * @author bratseth
 */
public class ApplicationStats {

    private final ApplicationId id;
    private final Load load;
    private final double cost;
    private final double unutilizedCost;

    public  ApplicationStats(ApplicationId id, Load load, double cost, double unutilizedCost) {
        this.id = id;
        this.load = load;
        this.cost = cost;
        this.unutilizedCost = unutilizedCost;
    }

    public ApplicationId id() { return id; }
    public Load load() { return load; }
    public double cost() { return cost; }
    public double unutilizedCost() { return unutilizedCost; }

}
