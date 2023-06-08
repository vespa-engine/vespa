// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.flags.DoubleFlag;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeMembership;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.reports.DropDocumentsReport;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.OrchestratorException;
import com.yahoo.vespa.hosted.node.admin.container.Container;
import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.container.ContainerResources;
import com.yahoo.vespa.hosted.node.admin.container.RegistryCredentials;
import com.yahoo.vespa.hosted.node.admin.container.RegistryCredentialsProvider;
import com.yahoo.vespa.hosted.node.admin.maintenance.ContainerWireguardTask;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.identity.CredentialsMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.servicedump.VespaServiceDumper;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.ConvergenceException;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.flags.FetchVector.Dimension.APPLICATION_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CLUSTER_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CLUSTER_TYPE;
import static com.yahoo.vespa.flags.FetchVector.Dimension.HOSTNAME;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextSupplier.ContextSupplierInterruptedException;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.ABSENT;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.STARTING;
import static com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl.ContainerState.UNKNOWN;

/**
 * @author dybis
 * @author bakksjo
 */
public class NodeAgentImpl implements NodeAgent {

    // Container is started with uncapped CPU and is kept that way until the first successful health check + this duration
    // Subtract 1 second to avoid warmup coming in lockstep with tick time and always end up using an extra tick when there are just a few ms left
    private static final Duration DEFAULT_WARM_UP_DURATION = Duration.ofSeconds(90).minus(Duration.ofSeconds(1));

    private static final Logger logger = Logger.getLogger(NodeAgentImpl.class.getName());

    private final NodeAgentContextSupplier contextSupplier;
    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final ContainerOperations containerOperations;
    private final RegistryCredentialsProvider registryCredentialsProvider;
    private final StorageMaintainer storageMaintainer;
    private final List<CredentialsMaintainer> credentialsMaintainers;
    private final Optional<AclMaintainer> aclMaintainer;
    private final Optional<HealthChecker> healthChecker;
    private final Timer timer;
    private final Duration warmUpDuration;
    private final DoubleFlag containerCpuCap;
    private final VespaServiceDumper serviceDumper;
    private final List<ContainerWireguardTask> wireguardTasks;

    private Thread loopThread;
    private ContainerState containerState = UNKNOWN;
    private NodeSpec lastNode;

    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private boolean hasResumedNode = false;
    private boolean hasStartedServices = true;
    private Optional<Instant> firstSuccessfulHealthCheckInstant = Optional.empty();
    private boolean suspendedInOrchestrator = false;

    private int numberOfUnhandledException = 0;
    private long currentRebootGeneration = 0;
    private Optional<Long> currentRestartGeneration = Optional.empty();

    /**
     * ABSENT means container is definitely absent - A container that was absent will not suddenly appear without
     * NodeAgent explicitly starting it.
     * STARTING state is set just before we attempt to start a container, if successful we move to the next state.
     * Otherwise we can't be certain. A container that was running a minute ago may no longer be running without
     * NodeAgent doing anything (container could have crashed). Therefore we always have to ask docker daemon
     * to get updated state of the container.
     */
    enum ContainerState {
        ABSENT,
        STARTING,
        UNKNOWN
    }


    public NodeAgentImpl(NodeAgentContextSupplier contextSupplier, NodeRepository nodeRepository,
                         Orchestrator orchestrator, ContainerOperations containerOperations,
                         RegistryCredentialsProvider registryCredentialsProvider, StorageMaintainer storageMaintainer,
                         FlagSource flagSource, List<CredentialsMaintainer> credentialsMaintainers,
                         Optional<AclMaintainer> aclMaintainer, Optional<HealthChecker> healthChecker, Timer timer,
                         VespaServiceDumper serviceDumper, List<ContainerWireguardTask> wireguardTasks) {
        this(contextSupplier, nodeRepository, orchestrator, containerOperations, registryCredentialsProvider,
             storageMaintainer, flagSource, credentialsMaintainers, aclMaintainer, healthChecker, timer,
             DEFAULT_WARM_UP_DURATION, serviceDumper, wireguardTasks);
    }

