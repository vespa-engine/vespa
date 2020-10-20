// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

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

    public static QuotaUsage create(OptionalDouble rate) {
        if (rate.isEmpty()) {
            return QuotaUsage.none;
        }
        return new QuotaUsage(rate.getAsDouble());
    }

    public static QuotaUsage create(double rate) {
        return new QuotaUsage(rate);
    }

    public static QuotaUsage sum(QuotaUsage a, QuotaUsage b) {
        return new QuotaUsage(a.rate + b.rate);
    }

    @Override
    public String toString() {
        return "QuotaUsage{" +
                "rate=" + rate +
                '}';
    }

}
