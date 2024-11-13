// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.impl;

import com.yahoo.tensor.Label;
import com.yahoo.tensor.Tensor;

/**
 * {@link Label} implementation used by {@link LabelCache}.
 * 
 * @author glebashnik
 */
class LabelImpl implements Label {
    private final long numeric;
    private String string = null;
    
    LabelImpl(long numeric) {
        this.numeric = numeric;
    }

    LabelImpl(long numeric, String string) {
        this.numeric = numeric;
        this.string = string;
    }

    public long toNumeric() {
        return numeric;
    }
    
    public String toString() {
        if (numeric == Tensor.invalidIndex) {
            return null;
        }
        // String label for indexed dimension are created at runtime to reduce memory usage.
        if (string == null) {
            return String.valueOf(numeric);
        }
        
        return string;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        var label = (LabelImpl) object;
        return numeric == label.numeric;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(numeric);
    }
}
