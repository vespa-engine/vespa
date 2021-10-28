// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.policy.HostedVespaOrchestration;
import com.yahoo.vespa.orchestrator.policy.OrchestrationParams;
import com.yahoo.vespa.orchestrator.status.ApplicationLock;

import java.time.Clock;

/**
 * @author mpolden
 */
public class ApplicationApiFactory {

    private final OrchestrationParams orchestrationParams;
    private final Clock clock;

    public ApplicationApiFactory(int numberOfConfigServers, int numProxies, Clock clock) {
        this.orchestrationParams = HostedVespaOrchestration.create(numberOfConfigServers, numProxies);
        this.clock = clock;
    }

    public ApplicationApi create(NodeGroup nodeGroup,
                                 ApplicationLock lock,
                                 ClusterControllerClientFactory clusterControllerClientFactory) {
        ApplicationId applicationId = OrchestratorUtil.toApplicationId(lock.getApplicationInstanceReference());
        return new ApplicationApiImpl(nodeGroup, lock, clusterControllerClientFactory,
                                      orchestrationParams.getApplicationParams(applicationId), clock);
    }

}