    public NodeAgentImpl(NodeAgentContextSupplier contextSupplier, NodeRepository nodeRepository,
                         Orchestrator orchestrator, ContainerOperations containerOperations,
                         RegistryCredentialsProvider registryCredentialsProvider, StorageMaintainer storageMaintainer,
                         FlagSource flagSource, List<CredentialsMaintainer> credentialsMaintainers,
                         Optional<AclMaintainer> aclMaintainer, Optional<HealthChecker> healthChecker, Timer timer,
                         Duration warmUpDuration, VespaServiceDumper serviceDumper,
                         List<ContainerWireguardTask> wireguardTasks) {
        this.contextSupplier = contextSupplier;
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.containerOperations = containerOperations;
        this.registryCredentialsProvider = registryCredentialsProvider;
        this.storageMaintainer = storageMaintainer;
        this.credentialsMaintainers = credentialsMaintainers;
        this.aclMaintainer = aclMaintainer;
        this.healthChecker = healthChecker;
        this.timer = timer;
        this.warmUpDuration = warmUpDuration;
        this.containerCpuCap = PermanentFlags.CONTAINER_CPU_CAP.bindTo(flagSource);
        this.serviceDumper = serviceDumper;
        this.wireguardTasks = new ArrayList<>(wireguardTasks);
    }

    @Override
    public void start(NodeAgentContext initialContext) {
        if (loopThread != null)
            throw new IllegalStateException("Can not re-start a node agent.");

        loopThread = new Thread(() -> {
            while (!terminated.get()) {
                try {
                    converge(contextSupplier.nextContext());
                } catch (ContextSupplierInterruptedException ignored) { }
            }
        });
        loopThread.setName("tick-" + initialContext.hostname());
        loopThread.start();
    }

    @Override
    public void stopForRemoval(NodeAgentContext context) {
        if (!terminated.compareAndSet(false, true))
            throw new IllegalStateException("Can not re-stop a node agent.");

        contextSupplier.interrupt();

        do {
            try {
                loopThread.join();
            } catch (InterruptedException ignored) { }
        } while (loopThread.isAlive());

        context.log(logger, "Stopped");
    }

    void startServicesIfNeeded(NodeAgentContext context) {
        if (!hasStartedServices) {
            context.log(logger, "Invoking vespa-nodectl to start services");
            String output = containerOperations.startServices(context);
            if (!output.isBlank()) {
                context.log(logger, "Start services output: " + output);
            }
            hasStartedServices = true;
        }
    }

    void resumeNodeIfNeeded(NodeAgentContext context) {
        if (!hasResumedNode) {
            context.log(logger, "Invoking vespa-nodectl to resume services");
            String output = containerOperations.resumeNode(context);
            if (!output.isBlank()) {
                context.log(logger, "Resume services output: " + output);
            }
            hasResumedNode = true;
        }
    }

    private void updateNodeRepoWithCurrentAttributes(NodeAgentContext context, Optional<Instant> containerCreatedAt) {
        final NodeAttributes currentNodeAttributes = new NodeAttributes();
        final NodeAttributes newNodeAttributes = new NodeAttributes();
        boolean changed = false;

        if (context.node().wantedRestartGeneration().isPresent() &&
                !Objects.equals(context.node().currentRestartGeneration(), currentRestartGeneration)) {
            currentNodeAttributes.withRestartGeneration(context.node().currentRestartGeneration());
            newNodeAttributes.withRestartGeneration(currentRestartGeneration);
            changed = true;
        }

        boolean createdAtAfterRebootedEvent = context.node().events().stream()
                .filter(event -> event.type().equals("rebooted"))
                .map(event -> containerCreatedAt
                        .map(createdAt -> createdAt.isAfter(event.at()))
                        .orElse(false)) // Container not created
                .findFirst()
                .orElse(containerCreatedAt.isPresent()); // No rebooted event
        if (!Objects.equals(context.node().currentRebootGeneration(), currentRebootGeneration) || createdAtAfterRebootedEvent) {
            currentNodeAttributes.withRebootGeneration(context.node().currentRebootGeneration());
            newNodeAttributes.withRebootGeneration(currentRebootGeneration);
            changed = true;
        }

        Optional<DockerImage> wantedDockerImage = context.node().wantedDockerImage().filter(n -> containerState == UNKNOWN);
        if (!Objects.equals(context.node().currentDockerImage(), wantedDockerImage)) {
            DockerImage currentImage = context.node().currentDockerImage().orElse(DockerImage.EMPTY);
            DockerImage newImage = wantedDockerImage.orElse(DockerImage.EMPTY);

            currentNodeAttributes.withDockerImage(currentImage);
            currentNodeAttributes.withVespaVersion(context.node().currentVespaVersion().orElse(Version.emptyVersion));
            newNodeAttributes.withDockerImage(newImage);
            newNodeAttributes.withVespaVersion(context.node().wantedVespaVersion().orElse(Version.emptyVersion));
            changed = true;
        }

        Optional<DropDocumentsReport> report = context.node().reports().getReport(DropDocumentsReport.reportId(), DropDocumentsReport.class);
        if (report.isPresent() && report.get().startedAt() == null && report.get().readiedAt() != null) {
            newNodeAttributes.withReport(DropDocumentsReport.reportId(), report.get().withStartedAt(timer.currentTimeMillis()).toJsonNode());
            changed = true;
        }

        if (changed) {
            context.log(logger, "Publishing new set of attributes to node repo: %s -> %s",
                    currentNodeAttributes, newNodeAttributes);
            nodeRepository.updateNodeAttributes(context.hostname().value(), newNodeAttributes);
        }
    }

