// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.yahoo.tensor.impl.Label;

/**
 * An address to a subset of a tensors' cells, specifying a label for some, but not necessarily all, of the tensors
 * dimensions.
 *
 * @author bratseth
 */
// Implementation notes:
// - These are created in inner (though not innermost) loops, so they are implemented with minimal allocation.
//   We also avoid non-essential error checking.
// - We can add support for string labels later without breaking the API
public class PartialAddress {

    // Two arrays which contains corresponding dimension:label pairs.
    // The sizes of these are always equal.
    private final String[] dimensionNames;
    private final long[] labels;

    private PartialAddress(Builder builder) {
        this.dimensionNames = builder.dimensionNames;
        this.labels = builder.labels;
        builder.dimensionNames = null; // invalidate builder to safely take over array ownership
        builder.labels = null;
    }

    public String dimension(int i) {
        return dimensionNames[i];
    }

    /** Returns the numeric label of this dimension, or -1 if no label is specified for it */
    public long numericLabel(String dimensionName) {
        for (int i = 0; i < dimensionNames.length; i++)
            if (dimensionNames[i].equals(dimensionName))
                return labels[i];
        return Tensor.invalidIndex;
    }

    /** Returns the label of this dimension, or null if no label is specified for it */
    public String label(String dimensionName) {
        for (int i = 0; i < dimensionNames.length; i++)
            if (dimensionNames[i].equals(dimensionName))
                return Label.fromNumber(labels[i]);
        return null;
    }

    /**
     * Returns the label at position i
     *
     * @throws IllegalArgumentException if i is out of bounds
     */
    public String label(int i) {
        if (i >= size())
            throw new IllegalArgumentException("No label at position " + i + " in " + this);
        return Label.fromNumber(labels[i]);
    }

    public int size() { return dimensionNames.length; }

    /** Returns this as an address in the given tensor type */
    // We need the type here not just for validation but because this must map to the dimension order given by the type
    public TensorAddress asAddress(TensorType type) {
        if (type.rank() != size())
            throw new IllegalArgumentException(type + " has a different rank than " + this);
        long[] numericLabels = new long[labels.length];
        for (int i = 0; i < type.dimensions().size(); i++) {
            long label = numericLabel(type.dimensions().get(i).name());
            if (label == Tensor.invalidIndex)
                throw new IllegalArgumentException(type + " dimension names does not match " + this);
            numericLabels[i] = label;
        }
        return TensorAddress.of(numericLabels);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Partial address {");
        for (int i = 0; i < dimensionNames.length; i++)
            b.append(dimensionNames[i]).append(":").append(label(i)).append(", ");
        if (size() > 0)
            b.setLength(b.length() - 2);
        return b.toString();
    }

    public static class Builder {

        private String[] dimensionNames;
        private long[] labels;
        private int index = 0;

        public Builder(int size) {
            dimensionNames = new String[size];
            labels = new long[size];
        }

        public Builder add(String dimensionName, long label) {
            dimensionNames[index] = dimensionName;
            labels[index] = label;
            index++;
            return this;
        }

        public Builder add(String dimensionName, String label) {
            dimensionNames[index] = dimensionName;
            labels[index] = Label.toNumber(label);
            index++;
            return this;
        }

        public PartialAddress build() {
            return new PartialAddress(this);
        }

    }

}
