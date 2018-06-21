// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Timer;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.orchestrator.config.OrchestratorConfig;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClient;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerStateResponse;
import com.yahoo.vespa.orchestrator.model.ApplicationApi;
import com.yahoo.vespa.orchestrator.model.ApplicationApiImpl;
import com.yahoo.vespa.orchestrator.model.NodeGroup;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;
import com.yahoo.vespa.orchestrator.policy.BatchHostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaClusterPolicy;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;
import com.yahoo.vespa.orchestrator.policy.Policy;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.orchestrator.status.StatusService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author oyving
 * @author smorgrav
 */
public class OrchestratorImpl implements Orchestrator {
    private static final Logger log = Logger.getLogger(OrchestratorImpl.class.getName());

    private final Policy policy;
    private final StatusService statusService;
    private final InstanceLookupService instanceLookupService;
    private final int serviceMonitorConvergenceLatencySeconds;
    private final ClusterControllerClientFactory clusterControllerClientFactory;
    private final Timer timer;

    @Inject
    public OrchestratorImpl(ClusterControllerClientFactory clusterControllerClientFactory,
                            StatusService statusService,
                            OrchestratorConfig orchestratorConfig,
                            InstanceLookupService instanceLookupService,
                            Timer timer)
    {
        this(new HostedVespaPolicy(new HostedVespaClusterPolicy(), clusterControllerClientFactory),
                clusterControllerClientFactory,
                statusService,
                instanceLookupService,
                orchestratorConfig.serviceMonitorConvergenceLatencySeconds(),
                timer);
    }

    public OrchestratorImpl(Policy policy,
                            ClusterControllerClientFactory clusterControllerClientFactory,
                            StatusService statusService,
                            InstanceLookupService instanceLookupService,
                            int serviceMonitorConvergenceLatencySeconds,
                            Timer timer)
    {
        this.policy = policy;
        this.clusterControllerClientFactory = clusterControllerClientFactory;
        this.statusService = statusService;
        this.serviceMonitorConvergenceLatencySeconds = serviceMonitorConvergenceLatencySeconds;
        this.instanceLookupService = instanceLookupService;
        this.timer = timer;
    }

    @Override
    public Host getHost(HostName hostName) throws HostNameNotFoundException {
        ApplicationInstance applicationInstance = getApplicationInstance(hostName);
        List<ServiceInstance> serviceInstances = applicationInstance
                .serviceClusters().stream()
                .flatMap(cluster -> cluster.serviceInstances().stream())
                .filter(serviceInstance -> hostName.equals(serviceInstance.hostName()))
                .collect(Collectors.toList());

        HostStatus hostStatus = getNodeStatus(applicationInstance.reference(), hostName);

        return new Host(hostName, hostStatus, applicationInstance.reference(), serviceInstances);
    }

    @Override
    public HostStatus getNodeStatus(HostName hostName) throws HostNameNotFoundException {
        return getNodeStatus(getApplicationInstance(hostName).reference(), hostName);
    }

    @Override
    public void setNodeStatus(HostName hostName, HostStatus status) throws OrchestrationException {
        ApplicationInstanceReference reference = getApplicationInstance(hostName).reference();
        try (MutableStatusRegistry statusRegistry = statusService.lockApplicationInstance_forCurrentThreadOnly(reference)) {
            statusRegistry.setHostState(hostName, status);
        }
    }

