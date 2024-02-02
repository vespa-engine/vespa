// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.TensorAddress;

import static java.lang.Math.abs;

/**
 * A four-dimensional address.
 *
 * @author baldersheim
 */
final class TensorAddressAny4 extends TensorAddressAny {

    private final int label0, label1, label2, label3;

    TensorAddressAny4(int label0, int label1, int label2, int label3) {
        this.label0 = label0;
        this.label1 = label1;
        this.label2 = label2;
        this.label3 = label3;
    }

    @Override public int size() { return 4; }

    @Override
    public long numericLabel(int i) {
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
            case 0 -> new TensorAddressAny4(Convert.safe2Int(label), label1, label2, label3);
            case 1 -> new TensorAddressAny4(label0, Convert.safe2Int(label), label2, label3);
            case 2 -> new TensorAddressAny4(label0, label1, Convert.safe2Int(label), label3);
            case 3 -> new TensorAddressAny4(label0, label1, label2, Convert.safe2Int(label));
            default -> throw new IllegalArgumentException("No label " + labelIndex);
        };
    }

    @Override
    public int hashCode() {
        return abs(label0) |
                (abs(label1) << (1*32 - Integer.numberOfLeadingZeros(abs(label0)))) |
                (abs(label2) << (2*32 - (Integer.numberOfLeadingZeros(abs(label0)) + Integer.numberOfLeadingZeros(abs(label1))))) |
                (abs(label3) << (3*32 - (Integer.numberOfLeadingZeros(abs(label0)) + Integer.numberOfLeadingZeros(abs(label1)) + Integer.numberOfLeadingZeros(abs(label1)))));
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TensorAddressAny4 any) &&
                (label0 == any.label0) &&
                (label1 == any.label1) &&
                (label2 == any.label2) &&
                (label3 == any.label3);
    }

}
