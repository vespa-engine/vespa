// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.orchestrator.config.OrchestratorConfig;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClient;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState;
import com.yahoo.vespa.orchestrator.model.ApplicationApi;
import com.yahoo.vespa.orchestrator.model.ApplicationApiFactory;
import com.yahoo.vespa.orchestrator.model.ContentService;
import com.yahoo.vespa.orchestrator.model.NodeGroup;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;
import com.yahoo.vespa.orchestrator.policy.BatchHostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaClusterPolicy;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;
import com.yahoo.vespa.orchestrator.policy.Policy;
import com.yahoo.vespa.orchestrator.policy.SuspensionReasons;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.ApplicationLock;
import com.yahoo.vespa.orchestrator.status.HostInfo;
import com.yahoo.vespa.orchestrator.status.HostInfos;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.StatusService;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState.MAINTENANCE;
import static java.util.stream.Collectors.toSet;

/**
 * @author oyving
 * @author smorgrav
 */
public class OrchestratorImpl implements Orchestrator {

    private static final Logger log = Logger.getLogger(OrchestratorImpl.class.getName());

    private final Policy policy;
    private final StatusService statusService;
    private final ServiceMonitor serviceMonitor;
    private final int serviceMonitorConvergenceLatencySeconds;
    private final ClusterControllerClientFactory clusterControllerClientFactory;
    private final Clock clock;
    private final ApplicationApiFactory applicationApiFactory;

    @Inject
    public OrchestratorImpl(OrchestratorConfig orchestratorConfig,
                            ConfigserverConfig configServerConfig,
                            ClusterControllerClientFactory clusterControllerClientFactory,
                            StatusService statusService,
                            ServiceMonitor serviceMonitor,
                            FlagSource flagSource,
                            Zone zone)
    {
        this(clusterControllerClientFactory,
             statusService,
             serviceMonitor,
             flagSource,
             zone,
             Clock.systemUTC(),
             new ApplicationApiFactory(configServerConfig.zookeeperserver().size(),
                                       orchestratorConfig.numProxies(),
                                       Clock.systemUTC()),
             orchestratorConfig.serviceMonitorConvergenceLatencySeconds());
    }

    private OrchestratorImpl(ClusterControllerClientFactory clusterControllerClientFactory,
                             StatusService statusService,
                             ServiceMonitor serviceMonitor,
                             FlagSource flagSource,
                             Zone zone,
                             Clock clock,
                             ApplicationApiFactory applicationApiFactory,
                             int serviceMonitorConvergenceLatencySeconds)
    {
        this(new HostedVespaPolicy(new HostedVespaClusterPolicy(flagSource, zone),
                                   clusterControllerClientFactory,
                                   applicationApiFactory,
                                   flagSource),
             clusterControllerClientFactory,
             statusService,
             serviceMonitor,
             serviceMonitorConvergenceLatencySeconds,
             clock,
             applicationApiFactory,
             flagSource);
    }

    public OrchestratorImpl(Policy policy,
                            ClusterControllerClientFactory clusterControllerClientFactory,
                            StatusService statusService,
                            ServiceMonitor serviceMonitor,
                            int serviceMonitorConvergenceLatencySeconds,
                            Clock clock,
                            ApplicationApiFactory applicationApiFactory,
                            FlagSource flagSource)
    {
        this.policy = policy;
        this.clusterControllerClientFactory = clusterControllerClientFactory;
        this.statusService = statusService;
        this.serviceMonitorConvergenceLatencySeconds = serviceMonitorConvergenceLatencySeconds;
        this.serviceMonitor = serviceMonitor;
        this.clock = clock;
        this.applicationApiFactory = applicationApiFactory;

        serviceMonitor.registerListener(statusService);
    }

