// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClient;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerState;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerStateResponse;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.orchestrator.OrchestratorUtil.getHostStatusMap;
import static com.yahoo.vespa.orchestrator.OrchestratorUtil.getHostsUsedByApplicationInstance;
import static com.yahoo.vespa.orchestrator.OrchestratorUtil.getServiceClustersUsingHost;

/**
 * @author oyving
 */

public class HostedVespaPolicy implements Policy {

    public static final String ENOUGH_SERVICES_UP_CONSTRAINT = "enough-services-up";
    public static final String SET_NODE_STATE_CONSTRAINT = "controller-set-node-state";
    public static final String CLUSTER_CONTROLLER_AVAILABLE_CONSTRAINT = "controller-available";

    private static final Logger log = Logger.getLogger(HostedVespaPolicy.class.getName());

    private final ClusterControllerClientFactory clusterControllerClientFactory;

    public HostedVespaPolicy(ClusterControllerClientFactory clusterControllerClientFactory) {
        this.clusterControllerClientFactory = clusterControllerClientFactory;
    }

    private static long numContentServiceClusters(Set<? extends ServiceCluster<?>> serviceClustersOnHost) {
        return serviceClustersOnHost.stream().filter(VespaModelUtil::isContent).count();
    }


    @Override
    public void grantSuspensionRequest(ApplicationInstance<ServiceMonitorStatus> applicationInstance,
                                       HostName hostName,
                                       MutableStatusRegistry hostStatusService) throws HostStateChangeDeniedException {

        Set<ServiceCluster<ServiceMonitorStatus>> serviceClustersOnHost =
                getServiceClustersUsingHost(applicationInstance.serviceClusters(), hostName);

        Map<HostName, HostStatus> hostStatusMap = getHostStatusMap(
                getHostsUsedByApplicationInstance(applicationInstance),
                hostStatusService);

        boolean hasUpStorageInstance = false;
        for (ServiceCluster<ServiceMonitorStatus> serviceCluster : serviceClustersOnHost) {
            Set<ServiceInstance<ServiceMonitorStatus>> instancesOnThisHost;
            Set<ServiceInstance<ServiceMonitorStatus>> instancesOnOtherHosts;
            {
                Map<Boolean, Set<ServiceInstance<ServiceMonitorStatus>>> serviceInstancesByLocality =
                        serviceCluster.serviceInstances().stream()
                                .collect(
                                        Collectors.groupingBy(
                                                instance -> instance.hostName().equals(hostName),
                                                Collectors.toSet()));
                instancesOnThisHost = serviceInstancesByLocality.getOrDefault(true, Collections.emptySet());
                instancesOnOtherHosts = serviceInstancesByLocality.getOrDefault(false, Collections.emptySet());
            }

            if (VespaModelUtil.isStorage(serviceCluster)) {
                boolean thisHostHasSomeUpInstances = instancesOnThisHost.stream()
                        .map(ServiceInstance::serviceStatus)
                        .anyMatch(status -> status == ServiceMonitorStatus.UP);
                if (thisHostHasSomeUpInstances) {
                    hasUpStorageInstance = true;
                }
            }

            boolean thisHostHasOnlyDownInstances = instancesOnThisHost.stream()
                    .map(ServiceInstance::serviceStatus)
                    .allMatch(status -> status == ServiceMonitorStatus.DOWN);
            if (thisHostHasOnlyDownInstances) {
                // Suspending this host will not make a difference for this cluster, so no need to investigate further.
                continue;
            }

            Set<ServiceInstance<ServiceMonitorStatus>> possiblyDownInstancesOnOtherHosts =
                    instancesOnOtherHosts.stream()
                            .filter(instance -> effectivelyDown(instance, hostStatusMap))
                            .collect(Collectors.toSet());
            if (possiblyDownInstancesOnOtherHosts.isEmpty()) {
                // This short-circuits the percentage calculation below and ensures that we can always upgrade
                // any cluster by allowing one host at the time to be suspended, no matter what percentage of
                // the cluster that host amounts to.
                continue;
            }

            // Now calculate what the service suspension percentage will be if we suspend this host.
            int numServiceInstancesTotal = serviceCluster.serviceInstances().size();
            int numInstancesThatWillBeSuspended = union(possiblyDownInstancesOnOtherHosts, instancesOnThisHost).size();
            int percentThatWillBeSuspended = numInstancesThatWillBeSuspended * 100 / numServiceInstancesTotal;
            int suspendPercentageAllowed = ServiceClusterSuspendPolicy.getSuspendPercentageAllowed(serviceCluster);
            if (percentThatWillBeSuspended > suspendPercentageAllowed) {
                // It may seem like this may in some cases prevent upgrading, especially for small clusters (where the
                // percentage of service instances affected by suspending a single host may easily exceed the allowed
                // suspension percentage). Note that we always allow progress by allowing a single host to suspend.
                // See previous section.
                int currentSuspensionPercentage
                        = possiblyDownInstancesOnOtherHosts.size() * 100 / numServiceInstancesTotal;
                Set<HostName> otherHostsWithThisServiceCluster = instancesOnOtherHosts.stream()
                        .map(ServiceInstance::hostName)
                        .collect(Collectors.toSet());
                Set<HostName> hostsAllowedToBeDown = hostStatusMap.entrySet().stream()
                        .filter(entry -> entry.getValue() == HostStatus.ALLOWED_TO_BE_DOWN)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());
                Set<HostName> otherHostsAllowedToBeDown
                        = intersection(otherHostsWithThisServiceCluster, hostsAllowedToBeDown);
                throw new HostStateChangeDeniedException(
                        hostName,
                        ENOUGH_SERVICES_UP_CONSTRAINT,
                        serviceCluster.serviceType(),
                        "Suspension percentage would increase from " + currentSuspensionPercentage
                                + "% to " + percentThatWillBeSuspended
                                + "%, over the limit of " + suspendPercentageAllowed + "%."
                                + " These instances may be down: " + possiblyDownInstancesOnOtherHosts
                                + " and these hosts are allowed to be down: " + otherHostsAllowedToBeDown
                );
            }
        }

