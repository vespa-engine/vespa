// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Quota information transmitted to the configserver on deploy.  All limits are represented
 * with an Optional type where the empty optional means unlimited resource use.
 *
 * @author andreer
 * @author ogronnesby
 */
public class Quota {
    private static final Quota UNLIMITED = new Quota(OptionalInt.empty(), Optional.empty());
    private static final Quota ZERO = new Quota(OptionalInt.of(0), Optional.of(BigDecimal.ZERO));

    private final OptionalInt maxClusterSize;
    private final Optional<BigDecimal> budget; // in USD/hr, as calculated by NodeResources

    private Quota(OptionalInt maxClusterSize, Optional<BigDecimal> budget) {
        this.maxClusterSize = Objects.requireNonNull(maxClusterSize);
        this.budget = Objects.requireNonNull(budget);
    }

    public Quota withMaxClusterSize(int clusterSize) {
        return new Quota(OptionalInt.of(clusterSize), budget);
    }

    /** Construct a Quota that allows zero resource usage */
    public static Quota zero() {
        return ZERO;
    }

    /** Construct a Quota that allows unlimited resource usage */
    public static Quota unlimited() {
        return UNLIMITED;
    }

    public boolean isUnlimited() {
        return budget.isEmpty() && maxClusterSize().isEmpty();
    }

    public Quota withBudget(BigDecimal budget) {
        return new Quota(maxClusterSize, Optional.ofNullable(budget));
    }

    public Quota withBudget(int budget) {
        return withBudget(BigDecimal.valueOf(budget));
    }

    public Quota withoutBudget() {
        return new Quota(maxClusterSize, Optional.empty());
    }

    /** Maximum number of nodes in a cluster in a Vespa deployment */
    public OptionalInt maxClusterSize() {
        return maxClusterSize;
    }

    /** Maximum $/hour run-rate for the Vespa deployment */
    public Optional<BigDecimal> budget() {
        return budget;
    }

    public Quota subtractUsage(double rate) {
        if (budget().isEmpty()) return this; // (unlimited - rate) is still unlimited
        return this.withBudget(budget().get().subtract(BigDecimal.valueOf(rate)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quota quota = (Quota) o;
        return maxClusterSize.equals(quota.maxClusterSize) &&
                this.budget.map(BigDecimal::stripTrailingZeros).equals(
                        quota.budget.map(BigDecimal::stripTrailingZeros));
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