    @Override
    public Host getHost(HostName hostName) throws HostNameNotFoundException {
        ApplicationInstance applicationInstance = serviceMonitor
                .getApplicationNarrowedTo(hostName)
                .orElseThrow(() -> new HostNameNotFoundException(hostName));

        List<ServiceInstance> serviceInstances = applicationInstance
                .serviceClusters().stream()
                .flatMap(cluster -> cluster.serviceInstances().stream())
                .filter(serviceInstance -> hostName.equals(serviceInstance.hostName()))
                .toList();

        HostInfo hostInfo = statusService.getHostInfo(applicationInstance.reference(), hostName);

        return new Host(hostName, hostInfo, applicationInstance.reference(), serviceInstances);
    }

    @Override
    public HostStatus getNodeStatus(HostName hostName) throws HostNameNotFoundException {
        ApplicationInstanceReference reference = getApplicationInstanceReference(hostName);
        return statusService.getHostInfo(reference, hostName).status();
    }

    @Override
    public HostInfo getHostInfo(ApplicationInstanceReference reference, HostName hostname) {
        return statusService.getHostInfo(reference, hostname);
    }

    @Override
    public Function<HostName, Optional<HostInfo>> getHostResolver() {
        return hostName -> serviceMonitor
                .getApplicationInstanceReference(hostName)
                .map(reference -> statusService.getHostInfo(reference, hostName));
    }