    private Container startContainer(NodeAgentContext context) {
        ContainerResources wantedResources = warmUpDuration(context).isNegative() ?
                getContainerResources(context) : getContainerResources(context).withUnlimitedCpus();
        ContainerData containerData = containerOperations.createContainer(context, wantedResources);
        writeContainerData(context, containerData);
        containerOperations.startContainer(context);

        currentRebootGeneration = context.node().wantedRebootGeneration();
        currentRestartGeneration = context.node().wantedRestartGeneration();
        hasStartedServices = true; // Automatically started with the container
        hasResumedNode = false;
        context.log(logger, "Container successfully started, new containerState is " + containerState);
        return containerOperations.getContainer(context).orElseThrow(() ->
                ConvergenceException.ofError("Did not find container that was just started"));
    }

    private Optional<Container> removeContainerIfNeededUpdateContainerState(
            NodeAgentContext context, Optional<Container> existingContainer) {
        if (existingContainer.isPresent()) {
            List<String> reasons = shouldRemoveContainer(context, existingContainer.get());
            if (!reasons.isEmpty()) {
                removeContainer(context, existingContainer.get(), reasons, false);
                return Optional.empty();
            }

            shouldRestartServices(context, existingContainer.get()).ifPresent(restartReason -> {
                context.log(logger, "Invoking vespa-nodectl to restart services: " + restartReason);
                orchestratorSuspendNode(context);

                ContainerResources currentResources = existingContainer.get().resources();
                ContainerResources wantedResources = currentResources.withUnlimitedCpus();
                if ( ! warmUpDuration(context).isNegative() && ! wantedResources.equals(currentResources)) {
                    context.log(logger, "Updating container resources: %s -> %s",
                            existingContainer.get().resources().toStringCpu(), wantedResources.toStringCpu());
                    containerOperations.updateContainer(context, existingContainer.get().id(), wantedResources);
                }

                String output = containerOperations.restartVespa(context);
                if ( ! output.isBlank()) {
                    context.log(logger, "Restart services output: " + output);
                }
                currentRestartGeneration = context.node().wantedRestartGeneration();
                firstSuccessfulHealthCheckInstant = Optional.empty();
            });
        }

        return existingContainer;
    }

    private Optional<String> shouldRestartServices(NodeAgentContext context, Container existingContainer) {
        NodeSpec node = context.node();
        if (!existingContainer.state().isRunning() || node.state() != NodeState.active) return Optional.empty();

        // Restart generation is only optional because it does not exist for unallocated nodes
        if (currentRestartGeneration.get() < node.wantedRestartGeneration().get()) {
            return Optional.of("Restart requested - wanted restart generation has been bumped: "
                    + currentRestartGeneration.get() + " -> " + node.wantedRestartGeneration().get());
        }

        return Optional.empty();
    }

    private void stopServicesIfNeeded(NodeAgentContext context) {
        if (hasStartedServices && context.node().owner().isEmpty())
            stopServices(context);
    }

    private void stopServices(NodeAgentContext context) {
        context.log(logger, "Stopping services");
        if (containerState == ABSENT) return;
        hasStartedServices = hasResumedNode = false;
        firstSuccessfulHealthCheckInstant = Optional.empty();
        containerOperations.stopServices(context);
    }

    @Override
    public void stopForHostSuspension(NodeAgentContext context) {
        getContainer(context).ifPresent(container -> removeContainer(context, container, List.of("Suspending host"), true));
    }

