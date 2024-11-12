// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.TensorAddress;

import static java.lang.Math.abs;

/**
 * A two-dimensional address.
 *
 * @author baldersheim
 */
final class TensorAddressAny2 extends TensorAddressAny {
    private final Label label0, label1;

    TensorAddressAny2(Label label0, Label label1) {
        this.label0 = label0;
        this.label1 = label1;
    }

    @Override public int size() { return 2; }

    @Override
    public Label objectLabel(int i) {
        return switch (i) {
            case 0 -> label0;
            case 1 -> label1;
            default -> throw new IndexOutOfBoundsException("Index is not in [0,1]: " + i);
        };
    }

    @Override
    public TensorAddress withLabel(int labelIndex, long label) {
        return switch (labelIndex) {
            case 0 -> new TensorAddressAny2(LabelCache.GLOBAL.getOrCreateLabel(label), label1);
            case 1 -> new TensorAddressAny2(label0, LabelCache.GLOBAL.getOrCreateLabel(label));
            default -> throw new IllegalArgumentException("No label " + labelIndex);
        };
    }

    @Override
    public int hashCode() {
        long hash =  abs(label0.toNumeric()) |
                (abs(label1.toNumeric()) << (64 - Long.numberOfLeadingZeros(abs(label0.toNumeric()))));
        return (int) hash;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TensorAddressAny2 any) &&
                (label0.equals(any.label0)) &&
                (label1.equals(any.label1));
    }
}
