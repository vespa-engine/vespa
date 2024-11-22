// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.Label;
import com.yahoo.tensor.TensorAddress;

import static java.lang.Math.abs;

/**
 * A four-dimensional address.
 *
 * @author baldersheim
 */
final class TensorAddressAny4 extends TensorAddressAny {
    private final Label label0, label1, label2, label3;

    TensorAddressAny4(Label label0, Label label1, Label label2, Label label3) {
        this.label0 = label0;
        this.label1 = label1;
        this.label2 = label2;
        this.label3 = label3;
    }

    @Override public int size() { return 4; }

    @Override
    public Label objectLabel(int i) {
        return switch (i) {
            case 0 -> label0;
            case 1 -> label1;
            case 2 -> label2;
            case 3 -> label3;
            default -> throw new IndexOutOfBoundsException("Index is not in [0,3]: " + i);
        };
    }

    @Override
    public TensorAddress withLabel(int labelIndex, long label) {
        return switch (labelIndex) {
            case 0 -> new TensorAddressAny4(LabelCache.GLOBAL.getOrCreateLabel(label), label1, label2, label3);
            case 1 -> new TensorAddressAny4(label0, LabelCache.GLOBAL.getOrCreateLabel(label), label2, label3);
            case 2 -> new TensorAddressAny4(label0, label1, LabelCache.GLOBAL.getOrCreateLabel(label), label3);
            case 3 -> new TensorAddressAny4(label0, label1, label2, LabelCache.GLOBAL.getOrCreateLabel(label));
            default -> throw new IllegalArgumentException("No label " + labelIndex);
        };
    }

    @Override
    public int hashCode() {
        long hash =  abs(label0.asNumeric()) |
                (abs(label1.asNumeric()) << (64 - Long.numberOfLeadingZeros(abs(label0.asNumeric())))) |
                (abs(label2.asNumeric()) << (2*64 - (Long.numberOfLeadingZeros(abs(label0.asNumeric())) + Long.numberOfLeadingZeros(abs(label1.asNumeric()))))) |
                (abs(label3.asNumeric()) << (3*64 - (Long.numberOfLeadingZeros(abs(label0.asNumeric())) + Long.numberOfLeadingZeros(abs(label1.asNumeric())) + Long.numberOfLeadingZeros(abs(label1.asNumeric())))));
        return (int) hash;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TensorAddressAny4 any) &&
                (label0.isEqualTo(any.label0)) &&
                (label1.isEqualTo(any.label1)) &&
                (label2.isEqualTo(any.label2)) &&
                (label3.isEqualTo(any.label3));
    }
}
