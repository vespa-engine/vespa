// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerExecTimeoutException;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.provision.Node;

import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.ABSENT;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.RUNNING;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN;

/**
 * @author dybis
 * @author bakksjo
 */
public class NodeAgentImpl implements NodeAgent {
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private boolean isFrozen = true;
    private boolean wantFrozen = false;
    private boolean workToDoNow = true;

    private final Object monitor = new Object();

    private final PrefixLogger logger;
    private DockerImage imageBeingDownloaded = null;

    private final String hostname;
    private final ContainerName containerName;
    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final DockerOperations dockerOperations;
    private final Optional<StorageMaintainer> storageMaintainer;
    private final MetricReceiverWrapper metricReceiver;
    private final Environment environment;
    private final Clock clock;
    private final Optional<AclMaintainer> aclMaintainer;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final LinkedList<String> debugMessages = new LinkedList<>();

    private long delaysBetweenEachConvergeMillis = 30_000;
    private int numberOfUnhandledException = 0;
    private Instant lastConverge;

    private Thread loopThread;

    private final ScheduledExecutorService filebeatRestarter =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory("filebeatrestarter"));
    private final Consumer<String> serviceRestarter;
    private Future<?> currentFilebeatRestarter;

    enum ContainerState {
        ABSENT,
        RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN,
        RUNNING
    }

    private ContainerState containerState = ABSENT;

    // The attributes of the last successful node repo attribute update for this node. Used to avoid redundant calls.
    private NodeAttributes lastAttributesSet = null;
    private ContainerNodeSpec lastNodeSpec = null;
    private CpuUsageReporter lastCpuMetric;

    public NodeAgentImpl(
            final String hostName,
            final NodeRepository nodeRepository,
            final Orchestrator orchestrator,
            final DockerOperations dockerOperations,
            final Optional<StorageMaintainer> storageMaintainer,
            final MetricReceiverWrapper metricReceiver,
            final Environment environment,
            final Clock clock,
            final Optional<AclMaintainer> aclMaintainer) {
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.hostname = hostName;
        this.containerName = ContainerName.fromHostname(hostName);
        this.dockerOperations = dockerOperations;
        this.storageMaintainer = storageMaintainer;
        this.logger = PrefixLogger.getNodeAgentLogger(NodeAgentImpl.class, containerName);
        this.metricReceiver = metricReceiver;
        this.environment = environment;
        this.clock = clock;
        this.aclMaintainer = aclMaintainer;
        this.lastConverge = clock.instant();
        this.serviceRestarter = service -> {
            try {
                ProcessResult processResult = dockerOperations.executeCommandInContainerAsRoot(
                        containerName, "service", service, "restart");

                if (!processResult.isSuccess()) {
                    logger.error("Failed to restart service " + service + ": " + processResult);
                }
            } catch (Exception e) {
                logger.error("Failed to restart service " + service, e);
            }
        };

        // If the container is already running, initialize vespaVersion and lastCpuMetric
        lastCpuMetric = new CpuUsageReporter(clock.instant());
        dockerOperations.getContainer(containerName)
                .ifPresent(container -> {
                    if (container.state.isRunning()) {
                        lastCpuMetric = new CpuUsageReporter(container.created);
                    }
                    containerState = RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN;
                    logger.info("Container is already running, setting containerState to " + containerState);
                });
    }

    @Override
    public boolean setFrozen(boolean frozen) {
        synchronized (monitor) {
            if (wantFrozen != frozen) {
                wantFrozen = frozen;
                addDebugMessage(wantFrozen ? "Freezing" : "Unfreezing");
                signalWorkToBeDone();
            }

            return isFrozen == frozen;
        }
    }

    private void addDebugMessage(String message) {
        synchronized (debugMessages) {
            while (debugMessages.size() > 1000) {
                debugMessages.pop();
            }

            logger.debug(message);
            debugMessages.add("[" + sdf.format(new Date()) + "] " + message);
        }
    }

    @Override
    public Map<String, Object> debugInfo() {
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("Hostname", hostname);
        debug.put("isFrozen", isFrozen);
        debug.put("wantFrozen", wantFrozen);
        debug.put("terminated", terminated);
        debug.put("workToDoNow", workToDoNow);
        synchronized (debugMessages) {
            debug.put("History", new LinkedList<>(debugMessages));
        }
        debug.put("Node repo state", lastNodeSpec.nodeState.name());
        return debug;
    }

    @Override
    public void start(int intervalMillis) {
        addDebugMessage("Starting with interval " + intervalMillis + "ms");
        delaysBetweenEachConvergeMillis = intervalMillis;
        if (loopThread != null) {
            throw new RuntimeException("Can not restart a node agent.");
        }

        loopThread = new Thread(() -> {
            while (!terminated.get()) tick();
        });
        loopThread.setName("tick-" + hostname);
        loopThread.start();
    }

    @Override
    public void stop() {
        addDebugMessage("Stopping");
        filebeatRestarter.shutdown();
        if (!terminated.compareAndSet(false, true)) {
            throw new RuntimeException("Can not re-stop a node agent.");
        }
        signalWorkToBeDone();
        try {
            loopThread.join(10000);
            if (loopThread.isAlive()) {
                logger.error("Could not stop host thread " + hostname);
            }
        } catch (InterruptedException e1) {
            logger.error("Interrupted; Could not stop host thread " + hostname);
        }
        try {
            filebeatRestarter.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Interrupted; Could not stop filebeatrestarter thread");
        }
    }

    private void runLocalResumeScriptIfNeeded(final ContainerNodeSpec nodeSpec) {
        if (containerState != RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN) {
            return;
        }

        addDebugMessage("Starting optional node program resume command");
        dockerOperations.resumeNode(containerName);
        containerState = RUNNING;
        logger.info("Resume command successfully executed, new containerState is " + containerState);
    }

    private void updateNodeRepoWithCurrentAttributes(final ContainerNodeSpec nodeSpec) {
        final NodeAttributes nodeAttributes = new NodeAttributes()
                .withRestartGeneration(nodeSpec.wantedRestartGeneration.orElse(null))
                // update reboot gen with wanted gen if set, we ignore reboot for Docker nodes but
                // want the two to be equal in node repo
                .withRebootGeneration(nodeSpec.wantedRebootGeneration.orElse(0L))
                .withDockerImage(nodeSpec.wantedDockerImage.filter(node -> containerState != ABSENT).orElse(new DockerImage("")))
                .withVespaVersion(nodeSpec.wantedVespaVersion.filter(node -> containerState != ABSENT).orElse(""));

        publishStateToNodeRepoIfChanged(nodeAttributes);
    }

    private void publishStateToNodeRepoIfChanged(NodeAttributes currentAttributes) {
        // TODO: We should only update if the new current values do not match the node repo's current values
        if (!currentAttributes.equals(lastAttributesSet)) {
            logger.info("Publishing new set of attributes to node repo: "
                    + lastAttributesSet + " -> " + currentAttributes);
            addDebugMessage("Publishing new set of attributes to node repo: {" +
                    lastAttributesSet + "} -> {" + currentAttributes + "}");
            nodeRepository.updateNodeAttributes(hostname, currentAttributes);
            lastAttributesSet = currentAttributes;
        }
    }

    private void startContainer(ContainerNodeSpec nodeSpec) {
        aclMaintainer.ifPresent(AclMaintainer::run);
        dockerOperations.startContainer(containerName, nodeSpec);
        lastCpuMetric = new CpuUsageReporter(clock.instant());

        currentFilebeatRestarter = filebeatRestarter.scheduleWithFixedDelay(() -> serviceRestarter.accept("filebeat"), 1, 1, TimeUnit.DAYS);
        storageMaintainer.ifPresent(maintainer -> {
            maintainer.writeMetricsConfig(containerName, nodeSpec);
            maintainer.writeFilebeatConfig(containerName, nodeSpec);
        });


        addDebugMessage("startContainerIfNeeded: containerState " + containerState + " -> " +
                RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN);
        containerState = RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN;
        logger.info("Container successfully started, new containerState is " + containerState);
    }

    private Optional<Container> removeContainerIfNeededUpdateContainerState(ContainerNodeSpec nodeSpec, Optional<Container> existingContainer) {
        return existingContainer
                .flatMap(container -> removeContainerIfNeeded(nodeSpec, container))
                .map(container -> {
                        shouldRestartServices(nodeSpec).ifPresent(restartReason -> {
                            logger.info("Will restart services for container " + container + ": " + restartReason);
                            restartServices(nodeSpec, container);
                        });
                        return container;
                });
    }

    private Optional<String> shouldRestartServices(ContainerNodeSpec nodeSpec) {
        if (!nodeSpec.wantedRestartGeneration.isPresent()) return Optional.empty();

        if (!nodeSpec.currentRestartGeneration.isPresent() ||
                nodeSpec.currentRestartGeneration.get() < nodeSpec.wantedRestartGeneration.get()) {
            return Optional.of("Restart requested - wanted restart generation has been bumped: "
                    + nodeSpec.currentRestartGeneration.get() + " -> " + nodeSpec.wantedRestartGeneration.get());
        }
        return Optional.empty();
    }

    private void restartServices(ContainerNodeSpec nodeSpec, Container existingContainer) {
        if (existingContainer.state.isRunning() && nodeSpec.nodeState == Node.State.active) {
            ContainerName containerName = existingContainer.name;
            logger.info("Restarting services for " + containerName);
            // Since we are restarting the services we need to suspend the node.
            orchestratorSuspendNode();
            dockerOperations.restartVespaOnNode(containerName);
        }
    }

    @Override
    public void stopServices() {
        logger.info("Stopping services for " + containerName);
        dockerOperations.trySuspendNode(containerName);
        dockerOperations.stopServicesOnNode(containerName);
    }

    private Optional<String> shouldRemoveContainer(ContainerNodeSpec nodeSpec, Container existingContainer) {
        final Node.State nodeState = nodeSpec.nodeState;
        if (nodeState == Node.State.dirty || nodeState == Node.State.provisioned) {
            return Optional.of("Node in state " + nodeState + ", container should no longer be running");
        }
        if (nodeSpec.wantedDockerImage.isPresent() && !nodeSpec.wantedDockerImage.get().equals(existingContainer.image)) {
            return Optional.of("The node is supposed to run a new Docker image: "
                    + existingContainer + " -> " + nodeSpec.wantedDockerImage.get());
        }
        if (!existingContainer.state.isRunning()) {
            return Optional.of("Container no longer running");
        }
        return Optional.empty();
    }

    private Optional<Container> removeContainerIfNeeded(ContainerNodeSpec nodeSpec, Container existingContainer) {
        Optional<String> removeReason = shouldRemoveContainer(nodeSpec, existingContainer);
        if (removeReason.isPresent()) {
            logger.info("Will remove container " + existingContainer + ": " + removeReason.get());

            if (existingContainer.state.isRunning()) {
                if (nodeSpec.nodeState == Node.State.active) {
                    orchestratorSuspendNode();
                }

                try {
                    stopServices();
                } catch (Exception e) {
                    logger.info("Failed stopping services, ignoring", e);
                }
            }
            if (currentFilebeatRestarter != null) currentFilebeatRestarter.cancel(true);
            dockerOperations.removeContainer(existingContainer);
            metricReceiver.unsetMetricsForContainer(hostname);
            containerState = ABSENT;
            logger.info("Container successfully removed, new containerState is " + containerState);
            return Optional.empty();
        }
        return Optional.of(existingContainer);
    }


    private void scheduleDownLoadIfNeeded(ContainerNodeSpec nodeSpec) {
        if (nodeSpec.currentDockerImage.equals(nodeSpec.wantedDockerImage)) return;

        if (dockerOperations.shouldScheduleDownloadOfImage(nodeSpec.wantedDockerImage.get())) {
            if (nodeSpec.wantedDockerImage.get().equals(imageBeingDownloaded)) {
                // Downloading already scheduled, but not done.
                return;
            }
            imageBeingDownloaded = nodeSpec.wantedDockerImage.get();
            // Create a signalWorkToBeDone when download is finished.
            dockerOperations.scheduleDownloadOfImage(containerName, imageBeingDownloaded, this::signalWorkToBeDone);
        } else if (imageBeingDownloaded != null) { // Image was downloading, but now it's ready
            imageBeingDownloaded = null;
        }
    }

    private void signalWorkToBeDone() {
        synchronized (monitor) {
            if (!workToDoNow) {
                workToDoNow = true;
                addDebugMessage("Signaling work to be done");
                monitor.notifyAll();
            }
        }
    }

    void tick() {
        boolean isFrozenCopy;
        synchronized (monitor) {
            while (!workToDoNow) {
                long remainder = delaysBetweenEachConvergeMillis - Duration.between(lastConverge, clock.instant()).toMillis();
                if (remainder > 0) {
                    try {
                        monitor.wait(remainder);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted, but ignoring this: " + hostname);
                    }
                } else break;
            }
            lastConverge = clock.instant();
            workToDoNow = false;

            if (isFrozen != wantFrozen) {
                isFrozen = wantFrozen;
                logger.info("Updated NodeAgent's frozen state, new value: isFrozen: " + isFrozen);
            }
            isFrozenCopy = isFrozen;
        }

        if (isFrozenCopy) {
            addDebugMessage("tick: isFrozen");
        } else {
            try {
                converge();
            } catch (OrchestratorException e) {
                logger.info(e.getMessage());
                addDebugMessage(e.getMessage());
            } catch (Exception e) {
                numberOfUnhandledException++;
                logger.error("Unhandled exception, ignoring.", e);
                addDebugMessage(e.getMessage());
            } catch (Throwable t) {
                logger.error("Unhandled throwable, taking down system.", t);
                System.exit(234);
            }
        }
    }

    // Public for testing
    void converge() {
        final ContainerNodeSpec nodeSpec = nodeRepository.getContainerNodeSpec(hostname)
                .orElseThrow(() ->
                        new IllegalStateException(String.format("Node '%s' missing from node repository.", hostname)));

        Optional<Container> container = getContainer();
        if (!nodeSpec.equals(lastNodeSpec)) {
            addDebugMessage("Loading new node spec: " + nodeSpec.toString());
            lastNodeSpec = nodeSpec;

            // Every time the node spec changes, we should clear the metrics for this container as the dimensions
            // will change and we will be reporting duplicate metrics.
            // TODO: Should be retried if writing fails
            metricReceiver.unsetMetricsForContainer(hostname);
            if (container.isPresent()) {
                storageMaintainer.ifPresent(maintainer -> {
                    maintainer.writeMetricsConfig(containerName, nodeSpec);
                });
            }
        }

        switch (nodeSpec.nodeState) {
            case ready:
            case reserved:
            case parked:
            case failed:
                removeContainerIfNeededUpdateContainerState(nodeSpec, container);
                updateNodeRepoWithCurrentAttributes(nodeSpec);
                break;
            case active:
                storageMaintainer.ifPresent(maintainer -> {
                    maintainer.removeOldFilesFromNode(containerName);
                    maintainer.handleCoreDumpsForContainer(containerName, nodeSpec, environment);
                });
                scheduleDownLoadIfNeeded(nodeSpec);
                if (isDownloadingImage()) {
                    addDebugMessage("Waiting for image to download " + imageBeingDownloaded.asString());
                    return;
                }
                container = removeContainerIfNeededUpdateContainerState(nodeSpec, container);
                if (! container.isPresent()) {
                    startContainer(nodeSpec);
                }

                runLocalResumeScriptIfNeeded(nodeSpec);
                // Because it's more important to stop a bad release from rolling out in prod,
                // we put the resume call last. So if we fail after updating the node repo attributes
                // but before resume, the app may go through the tenant pipeline but will halt in prod.
                //
                // Note that this problem exists only because there are 2 different mechanisms
                // that should really be parts of a single mechanism:
                //  - The content of node repo is used to determine whether a new Vespa+application
                //    has been successfully rolled out.
                //  - Slobrok and internal orchestrator state is used to determine whether
                //    to allow upgrade (suspend).
                updateNodeRepoWithCurrentAttributes(nodeSpec);
                logger.info("Call resume against Orchestrator");
                orchestrator.resume(hostname);
                break;
            case inactive:
                storageMaintainer.ifPresent(maintainer -> maintainer.removeOldFilesFromNode(containerName));
                removeContainerIfNeededUpdateContainerState(nodeSpec, container);
                updateNodeRepoWithCurrentAttributes(nodeSpec);
                break;
            case provisioned:
                nodeRepository.markAsDirty(hostname);
                break;
            case dirty:
                storageMaintainer.ifPresent(maintainer -> maintainer.removeOldFilesFromNode(containerName));
                removeContainerIfNeededUpdateContainerState(nodeSpec, container);
                logger.info("State is " + nodeSpec.nodeState + ", will delete application storage and mark node as ready");
                storageMaintainer.ifPresent(maintainer -> maintainer.archiveNodeData(containerName));
                updateNodeRepoWithCurrentAttributes(nodeSpec);
                nodeRepository.markNodeAvailableForNewAllocation(hostname);
                break;
            default:
                throw new RuntimeException("UNKNOWN STATE " + nodeSpec.nodeState.name());
        }
    }

    @SuppressWarnings("unchecked")
    public void updateContainerNodeMetrics(int numAllocatedContainersOnHost) {
        final ContainerNodeSpec nodeSpec = lastNodeSpec;
        if (nodeSpec == null) return;

        Dimensions.Builder dimensionsBuilder = new Dimensions.Builder()
                .add("host", hostname)
                .add("role", "tenants")
                .add("flavor", nodeSpec.nodeFlavor)
                .add("state", nodeSpec.nodeState.toString())
                .add("zone", environment.getZone())
                .add("parentHostname", environment.getParentHostHostname());
        nodeSpec.vespaVersion.ifPresent(version -> dimensionsBuilder.add("vespaVersion", version));

        nodeSpec.owner.ifPresent(owner ->
                dimensionsBuilder
                        .add("tenantName", owner.tenant)
                        .add("applicationName", owner.application)
                        .add("instanceName", owner.instance)
                        .add("applicationId", owner.tenant + "." + owner.application + "." + owner.instance)
                        .add("app", owner.application + "." + owner.instance));

        nodeSpec.membership.ifPresent(membership ->
                dimensionsBuilder
                        .add("clustertype", membership.clusterType)
                        .add("clusterid", membership.clusterId));
        Dimensions dimensions = dimensionsBuilder.build();
        metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_NODE, dimensions, "alive").sample(1);
        // TODO: REMOVE
        metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "node.alive").sample(1);

        // The remaining metrics require container to exists and be running
        if (containerState == ABSENT) return;
        Optional<Docker.ContainerStats> containerStats = dockerOperations.getContainerStats(containerName);
        if (!containerStats.isPresent()) return;

        Docker.ContainerStats stats = containerStats.get();
        final String APP = MetricReceiverWrapper.APPLICATION_NODE;
        final long bytesInGB = 1 << 30;
        final long cpuContainerTotalTime = ((Number) ((Map) stats.getCpuStats().get("cpu_usage")).get("total_usage")).longValue();
        final long cpuSystemTotalTime = ((Number) stats.getCpuStats().get("system_cpu_usage")).longValue();
        final long memoryTotalBytes = ((Number) stats.getMemoryStats().get("limit")).longValue();
        final long memoryTotalBytesUsage = ((Number) stats.getMemoryStats().get("usage")).longValue();
        final long memoryTotalBytesCache = ((Number) ((Map) stats.getMemoryStats().get("stats")).get("cache")).longValue();
        final Optional<Long> diskTotalBytes = nodeSpec.minDiskAvailableGb.map(size -> (long) (size * bytesInGB));
        final Optional<Long> diskTotalBytesUsed = storageMaintainer.flatMap(maintainer -> maintainer
                        .updateIfNeededAndGetDiskMetricsFor(containerName));

        // CPU usage by a container is given by dividing used CPU time by the container with CPU time used by the entire
        // system. Because each container is allocated same amount of CPU shares, no container should use more than 1/n
        // of the total CPU time, where n is the number of running containers.
        double cpuPercentageOfHost = lastCpuMetric.getCpuUsagePercentage(cpuContainerTotalTime, cpuSystemTotalTime);
        double cpuPercentageOfAllocated = numAllocatedContainersOnHost * cpuPercentageOfHost;
        long memoryTotalBytesUsed = memoryTotalBytesUsage - memoryTotalBytesCache;
        double memoryPercentUsed = 100.0 * memoryTotalBytesUsed / memoryTotalBytes;
        Optional<Double> diskPercentUsed = diskTotalBytes.flatMap(total -> diskTotalBytesUsed.map(used -> 100.0 * used / total));

        metricReceiver.declareGauge(APP, dimensions, "cpu.util").sample(cpuPercentageOfAllocated);
        metricReceiver.declareGauge(APP, dimensions, "mem.limit").sample(memoryTotalBytes);
        metricReceiver.declareGauge(APP, dimensions, "mem.used").sample(memoryTotalBytesUsed);
        metricReceiver.declareGauge(APP, dimensions, "mem.util").sample(memoryPercentUsed);
        diskTotalBytes.ifPresent(diskLimit -> metricReceiver.declareGauge(APP, dimensions, "disk.limit").sample(diskLimit));
        diskTotalBytesUsed.ifPresent(diskUsed -> metricReceiver.declareGauge(APP, dimensions, "disk.used").sample(diskUsed));
        diskPercentUsed.ifPresent(diskUtil -> metricReceiver.declareGauge(APP, dimensions, "disk.util").sample(diskUtil));

        stats.getNetworks().forEach((interfaceName, interfaceStats) -> {
            Dimensions netDims = dimensionsBuilder.add("interface", interfaceName).build();
            Map<String, Number> infStats = (Map<String, Number>) interfaceStats;

            metricReceiver.declareGauge(APP, netDims, "net.in.bytes").sample(infStats.get("rx_bytes").longValue());
            metricReceiver.declareGauge(APP, netDims, "net.in.errors").sample(infStats.get("rx_errors").longValue());
            metricReceiver.declareGauge(APP, netDims, "net.in.dropped").sample(infStats.get("rx_dropped").longValue());
            metricReceiver.declareGauge(APP, netDims, "net.out.bytes").sample(infStats.get("tx_bytes").longValue());
            metricReceiver.declareGauge(APP, netDims, "net.out.errors").sample(infStats.get("tx_errors").longValue());
            metricReceiver.declareGauge(APP, netDims, "net.out.dropped").sample(infStats.get("tx_dropped").longValue());
        });


        // TODO: Remove when all alerts and dashboards have been updated to use new metric names
        metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "node.cpu.busy.pct").sample(cpuPercentageOfAllocated);

        addIfNotNull(dimensions, "node.cpu.throttled_time", stats.getCpuStats().get("throttling_data"), "throttled_time");
        addIfNotNull(dimensions, "node.memory.limit", stats.getMemoryStats(), "limit");

        long memoryUsageTotal = ((Number) stats.getMemoryStats().get("usage")).longValue();
        long memoryUsageCache = ((Number) ((Map) stats.getMemoryStats().get("stats")).get("cache")).longValue();
        long memoryUsage = memoryUsageTotal - memoryUsageCache;
        metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "node.memory.usage").sample(memoryUsage);

        stats.getNetworks().forEach((interfaceName, interfaceStats) -> {
            Dimensions netDims = dimensionsBuilder.add("interface", interfaceName).build();

            addIfNotNull(netDims, "node.net.in.bytes", interfaceStats, "rx_bytes");
            addIfNotNull(netDims, "node.net.in.errors", interfaceStats, "rx_errors");
            addIfNotNull(netDims, "node.net.in.dropped", interfaceStats, "rx_dropped");
            addIfNotNull(netDims, "node.net.out.bytes", interfaceStats, "tx_bytes");
            addIfNotNull(netDims, "node.net.out.errors", interfaceStats, "tx_errors");
            addIfNotNull(netDims, "node.net.out.dropped", interfaceStats, "tx_dropped");
        });

        diskTotalBytes.ifPresent(diskLimit ->
                metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "node.disk.limit").sample(diskLimit));
        diskTotalBytesUsed.ifPresent(diskUsed ->
                metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "node.disk.used").sample(diskUsed));
        // TODO END REMOVE

        metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_HOST_LIFE, dimensions, "uptime").sample(lastCpuMetric.getUptime());
        metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_HOST_LIFE, dimensions, "alive").sample(1);

        // Push metrics to the metrics proxy in each container - give it maximum 1 seconds to complete
        try {
            String[] cmd = {"rpc_invoke",  "-t", "1",  "tcp/localhost:19091",  "setExtraMetrics", buildRPCArgumentFromMetrics()};
            dockerOperations.executeCommandInContainerAsRoot(containerName, 5L, cmd);
        } catch (DockerExecTimeoutException|JsonProcessingException e) {
            logger.warning("Unable to push metrics to container: " + containerName, e);
        }
    }

    protected String buildRPCArgumentFromMetrics() throws JsonProcessingException {
        StringBuilder params = new StringBuilder();
        for (MetricReceiverWrapper.DimensionMetrics dimensionMetrics : metricReceiver.getAllMetrics()) {
            params.append(dimensionMetrics.toSecretAgentReport());
        }
        return "s:'" + params.toString() + "'";
    }

    @SuppressWarnings("unchecked")
    private void addIfNotNull(Dimensions dimensions, String yamasName, Object metrics, String metricName) {
        Map<String, Object> metricsMap = (Map<String, Object>) metrics;
        if (metricsMap == null || !metricsMap.containsKey(metricName)) return;
        try {
            metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, yamasName)
                    .sample(((Number) metricsMap.get(metricName)).doubleValue());
        } catch (Throwable e) {
            logger.warning("Failed to update " + yamasName + " metric with value " + metricsMap.get(metricName), e);
        }
    }

    private Optional<Container> getContainer() {
        if (containerState == ABSENT) return Optional.empty();
        return dockerOperations.getContainer(containerName);
    }

    @Override
    public String getHostname() {
        return hostname;
    }

    @Override
    public boolean isDownloadingImage() {
        return imageBeingDownloaded != null;
    }

    @Override
    public int getAndResetNumberOfUnhandledExceptions() {
        int temp = numberOfUnhandledException;
        numberOfUnhandledException = 0;
        return temp;
    }

    class CpuUsageReporter {
        private long totalContainerUsage = 0;
        private long totalSystemUsage = 0;
        private final Instant created;

        CpuUsageReporter(Instant created) {
            this.created = created;
        }

        double getCpuUsagePercentage(long currentContainerUsage, long currentSystemUsage) {
            long deltaSystemUsage = currentSystemUsage - totalSystemUsage;
            double cpuUsagePct = (deltaSystemUsage == 0 || totalSystemUsage == 0) ?
                    0 : 100.0 * (currentContainerUsage - totalContainerUsage) / deltaSystemUsage;

            totalContainerUsage = currentContainerUsage;
            totalSystemUsage = currentSystemUsage;
            return cpuUsagePct;
        }

        long getUptime() {
            return Duration.between(created, clock.instant()).getSeconds();
        }
    }

    // TODO: Also skip orchestration if we're downgrading in test/staging
    // How to implement:
    //  - test/staging: We need to figure out whether we're in test/staging, zone is available in Environment
    //  - downgrading: Impossible to know unless we look at the hosted version, which is
    //    not available in the docker image (nor its name). Not sure how to solve this. Should
    //    the node repo return the hosted version or a downgrade bit in addition to
    //    wanted docker image etc?
    // Should the tenant pipeline instead use BCP tool to upgrade faster!?
    //
    // More generally, the node repo response should contain sufficient info on what the docker image is,
    // to allow the node admin to make decisions that depend on the docker image. Or, each docker image
    // needs to contain routines for drain and suspend. For many images, these can just be dummy routines.
    private void orchestratorSuspendNode() {
        logger.info("Ask Orchestrator for permission to suspend node " + hostname);
        orchestrator.suspend(hostname);
    }
}
