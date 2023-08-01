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

        SuspensionLimit limit = getConcurrentSuspensionLimit(clusterApi);
        if (clusterApi.servicesDownIfGroupIsAllowedToBeDown() <= limit.allowedDown())
            return SuspensionReasons.nothingNoteworthy();
        if (clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown() <= limit.allowedDownPercentage())
            return SuspensionReasons.nothingNoteworthy();

        // Be a bit more cautious when removing nodes permanently
        if (!permanent) {
            Optional<SuspensionReasons> suspensionReasons = clusterApi.allServicesDown();
            if (suspensionReasons.isPresent()) {
                return suspensionReasons.get();
            }
        }

        final String message;
        if (limit.allowedDownPercentage() > 0) {
            final String numberDescription;
            final String fromDescription;
            final String toDescription;
            final String limitDescription;
            if (limit.allowedDown() > 1) {
                numberDescription = "number (percentage)";
                fromDescription = clusterApi.servicesDownOutsideGroup() + " (" + clusterApi.percentageOfServicesDownOutsideGroup() + "%)";
                toDescription = clusterApi.servicesDownIfGroupIsAllowedToBeDown() + " (" + clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown() + "%)";
                limitDescription = limit.allowedDown() + " (" + limit.allowedDownPercentage() + "%)";
            } else {
                numberDescription = "percentage";
                fromDescription = clusterApi.percentageOfServicesDownOutsideGroup() + "%";
                toDescription = clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown() + "%";
                limitDescription = limit.allowedDownPercentage() + "%";
            }

            message = "The %s of %s that are down would increase from %s to %s which is beyond the limit of %s"
                    .formatted(numberDescription, clusterApi.serviceDescription(true), fromDescription, toDescription, limitDescription);
        } else {
            message = "%d %s %s already down".formatted(clusterApi.servicesDownOutsideGroup(),
                                                        clusterApi.serviceDescription(false),
                                                        clusterApi.servicesDownOutsideGroup() == 1 ? "is" : "are");
        }

        throw new HostStateChangeDeniedException(clusterApi.getNodeGroup(),
                                                 ENOUGH_SERVICES_UP_CONSTRAINT,
                                                 message + ":" + clusterApi.downDescription());
    }

    // Non-private for testing purposes
    SuspensionLimit getConcurrentSuspensionLimit(ClusterApi clusterApi) {
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

        Optional<SuspensionLimit> override = clusterApi.clusterPolicyOverride().getSuspensionLimit();
        if (override.isPresent()) {
            return override.get();
        }

        if (clusterApi.serviceType().equals(ServiceType.CLUSTER_CONTROLLER)) {
            return SuspensionLimit.fromAllowedDown(1);
        }

        if (Set.of(ServiceType.STORAGE, ServiceType.SEARCH, ServiceType.DISTRIBUTOR, ServiceType.TRANSACTION_LOG_SERVER)
                .contains(clusterApi.serviceType())) {
            // Delegate to the cluster controller
            return SuspensionLimit.fromAllowedDownRatio(1);
        }

        if (clusterApi.serviceType().equals(ServiceType.CONTAINER)) {
            return SuspensionLimit.fromAllowedDownRatio(0.1);
        }

        if (VespaModelUtil.ADMIN_CLUSTER_ID.equals(clusterApi.clusterId())) {
            if (ServiceType.SLOBROK.equals(clusterApi.serviceType())) {
                return SuspensionLimit.fromAllowedDown(1);
            }

            return SuspensionLimit.fromAllowedDownRatio(1);
        } else if (ServiceType.METRICS_PROXY.equals(clusterApi.serviceType())) {
            return SuspensionLimit.fromAllowedDownRatio(1);
        }

        if (Set.of(ServiceType.CONFIG_SERVER, ServiceType.CONTROLLER).contains(clusterApi.serviceType())) {
            return SuspensionLimit.fromAllowedDown(1);
        }

        if (clusterApi.serviceType().equals(ServiceType.HOST_ADMIN)) {
            if (Set.of(ClusterId.CONFIG_SERVER_HOST, ClusterId.CONTROLLER_HOST).contains(clusterApi.clusterId())) {
                return SuspensionLimit.fromAllowedDown(1);
            }

            return zone.system().isCd()
                    ? SuspensionLimit.fromAllowedDownRatio(0.5)
                    : SuspensionLimit.fromAllowedDownRatio(0.2);
        }

        // The above should cover all cases, but if not we'll return a reasonable default:
        return SuspensionLimit.fromAllowedDownRatio(0.1);
    }
}
