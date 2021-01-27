// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class FeedBlockUtil {

    static class NodeAndUsages {
        public final int index;
        public final Map<String, Double> usages;

        public NodeAndUsages(int index, Map<String, Double> usages) {
            this.index = index;
            this.usages = usages;
        }
    }

    static class NameAndUsage {
        public final String name;
        public final double usage;

        public NameAndUsage(String name, double usage) {
            this.name = name;
            this.usage = usage;
        }
    }

    static NameAndUsage usage(String name, double usage) {
        return new NameAndUsage(name, usage);
    }

    static Map<String, Double> mapOf(NameAndUsage... usages) {
        return Arrays.stream(usages).collect(Collectors.toMap(u -> u.name, u -> u.usage));
    }

    static NodeAndUsages forNode(int index, NameAndUsage... usages) {
        return new NodeAndUsages(index, mapOf(usages));
    }

    static String createResourceUsageJson(Map<String, Double> usages) {
        String usageInnerJson = usages.entrySet().stream()
                .map(kv -> String.format("\"%s\":{\"usage\": %.3g}", kv.getKey(), kv.getValue()))
                .collect(Collectors.joining(","));
        return String.format("{\"content-node\":{\"resource-usage\":{%s}}}", usageInnerJson);
    }

}
