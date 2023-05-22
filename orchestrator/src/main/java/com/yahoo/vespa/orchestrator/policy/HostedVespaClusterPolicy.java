// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.orchestrator.model.ClusterApi;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;

import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy.ENOUGH_SERVICES_UP_CONSTRAINT;

public class HostedVespaClusterPolicy implements ClusterPolicy {

    private final Zone zone;

    public HostedVespaClusterPolicy(FlagSource flagSource, Zone zone) {
        this.zone = zone;
    }

    @Override
    public SuspensionReasons verifyGroupGoingDownIsFine(ClusterApi clusterApi) throws HostStateChangeDeniedException {
        return verifyGroupGoingDownIsFine(clusterApi, false);
    }

    @Override
    public void verifyGroupGoingDownPermanentlyIsFine(ClusterApi clusterApi) throws HostStateChangeDeniedException {
        verifyGroupGoingDownIsFine(clusterApi, true);
    }

    private SuspensionReasons verifyGroupGoingDownIsFine(ClusterApi clusterApi, boolean permanent)
            throws HostStateChangeDeniedException {
        if (clusterApi.noServicesOutsideGroupIsDown()) {
            return SuspensionReasons.nothingNoteworthy();
        }

        int percentageOfServicesAllowedToBeDown = getConcurrentSuspensionLimit(clusterApi).asPercentage();
        if (clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown() <= percentageOfServicesAllowedToBeDown) {
            return SuspensionReasons.nothingNoteworthy();
        }

        // Be a bit more cautious when removing nodes permanently
        if (!permanent) {
            Optional<SuspensionReasons> suspensionReasons = clusterApi.allServicesDown();
            if (suspensionReasons.isPresent()) {
                return suspensionReasons.get();
            }
        }

        String message = percentageOfServicesAllowedToBeDown <= 0
                ? clusterApi.percentageOfServicesDownOutsideGroup() + "% of the " + clusterApi.serviceDescription(true)
                  + " are down or suspended already:" + clusterApi.downDescription()
                : "The percentage of downed or suspended " + clusterApi.serviceDescription(true)
                  + " would increase from " + clusterApi.percentageOfServicesDownOutsideGroup() + "% to "
                  + clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown() + "% (limit is "
                  + percentageOfServicesAllowedToBeDown + "%):" + clusterApi.downDescription();

        throw new HostStateChangeDeniedException(clusterApi.getNodeGroup(), ENOUGH_SERVICES_UP_CONSTRAINT, message);
    }

    // Non-private for testing purposes
    ConcurrentSuspensionLimitForCluster getConcurrentSuspensionLimit(ClusterApi clusterApi) {
        // Possible service clusters on a node as of 2021-01-22:
        //
        //       CLUSTER ID           SERVICE TYPE                  HEALTH       ASSOCIATION
        //    1  CCN-controllers      container-clustercontrollers  Slobrok      1, 3, or 6 in content cluster
        //    2  CCN                  distributor                   Slobrok      content cluster
        //    3  CCN                  storagenode                   Slobrok      content cluster
        //    4  CCN                  searchnode                    Slobrok      content cluster
        //    5  CCN                  transactionlogserver          not checked  content cluster
        //    6  JCCN                 container                     Slobrok      jdisc container cluster
        //    7  admin                slobrok                       not checked  1-3 in jdisc container cluster
        //    8  metrics              metricsproxy-container        Slobrok      application
        //    9  admin                logd                          not checked  application
        //   10  admin                config-sentinel               not checked  application
        //   11  admin                configproxy                   not checked  application
        //   12  admin                logforwarder                  not checked  application
        //   13  controller           controller                    state/v1     controllers
        //   14  zone-config-servers  configserver                  state/v1     config servers
        //   15  controller-host      hostadmin                     state/v1     controller hosts
        //   16  configserver-host    hostadmin                     state/v1     config server hosts
        //   17  tenant-host          hostadmin                     state/v1     tenant hosts
        //   18  proxy-host           hostadmin                     state/v1     proxy hosts
        //
        // CCN refers to the content cluster's name, as specified in services.xml.
        // JCCN refers to the jdisc container cluster's name, as specified in services.xml.
        //
        // For instance a content node will have 2-5 and 8-12 and possibly 1, while a combined
        // cluster node may have all 1-12.
        //
        // The services on a node can be categorized into these main types, ref association column above:
        //   A  content
        //   B  container
        //   C  tenant host
        //   D  config server
        //   E  config server host
        //   F  controller
        //   G  controller host
        //   H  proxy (same as B)
        //   I  proxy host

        if (clusterApi.serviceType().equals(ServiceType.CLUSTER_CONTROLLER)) {
            return ConcurrentSuspensionLimitForCluster.ONE_NODE;
        }

        if (Set.of(ServiceType.STORAGE, ServiceType.SEARCH, ServiceType.DISTRIBUTOR, ServiceType.TRANSACTION_LOG_SERVER)
                .contains(clusterApi.serviceType())) {
            // Delegate to the cluster controller
            return ConcurrentSuspensionLimitForCluster.ALL_NODES;
        }

        if (clusterApi.serviceType().equals(ServiceType.CONTAINER)) {
            return ConcurrentSuspensionLimitForCluster.TEN_PERCENT;
        }

        if (VespaModelUtil.ADMIN_CLUSTER_ID.equals(clusterApi.clusterId())) {
            if (ServiceType.SLOBROK.equals(clusterApi.serviceType())) {
                return ConcurrentSuspensionLimitForCluster.ONE_NODE;
            }

            return ConcurrentSuspensionLimitForCluster.ALL_NODES;
        } else if (ServiceType.METRICS_PROXY.equals(clusterApi.serviceType())) {
            return ConcurrentSuspensionLimitForCluster.ALL_NODES;
        }

        if (Set.of(ServiceType.CONFIG_SERVER, ServiceType.CONTROLLER).contains(clusterApi.serviceType())) {
            return ConcurrentSuspensionLimitForCluster.ONE_NODE;
        }

        if (clusterApi.serviceType().equals(ServiceType.HOST_ADMIN)) {
            if (Set.of(ClusterId.CONFIG_SERVER_HOST, ClusterId.CONTROLLER_HOST).contains(clusterApi.clusterId())) {
                return ConcurrentSuspensionLimitForCluster.ONE_NODE;
            }

            return zone.system().isCd()
                    ? ConcurrentSuspensionLimitForCluster.FIFTY_PERCENT
                    : ConcurrentSuspensionLimitForCluster.TWENTY_PERCENT;
        }

        // The above should cover all cases, but if not we'll return a reasonable default:
        return ConcurrentSuspensionLimitForCluster.TEN_PERCENT;
    }
}
