package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.Objects;
import java.util.Optional;

/**
 * Quota information transmitted to the configserver on deploy.
 */
public class Quota {

    private final Optional<Integer> maxClusterSize;
    private final Optional<Integer> budget; // in USD/hr, as calculated by NodeResources

    public Quota(int maxClusterSize) {
        this(Optional.of(maxClusterSize), Optional.empty());
    }

    public Quota(int maxClusterSize, int dollarsPerHour) {
        this(Optional.of(maxClusterSize), Optional.of(dollarsPerHour));
    }

    public Quota(Optional<Integer> maxClusterSize, Optional<Integer> budget) {
        this.maxClusterSize = Objects.requireNonNull(maxClusterSize);
        this.budget = Objects.requireNonNull(budget);
    }

    public Optional<Integer> maxClusterSize() {
        return maxClusterSize;
    }

    public Optional<Integer> budget() {
        return budget;
    }

    public Quota withMaxClusterSize(int clusterSize) {
        return new Quota(Optional.of(clusterSize), budget);
    }

    public Quota withBudget(int budget) {
        return new Quota(maxClusterSize, Optional.of(budget));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quota quota = (Quota) o;
        return maxClusterSize.equals(quota.maxClusterSize) &&
                budget.equals(quota.budget);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxClusterSize, budget);
    }

    @Override
    public String toString() {
        return "Quota{" +
                "maxClusterSize=" + maxClusterSize +
                ", budget=" + budget +
                '}';
    }
}