    @Override
    public void resume(HostName hostName) throws HostStateChangeDeniedException, HostNameNotFoundException {
       /*
        * When making a state transition to this state, we have to consider that if the host has been in
        * ALLOWED_TO_BE_DOWN state, services on the host may recently have been stopped (and, presumably, started).
        * Service monitoring may not have had enough time to detect that services were stopped,
        * and may therefore mistakenly report services as up, even if they still haven't initialized and
        * are not yet ready for serving. Erroneously reporting both host and services as up causes a race
        * where services on other hosts may be stopped prematurely. A delay here ensures that service
        * monitoring will have had time to catch up. Since we don't want do the delay with the lock held,
        * and the host status service's locking functionality does not support something like condition
        * variables or Object.wait(), we break out here, releasing the lock before delaying.
        */
        sleep(serviceMonitorConvergenceLatencySeconds, TimeUnit.SECONDS);

        ApplicationInstance appInstance = getApplicationInstance(hostName);

        OrchestratorContext context = new OrchestratorContext(timer);
        try (MutableStatusRegistry statusRegistry = statusService.lockApplicationInstance_forCurrentThreadOnly(
                appInstance.reference(),
                context.getOriginalTimeoutInSeconds())) {
            final HostStatus currentHostState = statusRegistry.getHostStatus(hostName);

            if (HostStatus.NO_REMARKS == currentHostState) {
                return;
            }

            ApplicationInstanceStatus appStatus = statusService.forApplicationInstance(appInstance.reference()).getApplicationInstanceStatus();
            if (appStatus == ApplicationInstanceStatus.NO_REMARKS) {
                policy.releaseSuspensionGrant(context, appInstance, hostName, statusRegistry);
            }
        }
    }

    @Override
    public void suspend(HostName hostName) throws HostStateChangeDeniedException, HostNameNotFoundException {
        ApplicationInstance appInstance = getApplicationInstance(hostName);
        NodeGroup nodeGroup = new NodeGroup(appInstance, hostName);
        suspendGroup(nodeGroup);
    }

    @Override
    public void acquirePermissionToRemove(HostName hostName) throws OrchestrationException {
        ApplicationInstance appInstance = getApplicationInstance(hostName);
        NodeGroup nodeGroup = new NodeGroup(appInstance, hostName);

        OrchestratorContext context = new OrchestratorContext(timer);
        try (MutableStatusRegistry statusRegistry = statusService.lockApplicationInstance_forCurrentThreadOnly(
                appInstance.reference(),
                context.getOriginalTimeoutInSeconds())) {
            ApplicationApi applicationApi = new ApplicationApiImpl(
                    nodeGroup,
                    statusRegistry,
                    clusterControllerClientFactory);

            policy.acquirePermissionToRemove(context, applicationApi);
        }
    }

    // Public for testing purposes
    @Override
    public void suspendGroup(NodeGroup nodeGroup) throws HostStateChangeDeniedException, HostNameNotFoundException {
        ApplicationInstanceReference applicationReference = nodeGroup.getApplicationReference();

        OrchestratorContext context = new OrchestratorContext(timer);
        try (MutableStatusRegistry hostStatusRegistry =
                     statusService.lockApplicationInstance_forCurrentThreadOnly(
                             applicationReference,
                             context.getOriginalTimeoutInSeconds())) {
            ApplicationInstanceStatus appStatus = statusService.forApplicationInstance(applicationReference).getApplicationInstanceStatus();
            if (appStatus == ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN) {
                return;
            }

            ApplicationApi applicationApi = new ApplicationApiImpl(
                    nodeGroup,
                    hostStatusRegistry,
                    clusterControllerClientFactory);
            policy.grantSuspensionRequest(context, applicationApi);
        }
    }

    @Override
    public ApplicationInstanceStatus getApplicationInstanceStatus(ApplicationId appId) throws ApplicationIdNotFoundException {
        ApplicationInstanceReference appRef = OrchestratorUtil.toApplicationInstanceReference(appId,instanceLookupService);
        return statusService.forApplicationInstance(appRef).getApplicationInstanceStatus();
    }

    @Override
    public Set<ApplicationId> getAllSuspendedApplications() {
        Set<ApplicationInstanceReference> refSet = statusService.getAllSuspendedApplications();
        return refSet.stream().map(OrchestratorUtil::toApplicationId).collect(Collectors.toSet());
    }

    @Override
    public void resume(ApplicationId appId) throws ApplicationIdNotFoundException, ApplicationStateChangeDeniedException {
        setApplicationStatus(appId, ApplicationInstanceStatus.NO_REMARKS);
    }

