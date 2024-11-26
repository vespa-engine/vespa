// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.impl;


import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.yahoo.tensor.Label;
import com.yahoo.tensor.Tensor;

/**
 * {@link Label} implementation used by {@link LabelCache}.
 * 
 * @author glebashnik
 */
class LabelImpl implements Label {
    // Using caching function with avalanche effect to avoid too many hash bucket collisions.
    private static final HashFunction hashFunction = Hashing.murmur3_32_fixed();
    
    private final long numeric;
    private final String string;
    
    // Caching the hash code to avoid recalculating it when cached labels are reused in multiple tensors.
    private final int hashCode;
    
    LabelImpl(long numeric) {
        this.numeric = numeric;
        this.string = null;
        this.hashCode = hashFunction.hashLong(numeric).asInt();
    }

    LabelImpl(long numeric, String string) {
        this.numeric = numeric;
        this.string = string;
        this.hashCode = hashFunction.hashLong(numeric).asInt();
    }

    @Override
    public long asNumeric() {
        return numeric;
    }

    @Override
    public String asString() {
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
    public boolean isEqualTo(Label label) {
        return asNumeric() == label.asNumeric();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        var label = (LabelImpl) object;
        return isEqualTo(label);
    }
    
    @Override
    public int hashCode() {
        return hashCode;
    }
}
