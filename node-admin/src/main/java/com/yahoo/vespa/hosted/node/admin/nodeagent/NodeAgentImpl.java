// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.node.admin.util.SecretAgentScheduleMaker;
import com.yahoo.vespa.hosted.provision.Node;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.ABSENT;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.RUNNING;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN;

/**
 * @author dybis
 * @author bakksjo
 */
public class NodeAgentImpl implements NodeAgent {

    private AtomicBoolean isFrozen = new AtomicBoolean(false);
    private AtomicBoolean wantFrozen = new AtomicBoolean(false);
    private AtomicBoolean terminated = new AtomicBoolean(false);

    private boolean workToDoNow = true;

    private final PrefixLogger logger;

    private DockerImage imageBeingDownloaded = null;
    private final String hostname;

    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final DockerOperations dockerOperations;
    private final Optional<StorageMaintainer> storageMaintainer;
    private final MetricReceiverWrapper metricReceiver;
    private final Environment environment;

    private final Object monitor = new Object();

    private final LinkedList<String> debugMessages = new LinkedList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private long delaysBetweenEachTickMillis;
    private int numberOfUnhandledException = 0;

    private Thread loopThread;

    enum ContainerState {
        ABSENT,
        RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN,
        RUNNING
    }
    private ContainerState containerState = ABSENT;

    // The attributes of the last successful node repo attribute update for this node. Used to avoid redundant calls.
    private NodeAttributes lastAttributesSet = null;
    ContainerNodeSpec lastNodeSpec = null;
    CpuUsageReporter lastCpuMetric = new CpuUsageReporter();
    Optional<String> vespaVersion = Optional.empty();

    public NodeAgentImpl(
            final String hostName,
            final NodeRepository nodeRepository,
            final Orchestrator orchestrator,
            final DockerOperations dockerOperations,
            final Optional<StorageMaintainer> storageMaintainer,
            final MetricReceiverWrapper metricReceiver,
            final Environment environment) {
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.hostname = hostName;
        this.dockerOperations = dockerOperations;
        this.storageMaintainer = storageMaintainer;
        this.logger = PrefixLogger.getNodeAgentLogger(NodeAgentImpl.class,
                NodeRepositoryImpl.containerNameFromHostName(hostName));
        this.metricReceiver = metricReceiver;
        this.environment = environment;
    }

    @Override
    public void freeze() {
        if (!wantFrozen.get()) {
            addDebugMessage("Freezing");
        }
        wantFrozen.set(true);
        signalWorkToBeDone();
    }

    @Override
    public void unfreeze() {
        if (wantFrozen.get()) {
            addDebugMessage("Unfreezing");
        }
        wantFrozen.set(false);
        signalWorkToBeDone();
    }

    @Override
    public boolean isFrozen() {
        return isFrozen.get();
    }

    private void addDebugMessage(String message) {
        synchronized (monitor) {
            while (debugMessages.size() > 100) {
                debugMessages.pop();
            }

            debugMessages.add("[" + sdf.format(new Date()) + "] " + message);
        }
    }

    @Override
    public Map<String, Object> debugInfo() {
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("Hostname", hostname);
        debug.put("isFrozen", isFrozen());
        debug.put("wantFrozen", wantFrozen.get());
        debug.put("terminated", terminated.get());
        debug.put("workToDoNow", workToDoNow);
        synchronized (monitor) {
            debug.put("History", new LinkedList<>(debugMessages));
            debug.put("Node repo state", lastNodeSpec.nodeState.name());
        }
        return debug;
    }

    @Override
    public void start(int intervalMillis) {
        addDebugMessage("Starting with interval " + intervalMillis + "ms");
        delaysBetweenEachTickMillis = intervalMillis;
        if (loopThread != null) {
            throw new RuntimeException("Can not restart a node agent.");
        }

        // If the container is already running, initialize vespaVersion
        vespaVersion = dockerOperations.getContainer(hostname)
                .filter(container -> container.isRunning)
                .flatMap(container -> dockerOperations.getVespaVersion(container.name));

        loopThread = new Thread(this::loop);
        loopThread.setName("loop-" + hostname);
        loopThread.start();
    }

    @Override
    public void stop() {
        addDebugMessage("Stopping");
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
    }

