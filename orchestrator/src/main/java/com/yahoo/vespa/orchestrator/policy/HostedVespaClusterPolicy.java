// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.orchestrator.model.ClusterApi;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;

import static com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy.ENOUGH_SERVICES_UP_CONSTRAINT;

public class HostedVespaClusterPolicy implements ClusterPolicy {
    @Override
    public void verifyGroupGoingDownIsFine(ClusterApi clusterApi)
            throws HostStateChangeDeniedException {
        if (clusterApi.noServicesOutsideGroupIsDown()) {
            return;
        }

        if (clusterApi.noServicesInGroupIsUp()) {
            return;
        }

        int percentageOfServicesAllowedToBeDown = getConcurrentSuspensionLimit(clusterApi).asPercentage();
        if (clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown() <= percentageOfServicesAllowedToBeDown) {
            return;
        }

        throw new HostStateChangeDeniedException(
                clusterApi.getNodeGroup(),
                ENOUGH_SERVICES_UP_CONSTRAINT,
                "Suspension percentage for service type " + clusterApi.serviceType()
                        + " would increase from " + clusterApi.percentageOfServicesDown()
                        + "% to " + clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown()
                        + "%, over the limit of " + percentageOfServicesAllowedToBeDown + "%."
                        + " These instances may be down: " + clusterApi.servicesDownAndNotInGroupDescription()
                        + " and these hosts are allowed to be down: "
                        + clusterApi.nodesAllowedToBeDownNotInGroupDescription());
    }

    @Override
    public void verifyGroupGoingDownPermanentlyIsFine(ClusterApi clusterApi)
            throws HostStateChangeDeniedException {
        // This policy is similar to verifyGroupGoingDownIsFine, except that services being down in the group
        // is no excuse to allow suspension (like it is for verifyGroupGoingDownIsFine), since if we grant
        // suspension in this case they will permanently be down/removed.

        if (clusterApi.noServicesOutsideGroupIsDown()) {
            return;
        }

        int percentageOfServicesAllowedToBeDown = getConcurrentSuspensionLimit(clusterApi).asPercentage();
        if (clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown() <= percentageOfServicesAllowedToBeDown) {
            return;
        }

        throw new HostStateChangeDeniedException(
                clusterApi.getNodeGroup(),
                ENOUGH_SERVICES_UP_CONSTRAINT,
                "Down percentage for service type " + clusterApi.serviceType()
                        + " would increase to " + clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown()
                        + "%, over the limit of " + percentageOfServicesAllowedToBeDown + "%."
                        + " These instances may be down: " + clusterApi.servicesDownAndNotInGroupDescription()
                        + " and these hosts are allowed to be down: "
                        + clusterApi.nodesAllowedToBeDownNotInGroupDescription());
    }

    // Non-private for testing purposes
    ConcurrentSuspensionLimitForCluster getConcurrentSuspensionLimit(ClusterApi clusterApi) {
        if (clusterApi.isStorageCluster()) {
            return ConcurrentSuspensionLimitForCluster.ONE_NODE;
        }

        if (VespaModelUtil.ADMIN_CLUSTER_ID.equals(clusterApi.clusterId())) {
            if (VespaModelUtil.SLOBROK_SERVICE_TYPE.equals(clusterApi.serviceType())) {
                return ConcurrentSuspensionLimitForCluster.ONE_NODE;
            }

            return ConcurrentSuspensionLimitForCluster.ALL_NODES;
        }

        if (clusterApi.getApplication().applicationId().equals(VespaModelUtil.ZONE_APPLICATION_ID) &&
                clusterApi.clusterId().equals(VespaModelUtil.NODE_ADMIN_CLUSTER_ID)) {
            return ConcurrentSuspensionLimitForCluster.TWENTY_PERCENT;
        }

        return ConcurrentSuspensionLimitForCluster.TEN_PERCENT;
    }
}
