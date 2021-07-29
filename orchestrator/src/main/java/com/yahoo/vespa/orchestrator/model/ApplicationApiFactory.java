// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.status.ApplicationLock;

import java.time.Clock;

/**
 * @author mpolden
 */
public class ApplicationApiFactory {

    private final int numberOfConfigServers;
    private final Clock clock;

    public ApplicationApiFactory(int numberOfConfigServers, Clock clock) {
        this.numberOfConfigServers = numberOfConfigServers;
        this.clock = clock;
    }

    public ApplicationApi create(NodeGroup nodeGroup,
                                 ApplicationLock lock,
                                 ClusterControllerClientFactory clusterControllerClientFactory) {
        return new ApplicationApiImpl(nodeGroup, lock, clusterControllerClientFactory,
                numberOfConfigServers, clock);
    }

}
