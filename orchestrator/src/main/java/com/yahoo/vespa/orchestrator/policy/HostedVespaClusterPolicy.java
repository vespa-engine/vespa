// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.orchestrator.VespaModelUtil;

import static com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy.ENOUGH_SERVICES_UP_CONSTRAINT;

public class HostedVespaClusterPolicy implements ClusterPolicy {

    public void verifyGroupGoingDownIsFineForCluster(ClusterApi clusterApi)
            throws HostStateChangeDeniedException {
        if (clusterApi.noServicesOutsideGroupIsDown()) {
            return;
        }

        if (clusterApi.noServicesInGroupIsUp()) {
            return;
        }

        int percentageOfServicesAllowedToBeDown = getConcurrentSuspensionLimitForCluster(clusterApi).asPercentage();
        if (clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown() <= percentageOfServicesAllowedToBeDown) {
            return;
        }

        throw new HostStateChangeDeniedException(
                clusterApi.getNodeGroup(),
                ENOUGH_SERVICES_UP_CONSTRAINT,
                clusterApi.serviceType(),
                "Suspension percentage would increase from " + clusterApi.percentageOfServicesDown()
                        + "% to " + clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown()
                        + "%, over the limit of " + percentageOfServicesAllowedToBeDown + "%."
                        + " These instances may be down: " + clusterApi.servicesDownAndNotInGroup()
                        + " and these hosts are allowed to be down: " + clusterApi.nodesAllowedToBeDownNotInGroup());
    }

    static ConcurrentSuspensionLimitForCluster getConcurrentSuspensionLimitForCluster(ClusterApi clusterApi) {
        if (VespaModelUtil.ADMIN_CLUSTER_ID.equals(clusterApi.clusterId())) {
            if (VespaModelUtil.SLOBROK_SERVICE_TYPE.equals(clusterApi.serviceType())) {
                return ConcurrentSuspensionLimitForCluster.ONE_NODE;
            }

            return ConcurrentSuspensionLimitForCluster.ALL_NODES;
        }

        if (clusterApi.isStorageCluster()) {
            return ConcurrentSuspensionLimitForCluster.ONE_NODE;
        }

        return ConcurrentSuspensionLimitForCluster.TEN_PERCENT;
    }
}
