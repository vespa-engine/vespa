// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import java.util.Arrays;

/**
 * An address to a subset of a tensors' cells, specifying a label for some but not necessarily all of the tensors
 * dimensions.
 *
 * @author bratseth
 */
// Implementation notes:
// - These are created in inner (though not inner-most) loops so they are implemented with minimal allocation.
//   We also avoid non-essential error checking.
// - We can add support for string labels later without breaking the API
public class PartialAddress {

    // Two arrays which contains corresponding dimension:label pairs.
    // The sizes of these are always equal.
    private final String[] dimensionNames;
    private final Object[] labels;

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
                return asLong(labels[i]);
        return -1;
    }

    /** Returns the label of this dimension, or null if no label is specified for it */
    public String label(String dimensionName) {
        for (int i = 0; i < dimensionNames.length; i++)
            if (dimensionNames[i].equals(dimensionName))
                return labels[i].toString();
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
        return labels[i].toString();
    }

    public int size() { return dimensionNames.length; }

    /** Returns this as an address in the given tensor type */
    // We need the type here not just for validation but because this must map to the dimension order given by the type
    public TensorAddress asAddress(TensorType type) {
        if (type.rank() != size())
            throw new IllegalArgumentException(type + " has a different rank than " + this);
        if (Arrays.stream(labels).allMatch(l -> l instanceof Long)) {
            long[] numericLabels = new long[labels.length];
            for (int i = 0; i < type.dimensions().size(); i++) {
                long label = numericLabel(type.dimensions().get(i).name());
                if (label < 0)
                    throw new IllegalArgumentException(type + " dimension names does not match " + this);
                numericLabels[i] = label;
            }
            return TensorAddress.of(numericLabels);
        }
        else {
            String[] stringLabels = new String[labels.length];
            for (int i = 0; i < type.dimensions().size(); i++) {
                String label = label(type.dimensions().get(i).name());
                if (label == null)
                    throw new IllegalArgumentException(type + " dimension names does not match " + this);
                stringLabels[i] = label;
            }
            return TensorAddress.of(stringLabels);
        }
    }

    private long asLong(Object label) {
        if (label instanceof Long) {
            return (Long) label;
        }
        else {
            try {
                return Long.parseLong(label.toString());
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Label '" + label + "' is not numeric");
            }
        }
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
        private Object[] labels;
        private int index = 0;

        public Builder(int size) {
            dimensionNames = new String[size];
            labels = new Object[size];
        }

        public void add(String dimensionName, long label) {
            dimensionNames[index] = dimensionName;
            labels[index] = label;
            index++;
        }

        public void add(String dimensionName, String label) {
            dimensionNames[index] = dimensionName;
            labels[index] = label;
            index++;
        }

        public PartialAddress build() {
            return new PartialAddress(this);
        }

    }

}
