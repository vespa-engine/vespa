// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.binaryprefix;

/**
 * An amount scaled by a binary prefix.
 *
 * <p>
 * Examples: 2 kilo, 2 mega, ...
 * </p>
 *
 * @author Tony Vaagenes
 */
public final class BinaryScaledAmount {

    public final double amount;
    public final BinaryPrefix binaryPrefix;

    public BinaryScaledAmount(double amount, BinaryPrefix binaryPrefix) {
        this.amount = amount;
        this.binaryPrefix = binaryPrefix;
    }

    public BinaryScaledAmount() {
        this(0, BinaryPrefix.unit);
    }

    public long as(BinaryPrefix newBinaryPrefix) {
        return Math.round(newBinaryPrefix.convertFrom(amount, binaryPrefix));
    }

    public boolean equals(BinaryScaledAmount candidate) {
        return BinaryPrefix.unit.convertFrom(amount, binaryPrefix) ==
                BinaryPrefix.unit.convertFrom(candidate.amount, candidate.binaryPrefix);
    }

    public BinaryScaledAmount multiply(double d) {
        return new BinaryScaledAmount(d*amount, binaryPrefix);
    }

    public BinaryScaledAmount divide(double d) {
        return multiply(1/d);
    }

    @Override
    public boolean equals(Object candidate) {
        if (!(candidate instanceof BinaryScaledAmount)) {
            return false;
        } else {
            return equals((BinaryScaledAmount)candidate);
        }
    }

    @Override
    public int hashCode() {
        return (int)BinaryPrefix.unit.convertFrom(amount, binaryPrefix);
    }

}