    @Override
    public void setNodeStatus(HostName hostName, HostStatus status) throws OrchestrationException {
        ApplicationInstanceReference reference = getApplicationInstanceReference(hostName);
        OrchestratorContext context = OrchestratorContext.createContextForSingleAppOp(clock);
        try (ApplicationLock lock = statusService.lockApplication(context, reference)) {
            lock.setHostState(hostName, status);
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
        *
        * 2020-02-07: We should utilize suspendedSince timestamp on the HostInfo: The above
        * is equivalent to guaranteeing a minimum time after suspendedSince, before checking
        * the health with service monitor. This should for all practical purposes remove
        * the amount of time in this sleep.
        * Caveat: Cannot be implemented before lingering HostInfo has been fixed (VESPA-17546).
        */
        sleep(serviceMonitorConvergenceLatencySeconds, TimeUnit.SECONDS);

        ApplicationInstance appInstance = getApplicationInstance(hostName);

        OrchestratorContext context = OrchestratorContext.createContextForSingleAppOp(clock);
        try (ApplicationLock lock = statusService.lockApplication(context, appInstance.reference())) {
            HostStatus currentHostState = lock.getHostInfos().getOrNoRemarks(hostName).status();
            if (currentHostState == HostStatus.NO_REMARKS) {
                return;
            }

            // In 2 cases the resume will appear to succeed (no exception thrown),
            // but the host status and content cluster states will not be changed accordingly:
            //  1. When host is permanently down: the host will be removed from the application asap.
            //  2. The whole application is down: the content cluster states are set to maintenance,
            //     and the host may be taken down manually at any moment.
            if (currentHostState == HostStatus.PERMANENTLY_DOWN ||
                    lock.getApplicationInstanceStatus() == ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN) {
                return;
            }

            policy.releaseSuspensionGrant(context.createSubcontextWithinLock(), appInstance, hostName, lock);
        }
    }

    @Override
    public void suspend(HostName hostName) throws HostStateChangeDeniedException, HostNameNotFoundException {
        ApplicationInstance appInstance = getApplicationInstance(hostName);
        NodeGroup nodeGroup = new NodeGroup(appInstance, hostName);
        suspendGroup(OrchestratorContext.createContextForSingleAppOp(clock), nodeGroup);
    }

    @Override
    public void acquirePermissionToRemove(HostName hostName) throws OrchestrationException {
        ApplicationInstance appInstance = getApplicationInstance(hostName);
        NodeGroup nodeGroup = new NodeGroup(appInstance, hostName);

        OrchestratorContext context = OrchestratorContext.createContextForSingleAppOp(clock);
        try (ApplicationLock lock = statusService.lockApplication(context, appInstance.reference())) {
            ApplicationApi applicationApi = applicationApiFactory.create(nodeGroup, lock, clusterControllerClientFactory);

            policy.acquirePermissionToRemove(context.createSubcontextWithinLock(), applicationApi);
        }
    }

    /**
     * Suspend normal operations for a group of nodes in the same application.
     *
     * @param nodeGroup The group of nodes in an application.
     * @throws HostStateChangeDeniedException if the request cannot be met due to policy constraints.
     */
    void suspendGroup(OrchestratorContext context, NodeGroup nodeGroup) throws HostStateChangeDeniedException {
        ApplicationInstanceReference applicationReference = nodeGroup.getApplicationReference();

        final SuspensionReasons suspensionReasons;
        try (ApplicationLock lock = statusService.lockApplication(context, applicationReference)) {
            ApplicationInstanceStatus appStatus = lock.getApplicationInstanceStatus();
            if (appStatus == ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN) {
                return;
            }

            ApplicationApi applicationApi = applicationApiFactory.create(
                    nodeGroup, lock, clusterControllerClientFactory);
            suspensionReasons = policy.grantSuspensionRequest(context.createSubcontextWithinLock(), applicationApi);
        }

        suspensionReasons.makeLogMessage().ifPresent(log::info);
    }

    @Override
    public ApplicationInstanceStatus getApplicationInstanceStatus(ApplicationId appId) throws ApplicationIdNotFoundException {
        ApplicationInstanceReference reference = OrchestratorUtil.toApplicationInstanceReference(appId, serviceMonitor);
        return statusService.getApplicationInstanceStatus(reference);
    }

    @Override
    public Set<ApplicationId> getAllSuspendedApplications() {
        Set<ApplicationInstanceReference> refSet = statusService.getAllSuspendedApplications();
        return refSet.stream().map(OrchestratorUtil::toApplicationId).collect(toSet());
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
        try (OrchestratorContext context = OrchestratorContext.createContextForMultiAppOp(clock)) {
            List<NodeGroup> nodeGroupsOrderedByApplication;
            try {
                nodeGroupsOrderedByApplication = nodeGroupsOrderedForSuspend(hostNames);
            } catch (HostNameNotFoundException e) {
                throw new BatchHostNameNotFoundException(parentHostname, hostNames, e);
            }

            suspendAllNodeGroups(context, parentHostname, nodeGroupsOrderedByApplication, true);
            suspendAllNodeGroups(context, parentHostname, nodeGroupsOrderedByApplication, false);
        }
    }

    private void suspendAllNodeGroups(OrchestratorContext context,
                                      HostName parentHostname,
                                      List<NodeGroup> nodeGroupsOrderedByApplication,
                                      boolean probe)
            throws BatchHostStateChangeDeniedException, BatchInternalErrorException {
        for (NodeGroup nodeGroup : nodeGroupsOrderedByApplication) {
            try {
                suspendGroup(context.createSubcontextForSingleAppOp(probe), nodeGroup);
            } catch (HostStateChangeDeniedException e) {
                throw new BatchHostStateChangeDeniedException(parentHostname, nodeGroup, e);
            } catch (UncheckedTimeoutException e) {
                throw e;
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
                .toList();
    }

    private static int compareNodeGroupsForSuspend(NodeGroup leftNodeGroup, NodeGroup rightNodeGroup) {
        ApplicationInstanceReference leftApplicationReference = leftNodeGroup.getApplicationReference();
        ApplicationInstanceReference rightApplicationReference = rightNodeGroup.getApplicationReference();

        // ApplicationInstanceReference.toString() is e.g. "hosted-vespa:routing:dev:some-region:default"
        return leftApplicationReference.asString().compareTo(rightApplicationReference.asString());
    }

    private void setApplicationStatus(ApplicationId appId, ApplicationInstanceStatus status)
            throws ApplicationStateChangeDeniedException, ApplicationIdNotFoundException{
        OrchestratorContext context = OrchestratorContext.createContextForSingleAppOp(clock);
        ApplicationInstanceReference reference = OrchestratorUtil.toApplicationInstanceReference(appId, serviceMonitor);

        ApplicationInstance application = serviceMonitor.getApplication(reference)
                .orElseThrow(ApplicationIdNotFoundException::new);

        try (ApplicationLock lock = statusService.lockApplication(context, reference)) {

            // Short-circuit if already in wanted state
            if (status == lock.getApplicationInstanceStatus()) return;

            // Set content clusters for this application in maintenance on suspend
            if (status == ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN) {
                HostInfos hostInfosSnapshot = lock.getHostInfos();

                // Mark it allowed to be down before we manipulate the clustercontroller
                OrchestratorUtil.getHostsUsedByApplicationInstance(application)
                        .stream()
                        // This filter also ensures host status is not modified if a suspended host
                        // has status != ALLOWED_TO_BE_DOWN.
                        .filter(hostname -> !hostInfosSnapshot.getOrNoRemarks(hostname).status().isSuspended())
                        .forEach(hostname -> lock.setHostState(hostname, HostStatus.ALLOWED_TO_BE_DOWN));

                // If the clustercontroller throws an error the nodes will be marked as allowed to be down
                // and be set back up on next resume invocation.
                setClusterStateInController(context.createSubcontextWithinLock(), application, MAINTENANCE);
            }

            lock.setApplicationInstanceStatus(status);
        }
    }

    @Override
    public boolean isQuiescent(ApplicationId id) {
        try {
            ApplicationInstance application = serviceMonitor.getApplication(OrchestratorUtil.toApplicationInstanceReference(id, serviceMonitor))
                                                            .orElseThrow(ApplicationIdNotFoundException::new);

            List<ServiceCluster> contentClusters = application.serviceClusters().stream()
                                                              .filter(VespaModelUtil::isContent)
                                                              .toList();

            // For all content clusters, probe whether maintenance is OK.
            OrchestratorContext context = OrchestratorContext.createContextForBatchProbe(clock);
            for (ServiceCluster cluster : contentClusters) {
                List<HostName> clusterControllers = VespaModelUtil.getClusterControllerInstancesInOrder(application, cluster.clusterId());
                ClusterControllerClient client = clusterControllerClientFactory.createClient(clusterControllers, cluster.clusterId().s());
                for (ServiceInstance service : cluster.serviceInstances()) {
                    try {
                        if ( ! client.trySetNodeState(context, service.hostName(), VespaModelUtil.getStorageNodeIndex(service.configId()), MAINTENANCE, ContentService.STORAGE_NODE, false))
                            return false;
                    }
                    catch (Exception e) {
                        log.log(Level.INFO, "Failed probing for permission to set " + service + " in MAINTENANCE: " + Exceptions.toMessageString(e));
                        return false;
                    }
                }
            }
            return true;
        }
        catch (ApplicationIdNotFoundException ignored) {
            return false;
        }
    }

    private void setClusterStateInController(OrchestratorContext context,
                                             ApplicationInstance application,
                                             ClusterControllerNodeState state)
            throws ApplicationStateChangeDeniedException {
        // Get all content clusters for this application
        Set<ClusterId> contentClusterIds = application.serviceClusters().stream()
                .filter(VespaModelUtil::isContent)
                .map(ServiceCluster::clusterId)
                .collect(toSet());

        // For all content clusters set in maintenance
        for (ClusterId clusterId : contentClusterIds) {
            List<HostName> clusterControllers = VespaModelUtil.getClusterControllerInstancesInOrder(application, clusterId);
            ClusterControllerClient client = clusterControllerClientFactory.createClient(clusterControllers, clusterId.s());
            client.setApplicationState(context, application.applicationInstanceId(), state);
        }
    }

    private ApplicationInstanceReference getApplicationInstanceReference(HostName hostname) throws HostNameNotFoundException {
        return serviceMonitor.getApplicationInstanceReference(hostname)
                .orElseThrow(() -> new HostNameNotFoundException(hostname));
    }

    private ApplicationInstance getApplicationInstance(HostName hostName) throws HostNameNotFoundException{
        return serviceMonitor.getApplication(hostName)
                .orElseThrow(() -> new HostNameNotFoundException(hostName));
    }

    private static void sleep(long time, TimeUnit timeUnit) {
        try {
            Thread.sleep(timeUnit.toMillis(time));
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpectedly interrupted", e);
        }
    }

}
