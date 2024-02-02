// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.TensorAddress;

/**
 * A zero-dimensional address.
 *
 * @author baldersheim
 */
final class TensorAddressEmpty extends TensorAddressAny {

    static TensorAddress empty = new TensorAddressEmpty();

    private TensorAddressEmpty() {}

    @Override public int size() { return 0; }

    @Override public long numericLabel(int i) { throw new IllegalArgumentException("Empty address with no labels"); }

    @Override
    public TensorAddress withLabel(int labelIndex, long label) {
        throw new IllegalArgumentException("No label " + labelIndex);
    }

    @Override
    public int hashCode() { return 0; }

    @Override
    public boolean equals(Object o) { return o instanceof TensorAddressEmpty; }

}
