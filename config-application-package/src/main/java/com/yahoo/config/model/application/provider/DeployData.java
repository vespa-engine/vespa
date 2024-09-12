// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.provision.ApplicationId;

/**
 * Data generated or computed during deployment
 *
 * @author hmusum
 */
public class DeployData {

    private final ApplicationId applicationId;

    /** Timestamp when a deployment was made */
    private final long deployTimestamp;

    /** Whether this is an internal redeploy, not caused by an application package change */
    private final boolean internalRedeploy;

    /* Application generation. Incremented by one each time an application is deployed. */
    private final long generation;
    private final long currentlyActiveGeneration;

    public DeployData(ApplicationId applicationId,
                      Long deployTimestamp,
                      boolean internalRedeploy,
                      Long generation,
                      long currentlyActiveGeneration) {
        this.applicationId = applicationId;
        this.deployTimestamp = deployTimestamp;
        this.internalRedeploy = internalRedeploy;
        this.generation = generation;
        this.currentlyActiveGeneration = currentlyActiveGeneration;
    }

    public long getDeployTimestamp() { return deployTimestamp; }

    public boolean isInternalRedeploy() { return internalRedeploy; }

    public long getGeneration() { return generation; }

    public long getCurrentlyActiveGeneration() { return currentlyActiveGeneration; }

    public ApplicationId getApplicationId() { return applicationId; }

}
