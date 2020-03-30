// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.google.inject.Inject;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.container.jdisc.state.StateMonitor;

/**
 * A component which keeps track of whether or not this container instance should receive traffic
 * and respond that it is in good health.
 *
 * This is multithread safe.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
public class VipStatus {

    private final ClustersStatus clustersStatus;

    private final StateMonitor healthState;

    /** If this is non-null, its value decides whether this container is in rotation */
    private Boolean rotationOverride = null;

    private final boolean initiallyInRotation;

    /** The current state of this */
    private boolean currentlyInRotation;

    private final Object mutex = new Object();

    /** For testing */
    public VipStatus() {
        this(new ClustersStatus());
    }

    /** For testing */
    public VipStatus(QrSearchersConfig dispatchers) {
        this(dispatchers, new ClustersStatus());
    }

    /** For testing */
    public VipStatus(ClustersStatus clustersStatus) {
        this(new QrSearchersConfig.Builder().build(), clustersStatus);
    }

    /** For testing */
    public VipStatus(QrSearchersConfig dispatchers, ClustersStatus clustersStatus) {
        this(dispatchers, new VipStatusConfig.Builder().build(), clustersStatus, new StateMonitor());
    }

    @Inject
    public VipStatus(QrSearchersConfig dispatchers,
                     VipStatusConfig vipStatusConfig,
                     ClustersStatus clustersStatus,
                     StateMonitor healthState) {
        this.clustersStatus = clustersStatus;
        this.healthState = healthState;
        initiallyInRotation = vipStatusConfig.initiallyInRotation();
        healthState.status(StateMonitor.Status.initializing);
        clustersStatus.setContainerHasClusters(! dispatchers.searchcluster().isEmpty());
        updateCurrentlyInRotation();
    }

    @Deprecated // TODO: Remove on Vespa 8
    @Inject
    public VipStatus(QrSearchersConfig dispatchers, ClustersStatus clustersStatus, StateMonitor healthState) {
        this(dispatchers, new VipStatusConfig.Builder().build(), clustersStatus, healthState);
    }

    @Deprecated // TODO: Remove on Vespa 8
    public VipStatus(QrSearchersConfig dispatchers, VipStatusConfig ignored, ClustersStatus clustersStatus) {
        this(dispatchers, clustersStatus);
    }

    /**
     * Explicitly set this container in or out of rotation
     *
     * @param inRotation true to set this in rotation regardless of any clusters and of the default value,
     *                   false to set it out, and null to make this decision using the usual cluster-dependent logic
     */
    public void setInRotation(Boolean inRotation) {
        synchronized (mutex) {
            rotationOverride = inRotation;
            updateCurrentlyInRotation();
        }
    }

    /** Note that a cluster (which influences up/down state) is up */
    public void addToRotation(String clusterIdentifier) {
        clustersStatus.setUp(clusterIdentifier);
        updateCurrentlyInRotation();
    }

    /** Note that a cluster (which influences up/down state) is down */
    public void removeFromRotation(String clusterIdentifier) {
        clustersStatus.setDown(clusterIdentifier);
        updateCurrentlyInRotation();
    }

    /** @deprecated use addToRotation(String) instead  */
    @Deprecated // TODO: Remove on Vespa 8
    public void addToRotation(Object clusterIdentifier) {
        addToRotation((String) clusterIdentifier);
    }

    /** @deprecated use removeFromRotation(String) instead  */
    @Deprecated // TODO: Remove on Vespa 8
    public void removeFromRotation(Object clusterIdentifier) {
        removeFromRotation((String) clusterIdentifier);
    }

    private void updateCurrentlyInRotation() {
        synchronized (mutex) {
            if (rotationOverride != null) {
                currentlyInRotation = rotationOverride;
            } else {
                if (healthState.status() == StateMonitor.Status.up) {
                    currentlyInRotation = clustersStatus.containerShouldReceiveTraffic(ClustersStatus.Require.ONE);
                }
                else if (healthState.status() == StateMonitor.Status.initializing) {
                    currentlyInRotation = clustersStatus.containerShouldReceiveTraffic(ClustersStatus.Require.ALL)
                                          && initiallyInRotation;
                }
                else {
                    currentlyInRotation = clustersStatus.containerShouldReceiveTraffic(ClustersStatus.Require.ALL);
                }
            }

            // Change to/from 'up' when appropriate but don't change 'initializing' to 'down'
            if (currentlyInRotation)
                healthState.status(StateMonitor.Status.up);
            else if (healthState.status() == StateMonitor.Status.up)
                healthState.status(StateMonitor.Status.down);
        }
    }

    /** Returns whether this container should receive traffic at this time */
    public boolean isInRotation() {
        synchronized (mutex) {
            return currentlyInRotation;
        }
    }

}
