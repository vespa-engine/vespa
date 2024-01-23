package com.yahoo.tensor.impl;

public class TensorAddressAnyN extends TensorAdressAny {
    private final long [] labels;
    public TensorAddressAnyN(long [] labels) {
        this.labels = labels;
    }

    @Override public int size() { return labels.length; }
    @Override public long numericLabel(int i) { return labels[i]; }
}
