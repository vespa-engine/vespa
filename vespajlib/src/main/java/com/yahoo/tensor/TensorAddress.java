// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An immutable address to a tensor cell. This simply supplies a value to each dimension
 * in a particular tensor type.
 *
 * @author bratseth
 */
@Beta
public final class TensorAddress implements Comparable<TensorAddress> {

    public static final TensorAddress empty = new TensorAddress.Builder(TensorType.empty).build();

    private final ImmutableList<String> labels;

    public TensorAddress(String[] labels) {
        this.labels = ImmutableList.copyOf(labels);
    }

    /** Returns the labels of this as an immutable list in the order of the tensor this is the type of */
    public List<String> elements() { return labels; }

    @Override
    public int compareTo(TensorAddress other) {
        for (int i = 0; i < labels.size(); i++) {
            int elementComparison = this.labels.get(i).compareTo(other.labels.get(i));
            if (elementComparison != 0) return elementComparison;
        }

        return 0;
    }

    @Override
    public int hashCode() { return labels.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof TensorAddress)) return false;
        return ((TensorAddress)other).labels.equals(this.labels);
    }

    @Override
    public String toString() {
        return labels.toString();
    }

    /** Returns this on the appropriate form given the type */
    public String toString(TensorType type) {
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < labels.size(); i++) {
            b.append(type.dimensions().get(i).name()).append(":").append(labels.get(i));
            b.append(",");
        }
        if (b.length() > 1)
            b.setLength(b.length() - 1);
        b.append("}");
        return b.toString();
    }

    /** Supports building of a tensor address */
    public static class Builder {

        private final TensorType type;
        private final String[] labels;
        
        public Builder(TensorType type) {
            this.type = type;
            labels = new String[type.dimensions().size()];
        }
        
        /**
         * Adds a label in a dimension to this.
         *
         * @return this for convenience
         */
        public Builder add(String dimension, String label) {
            Objects.requireNonNull(dimension, "Dimension cannot be null");
            Objects.requireNonNull(label, "Label cannot be null");
            Optional<Integer> labelIndex = type.indexOfDimension(dimension);
            if ( ! labelIndex.isPresent())
                throw new IllegalArgumentException(type + " does not contain dimension '" + dimension + "'");
            labels[labelIndex.get()] = label;
            return this;
        }

        public TensorAddress build() {
            for (int i = 0; i < labels.length; i++)
                if (labels[i] == null)
                    throw new IllegalArgumentException("Missing a value for dimension " + 
                                                       type.dimensions().get(i).name() + " for " + type);
            return new TensorAddress(labels);
        }

    }

}
