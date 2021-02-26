// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Given a mapping of (opaque) resource names and their exclusive limits,
 * this class acts as an utility to easily enumerate all the resources that
 * a given node (or set of nodes) have exhausted.
 *
 * In order to support hysteresis, optionally takes in the _current_ feed
 * block state. This lets the calculator make the decision to emit a resource
 * exhaustion for a node that is technically below the feed block limit, as
 * long as it's not yet below the hysteresis threshold.
 */
public class ResourceExhaustionCalculator {

    private final boolean feedBlockEnabled;
    private final Map<String, Double> feedBlockLimits;
    private final double feedBlockNoiseLevel;
    private final Set<NodeAndResourceType> previouslyBlockedNodeResources;

    private static class NodeAndResourceType {
        public final int nodeIndex;
        public final String resourceType;

        public NodeAndResourceType(int nodeIndex, String resourceType) {
            this.nodeIndex = nodeIndex;
            this.resourceType = resourceType;
        }

        public static NodeAndResourceType of(int nodeIndex, String resourceType) {
            return new NodeAndResourceType(nodeIndex, resourceType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeAndResourceType that = (NodeAndResourceType) o;
            return nodeIndex == that.nodeIndex &&
                    Objects.equals(resourceType, that.resourceType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeIndex, resourceType);
        }
    }

    public ResourceExhaustionCalculator(boolean feedBlockEnabled, Map<String, Double> feedBlockLimits) {
        this.feedBlockEnabled = feedBlockEnabled;
        this.feedBlockLimits = feedBlockLimits;
        this.feedBlockNoiseLevel = 0.0;
        this.previouslyBlockedNodeResources = Collections.emptySet();
    }

    public ResourceExhaustionCalculator(boolean feedBlockEnabled, Map<String, Double> feedBlockLimits,
                                        ClusterStateBundle.FeedBlock previousFeedBlock,
                                        double feedBlockNoiseLevel) {
        this.feedBlockEnabled = feedBlockEnabled;
        this.feedBlockLimits = feedBlockLimits;
        this.feedBlockNoiseLevel = feedBlockNoiseLevel;
        if (previousFeedBlock != null) {
            this.previouslyBlockedNodeResources = previousFeedBlock.getConcreteExhaustions().stream()
                    .map(ex -> NodeAndResourceType.of(ex.node.getIndex(), ex.resourceType))
                    .collect(Collectors.toSet());
        } else {
            this.previouslyBlockedNodeResources = Collections.emptySet();
        }
    }

    public ClusterStateBundle.FeedBlock inferContentClusterFeedBlockOrNull(Collection<NodeInfo> nodeInfos) {
        if (!feedBlockEnabled) {
            return null;
        }
        var exhaustions = enumerateNodeResourceExhaustionsAcrossAllNodes(nodeInfos);
        if (exhaustions.isEmpty()) {
            return null;
        }
        int maxDescriptions = 3;
        String description = exhaustions.stream()
                .limit(maxDescriptions)
                .map(NodeResourceExhaustion::toExhaustionAddedDescription)
                .collect(Collectors.joining(", "));
        if (exhaustions.size() > maxDescriptions) {
            description += String.format(" (... and %d more)", exhaustions.size() - maxDescriptions);
        }
        // FIXME we currently will trigger a cluster state recomputation even if the number of
        // exhaustions is greater than what is returned as part of the description. Though at
        // that point, cluster state recomputations will be the least of your worries...!
        return ClusterStateBundle.FeedBlock.blockedWith(description, exhaustions);
    }

    public Set<NodeResourceExhaustion> resourceExhaustionsFromHostInfo(NodeInfo nodeInfo, HostInfo hostInfo) {
        Set<NodeResourceExhaustion> exceedingLimit = null;
        for (var usage : hostInfo.getContentNode().getResourceUsage().entrySet()) {
            double configuredLimit = feedBlockLimits.getOrDefault(usage.getKey(), 1.0);
            // To enable hysteresis on feed un-block we adjust the effective limit iff the particular
            // <node, resource> tuple was blocked in the previous state.
            boolean wasBlocked = previouslyBlockedNodeResources.contains(NodeAndResourceType.of(nodeInfo.getNodeIndex(), usage.getKey()));
            double effectiveLimit = wasBlocked ? Math.max(configuredLimit - feedBlockNoiseLevel, 0.0)
                                               : configuredLimit;
            if (usage.getValue().getUsage() > effectiveLimit) {
                if (exceedingLimit == null) {
                    exceedingLimit = new LinkedHashSet<>();
                }
                exceedingLimit.add(new NodeResourceExhaustion(nodeInfo.getNode(), usage.getKey(), usage.getValue(),
                                                              effectiveLimit, nodeInfo.getRpcAddress()));
            }
        }
        return (exceedingLimit != null) ? exceedingLimit : Collections.emptySet();
    }

    public Set<NodeResourceExhaustion> enumerateNodeResourceExhaustions(NodeInfo nodeInfo) {
        if (!nodeInfo.isStorage()) {
            return Collections.emptySet();
        }
        return resourceExhaustionsFromHostInfo(nodeInfo, nodeInfo.getHostInfo());
    }

    private static boolean nodeMayContributeToFeedBlocked(NodeInfo info) {
        return (info.getWantedState().getState().oneOf("ur") &&
                info.getReportedState().getState().oneOf("ui"));
    }

    // Returns 0-n entries per content node in the cluster, where n is the number of exhausted
    // resource types on any given node.
    public Set<NodeResourceExhaustion> enumerateNodeResourceExhaustionsAcrossAllNodes(Collection<NodeInfo> nodeInfos) {
        return nodeInfos.stream()
                .filter(info -> nodeMayContributeToFeedBlocked(info))
                .flatMap(info -> enumerateNodeResourceExhaustions(info).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
