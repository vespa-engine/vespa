// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import java.util.IdentityHashMap;
import java.util.Map;

import com.google.inject.Inject;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.core.VipStatusConfig;

/**
 * API for programmatically removing the container from VIP rotation.
 *
 * @author Steinar Knutsen
 */
public class VipStatus {

    private final ClustersStatus clustersStatus;

    /** If this is non-null, its value decides whether this container is in rotation */
    private Boolean inRotationOverride;

    public VipStatus() {
        this(new QrSearchersConfig(new QrSearchersConfig.Builder()),
             new VipStatusConfig(new VipStatusConfig.Builder()),
             new ClustersStatus());
    }

    public VipStatus(QrSearchersConfig dispatchers) {
        this(dispatchers, new VipStatusConfig(new VipStatusConfig.Builder()), new ClustersStatus());
    }

    public VipStatus(ClustersStatus clustersStatus) {
        this.clustersStatus = clustersStatus;
    }

    @Inject
    public VipStatus(QrSearchersConfig dispatchers, VipStatusConfig vipStatusConfig, ClustersStatus clustersStatus) {
        this.clustersStatus = clustersStatus;
        clustersStatus.setReceiveTrafficByDefault(vipStatusConfig.initiallyInRotation());
        clustersStatus.setContainerHasClusters(! dispatchers.searchcluster().isEmpty());
    }

    /**
     * Explicitly set this container in or out of rotation
     *
     * @param inRotation true to set this in rotation regardless of any clusters and of the default value,
     *                   false to set it out, and null to make this decision using the usual cluster-dependent logic
     */
    public void setInRotation(Boolean inRotation) {
        this.inRotationOverride = inRotation;
    }

    /** Note that a cluster (which influences up/down state) is up */
    public void addToRotation(Object clusterIdentifier) {
        clustersStatus.setUp(clusterIdentifier);
    }

    /** Note that a cluster (which influences up/down state) is down */
    public void removeFromRotation(Object clusterIdentifier) {
        clustersStatus.setDown(clusterIdentifier);
    }

    /** Returns whether this container should receive traffic at this time */
    public boolean isInRotation() {
        if (inRotationOverride != null) return inRotationOverride;
        return clustersStatus.containerShouldReceiveTraffic();
    }

}
