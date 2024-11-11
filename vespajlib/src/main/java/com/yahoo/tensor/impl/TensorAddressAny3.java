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
    private final Label label0, label1, label2;

    TensorAddressAny3(Label label0, Label label1, Label label2) {
        this.label0 = label0;
        this.label1 = label1;
        this.label2 = label2;
    }

    @Override public int size() { return 3; }

    @Override
    public long numericLabel(int i) {
        return switch (i) {
            case 0 -> label0.toNumeric();
            case 1 -> label1.toNumeric();
            case 2 -> label2.toNumeric();
            default -> throw new IndexOutOfBoundsException("Index is not in [0,2]: " + i);
        };
    }

    @Override
    public TensorAddress withLabel(int labelIndex, long label) {
        return switch (labelIndex) {
            case 0 -> new TensorAddressAny3(LabelCache.getOrCreateLabel(label), label1, label2);
            case 1 -> new TensorAddressAny3(label0, LabelCache.getOrCreateLabel(label), label2);
            case 2 -> new TensorAddressAny3(label0, label1, LabelCache.getOrCreateLabel(label));
            default -> throw new IllegalArgumentException("No label " + labelIndex);
        };
    }

    @Override
    public int hashCode() {
        long hash =  abs(label0.toNumeric()) |
                (abs(label1.toNumeric()) << (64 - Long.numberOfLeadingZeros(abs(label0.toNumeric())))) |
                (abs(label2.toNumeric()) << (2*64 - (Long.numberOfLeadingZeros(abs(label0.toNumeric())) + Long.numberOfLeadingZeros(abs(label1.toNumeric())))));
        return (int) hash;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TensorAddressAny3 any) &&
                (label0.equals(any.label0)) &&
                (label1.equals(any.label1)) &&
                (label2.equals(any.label2));
    }
}
