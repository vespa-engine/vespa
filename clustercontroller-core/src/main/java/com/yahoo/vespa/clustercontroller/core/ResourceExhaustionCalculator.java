// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Given a mapping of (opaque) resource names and their exclusive limits,
 * this class acts as an utility to easily enumerate all the resources that
 * a given node (or set of nodes) have exhausted.
 */
public class ResourceExhaustionCalculator {

    private final boolean feedBlockEnabled;
    private final Map<String, Double> feedBlockLimits;

    public ResourceExhaustionCalculator(boolean feedBlockEnabled, Map<String, Double> feedBlockLimits) {
        this.feedBlockEnabled = feedBlockEnabled;
        this.feedBlockLimits = feedBlockLimits;
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
                .map(n -> String.format("%s on node %s (%.3g > %.3g)",
                        n.resourceType, n.node.getIndex(),
                        n.resourceUsage.getUsage(), n.limit))
                .collect(Collectors.joining(", "));
        if (exhaustions.size() > maxDescriptions) {
            description += String.format(" (... and %d more)", exhaustions.size() - maxDescriptions);
        }
        return ClusterStateBundle.FeedBlock.blockedWithDescription(description);
    }

    public List<NodeResourceExhaustion> resourceExhaustionsFromHostInfo(Node node, HostInfo hostInfo) {
        List<NodeResourceExhaustion> exceedingLimit = null;
        for (var usage : hostInfo.getContentNode().getResourceUsage().entrySet()) {
            double limit = feedBlockLimits.getOrDefault(usage.getKey(), 1.0);
            if (usage.getValue().getUsage() > limit) {
                if (exceedingLimit == null) {
                    exceedingLimit = new ArrayList<>();
                }
                exceedingLimit.add(new NodeResourceExhaustion(node, usage.getKey(), usage.getValue(), limit));
            }
        }
        return (exceedingLimit != null) ? exceedingLimit : Collections.emptyList();
    }

    public List<NodeResourceExhaustion> enumerateNodeResourceExhaustions(NodeInfo nodeInfo) {
        if (!nodeInfo.isStorage()) {
            return Collections.emptyList();
        }
        return resourceExhaustionsFromHostInfo(nodeInfo.getNode(), nodeInfo.getHostInfo());
    }

    // Returns 0-n entries per content node in the cluster, where n is the number of exhausted
    // resource types on any given node.
    public List<NodeResourceExhaustion> enumerateNodeResourceExhaustionsAcrossAllNodes(Collection<NodeInfo> nodeInfos) {
        return nodeInfos.stream()
                .flatMap(info -> enumerateNodeResourceExhaustions(info).stream())
                .collect(Collectors.toList());
    }

}