    public void suspend(NodeAgentContext context) {
        if (containerState == ABSENT) return;
        try {
            hasResumedNode = false;
            context.log(logger, "Invoking vespa-nodectl to suspend services");
            String output = containerOperations.suspendNode(context);
            if (!output.isBlank()) {
                context.log(logger, "Suspend services output: " + output);
            }
        } catch (RuntimeException e) {
            // It's bad to continue as-if nothing happened, but on the other hand if we do not proceed to
            // remove container, we will not be able to upgrade to fix any problems in the suspend logic!
            context.log(logger, Level.WARNING, "Failed trying to suspend container", e);
        }
    }

    private List<String> shouldRemoveContainer(NodeAgentContext context, Container existingContainer) {
        final NodeState nodeState = context.node().state();
        List<String> reasons = new ArrayList<>();
        if (nodeState == NodeState.dirty || nodeState == NodeState.provisioned)
            reasons.add("Node in state " + nodeState + ", container should no longer be running");

        if (context.node().wantedDockerImage().isPresent() &&
                !context.node().wantedDockerImage().get().equals(existingContainer.image())) {
            reasons.add("The node is supposed to run a new Docker image: "
                        + existingContainer.image().asString() + " -> " + context.node().wantedDockerImage().get().asString());
        }

        if (!existingContainer.state().isRunning())
            reasons.add("Container no longer running");

        if (currentRebootGeneration < context.node().wantedRebootGeneration()) {
            reasons.add(String.format("Container reboot wanted. Current: %d, Wanted: %d",
                    currentRebootGeneration, context.node().wantedRebootGeneration()));
        }

        ContainerResources wantedContainerResources = getContainerResources(context);
        if (!wantedContainerResources.equalsMemory(existingContainer.resources())) {
            reasons.add("Container should be running with different memory allocation, wanted: " +
                        wantedContainerResources.toStringMemory() + ", actual: " + existingContainer.resources().toStringMemory());
        }

        if (containerState == STARTING)
            reasons.add("Container failed to start");

        return reasons;
    }

    private void removeContainer(NodeAgentContext context, Container existingContainer, List<String> reasons, boolean alreadySuspended) {
        context.log(logger, "Will remove container: " + String.join(", ", reasons));

        if (existingContainer.state().isRunning()) {
            if (!alreadySuspended) {
                orchestratorSuspendNode(context);
            }

            try {
                if (context.node().state() == NodeState.active) {
                    suspend(context);
                }
                stopServices(context);
            } catch (Exception e) {
                context.log(logger, Level.WARNING, "Failed stopping services, ignoring", e);
            }
        }

        storageMaintainer.handleCoreDumpsForContainer(context, Optional.of(existingContainer), true);
        containerOperations.removeContainer(context, existingContainer);
        containerState = ABSENT;
        context.log(logger, "Container successfully removed, new containerState is " + containerState);
    }


    private Container updateContainerIfNeeded(NodeAgentContext context, Container existingContainer) {
        ContainerResources wantedContainerResources = getContainerResources(context);

        if (healthChecker.isPresent() && firstSuccessfulHealthCheckInstant
                .map(timer.currentTime().minus(warmUpDuration(context))::isBefore)
                .orElse(true))
            return existingContainer;

        if (wantedContainerResources.equalsCpu(existingContainer.resources())) return existingContainer;
        context.log(logger, "Container should be running with different CPU allocation, wanted: %s, current: %s",
                    wantedContainerResources.toStringCpu(), existingContainer.resources().toStringCpu());

        // Only update CPU resources
        containerOperations.updateContainer(context, existingContainer.id(), wantedContainerResources.withMemoryBytes(existingContainer.resources().memoryBytes()));
        return containerOperations.getContainer(context).orElseThrow(() ->
                ConvergenceException.ofError("Did not find container that was just updated"));
    }

    private ContainerResources getContainerResources(NodeAgentContext context) {
        double cpuCap = context.vcpuOnThisHost() * containerCpuCap
                               .with(APPLICATION_ID, context.node().owner().map(ApplicationId::serializedForm))
                               .with(CLUSTER_ID, context.node().membership().map(NodeMembership::clusterId))
                               .with(CLUSTER_TYPE, context.node().membership().map(membership -> membership.type().value()))
                               .with(HOSTNAME, context.node().hostname())
                               .value();

        return ContainerResources.from(cpuCap, context.vcpuOnThisHost(), context.node().memoryGb());
    }

