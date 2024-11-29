// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.yahoo.tensor.impl.LabelCache;
import com.yahoo.tensor.impl.TensorAddressAny;

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
    private final Label[] labels;

    private PartialAddress(Builder builder) {
        this.dimensionNames = builder.dimensionNames;
        this.labels = builder.labels;
        builder.dimensionNames = null; // invalidate builder to safely take over array ownership
        builder.labels = null;
    }

    public String dimension(int i) {
        return dimensionNames[i];
    }


    /** Returns the label object of this dimension, or -1 if no label is specified for it */
    public Label objectLabel(String dimensionName) {
        for (int i = 0; i < dimensionNames.length; i++)
            if (dimensionNames[i].equals(dimensionName))
                return labels[i];
        
        return LabelCache.INVALID_INDEX_LABEL;
    }
    
    /** Returns the numeric label of this dimension, or -1 if no label is specified for it */
    public long numericLabel(String dimensionName) {
        return objectLabel(dimensionName).asNumeric();
    }

    /** Returns the string label of this dimension, or null if no label is specified for it */
    public String label(String dimensionName) {
        return objectLabel(dimensionName).asString();
    }

    /**
     * Returns label object at position i
     *
     * @throws IllegalArgumentException if i is out of bounds
     */
    public Label objectLabel(int i) {
        if (i >= size())
            throw new IllegalArgumentException("No label at position " + i + " in " + this);
        return labels[i];
    }

    /**
     * Returns string label at position i
     *
     * @throws IllegalArgumentException if i is out of bounds
     */
    public String label(int i) {
        return objectLabel(i).asString();
    }

    public int size() { return dimensionNames.length; }

    /** Returns this as an address in the given tensor type */
    // We need the type here not just for validation but because this must map to the dimension order given by the type
    public TensorAddress asAddress(TensorType type) {
        if (type.rank() != size())
            throw new IllegalArgumentException(type + " has a different rank than " + this);
        Label[] labels = new Label[this.labels.length];
        for (int i = 0; i < type.dimensions().size(); i++) {
            Label label = objectLabel(type.dimensions().get(i).name());
            if (label.isEqualTo(LabelCache.INVALID_INDEX_LABEL))
                throw new IllegalArgumentException(type + " dimension names does not match " + this);
            labels[i] = label;
        }
        return TensorAddressAny.ofUnsafe(labels);
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
        private Label[] labels;
        private int index = 0;

        public Builder(int size) {
            dimensionNames = new String[size];
            labels = new Label[size];
        }

        public Builder add(String dimensionName, Label label) {
            dimensionNames[index] = dimensionName;
            labels[index] = label;
            index++;
            return this;
        }

        public Builder add(String dimensionName, long label) {
            dimensionNames[index] = dimensionName;
            labels[index] = LabelCache.GLOBAL.getOrCreateLabel(label);
            index++;
            return this;
        }

        public Builder add(String dimensionName, String label) {
            dimensionNames[index] = dimensionName;
            labels[index] = LabelCache.GLOBAL.getOrCreateLabel(label);
            index++;
            return this;
        }

        public PartialAddress build() {
            return new PartialAddress(this);
        }

    }

}
