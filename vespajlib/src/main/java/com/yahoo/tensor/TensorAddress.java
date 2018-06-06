// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An immutable address to a tensor cell. This simply supplies a value to each dimension
 * in a particular tensor type. By itself it is just a list of cell labels, it's meaning depends on its accompanying type.
 *
 * @author bratseth
 */
public abstract class TensorAddress implements Comparable<TensorAddress> {

    public static TensorAddress of(String[] labels) {
        return new StringTensorAddress(labels);
    }

    public static TensorAddress of(long ... labels) {
        return new NumericTensorAddress(labels);
    }

    /** Returns the number of labels in this */
    public abstract int size();

    /**
     * Returns the i'th label in this
     *
     * @throws IllegalArgumentException if there is no label at this index
     */
    public abstract String label(int i);

    /**
     * Returns the i'th label in this as a long.
     * Prefer this if you know that this is a numeric address, but not otherwise.
     *
     * @throws IllegalArgumentException if there is no label at this index
     */
    public abstract long numericLabel(int i);

    public abstract TensorAddress withLabel(int labelIndex, long label);

    public final boolean isEmpty() { return size() == 0; }

    @Override
    public int compareTo(TensorAddress other) {
        // TODO: Formal issue (only): Ordering with different address sizes
        for (int i = 0; i < size(); i++) {
            int elementComparison = this.label(i).compareTo(other.label(i));
            if (elementComparison != 0) return elementComparison;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < size(); i++)
            result = 31 * result + label(i).hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof TensorAddress)) return false;
        TensorAddress other = (TensorAddress)o;
        if (other.size() != this.size()) return false;
        for (int i = 0; i < this.size(); i++)
            if ( ! this.label(i).equals(other.label(i)))
                return false;
        return true;
    }

    /** Returns this as a string on the appropriate form given the type */
    public final String toString(TensorType type) {
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < size(); i++) {
            b.append(type.dimensions().get(i).name()).append(":").append(label(i));
            b.append(",");
        }
        if (b.length() > 1)
            b.setLength(b.length() - 1);
        b.append("}");
        return b.toString();
    }

    private static final class StringTensorAddress extends TensorAddress {

        private final String[] labels;

        private StringTensorAddress(String ... labels) {
            this.labels = Arrays.copyOf(labels, labels.length);
        }

        @Override
        public int size() { return labels.length; }

        @Override
        public String label(int i) { return labels[i]; }

        @Override
        public long numericLabel(int i) {
            try {
                return Long.parseLong(labels[i]);
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected a long label in " + this + " at position " + i);
            }
        }

        @Override
        public TensorAddress withLabel(int index, long label) {
            String[] labels = Arrays.copyOf(this.labels, this.labels.length);
            labels[index] = String.valueOf(label);
            return new StringTensorAddress(labels);
        }

        @Override
        public String toString() {
            return Arrays.toString(labels);
        }

    }

    private static final class NumericTensorAddress extends TensorAddress {

        private final long[] labels;

        private NumericTensorAddress(long[] labels) {
            this.labels = Arrays.copyOf(labels, labels.length);
        }

        @Override
        public int size() { return labels.length; }

        @Override
        public String label(int i) { return String.valueOf(labels[i]); }

        @Override
        public long numericLabel(int i) { return labels[i]; }

        @Override
        public TensorAddress withLabel(int index, long label) {
            long[] labels = Arrays.copyOf(this.labels, this.labels.length);
            labels[index] = label;
            return new NumericTensorAddress(labels);
        }

        @Override
        public String toString() {
            return Arrays.toString(labels);
        }

    }

    /** Supports building of a tensor address */
    public static class Builder {

        private Pattern identifierPattern = Pattern.compile("[A-Za-z0-9_]+");

        private final TensorType type;
        private final String[] labels;

        public Builder(TensorType type) {
            this(type, new String[type.dimensions().size()]);
        }

        private Builder(TensorType type, String[] labels) {
            this.type = type;
            this.labels = labels;
        }

        /**
         * Adds a label in a dimension to this.
         *
         * @return this for convenience
         */
        public Builder add(String dimension, String label) {
            requireIdentifier(dimension, "dimension");
            requireIdentifier(label, "label");
            Optional<Integer> labelIndex = type.indexOfDimension(dimension);
            if ( ! labelIndex.isPresent())
                throw new IllegalArgumentException(type + " does not contain dimension '" + dimension + "'");
            labels[labelIndex.get()] = label;
            return this;
        }

        /** Creates a copy of this which can be modified separately */
        public Builder copy() {
            return new Builder(type, Arrays.copyOf(labels, labels.length));
        }

        public TensorAddress build() {
            for (int i = 0; i < labels.length; i++)
                if (labels[i] == null)
                    throw new IllegalArgumentException("Missing a value for dimension " +
                                                       type.dimensions().get(i).name() + " for " + type);
            return TensorAddress.of(labels);
        }

        private void requireIdentifier(String s, String parameterName) {
            if (s == null)
                throw new IllegalArgumentException(parameterName + " can not be null");
            if ( ! identifierPattern.matcher(s).matches())
                throw new IllegalArgumentException(parameterName + " must be an identifier or integer, not '" + s + "'");
        }

    }

}
