// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.TensorAddress;

import java.util.Arrays;

import static java.lang.Math.abs;

/**
 * An n-dimensional address.
 *
 * @author baldersheim
 */
final class TensorAddressAnyN extends TensorAddressAny {

    private final int[] labels;

    TensorAddressAnyN(int[] labels) {
        if (labels.length < 1) throw new IllegalArgumentException("Need at least 1 label");
        this.labels = labels;
    }

    @Override public int size() { return labels.length; }

    @Override public long numericLabel(int i) { return labels[i]; }

    @Override
    public TensorAddress withLabel(int labelIndex, long label) {
        int[] copy = Arrays.copyOf(labels, labels.length);
        copy[labelIndex] = Convert.safe2Int(label);
        return new TensorAddressAnyN(copy);
    }

    @Override public int hashCode() {
        int hash = abs(labels[0]);
        for (int i = 0; i < size(); i++) {
            hash = hash | (abs(labels[i]) << (32 - Integer.numberOfLeadingZeros(hash)));
        }
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof TensorAddressAnyN any) || (size() != any.size())) return false;
        for (int i = 0; i < size(); i++) {
            if (labels[i] != any.labels[i]) return false;
        }
        return true;
    }

}
