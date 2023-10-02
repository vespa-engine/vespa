// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

abstract class Normalizer {

    private final String name;
    private final String needInput;
    protected final double[] data;
    protected int size = 0;

    Normalizer(String name, String needInput, int maxSize) {
        this.name = name;
        this.needInput = needInput;
        this.data = new double[maxSize];
    }

    int addInput(double value) {
        data[size] = value;
        return size++;
    }

    double getOutput(int index) { return data[index]; }

    String name() { return name; }

    String input() { return needInput; }

    abstract void normalize();

    abstract String normalizing();
}
