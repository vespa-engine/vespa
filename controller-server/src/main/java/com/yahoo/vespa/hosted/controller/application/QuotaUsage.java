// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * @author ogronnesby
 */
public class QuotaUsage {

    public static final QuotaUsage none = new QuotaUsage(0.0);

    private final double rate;

    private QuotaUsage(double rate) {
        this.rate = rate;
    }

    public double rate() {
        return rate;
    }

    public QuotaUsage add(QuotaUsage addend) {
        return create(rate + addend.rate);
    }

    public QuotaUsage sub(QuotaUsage subtrahend) {
        return create(rate - subtrahend.rate);
    }

    public static QuotaUsage create(OptionalDouble rate) {
        if (rate.isEmpty()) {
            return QuotaUsage.none;
        }
        return new QuotaUsage(rate.getAsDouble());
    }

    public static QuotaUsage create(double rate) {
        return new QuotaUsage(rate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuotaUsage that = (QuotaUsage) o;
        return Double.compare(that.rate, rate) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rate);
    }

    @Override
    public String toString() {
        return "QuotaUsage{" +
                "rate=" + rate +
                '}';
    }

}
