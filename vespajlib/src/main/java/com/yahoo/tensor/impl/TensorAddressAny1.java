// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.Label;
import com.yahoo.tensor.TensorAddress;

/**
 * A one-dimensional address.
 *
 * @author baldersheim
 */
final class TensorAddressAny1 extends TensorAddressAny {
    private final Label label;

    TensorAddressAny1(Label label) { this.label = label; }

    @Override public int size() { return 1; }
    
    @Override
    public Label objectLabel(int i) {
        if (i == 0) {
            return label;
        }
        throw new IndexOutOfBoundsException("Index is not zero: " + i);
    }

    @Override
    public TensorAddress withLabel(int labelIndex, long label) {
        if (labelIndex == 0) return new TensorAddressAny1(LabelCache.GLOBAL.getOrCreateLabel(label));
        throw new IllegalArgumentException("No label " + labelIndex);
    }

    @Override public int hashCode() { return (int)Math.abs(label.asNumeric()); }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TensorAddressAny1 any) && (label.asNumeric() == any.label.asNumeric());
    }

}
