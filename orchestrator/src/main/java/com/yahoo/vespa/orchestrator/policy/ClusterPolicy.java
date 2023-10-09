// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.orchestrator.model.ClusterApi;

public interface ClusterPolicy {
    /**
     * There's an implicit group of nodes known to clusterApi.  This method answers whether
     * it would be fine, just looking at this cluster (and disregarding Cluster Controller/storage
     * which is handled separately), to allow all services on all the nodes in the group to go down.
     *
     * @return notable reasons for why this group is fine with going down.
     */
    SuspensionReasons verifyGroupGoingDownIsFine(ClusterApi clusterApi) throws HostStateChangeDeniedException;

    /**
     * There's an implicit group of nodes known to clusterApi.  This method answers whether
     * it would be fine, just looking at this cluster, to allow all services on all the nodes
     * in the group to go down PERMANENTLY (removed from application).
     */
    void verifyGroupGoingDownPermanentlyIsFine(ClusterApi clusterApi) throws HostStateChangeDeniedException;
}
