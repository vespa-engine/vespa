// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

/**
 * A class for holding values generated or computed during deployment
 *
 * @author hmusum
 */
public class DeployData {

    /** Which user deployed */
    private final String deployedByUser;

    /** Name of application given by user */
    private final String applicationName;

    /** The absolute path to the directory holding the application */
    private final String deployedFromDir;

    /** Timestamp when a deployment was made */
    private final long deployTimestamp;

    /** Whether this is an internal redeploy, not caused by an application package change */
    private final boolean internalRedeploy;

    /* Application generation. Incremented by one each time an application is deployed. */
    private final long generation;
    private final long currentlyActiveGeneration;

    public DeployData(String deployedByUser,
                      String deployedFromDir,
                      String applicationName,
                      Long deployTimestamp,
                      boolean internalRedeploy,
                      Long generation,
                      long currentlyActiveGeneration) {
        this.deployedByUser = deployedByUser;
        this.deployedFromDir = deployedFromDir;
        this.applicationName = applicationName;
        this.deployTimestamp = deployTimestamp;
        this.internalRedeploy = internalRedeploy;
        this.generation = generation;
        this.currentlyActiveGeneration = currentlyActiveGeneration;
    }

    public String getDeployedByUser() {
        return deployedByUser;
    }

    public String getDeployedFromDir() {
        return deployedFromDir;
    }

    public long getDeployTimestamp() {
        return deployTimestamp;
    }

    public boolean isInternalRedeploy() { return internalRedeploy; }

    public long getGeneration() {
        return generation;
    }

    public long getCurrentlyActiveGeneration() {
        return currentlyActiveGeneration;
    }

    public String getApplicationName() {
        return applicationName;
    }

}
