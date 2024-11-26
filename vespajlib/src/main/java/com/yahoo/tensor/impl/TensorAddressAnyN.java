// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.Label;
import com.yahoo.tensor.TensorAddress;

import java.util.Arrays;

/**
 * An n-dimensional address.
 *
 * @author baldersheim
 */
final class TensorAddressAnyN extends TensorAddressAny {

    private final Label[] labels;

    TensorAddressAnyN(Label[] labels) {
        if (labels.length < 1) throw new IllegalArgumentException("Need at least 1 label");
        this.labels = labels;
    }

    @Override public int size() { 
        return labels.length; 
    }

    @Override
    public Label objectLabel(int i) {
        if (i < 0 || i >= size()) 
            throw new IndexOutOfBoundsException("Index is not in [0," + (size() - 1) + "]: " + i);
        
        return labels[i]; 
    }

    @Override
    public TensorAddress withLabel(int labelIndex, long label) {
        var copy = Arrays.copyOf(labels, labels.length);
        copy[labelIndex] = LabelCache.GLOBAL.getOrCreateLabel(label);
        return new TensorAddressAnyN(copy);
    }

    // Same as Arrays.hashCode(labels) but without null checks.
    @Override public int hashCode() {
        int result = 1;

        for (var label : labels)
            result = 31 * result + label.hashCode();

        return result;
     }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof TensorAddressAnyN any) || (size() != any.size())) return false;
        for (int i = 0; i < size(); i++) {
            if (!labels[i].isEqualTo(any.labels[i])) return false;
        }
        return true;
    }

}
