// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

abstract class Normalizer {

    protected final double[] data;
    protected int size = 0;

    Normalizer(int maxSize) {
        this.data = new double[maxSize];
    }

    int addInput(double value) {
        data[size] = value;
        return size++;
    }

    double getOutput(int index) { return data[index]; }

    abstract void normalize();

    abstract String normalizing();
}