    private boolean downloadImageIfNeeded(NodeAgentContext context, Optional<Container> container) {
        NodeSpec node = context.node();
        if (node.wantedDockerImage().equals(container.map(c -> c.image()))) return false;

        RegistryCredentials credentials = registryCredentialsProvider.get();
        return node.wantedDockerImage()
                   .map(image -> containerOperations.pullImageAsyncIfNeeded(context, image, credentials))
                   .orElse(false);
    }

    private void dropDocsIfNeeded(NodeAgentContext context, Optional<Container> container) {
        Optional<DropDocumentsReport> report = context.node().reports()
                .getReport(DropDocumentsReport.reportId(), DropDocumentsReport.class);
        if (report.isEmpty() || report.get().readiedAt() != null) return;

        if (report.get().droppedAt() == null) {
            container.ifPresent(c -> removeContainer(context, c, List.of("Dropping documents"), true));
            FileFinder.from(context.paths().underVespaHome("var/db/vespa/search")).deleteRecursively(context);
            nodeRepository.updateNodeAttributes(context.node().hostname(),
                    new NodeAttributes().withReport(DropDocumentsReport.reportId(), report.get().withDroppedAt(timer.currentTimeMillis()).toJsonNode()));
        }

        throw ConvergenceException.ofTransient("Documents already dropped, waiting for signal to start the container");
    }

    public void converge(NodeAgentContext context) {
        try {
            doConverge(context);
            context.log(logger, Level.INFO, "Converged");
        } catch (ConvergenceException e) {
            context.log(logger, e.getMessage());
            if (e.isError())
                numberOfUnhandledException++;
        } catch (Throwable e) {
            numberOfUnhandledException++;
            context.log(logger, Level.SEVERE, "Unhandled exception, ignoring", e);
        }
    }

    // Non-private for testing
    void doConverge(NodeAgentContext context) {
        NodeSpec node = context.node();
        Optional<Container> container = getContainer(context);

        // Current reboot generation uninitialized or incremented from outside to cancel reboot
        if (currentRebootGeneration < node.currentRebootGeneration())
            currentRebootGeneration = node.currentRebootGeneration();

        // Either we have changed allocation status (restart gen. only available to allocated nodes), or
        // restart generation has been incremented from outside to cancel restart
        if (currentRestartGeneration.isPresent() != node.currentRestartGeneration().isPresent() ||
                currentRestartGeneration.map(current -> current < node.currentRestartGeneration().get()).orElse(false))
            currentRestartGeneration = node.currentRestartGeneration();

        if (!node.equals(lastNode)) {
            logChangesToNodeSpec(context, lastNode, node);
            lastNode = node;
        }

        switch (node.state()) {
            case ready:
            case reserved:
            case failed:
            case inactive:
            case parked:
                storageMaintainer.syncLogs(context, true);
                removeContainerIfNeededUpdateContainerState(context, container);
                updateNodeRepoWithCurrentAttributes(context, Optional.empty());
                stopServicesIfNeeded(context);
                break;
            case active:
                storageMaintainer.syncLogs(context, true);
                storageMaintainer.cleanDiskIfFull(context);
                storageMaintainer.handleCoreDumpsForContainer(context, container, false);

                if (downloadImageIfNeeded(context, container)) {
                    context.log(logger, "Waiting for image to download " + context.node().wantedDockerImage().get().asString());
                    return;
                }
                dropDocsIfNeeded(context, container);
                container = removeContainerIfNeededUpdateContainerState(context, container);
                credentialsMaintainers.forEach(maintainer -> maintainer.converge(context));
                if (container.isEmpty()) {
                    containerState = STARTING;
                    container = Optional.of(startContainer(context));
                    containerState = UNKNOWN;
                } else {
                    container = Optional.of(updateContainerIfNeeded(context, container.get()));
                }

                aclMaintainer.ifPresent(maintainer -> maintainer.converge(context));
                final Optional<Container> finalContainer = container;
                wireguardTasks.forEach(task -> task.converge(context, finalContainer.get().id()));
                startServicesIfNeeded(context);
                resumeNodeIfNeeded(context);
                if (healthChecker.isPresent()) {
                    healthChecker.get().verifyHealth(context);
                    if (firstSuccessfulHealthCheckInstant.isEmpty())
                        firstSuccessfulHealthCheckInstant = Optional.of(timer.currentTime());

                    Duration timeLeft = Duration.between(timer.currentTime(), firstSuccessfulHealthCheckInstant.get().plus(warmUpDuration(context)));
                    if (!container.get().resources().equalsCpu(getContainerResources(context)))
                        throw ConvergenceException.ofTransient("Refusing to resume until warm up period ends (" +
                                (timeLeft.isNegative() ? "next tick" : "in " + timeLeft) + ")");
                }
                serviceDumper.processServiceDumpRequest(context);

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
                updateNodeRepoWithCurrentAttributes(context, container.map(Container::createdAt));
                if (suspendedInOrchestrator || node.orchestratorStatus().isSuspended()) {
                    context.log(logger, "Call resume against Orchestrator");
                    orchestrator.resume(context.hostname().value());
                    suspendedInOrchestrator = false;
                }
                break;
            case provisioned:
                nodeRepository.setNodeState(context.hostname().value(), NodeState.ready);
                break;
            case dirty:
                removeContainerIfNeededUpdateContainerState(context, container);
                context.log(logger, "State is " + node.state() + ", will delete application storage and mark node as ready");
                credentialsMaintainers.forEach(maintainer -> maintainer.clearCredentials(context));
                storageMaintainer.syncLogs(context, false);
                storageMaintainer.archiveNodeStorage(context);
                updateNodeRepoWithCurrentAttributes(context, Optional.empty());
                nodeRepository.setNodeState(context.hostname().value(), NodeState.ready);
                break;
            default:
                throw ConvergenceException.ofError("UNKNOWN STATE " + node.state().name());
        }
    }

