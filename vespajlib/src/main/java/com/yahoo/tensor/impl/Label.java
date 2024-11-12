// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.impl;
import com.yahoo.tensor.Tensor;

/**
 * A label for a tensor dimension.
 * It works for both mapped dimensions with string labels and indexed dimensions with numeric labels.
 * For mapped dimensions, a negative numeric label is assigned by LabelCache.
 * For indexed dimension, the index itself is used as a positive numeric label.
 * Tensor operations rely on the numeric label for performance.
 * 
 * @author glebashnik
 */
public class Label {
    public static final Label INVALID_INDEX_LABEL = new Label(Tensor.invalidIndex, null);

    private final long numeric;
    private String string = null;
    
    Label(long numeric) {
        this.numeric = numeric;
    }

    Label(long numeric, String string) {
        this.numeric = numeric;
        this.string = string;
    }

    public long toNumeric() {
        return numeric;
    }
    
    public String toString() {
        if (numeric == INVALID_INDEX_LABEL.numeric) {
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
        var label = (Label) object;
        return numeric == label.numeric;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(numeric);
    }
}
