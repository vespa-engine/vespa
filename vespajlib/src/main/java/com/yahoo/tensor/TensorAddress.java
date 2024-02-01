// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.yahoo.tensor.impl.Convert;
import com.yahoo.tensor.impl.Label;
import com.yahoo.tensor.impl.TensorAddressAny;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An immutable address to a tensor cell. This simply supplies a value to each dimension
 * in a particular tensor type. By itself it is just a list of cell labels, it's meaning depends on its accompanying type.
 *
 * @author bratseth
 */
public abstract class TensorAddress implements Comparable<TensorAddress> {

    public static TensorAddress of(String[] labels) {
        return TensorAddressAny.of(labels);
    }

    public static TensorAddress ofLabels(String... labels) {
        return TensorAddressAny.of(labels);
    }

    public static TensorAddress of(long... labels) {
        return TensorAddressAny.of(labels);
    }

    public static TensorAddress of(int... labels) {
        return TensorAddressAny.of(labels);
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
    public String toString() {
        StringBuilder sb = new StringBuilder("cell address (");
        int size = size();
        if (size > 0) {
            sb.append(label(0));
            for (int i = 1; i < size; i++) {
                sb.append(',').append(label(i));
            }
        }

        return sb.append(')').toString();
    }

    /**
     * Returns this as a string on the appropriate form given the type
     */
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

    /**
     * Returns a label as a string with appropriate quoting/escaping when necessary
     */
    public static String labelToString(String label) {
        if (TensorType.labelMatcher.matches(label)) return label; // no quoting
        if (label.contains("'")) return "\"" + label + "\"";
        return "'" + label + "'";
    }

    /** Returns an address with only some of the dimension. Ordering will also be according to indexMap */
    public TensorAddress partialCopy(int[] indexMap) {
        int[] labels = new int[indexMap.length];
        for (int i = 0; i < labels.length; ++i) {
            labels[i] = (int)numericLabel(indexMap[i]);
        }
        return TensorAddressAny.ofUnsafe(labels);
    }

    /** Creates a complete address by taking the mapped dimmensions from this and the indexed from the indexedPart */
    public TensorAddress fullAddressOf(List<TensorType.Dimension> dimensions, int[] densePart) {
        int[] labels = new int[dimensions.size()];
        int mappedIndex = 0;
        int indexedIndex = 0;
        for (int i = 0; i < labels.length; i++) {
            TensorType.Dimension d = dimensions.get(i);
            if (d.isIndexed()) {
                labels[i] = densePart[indexedIndex];
                indexedIndex++;
            } else {
                labels[i] = (int)numericLabel(mappedIndex);
                mappedIndex++;
            }
        }
        return TensorAddressAny.ofUnsafe(labels);
    }

    /**
     * Returns an address containing the mapped dimensions of this.
     *
     * @param mappedType the type of the mapped subset of the type this is an address of;
     *                   which is also the type of the returned address
     * @param dimensions all the dimensions of the type this is an address of
     */
    public TensorAddress mappedPartialAddress(TensorType mappedType, List<TensorType.Dimension> dimensions) {
        if (dimensions.size() != size())
            throw new IllegalArgumentException("Tensor type of " + this + " is not the same size as " + this);
        TensorAddress.Builder builder = new TensorAddress.Builder(mappedType);
        for (int i = 0; i < dimensions.size(); ++i) {
            TensorType.Dimension dimension = dimensions.get(i);
            if ( ! dimension.isIndexed())
                builder.add(dimension.name(), (int)numericLabel(i));
        }
        return builder.build();
    }

    /** Builder of a tensor address */
    public static class Builder {

        final TensorType type;
        final int[] labels;

        private static int[] createEmptyLabels(int size) {
            int[] labels = new int[size];
            Arrays.fill(labels, Tensor.invalidIndex);
            return labels;
        }

        public Builder(TensorType type) {
            this(type, createEmptyLabels(type.dimensions().size()));
        }

        private Builder(TensorType type, int[] labels) {
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
                                                   type + ": Must have exactly one mapped dimension");
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
            int labelIndex = type.indexOfDimensionAsInt(dimension);
            if ( labelIndex < 0)
                throw new IllegalArgumentException(type + " does not contain dimension '" + dimension + "'");
            labels[labelIndex] = Label.toNumber(label);
            return this;
        }

        public Builder add(String dimension, long label) {
            return add(dimension, Convert.safe2Int(label));
        }
        public Builder add(String dimension, int label) {
            Objects.requireNonNull(dimension, "dimension cannot be null");
            int labelIndex = type.indexOfDimensionAsInt(dimension);
            if ( labelIndex < 0)
                throw new IllegalArgumentException(type + " does not contain dimension '" + dimension + "'");
            labels[labelIndex] = label;
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
                if (labels[i] == Tensor.invalidIndex)
                    throw new IllegalArgumentException("Missing a label for dimension '" +
                                                       type.dimensions().get(i).name() + "' for " + type);
        }

        public TensorAddress build() {
            validate();
            return TensorAddressAny.ofUnsafe(labels);
        }

    }

    /** Builder of an address to a subset of the dimensions of a tensor type */
    public static class PartialBuilder extends Builder {

        public PartialBuilder(TensorType type) {
            super(type);
        }

        private PartialBuilder(TensorType type, int[] labels) {
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