    @Override
    public void suspend(ApplicationId appId) throws ApplicationIdNotFoundException, ApplicationStateChangeDeniedException {
        setApplicationStatus(appId, ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN);
    }

    @Override
    public void suspendAll(HostName parentHostname, List<HostName> hostNames)
            throws BatchHostStateChangeDeniedException, BatchHostNameNotFoundException, BatchInternalErrorException {
        List<NodeGroup> nodeGroupsOrderedByApplication;
        try {
            nodeGroupsOrderedByApplication = nodeGroupsOrderedForSuspend(hostNames);
        } catch (HostNameNotFoundException e) {
            throw new BatchHostNameNotFoundException(parentHostname, hostNames, e);
        }

        for (NodeGroup nodeGroup : nodeGroupsOrderedByApplication) {
            try {
                suspendGroup(nodeGroup);
            } catch (HostStateChangeDeniedException e) {
                throw new BatchHostStateChangeDeniedException(parentHostname, nodeGroup, e);
            } catch (HostNameNotFoundException e) {
                // Should never get here since since we would have received HostNameNotFoundException earlier.
                throw new BatchHostNameNotFoundException(parentHostname, hostNames, e);
            } catch (RuntimeException e) {
                throw new BatchInternalErrorException(parentHostname, nodeGroup, e);
            }
        }
    }

    /**
     * PROBLEM
     * Take the example of 2 Docker hosts:
     *  - Docker host 1 has two nodes A1 and B1, belonging to the application with
     *    a globally unique ID A and B, respectively.
     *  - Similarly, Docker host 2 has two nodes running content nodes A2 and B2,
     *    and we assume both A1 and A2 (and B1 and B2) have services within the same service cluster.
     *
     * Suppose both Docker hosts wanting to reboot, and
     *  - Docker host 1 asks to suspend A1 and B1, while
     *  - Docker host 2 asks to suspend B2 and A2.
     *
     * The Orchestrator may allow suspend of A1 and B2, before requesting the suspension of B1 and A2.
     * None of these can be suspended (assuming max 1 suspended content node per content cluster),
     * and so both requests for suspension will fail.
     *
     * Note that it's not a deadlock - both client will fail immediately and resume both A1 and B2 before
     * responding to the client, and if host 1 asks later w/o host 2 asking at the same time,
     * it will be given permission to suspend. However if both hosts were to request in lock-step,
     * there would be starvation. And in general, it would fail requests for suspension more
     * than necessary.
     *
     * SOLUTION
     * The solution we're using is to order the hostnames by the globally unique application instance ID,
     * e.g. hosted-vespa:routing:dev:some-region:default. In the example above, it would guarantee
     * Docker host 2 would ensure ask to suspend B2 before A2. We take care of that ordering here.
     *
     * NodeGroups complicate the above picture a little:  Each A1, A2, B1, and B2 is a NodeGroup that may
     * contain several nodes (on the same Docker host).  But the argument still applies.
     */
    private List<NodeGroup> nodeGroupsOrderedForSuspend(List<HostName> hostNames) throws HostNameNotFoundException {
        Map<ApplicationInstanceReference, NodeGroup> nodeGroupMap = new HashMap<>(hostNames.size());
        for (HostName hostName : hostNames) {
            ApplicationInstance application = getApplicationInstance(hostName);

            NodeGroup nodeGroup = nodeGroupMap.get(application.reference());
            if (nodeGroup == null) {
                nodeGroup = new NodeGroup(application);
                nodeGroupMap.put(application.reference(), nodeGroup);
            }

            nodeGroup.addNode(hostName);
        }

        return nodeGroupMap.values().stream()
                .sorted(OrchestratorImpl::compareNodeGroupsForSuspend)
                .collect(Collectors.toList());
    }

