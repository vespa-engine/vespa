// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

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

    // Two arrays which contains corresponding dimension=label pairs.
    // The sizes of these are always equal.
    private final String[] dimensionNames;
    private final long[] labels;

    private PartialAddress(Builder builder) {
        this.dimensionNames = builder.dimensionNames;
        this.labels = builder.labels;
        builder.dimensionNames = null; // invalidate builder to safely take over array ownership
        builder.labels = null;
    }

    /** Returns the int label of this dimension, or -1 if no label is specified for it */
    long numericLabel(String dimensionName) {
        for (int i = 0; i < dimensionNames.length; i++)
            if (dimensionNames[i].equals(dimensionName))
                return labels[i];
        return -1;
    }

    public static class Builder {

        private String[] dimensionNames;
        private long[] labels;
        private int index = 0;

        public Builder(int size) {
            dimensionNames = new String[size];
            labels = new long[size];
        }

        public void add(String dimensionName, long label) {
            dimensionNames[index] = dimensionName;
            labels[index] = label;
            index++;
        }

        public PartialAddress build() {
            return new PartialAddress(this);
        }

    }

}
