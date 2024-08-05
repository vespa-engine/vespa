// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Immutable representation of the high-level difference between two distribution configs.
 *
 * <code>toString()</code> gives a human-readable summary of the diff.
 *
 * See <code>DistributionDiffCalculatorTest</code> for examples of diff output.
 */
public class DistributionDiff {

    public record NodeDiff(List<Integer> added, List<Integer> removed) {}

    public record IntegerDiff(int before, int after) {

        boolean differs() { return before != after; }

        static IntegerDiff of(int before, int after) {
            return new IntegerDiff(before, after);
        }

    }

    // (Ordered) mapping from canonical group name to a node diff for that particular group.
    private final TreeMap<String, NodeDiff> groupDiffs;
    private final IntegerDiff groupCountDiff;
    private final IntegerDiff redundancyDiff;
    private final IntegerDiff searchableCopiesDiff;

    public DistributionDiff(TreeMap<String, NodeDiff> groupDiffs,
                            IntegerDiff groupCountDiff,
                            IntegerDiff redundancyDiff,
                            IntegerDiff searchableCopiesDiff) {
        this.groupCountDiff = groupCountDiff;
        this.groupDiffs = groupDiffs;
        this.redundancyDiff = redundancyDiff;
        this.searchableCopiesDiff = searchableCopiesDiff;
    }

    @Override
    public String toString() {
        var diffs = new ArrayList<String>();
        addIfDiffers(redundancyDiff, "redundancy", diffs);
        addIfDiffers(searchableCopiesDiff, "searchable-copies", diffs);
        addIfDiffers(groupCountDiff, "groups", diffs);
        for (var kv : groupDiffs.entrySet()) {
            addGroupDiffStringIfDiffers(kv, diffs);
        }
        return String.join(", ", diffs);
    }

    private static void addIfDiffers(IntegerDiff diff, String name, List<String> diffs) {
        if (diff.differs()) {
            diffs.add("%s: %d -> %d".formatted(name, diff.before, diff.after));
        }
    }

    private static void addGroupDiffStringIfDiffers(Map.Entry<String, NodeDiff> kv, List<String> diffs) {
        var v = kv.getValue();
        if (v.added.isEmpty() && v.removed.isEmpty()) {
            return;
        }
        var sb = new StringBuilder();
        sb.append(kv.getKey()).append(": ");
        if (!v.added.isEmpty()) {
            appendIntsAsSet(sb, "added", v.added);
        }
        if (!v.removed.isEmpty()) {
            if (!v.added.isEmpty()) {
                sb.append(' ');
            }
            appendIntsAsSet(sb, "removed", v.removed);
        }
        diffs.add(sb.toString());
    }

    private static void appendIntsAsSet(StringBuilder sb, String setName, List<Integer> ints) {
        sb.append(setName).append(" {");
        sb.append(ints.stream().map(i -> Integer.toString(i)).collect(Collectors.joining(", ")));
        sb.append('}');
    }

}