    private static int compareNodeGroupsForSuspend(NodeGroup leftNodeGroup, NodeGroup rightNodeGroup) {
        ApplicationInstanceReference leftApplicationReference = leftNodeGroup.getApplicationReference();
        ApplicationInstanceReference rightApplicationReference = rightNodeGroup.getApplicationReference();

        // ApplicationInstanceReference.toString() is e.g. "hosted-vespa:routing:dev:some-region:default"
        return leftApplicationReference.asString().compareTo(rightApplicationReference.asString());
    }

    private HostStatus getNodeStatus(ApplicationInstanceReference applicationRef, HostName hostName) {
        return statusService.forApplicationInstance(applicationRef).getHostStatus(hostName);
    }

    private void setApplicationStatus(ApplicationId appId, ApplicationInstanceStatus status) 
            throws ApplicationStateChangeDeniedException, ApplicationIdNotFoundException{
        OrchestratorContext context = new OrchestratorContext(timer);
        ApplicationInstanceReference appRef = OrchestratorUtil.toApplicationInstanceReference(appId, instanceLookupService);
        try (MutableStatusRegistry statusRegistry =
                     statusService.lockApplicationInstance_forCurrentThreadOnly(
                             appRef,
                             context.getOriginalTimeoutInSeconds())) {

            // Short-circuit if already in wanted state
            if (status == statusRegistry.getApplicationInstanceStatus()) return;

            // Set content clusters for this application in maintenance on suspend
            if (status == ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN) {
                ApplicationInstance application = getApplicationInstance(appRef);

                // Mark it allowed to be down before we manipulate the clustercontroller
                OrchestratorUtil.getHostsUsedByApplicationInstance(application)
                        .forEach(h -> statusRegistry.setHostState(h, HostStatus.ALLOWED_TO_BE_DOWN));

                // If the clustercontroller throws an error the nodes will be marked as allowed to be down
                // and be set back up on next resume invocation.
                setClusterStateInController(context, application, ClusterControllerNodeState.MAINTENANCE);
            }

            statusRegistry.setApplicationInstanceStatus(status);
        }
    }

    private void setClusterStateInController(OrchestratorContext context,
                                             ApplicationInstance application,
                                             ClusterControllerNodeState state)
            throws ApplicationStateChangeDeniedException, ApplicationIdNotFoundException {
        // Get all content clusters for this application
        Set<ClusterId> contentClusterIds = application.serviceClusters().stream()
                .filter(VespaModelUtil::isContent)
                .map(ServiceCluster::clusterId)
                .collect(Collectors.toSet());

        // For all content clusters set in maintenance
        log.log(LogLevel.INFO, String.format("Setting content clusters %s for application %s to %s",
                contentClusterIds,application.applicationInstanceId(),state));
        for (ClusterId clusterId : contentClusterIds) {
            List<HostName> clusterControllers = VespaModelUtil.getClusterControllerInstancesInOrder(application, clusterId);
            ClusterControllerClient client = clusterControllerClientFactory.createClient(
                    clusterControllers,
                    clusterId.s());
            try {
                ClusterControllerStateResponse response = client.setApplicationState(context, state);
                if (!response.wasModified) {
                    String msg = String.format("Fail to set application %s, cluster name %s to cluster state %s due to: %s",
                            application.applicationInstanceId(), clusterId, state, response.reason);
                    throw new ApplicationStateChangeDeniedException(msg);
                }
            } catch (IOException e) {
                throw new ApplicationStateChangeDeniedException(e.getMessage());
            }
        }
    }

    private ApplicationInstance getApplicationInstance(HostName hostName) throws HostNameNotFoundException{
        return instanceLookupService.findInstanceByHost(hostName).orElseThrow(
                () -> new HostNameNotFoundException(hostName));
    }

    private ApplicationInstance getApplicationInstance(ApplicationInstanceReference appRef) throws ApplicationIdNotFoundException {
        return instanceLookupService.findInstanceById(appRef).orElseThrow(ApplicationIdNotFoundException::new);
    }

    private static void sleep(long time, TimeUnit timeUnit) {
        try {
            Thread.sleep(timeUnit.toMillis(time));
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpectedly interrupted", e);
        }
    }
}
