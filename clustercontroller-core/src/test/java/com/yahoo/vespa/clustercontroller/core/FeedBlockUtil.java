// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.hostinfo.ResourceUsage;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.vespa.clustercontroller.core.ClusterFixture.storageNode;

public class FeedBlockUtil {

    static class NodeAndUsages {
        public final int index;
        public final Set<UsageDetails> usages;

        public NodeAndUsages(int index, Set<UsageDetails> usages) {
            this.index = index;
            this.usages = usages;
        }
    }

    static class UsageDetails {
        public final String type;
        public final String name;
        public final double usage;

        public UsageDetails(String type, String name, double usage) {
            this.type = type;
            this.name = name;
            this.usage = usage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UsageDetails that = (UsageDetails) o;
            return Double.compare(that.usage, usage) == 0 &&
                    Objects.equals(type, that.type) &&
                    Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name, usage);
        }
    }

    static UsageDetails usage(String type, double usage) {
        return new UsageDetails(type, null, usage);
    }

    static UsageDetails usage(String type, String name, double usage) {
        return new UsageDetails(type, name, usage);
    }

    static Map<String, Double> mapOf(UsageDetails... usages) {
        return Arrays.stream(usages).collect(Collectors.toMap(u -> u.type, u -> u.usage));
    }

    static Set<UsageDetails> setOf(UsageDetails... usages) {
        // Preserve input order to make stringification tests deterministic
        return Arrays.stream(usages).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static NodeAndUsages forNode(int index, UsageDetails... usages) {
        return new NodeAndUsages(index, setOf(usages));
    }

    static String createResourceUsageJson(Set<UsageDetails> usages) {
        // We deal only in the finest of manual JSON string building technologies(tm).
        String usageInnerJson = usages.stream()
                .map(u -> String.format("\"%s\":{\"usage\": %.3g%s}",
                        u.type, u.usage,
                        (u.name != null ? String.format(",\"name\":\"%s\"", u.name) : "")))
                .collect(Collectors.joining(","));
        return String.format("{\"content-node\":{\"resource-usage\":{%s}}}", usageInnerJson);
    }

    static NodeResourceExhaustion exhaustion(int index, String type) {
        return new NodeResourceExhaustion(new Node(NodeType.STORAGE, index), type, new ResourceUsage(0.8, null), 0.7, "foo");
    }

    static NodeResourceExhaustion exhaustion(int index, String type, double usage) {
        return new NodeResourceExhaustion(new Node(NodeType.STORAGE, index), type, new ResourceUsage(usage, null), 0.7, "foo");
    }

    static Set<NodeResourceExhaustion> setOf(NodeResourceExhaustion... exhaustions) {
        return Arrays.stream(exhaustions).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static ClusterFixture createFixtureWithReportedUsages(NodeAndUsages... nodeAndUsages) {
        var highestIndex = Arrays.stream(nodeAndUsages).mapToInt(u -> u.index).max();
        if (highestIndex.isEmpty()) {
            throw new IllegalArgumentException("Can't have an empty cluster");
        }
        var cf = ClusterFixture
                .forFlatCluster(highestIndex.getAsInt() + 1)
                .assignDummyRpcAddresses()
                .bringEntireClusterUp();
        for (var nu : nodeAndUsages) {
            cf.cluster().getNodeInfo(storageNode(nu.index))
                    .setHostInfo(HostInfo.createHostInfo(createResourceUsageJson(nu.usages)));
        }
        return cf;
    }



}
