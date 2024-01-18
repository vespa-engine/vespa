package com.yahoo.tensor.impl;

import com.yahoo.tensor.TensorAddress;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class NumericTensorAddress extends TensorAddress {
    private static final String [] SMALL_INDEXES = createSmallIndexesAsStrings(1000);

    private final long[] labels;

    private static String[] createSmallIndexesAsStrings(int count) {
        String [] asStrings = new String[count];
        for (int i = 0; i < count; i++) {
            asStrings[i] = String.valueOf(i);
        }
        return asStrings;
    }

    private NumericTensorAddress(long[] labels) {
        this.labels = labels;
    }

    public static NumericTensorAddress of(long ... labels) {
        return new NumericTensorAddress(Arrays.copyOf(labels, labels.length));
    }

    public static NumericTensorAddress unsafeOf(long ... labels) {
        return new NumericTensorAddress(labels);
    }

    @Override
    public int size() { return labels.length; }

    @Override
    public String label(int i) { return asString(labels[i]); }

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
        return "cell address (" + Arrays.stream(labels).mapToObj(NumericTensorAddress::asString).collect(Collectors.joining(",")) + ")";
    }

    public static String asString(long index) {
        return ((index >= 0) && (index < SMALL_INDEXES.length)) ? SMALL_INDEXES[(int)index] : String.valueOf(index);
    }

}

