// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

}
