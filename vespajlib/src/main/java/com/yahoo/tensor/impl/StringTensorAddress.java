package com.yahoo.tensor.impl;

import com.yahoo.tensor.TensorAddress;

import java.util.Arrays;

public final class StringTensorAddress extends TensorAddress {

    private final String[] labels;

    private StringTensorAddress(String [] labels) {
        this.labels = labels;
    }

    public static StringTensorAddress of(String[] labels) {
        return new StringTensorAddress(Arrays.copyOf(labels, labels.length));
    }

    public static StringTensorAddress unsafeOf(String[] labels) {
        return new StringTensorAddress(labels);
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
        labels[index] = NumericTensorAddress.asString(label);
        return new StringTensorAddress(labels);
    }


    @Override
    public String toString() {
        return "cell address (" + String.join(",", labels) + ")";
    }

}
