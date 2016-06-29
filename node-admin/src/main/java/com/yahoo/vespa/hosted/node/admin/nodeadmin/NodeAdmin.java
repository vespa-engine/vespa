package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.collections.Pair;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.Container;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin.NodeAdminStateUpdater.State.RESUMED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin.NodeAdminStateUpdater.State.SUSPENDED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * API for NodeAdmin seen from outside.
 * @author dybis
 */
public interface NodeAdmin {

    void refreshContainersToRun(final List<ContainerNodeSpec> containersToRun);

    boolean freezeAndCheckIfAllFrozen();

    void unfreeze();

    Set<HostName> getListOfHosts();

    String debugInfo();

    /**
     * Administers a host (for now only docker hosts) and its nodes (docker containers nodes).
     *
     * @author stiankri
     */
    class NodeAdminImpl implements NodeAdmin {
        private static final Logger logger = Logger.getLogger(NodeAdmin.class.getName());

        private static final long MIN_AGE_IMAGE_GC_MILLIS = Duration.ofMinutes(15).toMillis();

        private final Docker docker;
        private final Function<HostName, NodeAgent> nodeAgentFactory;

        private final Map<HostName, NodeAgent> nodeAgents = new HashMap<>();

        private Map<DockerImage, Long> firstTimeEligibleForGC = Collections.emptyMap();

        /**
         * @param docker interface to docker daemon and docker-related tasks
         * @param nodeAgentFactory factory for {@link NodeAgent} objects
         */
        public NodeAdminImpl(final Docker docker, final Function<HostName, NodeAgent> nodeAgentFactory) {
            this.docker = docker;
            this.nodeAgentFactory = nodeAgentFactory;
        }

        public void refreshContainersToRun(final List<ContainerNodeSpec> containersToRun) {
            final List<Container> existingContainers = docker.getAllManagedContainers();

            synchronizeLocalContainerState(containersToRun, existingContainers);

            garbageCollectDockerImages(containersToRun);
        }

        public boolean freezeAndCheckIfAllFrozen() {
            for (NodeAgent nodeAgent : nodeAgents.values()) {
                nodeAgent.execute(NodeAgent.Command.SET_FREEZE, false);
            }
            for (NodeAgent nodeAgent : nodeAgents.values()) {
                if (nodeAgent.getState() != NodeAgent.State.FROZEN) {
                    return false;
                }
            }
            return true;
        }

        public void unfreeze() {
            for (NodeAgent nodeAgent : nodeAgents.values()) {
                nodeAgent.execute(NodeAgent.Command.UNFREEZE, false);
            }
        }

        public Set<HostName> getListOfHosts() {
            return nodeAgents.keySet();
        }

        @Override
        public String debugInfo() {
            StringBuilder debug = new StringBuilder();
            for (Map.Entry<HostName, NodeAgent> node : nodeAgents.entrySet()) {
                debug.append("Node ").append(node.getKey().toString());
                debug.append(" state ").append(node.getValue().getState());
            }
            return debug.toString();
        }

        private void garbageCollectDockerImages(final List<ContainerNodeSpec> containersToRun) {
            final Set<DockerImage> deletableDockerImages = getDeletableDockerImages(
                    docker.getUnusedDockerImages(), containersToRun);
            final long currentTime = System.currentTimeMillis();
            // TODO: This logic should be unit tested.
            firstTimeEligibleForGC = deletableDockerImages.stream()
                    .collect(Collectors.toMap(
                            dockerImage -> dockerImage,
                            dockerImage -> Optional.ofNullable(firstTimeEligibleForGC.get(dockerImage)).orElse(currentTime)));
            // Delete images that have been eligible for some time.
            firstTimeEligibleForGC.forEach((dockerImage, timestamp) -> {
                if (currentTime - timestamp > MIN_AGE_IMAGE_GC_MILLIS) {
                    docker.deleteImage(dockerImage);
                }
            });
        }

        // Turns an Optional<T> into a Stream<T> of length zero or one depending upon whether a value is present.
        // This is a workaround for Java 8 not having Stream.flatMap(Optional).
        private static <T> Stream<T> streamOf(Optional<T> opt) {
            return opt.map(Stream::of)
                    .orElseGet(Stream::empty);
        }

        static Set<DockerImage> getDeletableDockerImages(
                final Set<DockerImage> currentlyUnusedDockerImages,
                final List<ContainerNodeSpec> pendingContainers) {
            final Set<DockerImage> imagesNeededNowOrInTheFuture = pendingContainers.stream()
                    .flatMap(nodeSpec -> streamOf(nodeSpec.wantedDockerImage))
                    .collect(Collectors.toSet());
            return diff(currentlyUnusedDockerImages, imagesNeededNowOrInTheFuture);
        }

        // Set-difference. Returns minuend minus subtrahend.
        private static <T> Set<T> diff(final Set<T> minuend, final Set<T> subtrahend) {
            final HashSet<T> result = new HashSet<>(minuend);
            result.removeAll(subtrahend);
            return result;
        }

        // Returns a full outer join of two data sources (of types T and U) on some extractable attribute (of type V).
        // Full outer join means that all elements of both data sources are included in the result,
        // even when there is no corresponding element (having the same attribute) in the other data set,
        // in which case the value from the other source will be empty.
        static <T, U, V> Stream<Pair<Optional<T>, Optional<U>>> fullOuterJoin(
                final Stream<T> tStream, final Function<T, V> tAttributeExtractor,
                final Stream<U> uStream, final Function<U, V> uAttributeExtractor) {
            final Map<V, T> tMap = tStream.collect(Collectors.toMap(tAttributeExtractor, t -> t));
            final Map<V, U> uMap = uStream.collect(Collectors.toMap(uAttributeExtractor, u -> u));
            return Stream.concat(tMap.keySet().stream(), uMap.keySet().stream())
                    .distinct()
                    .map(key -> new Pair<>(Optional.ofNullable(tMap.get(key)), Optional.ofNullable(uMap.get(key))));
        }

