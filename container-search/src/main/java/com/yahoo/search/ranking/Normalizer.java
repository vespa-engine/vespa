// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

abstract class Normalizer {

    protected double[] data;
    protected int size = 0;

    private static int initialCapacity(int hint) {
        for (int capacity = 64; capacity < 4096; capacity *= 2) {
            if (hint <= capacity) {
                return capacity;
            }
        }
        return 4096;
    }

    Normalizer(int sizeHint) {
        this.data = new double[initialCapacity(sizeHint)];
    }

    int addInput(double value) {
        if (size == data.length) {
            int newSize = size * 2;
            var tmp = new double[newSize];
            System.arraycopy(data, 0, tmp, 0, size);
            this.data = tmp;
        }
        data[size] = value;
        return size++;
    }

    double getOutput(int index) { return data[index]; }

    abstract void normalize();

    abstract String normalizing();
}
