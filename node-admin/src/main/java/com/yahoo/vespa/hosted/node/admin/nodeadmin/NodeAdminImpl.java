// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.maintenance.MaintenanceScheduler;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Administers a host (for now only docker hosts) and its nodes (docker containers nodes).
 *
 * @author stiankri
 */
public class NodeAdminImpl implements NodeAdmin {
    private static final PrefixLogger logger = PrefixLogger.getNodeAdminLogger(NodeAdmin.class);

    private static final long MIN_AGE_IMAGE_GC_MILLIS = Duration.ofMinutes(15).toMillis();

    private final Docker docker;
    private final Function<HostName, NodeAgent> nodeAgentFactory;
    private final MaintenanceScheduler maintenanceScheduler;
    private AtomicBoolean frozen = new AtomicBoolean(false);

    private final Map<HostName, NodeAgent> nodeAgents = new HashMap<>();

    private Map<DockerImage, Long> firstTimeEligibleForGC = Collections.emptyMap();

    private final int nodeAgentScanIntervalMillis;

    /**
     * @param docker interface to docker daemon and docker-related tasks
     * @param nodeAgentFactory factory for {@link NodeAgent} objects
     */
    public NodeAdminImpl(final Docker docker, final Function<HostName, NodeAgent> nodeAgentFactory,
                         final MaintenanceScheduler maintenanceScheduler, int nodeAgentScanIntervalMillis) {
        this.docker = docker;
        this.nodeAgentFactory = nodeAgentFactory;
        this.maintenanceScheduler = maintenanceScheduler;
        this.nodeAgentScanIntervalMillis = nodeAgentScanIntervalMillis;
    }

    public void refreshContainersToRun(final List<ContainerNodeSpec> containersToRun) {
        final List<Container> existingContainers = docker.getAllManagedContainers();

        maintenanceScheduler.cleanNodeAdmin();
        synchronizeNodeSpecsToNodeAgents(containersToRun, existingContainers);
        garbageCollectDockerImages(containersToRun);
    }

    public boolean freezeNodeAgentsAndCheckIfAllFrozen() {
        for (NodeAgent nodeAgent : nodeAgents.values()) {
            // We could make this blocking, this could speed up the suspend call a bit, but not sure if it is
            // worth it (it could block the rest call for some time and might have implications).
            nodeAgent.freeze();
        }
        for (NodeAgent nodeAgent : nodeAgents.values()) {
            if (! nodeAgent.isFrozen()) {
                return false;
            }
        }
        return true;
    }

    public void unfreezeNodeAgents() {
        for (NodeAgent nodeAgent : nodeAgents.values()) {
            nodeAgent.unfreeze();
        }
    }

    public boolean isFrozen() {
        return frozen.get();
    }

    public void setFrozen(boolean frozen) {
        this.frozen.set(frozen);
    }

    public Set<HostName> getListOfHosts() {
        return nodeAgents.keySet();
    }

    @Override
    public Map<String, Object> debugInfo() {
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("isFrozen", frozen);

        List<Map<String, Object>> nodeAgentDebugs = nodeAgents.entrySet().stream()
                .map(node -> node.getValue().debugInfo()).collect(Collectors.toList());
        debug.put("NodeAgents", nodeAgentDebugs);
        return debug;
    }

    @Override
    public void shutdown() {
        for (NodeAgent nodeAgent : nodeAgents.values()) {
            nodeAgent.stop();
        }
    }

    private void garbageCollectDockerImages(final List<ContainerNodeSpec> containersToRun) {
        final long currentTime = System.currentTimeMillis();
        Set<DockerImage> imagesToSpare = containersToRun.stream()
                .flatMap(nodeSpec -> streamOf(nodeSpec.wantedDockerImage))
                .filter(image -> currentTime - firstTimeEligibleForGC.getOrDefault(image, currentTime) > MIN_AGE_IMAGE_GC_MILLIS)
                .collect(Collectors.toSet());

        docker.deleteUnusedDockerImages(imagesToSpare);
    }

    // Turns an Optional<T> into a Stream<T> of length zero or one depending upon whether a value is present.
    // This is a workaround for Java 8 not having Stream.flatMap(Optional).
    private static <T> Stream<T> streamOf(Optional<T> opt) {
        return opt.map(Stream::of)
                .orElseGet(Stream::empty);
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

    // TODO This method should rather take a list of Hostname instead of Container. However, it triggers
    // a refactoring of the logic. Which is hard due to the style of programming.
    // The method streams the list of containers twice.
    void synchronizeNodeSpecsToNodeAgents(
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
                ensureNodeAgentForNodeIsStarted(nodeSpec.get());
            } catch (IOException e) {
                logger.warning("Failed to bring container to desired state", e);
            }
        });
    }

    private void ensureNodeAgentForNodeIsStarted(final ContainerNodeSpec nodeSpec) throws IOException {
        if (nodeAgents.containsKey(nodeSpec.hostname)) {
            return;
        }
        final NodeAgent agent = nodeAgentFactory.apply(nodeSpec.hostname);
        nodeAgents.put(nodeSpec.hostname, agent);
        agent.start(nodeAgentScanIntervalMillis);
    }
}
