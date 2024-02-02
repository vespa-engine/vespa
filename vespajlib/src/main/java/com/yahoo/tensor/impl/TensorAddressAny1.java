// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.TensorAddress;

/**
 * A one-dimensional address.
 *
 * @author baldersheim
 */
final class TensorAddressAny1 extends TensorAddressAny {

    private final int label;

    TensorAddressAny1(int label) { this.label = label; }

    @Override public int size() { return 1; }

    @Override
    public long numericLabel(int i) {
        if (i == 0) {
            return label;
        }
        throw new IndexOutOfBoundsException("Index is not zero: " + i);
    }

    @Override
    public TensorAddress withLabel(int labelIndex, long label) {
        if (labelIndex == 0) return new TensorAddressAny1(Convert.safe2Int(label));
        throw new IllegalArgumentException("No label " + labelIndex);
    }

    @Override public int hashCode() { return Math.abs(label); }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TensorAddressAny1 any) && (label == any.label);
    }

}
