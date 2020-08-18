package com.yahoo.config.model.api;

import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;

import java.util.Optional;

public class Quota {
    private final Optional<Integer> maxClusterSize;
    private final Optional<Integer> budget;

    private Quota(Optional<Integer> maybeClusterSize, Optional<Integer> budget) {
        this.maxClusterSize = maybeClusterSize;
        this.budget = budget;
    }

    public static Quota fromSlime(Inspector inspector) {
        var clusterSize = SlimeUtils.optionalLong(inspector.field("clusterSize"));
        var budget = SlimeUtils.optionalLong(inspector.field("budget"));
        return new Quota(clusterSize.map(Long::intValue), budget.map(Long::intValue));
    }

    public static Quota empty() {
        return new Quota(Optional.empty(), Optional.empty());
    }

    public Optional<Integer> maxClusterSize() {
        return maxClusterSize;
    }

    public Optional<Integer> budget() {
        return budget;
    }
}
