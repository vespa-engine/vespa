// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.Label;
import com.yahoo.tensor.TensorAddress;

import java.util.Objects;

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

    // Same as Objects.hash(label1, label0) but a little faster since it avoids creating an array, loop and null checks.
    // The order of labels is important - it has a big impact on the performance of mapped tensors in a dot product.
    @Override
    public int hashCode() {
        return 31 * 31 
                + 31 * label1.hashCode() 
                + label0.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TensorAddressAny2 any) &&
                (label0.isEqualTo(any.label0)) &&
                (label1.isEqualTo(any.label1));
    }
}