    private void runLocalResumeScriptIfNeeded(final ContainerNodeSpec nodeSpec) {
        if (containerState != RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN) {
            return;
        }
        addDebugMessage("Starting optional node program resume command");
        logger.info("Starting optional node program resume command");
        dockerOperations.resumeNode(nodeSpec.containerName);
        containerState = RUNNING;
    }

    private void updateNodeRepoAndMarkNodeAsReady(ContainerNodeSpec nodeSpec) {
        publishStateToNodeRepoIfChanged(
                nodeSpec.hostname,
                // Clear current Docker image and vespa version, as nothing is running on this node
                new NodeAttributes()
                        .withRestartGeneration(nodeSpec.wantedRestartGeneration.orElse(null))
                        .withRebootGeneration(nodeSpec.wantedRebootGeneration.orElse(0L))
                        .withDockerImage(new DockerImage(""))
                        .withVespaVersion(""));
        nodeRepository.markAsReady(nodeSpec.hostname);
    }

    private void updateNodeRepoWithCurrentAttributes(final ContainerNodeSpec nodeSpec) {
        final NodeAttributes nodeAttributes = new NodeAttributes()
                .withRestartGeneration(nodeSpec.wantedRestartGeneration.orElse(null))
                // update reboot gen with wanted gen if set, we ignore reboot for Docker nodes but
                // want the two to be equal in node repo
                .withRebootGeneration(nodeSpec.wantedRebootGeneration.orElse(0L))
                .withDockerImage(nodeSpec.wantedDockerImage.orElse(new DockerImage("")))
                .withVespaVersion(vespaVersion.orElse(""));

        publishStateToNodeRepoIfChanged(nodeSpec.hostname, nodeAttributes);
    }

    private void publishStateToNodeRepoIfChanged(String hostName, NodeAttributes currentAttributes) {
        // TODO: We should only update if the new current values do not match the node repo's current values
        if (!currentAttributes.equals(lastAttributesSet)) {
            logger.info("Publishing new set of attributes to node repo: "
                                + lastAttributesSet + " -> " + currentAttributes);
            addDebugMessage("Publishing new set of attributes to node repo: {" +
                                    lastAttributesSet + "} -> {" + currentAttributes + "}");
            nodeRepository.updateNodeAttributes(hostName, currentAttributes);
            lastAttributesSet = currentAttributes;
        }
    }

    private void startContainerIfNeeded(final ContainerNodeSpec nodeSpec) {
        if (dockerOperations.startContainerIfNeeded(nodeSpec)) {
            vespaVersion = dockerOperations.getVespaVersion(nodeSpec.containerName);
            metricReceiver.unsetMetricsForContainer(hostname);

            configureContainerMetrics(nodeSpec);
            addDebugMessage("startContainerIfNeeded: containerState " + containerState + " -> " +
                            RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN);
            containerState = RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN;
        } else {
            // In case container was already running on startup, we found the container, but should call
            if (containerState == ABSENT) {
                addDebugMessage("startContainerIfNeeded: was already running, containerState set to " +
                        RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN);
                containerState = RUNNING_HOWEVER_RESUME_SCRIPT_NOT_RUN;
            }
        }
    }

    private void removeContainerIfNeededUpdateContainerState(ContainerNodeSpec nodeSpec) throws Exception {
        if (removeContainerIfNeeded(nodeSpec, hostname, orchestrator)) {
            addDebugMessage("removeContainerIfNeededUpdateContainerState: containerState " + containerState + " -> ABSENT");
            containerState = ABSENT;
        }
        Optional<String> restartReason = shouldRestartServices(nodeSpec);
        if (restartReason.isPresent()) {
            Optional<Container> existingContainer = dockerOperations.getContainer(hostname);
            if (existingContainer.isPresent()) {
                logger.info("Will restart services for container " + existingContainer.get() + ": " + restartReason.get());
                restartServices(nodeSpec, existingContainer.get(), orchestrator);
            }
        }
    }

    private Optional<String> shouldRestartServices(ContainerNodeSpec nodeSpec) {
        if ( ! nodeSpec.wantedRestartGeneration.isPresent()) return Optional.empty();

        if (! nodeSpec.currentRestartGeneration.isPresent() ||
                nodeSpec.currentRestartGeneration.get() < nodeSpec.wantedRestartGeneration.get()) {
            return Optional.of("Restart requested - wanted restart generation has been bumped: "
                    + nodeSpec.currentRestartGeneration.get() + " -> " + nodeSpec.wantedRestartGeneration.get());
        }
        return Optional.empty();
    }

