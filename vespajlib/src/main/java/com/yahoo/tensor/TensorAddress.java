// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An immutable address to a tensor cell. This simply supplies a value to each dimension
 * in a particular tensor type. By itself it is just a list of cell labels, it's meaning depends on its accompanying type.
 *
 * @author bratseth
 */
public abstract class TensorAddress implements Comparable<TensorAddress> {

    private static final String [] SMALL_INDEXES = createSmallIndexesAsStrings(1000);

    public static TensorAddress of(String[] labels) {
        return new StringTensorAddress(labels);
    }

    public static TensorAddress ofLabels(String ... labels) {
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
        for (int i = 0; i < size(); i++) {
            if (label(i) != null)
                result = 31 * result + label(i).hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof TensorAddress other)) return false;
        if (other.size() != this.size()) return false;
        for (int i = 0; i < this.size(); i++)
            if ( ! Objects.equals(this.label(i), other.label(i)))
                return false;
        return true;
    }

    /** Returns this as a string on the appropriate form given the type */
    public final String toString(TensorType type) {
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < size(); i++) {
            b.append(type.dimensions().get(i).name()).append(":").append(labelToString(label(i)));
            b.append(",");
        }
        if (b.length() > 1)
            b.setLength(b.length() - 1);
        b.append("}");
        return b.toString();
    }

    /** Returns a label as a string with appropriate quoting/escaping when necessary */
    public static String labelToString(String label) {
        if (TensorType.labelMatcher.matches(label)) return label; // no quoting
        if (label.contains("'")) return "\"" + label + "\"";
        return "'" + label + "'";
    }

    private static String[] createSmallIndexesAsStrings(int count) {
        String [] asStrings = new String[count];
        for (int i = 0; i < count; i++) {
            asStrings[i] = String.valueOf(i);
        }
        return asStrings;
    }

    private static String asString(long index) {
        return ((index >= 0) && (index < SMALL_INDEXES.length)) ? SMALL_INDEXES[(int)index] : String.valueOf(index);
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
                throw new IllegalArgumentException("Expected an integer label in " + this + " at position " + i + " but got '" + labels[i] + "'");
            }
        }

        @Override
        public TensorAddress withLabel(int index, long label) {
            String[] labels = Arrays.copyOf(this.labels, this.labels.length);
            labels[index] = TensorAddress.asString(label);
            return new StringTensorAddress(labels);
        }


        @Override
        public String toString() {
            return "cell address (" + String.join(",", labels) + ")";
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
        public String label(int i) { return TensorAddress.asString(labels[i]); }

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
            return "cell address (" + Arrays.stream(labels).mapToObj(TensorAddress::asString).collect(Collectors.joining(",")) + ")";
        }

    }

    /** Builder of a tensor address */
    public static class Builder {

        final TensorType type;
        final String[] labels;

        public Builder(TensorType type) {
            this(type, new String[type.dimensions().size()]);
        }

        private Builder(TensorType type, String[] labels) {
            this.type = type;
            this.labels = labels;
        }

        /**
         * Adds the label to the only mapped dimension of this.
         *
         * @throws IllegalArgumentException if this does not have exactly one dimension
         */
        public Builder add(String label) {
            var mappedSubtype = type.mappedSubtype();
            if (mappedSubtype.rank() != 1)
                throw new IllegalArgumentException("Cannot add a label without explicit dimension to a tensor of type " +
                                                   type + ": Must have exactly one sparse dimension");
            add(mappedSubtype.dimensions().get(0).name(), label);
            return this;
        }

        /**
         * Adds a label in a dimension to this.
         *
         * @return this for convenience
         */
        public Builder add(String dimension, String label) {
            Objects.requireNonNull(dimension, "dimension cannot be null");
            Objects.requireNonNull(label, "label cannot be null");
            Optional<Integer> labelIndex = type.indexOfDimension(dimension);
            if ( labelIndex.isEmpty())
                throw new IllegalArgumentException(type + " does not contain dimension '" + dimension + "'");
            labels[labelIndex.get()] = label;
            return this;
        }

        /** Creates a copy of this which can be modified separately */
        public Builder copy() {
            return new Builder(type, Arrays.copyOf(labels, labels.length));
        }

        /** Returns the type of the tensor this address is being built for. */
        public TensorType type() { return type; }

        void validate() {
            for (int i = 0; i < labels.length; i++)
                if (labels[i] == null)
                    throw new IllegalArgumentException("Missing a label for dimension '" +
                                                       type.dimensions().get(i).name() + "' for " + type);
        }

        public TensorAddress build() {
            validate();
            return TensorAddress.of(labels);
        }

    }

    /** Builder of an address to a subset of the dimensions of a tensor type */
    public static class PartialBuilder extends Builder {

        public PartialBuilder(TensorType type) {
            super(type);
        }

        private PartialBuilder(TensorType type, String[] labels) {
            super(type, labels);
        }

        /** Creates a copy of this which can be modified separately */
        public Builder copy() {
            return new PartialBuilder(type, Arrays.copyOf(labels, labels.length));
        }

        @Override
        void validate() { }

    }

}
