// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.jdisc.Metric;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * A component which keeps track of whether this container instance should receive traffic
 * and respond that it is in good health.
 *
 * This is multithread safe.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
public class VipStatus {

    private final Metric metric;

    private final ClustersStatus clustersStatus;

    private final StateMonitor healthState;

    /** If this is non-null, its value decides whether this container is in rotation */
    private Boolean rotationOverride = null;

    private final boolean initiallyInRotation;

    /**
     * Whether the container is considered live, e.g. not deadlocked or CPU starved.
     * Unlike the cluster-derived state, this is not auto-recovered by unrelated
     * addToRotation/removeFromRotation calls - only an explicit setLivenessOk() changes it.
     */
    private volatile boolean liveness = true;

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
        this(dispatchers, new VipStatusConfig.Builder().build(), clustersStatus, StateMonitor.createForTesting(), new NullMetric());
    }

    @Inject
    public VipStatus(QrSearchersConfig dispatchers,
                     VipStatusConfig vipStatusConfig,
                     ClustersStatus clustersStatus,
                     StateMonitor healthState,
                     Metric metric) {
        this.clustersStatus = clustersStatus;
        this.healthState = healthState;
        this.metric = metric;
        initiallyInRotation = vipStatusConfig.initiallyInRotation();
        clustersStatus.setClusters(dispatchers.searchcluster().stream().map(c -> c.name()).collect(Collectors.toSet()));
        updateCurrentlyInRotation();
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

    /**
     * Sets whether this container is considered live, e.g. not deadlocked or CPU starved.
     * When set to false, this container is taken out of rotation regardless of cluster status,
     * until liveness is reported ok again.
     */
    public void setLivenessOk(boolean ok) {
        synchronized (mutex) {
            liveness = ok;
            updateCurrentlyInRotation();
        }
    }

    private void updateCurrentlyInRotation() {
        synchronized (mutex) {
            if (rotationOverride != null) {
                currentlyInRotation = rotationOverride;
            } else {
                boolean clustersOk;
                if (healthState.status() == StateMonitor.Status.up) {
                    clustersOk = clustersStatus.containerShouldReceiveTraffic(ClustersStatus.Require.ONE);
                }
                else if (healthState.status() == StateMonitor.Status.initializing) {
                    clustersOk = clustersStatus.containerShouldReceiveTraffic(ClustersStatus.Require.ALL)
                                 && initiallyInRotation;
                }
                else {
                    clustersOk = clustersStatus.containerShouldReceiveTraffic(ClustersStatus.Require.ALL);
                }
                currentlyInRotation = liveness && clustersOk;
            }

            // Change to/from 'up' when appropriate but don't change 'initializing' to 'down'
            if (currentlyInRotation)
                healthState.status(StateMonitor.Status.up);
            else if (healthState.status() == StateMonitor.Status.up)
                healthState.status(StateMonitor.Status.down);

            metric.set(ContainerMetrics.IN_SERVICE.baseName(), currentlyInRotation ? 1 : 0, metric.createContext(Map.of()));
        }
    }

    /** Returns whether this container should receive traffic at this time */
    public boolean isInRotation() {
        synchronized (mutex) {
            return currentlyInRotation;
        }
    }

    private static class NullMetric implements Metric {

        @Override
        public void set(String key, Number val, Context ctx) { }

        @Override
        public void add(String key, Number val, Context ctx) { }

        @Override
        public Context createContext(Map<String, ?> properties) {
            return new NullContext();
        }

        private static class NullContext implements Context {
        }

    }

}