    private void restartServices(ContainerNodeSpec nodeSpec, Container existingContainer, Orchestrator orchestrator)
            throws Exception {
        if (existingContainer.isRunning) {
            ContainerName containerName = existingContainer.name;
            if (nodeSpec.nodeState == Node.State.active) {
                logger.info("Restarting services for " + containerName);
                // Since we are restarting the services we need to suspend the node.
                orchestratorSuspendNode(orchestrator, nodeSpec, logger);
                dockerOperations.restartServicesOnNode(containerName);
            }
        }
    }

    @Override
    public void stopServices(ContainerName containerName) {
            logger.info("Stopping services for " + containerName);
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
        if (!existingContainer.isRunning) {
            return Optional.of("Container no longer running");
        }
        return Optional.empty();
    }

    // Returns true if container is absent on return
    private boolean removeContainerIfNeeded(ContainerNodeSpec nodeSpec, String hostname, Orchestrator orchestrator)
            throws Exception {
        Optional<Container> existingContainer = dockerOperations.getContainer(hostname);
        if (!existingContainer.isPresent()) {
            return true;
        }

        Optional<String> removeReason = shouldRemoveContainer(nodeSpec, existingContainer.get());
        if (removeReason.isPresent()) {
            logger.info("Will remove container " + existingContainer.get() + ": " + removeReason.get());

            if (existingContainer.get().isRunning) {
                final ContainerName containerName = existingContainer.get().name;
                orchestratorSuspendNode(orchestrator, nodeSpec, logger);
                dockerOperations.trySuspendNode(containerName);
                stopServices(containerName);
            }
            dockerOperations.removeContainer(nodeSpec, existingContainer.get());
            metricReceiver.unsetMetricsForContainer(hostname);
            lastCpuMetric = new CpuUsageReporter();
            vespaVersion = Optional.empty();
            return true;
        }
        return false;
    }


    private void scheduleDownLoadIfNeeded(ContainerNodeSpec nodeSpec) {
        if (dockerOperations.shouldScheduleDownloadOfImage(nodeSpec.wantedDockerImage.get())) {
            if (nodeSpec.wantedDockerImage.get().equals(imageBeingDownloaded)) {
                // Downloading already scheduled, but not done.
                return;
            }
            imageBeingDownloaded = nodeSpec.wantedDockerImage.get();
            // Create a signalWorkToBeDone when download is finished.
            dockerOperations.scheduleDownloadOfImage(nodeSpec, this::signalWorkToBeDone);
        } else if (imageBeingDownloaded != null) { // Image was downloading, but now it's ready
            imageBeingDownloaded = null;
        }
    }

    @Override
    public void signalWorkToBeDone() {
        if (!workToDoNow) {
            addDebugMessage("Signaling work to be done");
        }

        synchronized (monitor) {
            workToDoNow = true;
            monitor.notifyAll();
        }
    }