        if (hasUpStorageInstance) {
            // If there is an UP storage service on the host, we need to make sure
            // there's sufficient redundancy before allowing the suspension. This will
            // also avoid redistribution (which is unavoidable if the storage instance
            // is already down).
            setNodeStateInController(applicationInstance, hostName, ClusterControllerState.MAINTENANCE);
        }


        // We have "go" for suspending the services on the host,store decision.
        hostStatusService.setHostState(hostName, HostStatus.ALLOWED_TO_BE_DOWN);
        log.log(LogLevel.INFO, hostName + " is now allowed to be down (suspended)");
    }

    private static <T> Set<T> union(Set<T> setA, Set<T> setB) {
        Set<T> union = new HashSet<>(setA);
        union.addAll(setB);
        return union;
    }

    private static <T> Set<T> intersection(Set<T> setA, Set<T> setB) {
        Set<T> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        return intersection;
    }

    @Override
    public void releaseSuspensionGrant(
            ApplicationInstance<ServiceMonitorStatus> applicationInstance,
            HostName hostName,
            MutableStatusRegistry hostStatusService) throws HostStateChangeDeniedException {
        Set<ServiceCluster<ServiceMonitorStatus>> serviceClustersOnHost =
                getServiceClustersUsingHost(applicationInstance.serviceClusters(), hostName);

        // TODO: Always defer to Cluster Controller whether it's OK to resume host (if content node).
        if (numContentServiceClusters(serviceClustersOnHost) > 0) {
            setNodeStateInController(applicationInstance, hostName, ClusterControllerState.UP);
        }
        hostStatusService.setHostState(hostName, HostStatus.NO_REMARKS);
        log.log(LogLevel.INFO, hostName + " is no longer allowed to be down (resumed)");
    }

    private static boolean effectivelyDown(ServiceInstance<ServiceMonitorStatus> serviceInstance,
                                           Map<HostName, HostStatus> hostStatusMap) {
        ServiceMonitorStatus instanceStatus = serviceInstance.serviceStatus();
        HostStatus hostStatus = hostStatusMap.get(serviceInstance.hostName());
        return hostStatus == HostStatus.ALLOWED_TO_BE_DOWN || instanceStatus == ServiceMonitorStatus.DOWN;
    }

    private void setNodeStateInController(ApplicationInstance<?> application,
                                          HostName hostName,
                                          ClusterControllerState nodeState) throws HostStateChangeDeniedException {
        ClusterId contentClusterId = VespaModelUtil.getContentClusterName(application, hostName);
        List<HostName> clusterControllers = VespaModelUtil.getClusterControllerInstancesInOrder(application, contentClusterId);
        ClusterControllerClient client = clusterControllerClientFactory.createClient(
                clusterControllers,
                contentClusterId.s());
        int nodeIndex = VespaModelUtil.getStorageNodeIndex(application, hostName);

        log.log(LogLevel.DEBUG,
                "application " + application.applicationInstanceId() +
                ", host " + hostName +
                ", cluster name " + contentClusterId +
                ", node index " + nodeIndex +
                ", node state " + nodeState);

        ClusterControllerStateResponse response;
        try {
            response = client.setNodeState(nodeIndex, nodeState);
        } catch (IOException e) {
            throw new HostStateChangeDeniedException(
                    hostName,
                    CLUSTER_CONTROLLER_AVAILABLE_CONSTRAINT,
                    VespaModelUtil.CLUSTER_CONTROLLER_SERVICE_TYPE,
                    "Failed to communicate with cluster controllers " + clusterControllers + ": " + e,
                    e);
        }

        if ( ! response.wasModified) {
            throw new HostStateChangeDeniedException(
                    hostName,
                    SET_NODE_STATE_CONSTRAINT,
                    VespaModelUtil.CLUSTER_CONTROLLER_SERVICE_TYPE,
                    "Failed to set state to " + nodeState + " in controller: " + response.reason);
        }
    }

}