    private static void logChangesToNodeSpec(NodeAgentContext context, NodeSpec lastNode, NodeSpec node) {
        StringBuilder builder = new StringBuilder();
        appendIfDifferent(builder, "state", lastNode, node, NodeSpec::state);
        if (builder.length() > 0) {
            context.log(logger, Level.INFO, "Changes to node: " + builder);
        }
    }

    private static <T> String fieldDescription(T value) {
        return value == null ? "[absent]" : value.toString();
    }

    private static <T> void appendIfDifferent(StringBuilder builder, String name, NodeSpec oldNode, NodeSpec newNode, Function<NodeSpec, T> getter) {
        T oldValue = oldNode == null ? null : getter.apply(oldNode);
        T newValue = getter.apply(newNode);
        if (!Objects.equals(oldValue, newValue)) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(name).append(" ").append(fieldDescription(oldValue)).append(" -> ").append(fieldDescription(newValue));
        }
    }

    private Optional<Container> getContainer(NodeAgentContext context) {
        if (containerState == ABSENT) return Optional.empty();
        Optional<Container> container = containerOperations.getContainer(context);
        if (container.isEmpty()) containerState = ABSENT;
        return container;
    }

    @Override
    public int getAndResetNumberOfUnhandledExceptions() {
        int temp = numberOfUnhandledException;
        numberOfUnhandledException = 0;
        return temp;
    }

    private void orchestratorSuspendNode(NodeAgentContext context) {
        if (context.node().state() != NodeState.active) return;

        context.log(logger, "Ask Orchestrator for permission to suspend node");
        try {
            orchestrator.suspend(context.hostname().value());
            suspendedInOrchestrator = true;
        } catch (OrchestratorException e) {
            // Ensure the ACLs are up to date: The reason we're unable to suspend may be because some other
            // node is unable to resume because the ACL rules of SOME Docker container is wrong...
            // Same can happen with stale WireGuard config, so update that too
            try {
                aclMaintainer.ifPresent(maintainer -> maintainer.converge(context));
                wireguardTasks.forEach(task -> getContainer(context).ifPresent(c -> task.converge(context, c.id())));
            } catch (RuntimeException suppressed) {
                logger.log(Level.WARNING, "Suppressing ACL update failure: " + suppressed);
                e.addSuppressed(suppressed);
            }

            throw e;
        }
    }

    protected void writeContainerData(NodeAgentContext context, ContainerData containerData) { }

    protected List<CredentialsMaintainer> credentialsMaintainers() {
        return credentialsMaintainers;
    }

    private Duration warmUpDuration(NodeAgentContext context) {
        ZoneApi zone = context.zone();
        Optional<NodeMembership> membership = context.node().membership();
        return zone.getEnvironment().isTest()
               || context.nodeType() != NodeType.tenant
               || membership.map(mem -> ! (mem.type().hasContainer() || mem.type().isAdmin())).orElse(false)
                ? Duration.ofSeconds(-1)
                : warmUpDuration.dividedBy(zone.getSystemName().isCd() ? 3 : 1);
    }

}
