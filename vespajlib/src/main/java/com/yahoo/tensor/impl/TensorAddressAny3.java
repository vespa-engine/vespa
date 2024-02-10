// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.TensorAddress;

import static java.lang.Math.abs;

/**
 * A three-dimensional address.
 *
 * @author baldersheim
 */
final class TensorAddressAny3 extends TensorAddressAny {

    private final long label0, label1, label2;

    TensorAddressAny3(long label0, long label1, long label2) {
        this.label0 = label0;
        this.label1 = label1;
        this.label2 = label2;
    }

    @Override public int size() { return 3; }

    @Override
    public long numericLabel(int i) {
        return switch (i) {
            case 0 -> label0;
            case 1 -> label1;
            case 2 -> label2;
            default -> throw new IndexOutOfBoundsException("Index is not in [0,2]: " + i);
        };
    }

    @Override
    public TensorAddress withLabel(int labelIndex, long label) {
        return switch (labelIndex) {
            case 0 -> new TensorAddressAny3(label, label1, label2);
            case 1 -> new TensorAddressAny3(label0, label, label2);
            case 2 -> new TensorAddressAny3(label0, label1, label);
            default -> throw new IllegalArgumentException("No label " + labelIndex);
        };
    }

    @Override
    public int hashCode() {
        long hash = abs(label0) |
                (abs(label1) << (1*64 - Long.numberOfLeadingZeros(abs(label0)))) |
                (abs(label2) << (2*64 - (Long.numberOfLeadingZeros(abs(label0)) + Long.numberOfLeadingZeros(abs(label1)))));
        return (int) hash;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TensorAddressAny3 any) &&
                (label0 == any.label0) &&
                (label1 == any.label1) &&
                (label2 == any.label2);
    }

}
