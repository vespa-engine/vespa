// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.docker.Container;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Administers a host (for now only docker hosts) and its nodes (docker containers nodes).
 *
 * @author stiankri
 */
public class NodeAdminImpl implements NodeAdmin {
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
            nodeAgent.execute(NodeAgent.Command.FREEZE);
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
            nodeAgent.execute(NodeAgent.Command.UNFREEZE);
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
        agent.execute(NodeAgent.Command.UPDATE_FROM_NODE_REPO);
    }
}
