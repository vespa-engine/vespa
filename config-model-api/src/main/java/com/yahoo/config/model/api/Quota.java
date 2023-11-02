// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Quota for the application deployed.  If the application exceeds this quota, deployment will fail.
 *
 * @author ogronnesby
 */
public class Quota {
    private static final Quota UNLIMITED = new Quota(Optional.empty(), Optional.empty());

    /** The max size of a cluster inside application */
    private final Optional<Integer> maxClusterSize;

    /** The max budget in dollars per hour */
    private final Optional<BigDecimal> budget;

    public Quota(Optional<Integer> maxClusterSize, Optional<BigDecimal> budget) {
        this.maxClusterSize = Objects.requireNonNull(maxClusterSize);
        this.budget = Objects.requireNonNull(budget);
    }

    public static Quota fromSlime(Inspector inspector) {
        var clusterSize = SlimeUtils.optionalInteger(inspector.field("clusterSize"));
        var budget = budgetFromSlime(inspector.field("budget"));
        return new Quota(clusterSize.stream().boxed().findFirst(), budget);
    }

    public Quota withBudget(BigDecimal budget) {
        return new Quota(this.maxClusterSize, Optional.of(budget));
    }

    public Quota withClusterSize(int clusterSize) {
        return new Quota(Optional.of(clusterSize), this.budget);
    }

    public Slime toSlime() {
        var slime = new Slime();
        toSlime(slime.setObject());
        return slime;
    }

    public void toSlime(Cursor root) {
        maxClusterSize.ifPresent(clusterSize -> root.setLong("clusterSize", clusterSize));
        budget.ifPresent(b -> root.setString("budget", b.toPlainString()));
    }

    public static Quota unlimited() { return UNLIMITED; }

    public Optional<Integer> maxClusterSize() {
        return maxClusterSize;
    }

    public Optional<BigDecimal> budgetAsDecimal() { return budget; }

    public Optional<Integer> budget() { return budget.map(BigDecimal::intValue); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quota quota = (Quota) o;
        return Objects.equals(maxClusterSize, quota.maxClusterSize) &&
                Objects.equals(budget, quota.budget);
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

    /**
     * Since Slime does not support any decimal numeric value that isn't a floating point of some sort, we need
     * to be liberal in what we accept.  Since we are dealing with currency, ideally we would have a decimal
     * data type all the way through.
     *
     * There are three ways of communicating the budget to the Quota class:
     *   1. A LONG means we are looking at the budget in whole dollars.  This is the legacy way.
     *   2. A STRING formatted as a number is a full precision decimal number.  This is the proper way.
     *   3. A DOUBLE gets translated into a decimal type, but loses precision.  This is the CYA way.
     */
    private static Optional<BigDecimal> budgetFromSlime(Inspector inspector) {
        if (inspector.type() == Type.STRING) return Optional.of(inspector.asString()).map(BigDecimal::new);
        if (inspector.type() == Type.LONG) return Optional.of(inspector.asLong()).map(BigDecimal::new);
        if (inspector.type() == Type.DOUBLE) return Optional.of(inspector.asDouble()).map(BigDecimal::new);
        return Optional.empty();
    }
}