        void synchronizeLocalContainerState(
                final List<ContainerNodeSpec> containersToRun,
                final List<Container> existingContainers) {
            final Stream<Pair<Optional<ContainerNodeSpec>, Optional<Container>>> nodeSpecContainerPairs = fullOuterJoin(
                    containersToRun.stream(), nodeSpec -> nodeSpec.hostname,
                    existingContainers.stream(), container -> container.hostname);

            final Set<HostName> nodeHostNames = containersToRun.stream()
                    .map(spec -> spec.hostname)
                    .collect(Collectors.toSet());
            final Set<HostName> obsoleteAgentHostNames = diff(nodeAgents.keySet(), nodeHostNames);
            obsoleteAgentHostNames.forEach(hostName -> nodeAgents.remove(hostName).stop());

            nodeSpecContainerPairs.forEach(nodeSpecContainerPair -> {
                final Optional<ContainerNodeSpec> nodeSpec = nodeSpecContainerPair.getFirst();
                final Optional<Container> existingContainer = nodeSpecContainerPair.getSecond();

                if (!nodeSpec.isPresent()) {
                    assert existingContainer.isPresent();
                    logger.warning("Container " + existingContainer.get() + " exists, but is not in node repository runlist");
                    return;
                }

                try {
                    updateAgent(nodeSpec.get());
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Failed to bring container to desired state", e);
                }
            });
        }

        private void updateAgent(final ContainerNodeSpec nodeSpec) throws IOException {
            final NodeAgent agent;
            if (nodeAgents.containsKey(nodeSpec.hostname)) {
                agent = nodeAgents.get(nodeSpec.hostname);
            } else {
                agent = nodeAgentFactory.apply(nodeSpec.hostname);
                nodeAgents.put(nodeSpec.hostname, agent);
                agent.start();
            }
            agent.execute(NodeAgent.Command.UPDATE_FROM_NODE_REPO, false);
        }
    }

    /**
     * Pulls information from node repository and forwards containers to run to node admin.
     *
     * @author dybis, stiankri
     */
    class NodeAdminStateUpdater extends AbstractComponent {
        private static final Logger log = Logger.getLogger(NodeAdminStateUpdater.class.getName());

        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        private final NodeAdmin nodeAdmin;
        private boolean isRunningUpdates = true;
        private final Object monitor = new Object();
        final Orchestrator orchestrator;
        private final String baseHostName;

        public NodeAdminStateUpdater(
                final NodeRepository nodeRepository,
                final NodeAdmin nodeAdmin,
                long initialSchedulerDelayMillis,
                long intervalSchedulerInMillis,
                Orchestrator orchestrator,
                String baseHostName) {
            scheduler.scheduleWithFixedDelay(
                    ()-> fetchContainersToRunFromNodeRepository(nodeRepository),
                    initialSchedulerDelayMillis,
                    intervalSchedulerInMillis,
                    MILLISECONDS);
            this.nodeAdmin = nodeAdmin;
            this.orchestrator = orchestrator;
            this.baseHostName = baseHostName;
        }

        public String getDebugPage() {
            StringBuilder info = new StringBuilder();
            synchronized (monitor) {
                info.append("isRunningUpdates is " + isRunningUpdates+ ". ");
                info.append("NodeAdmin: ");
                info.append(nodeAdmin.debugInfo());
            }
            return info.toString();
        }

        public enum State { RESUMED, SUSPENDED}

        /**
         * @return empty on success and failure message on failure.
         */
        public Optional<String> setResumeStateAndCheckIfResumed(State wantedState) {
            synchronized (monitor) {
                isRunningUpdates = wantedState == RESUMED;

                if (wantedState == SUSPENDED) {
                    if (!nodeAdmin.freezeAndCheckIfAllFrozen()) {
                        return Optional.of("Not all node agents are frozen.");
                    }
                    List<String> hosts = new ArrayList<>();
                    nodeAdmin.getListOfHosts().forEach(host -> hosts.add(host.toString()));
                    return orchestrator.suspend(baseHostName, hosts);
                } else {
                    nodeAdmin.unfreeze();
                    // we let the NodeAgent do the resume against the orchestrator.
                    return Optional.empty();
                }
            }
        }

        private void fetchContainersToRunFromNodeRepository(final NodeRepository nodeRepository) {
            synchronized (monitor) {
                if (! isRunningUpdates) {
                    log.log(Level.FINE, "Is frozen, skipping");
                    return;
                }
                final List<ContainerNodeSpec> containersToRun;
                try {
                    containersToRun = nodeRepository.getContainersToRun();
                } catch (Throwable t) {
                    log.log(Level.WARNING, "Failed fetching container info from node repository", t);
                    return;
                }
                if (containersToRun == null) {
                    log.log(Level.WARNING, "Got null from NodeRepo.");
                    return;
                }
                try {
                    nodeAdmin.refreshContainersToRun(containersToRun);
                } catch (Throwable t) {
                    log.log(Level.WARNING, "Failed updating node admin: ", t);
                    return;
                }
            }
        }

        @Override
        public void deconstruct() {
            scheduler.shutdown();
            try {
                if (! scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Did not manage to shutdown scheduler.");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