    private void loop() {
        while (! terminated.get()) {
            synchronized (monitor) {
                long waittimeLeft = delaysBetweenEachTickMillis;
                while (waittimeLeft > 1 && !workToDoNow) {
                    Instant start = Instant.now();
                    try {
                        monitor.wait(waittimeLeft);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted, but ignoring this: " + hostname);
                        continue;
                    }
                    waittimeLeft -= Duration.between(start, Instant.now()).toMillis();
                }
                workToDoNow = false;
            }
            isFrozen.set(wantFrozen.get());
            if (isFrozen.get()) {
                addDebugMessage("loop: isFrozen");
            } else {
                try {
                    tick();
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
    }

    // Public for testing
    void tick() throws Exception {
        final ContainerNodeSpec nodeSpec = nodeRepository.getContainerNodeSpec(hostname)
                .orElseThrow(() ->
                        new IllegalStateException(String.format("Node '%s' missing from node repository.", hostname)));

        synchronized (monitor) {
            if (!nodeSpec.equals(lastNodeSpec)) {
                addDebugMessage("Loading new node spec: " + nodeSpec.toString());
                lastNodeSpec = nodeSpec;
            }
        }

        switch (nodeSpec.nodeState) {
            case ready:
            case reserved:
            case parked:
            case failed:
                removeContainerIfNeededUpdateContainerState(nodeSpec);
                updateNodeRepoWithCurrentAttributes(nodeSpec);
                break;
            case active:
                storageMaintainer.ifPresent(maintainer -> {
                    maintainer.removeOldFilesFromNode(nodeSpec.containerName);
                    maintainer.handleCoreDumpsForContainer(nodeSpec, environment);
                });
                scheduleDownLoadIfNeeded(nodeSpec);
                if (imageBeingDownloaded != null) {
                    addDebugMessage("Waiting for image to download " + imageBeingDownloaded.asString());
                    return;
                }
                removeContainerIfNeededUpdateContainerState(nodeSpec);

                startContainerIfNeeded(nodeSpec);
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
                orchestrator.resume(nodeSpec.hostname);
                break;
            case inactive:
                storageMaintainer.ifPresent(maintainer -> maintainer.removeOldFilesFromNode(nodeSpec.containerName));
                removeContainerIfNeededUpdateContainerState(nodeSpec);
                updateNodeRepoWithCurrentAttributes(nodeSpec);
                break;
            case provisioned:
            case dirty:
                storageMaintainer.ifPresent(maintainer -> maintainer.removeOldFilesFromNode(nodeSpec.containerName));
                removeContainerIfNeededUpdateContainerState(nodeSpec);
                logger.info("State is " + nodeSpec.nodeState + ", will delete application storage and mark node as ready");
                storageMaintainer.ifPresent(maintainer -> maintainer.archiveNodeData(nodeSpec.containerName));
                updateNodeRepoAndMarkNodeAsReady(nodeSpec);
                break;
            default:
                throw new RuntimeException("UNKNOWN STATE " + nodeSpec.nodeState.name());
        }
    }

    @SuppressWarnings("unchecked")
    public void updateContainerNodeMetrics() {
        ContainerNodeSpec nodeSpec;
        synchronized (monitor) {
            nodeSpec = lastNodeSpec;
        }

        if (nodeSpec == null || nodeSpec.nodeState != Node.State.active) return;
        Optional<Docker.ContainerStats> containerStats = dockerOperations.getContainerStats(nodeSpec.containerName);
        if ( ! containerStats.isPresent()) return;

        Docker.ContainerStats stats = containerStats.get();
        Dimensions.Builder dimensionsBuilder = new Dimensions.Builder()
                .add("host", hostname)
                .add("role", "tenants")
                .add("flavor", nodeSpec.nodeFlavor)
                .add("state", nodeSpec.nodeState.toString())
                .add("zone", environment.getZone())
                .add("parentHostname", environment.getParentHostHostname());

        nodeSpec.owner.ifPresent(owner ->
                dimensionsBuilder
                        .add("tenantName", owner.tenant)
                        .add("app", owner.application + "." + owner.instance));

        nodeSpec.membership.ifPresent(membership ->
                dimensionsBuilder
                        .add("clustertype", membership.clusterType)
                        .add("clusterid", membership.clusterId));

        vespaVersion.ifPresent(version -> dimensionsBuilder.add("vespaVersion", version));

        Dimensions dimensions = dimensionsBuilder.build();
        long currentCpuContainerTotalTime = ((Number) ((Map) stats.getCpuStats().get("cpu_usage")).get("total_usage")).longValue();
        long currentCpuSystemTotalTime = ((Number) stats.getCpuStats().get("system_cpu_usage")).longValue();

        double cpuPercentage = lastCpuMetric.getCpuUsagePercentage(currentCpuContainerTotalTime, currentCpuSystemTotalTime);
        metricReceiver.declareGauge(dimensions, "node.cpu.busy.pct").sample(cpuPercentage);

        addIfNotNull(dimensions, "node.cpu.throttled_time", stats.getCpuStats().get("throttling_data"), "throttled_time");
        addIfNotNull(dimensions, "node.memory.limit", stats.getMemoryStats(), "limit");
        addIfNotNull(dimensions, "node.memory.usage", stats.getMemoryStats(), "usage");

        stats.getNetworks().forEach((interfaceName, interfaceStats) -> {
            Dimensions netDims = dimensionsBuilder.add("interface", interfaceName).build();

            addIfNotNull(netDims, "node.network.bytes_rcvd", interfaceStats, "rx_bytes");
            addIfNotNull(netDims, "node.network.bytes_sent", interfaceStats, "tx_bytes");
        });

        storageMaintainer.ifPresent(maintainer -> maintainer
                .updateIfNeededAndGetDiskMetricsFor(nodeSpec.containerName)
                .forEach((metricName, metricValue) ->
                        metricReceiver.declareGauge(dimensions, metricName).sample(metricValue.doubleValue())));
    }

    @SuppressWarnings("unchecked")
    private void addIfNotNull(Dimensions dimensions, String yamasName, Object metrics, String metricName) {
        Map<String, Object> metricsMap = (Map<String, Object>) metrics;
        if (metricsMap == null || !metricsMap.containsKey(metricName)) return;
        try {
            metricReceiver.declareGauge(dimensions, yamasName).sample(((Number) metricsMap.get(metricName)).doubleValue());
        } catch (Throwable e) {
            logger.warning("Failed to update " + yamasName + " metric with value " + metricsMap.get(metricName), e);
        }
    }

    public Optional<ContainerNodeSpec> getContainerNodeSpec() {
        synchronized (monitor) {
            return Optional.ofNullable(lastNodeSpec);
        }
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

    private void configureContainerMetrics(ContainerNodeSpec nodeSpec) {
        if (! storageMaintainer.isPresent()) return;
        final Path yamasAgentFolder = environment.pathInNodeAdminFromPathInNode(nodeSpec.containerName, "/etc/yamas-agent/");

        Path vespaCheckPath = Paths.get(getDefaults().underVespaHome("libexec/yms/yms_check_vespa"));
        SecretAgentScheduleMaker scheduleMaker = new SecretAgentScheduleMaker("vespa", 60, vespaCheckPath, "all")
                .withTag("namespace", "Vespa")
                .withTag("role", "tenants")
                .withTag("flavor", nodeSpec.nodeFlavor)
                .withTag("state", nodeSpec.nodeState.toString())
                .withTag("zone", environment.getZone())
                .withTag("parentHostname", environment.getParentHostHostname());

        nodeSpec.owner.ifPresent(owner ->
                scheduleMaker
                        .withTag("tenantName", owner.tenant)
                        .withTag("app", owner.application + "." + owner.instance));

        nodeSpec.membership.ifPresent(membership ->
                scheduleMaker
                        .withTag("clustertype", membership.clusterType)
                        .withTag("clusterid", membership.clusterId));

        vespaVersion.ifPresent(version -> scheduleMaker.withTag("vespaVersion", version));

        try {
            scheduleMaker.writeTo(yamasAgentFolder);
            final String[] restartYamasAgent = new String[] {"service" , "yamas-agent", "restart"};
            dockerOperations.executeCommandInContainer(nodeSpec.containerName, restartYamasAgent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write secret-agent schedules for " + nodeSpec.containerName, e);
        }
    }

    class CpuUsageReporter {
        private long totalContainerUsage = 0;
        private long totalSystemUsage = 0;

        double getCpuUsagePercentage(long currentContainerUsage, long currentSystemUsage) {
            long deltaSystemUsage = currentSystemUsage - totalSystemUsage;
            double cpuUsagePct = (deltaSystemUsage == 0 || totalSystemUsage == 0) ?
                    0 : 100.0 * (currentContainerUsage - totalContainerUsage) / deltaSystemUsage;

            totalContainerUsage = currentContainerUsage;
            totalSystemUsage = currentSystemUsage;
            return cpuUsagePct;
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
    private void orchestratorSuspendNode(Orchestrator orchestrator, ContainerNodeSpec nodeSpec, PrefixLogger logger) throws OrchestratorException {
        final String hostname = nodeSpec.hostname;
        logger.info("Ask Orchestrator for permission to suspend node " + hostname);
        if ( ! orchestrator.suspend(hostname)) {
            logger.info("Orchestrator rejected suspend of node " + hostname);
            // TODO: change suspend() to throw an exception if suspend is denied
            throw new OrchestratorException("Failed to get permission to suspend " + hostname);
        }
    }
}
