// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

import java.util.Arrays;

import static com.yahoo.tensor.impl.Label.toNumber;
import static com.yahoo.tensor.impl.Label.fromNumber;

/**
 * Parent of tensor address family centered around each dimension as int.
 * A positive number represents a numeric index usable as a direect addressing.
 * - 1 is representing an invalid/null address
 * Other negative numbers are an enumeration maintained in {@link Label}
 *
 * @author baldersheim
 */
abstract public class TensorAddressAny extends TensorAddress {

    @Override
    public String label(int i) {
        return fromNumber(numericLabel(i));
    }

    public static TensorAddress of() {
        return TensorAddressEmpty.empty;
    }

    public static TensorAddress of(String label) {
        return new TensorAddressAny1(toNumber(label));
    }

    public static TensorAddress of(String label0, String label1) {
        return new TensorAddressAny2(toNumber(label0), toNumber(label1));
    }

    public static TensorAddress of(String label0, String label1, String label2) {
        return new TensorAddressAny3(toNumber(label0), toNumber(label1), toNumber(label2));
    }

    public static TensorAddress of(String label0, String label1, String label2, String label3) {
        return new TensorAddressAny4(toNumber(label0), toNumber(label1), toNumber(label2), toNumber(label3));
    }

    public static TensorAddress of(String[] labels) {
        long[] labelsAsLong = new long[labels.length];
        for (int i = 0; i < labels.length; i++) {
            labelsAsLong[i] = toNumber(labels[i]);
        }
        return ofUnsafe(labelsAsLong);
    }

    public static TensorAddress of(int label) {
        return new TensorAddressAny1(sanitize(label));
    }

    public static TensorAddress of(int label0, int label1) {
        return new TensorAddressAny2(sanitize(label0), sanitize(label1));
    }

    public static TensorAddress of(int label0, int label1, int label2) {
        return new TensorAddressAny3(sanitize(label0), sanitize(label1), sanitize(label2));
    }

    public static TensorAddress of(int label0, int label1, int label2, int label3) {
        return new TensorAddressAny4(sanitize(label0), sanitize(label1), sanitize(label2), sanitize(label3));
    }

    public static TensorAddress of(int ... labels) {
        return switch (labels.length) {
            case 0 -> of();
            case 1 -> of(labels[0]);
            case 2 -> of(labels[0], labels[1]);
            case 3 -> of(labels[0], labels[1], labels[2]);
            case 4 -> of(labels[0], labels[1], labels[2], labels[3]);
            default -> {
                long[] copy = new long[labels.length];
                for (int i = 0; i < labels.length; i++) {
                    copy[i] = sanitize(labels[i]);
                }
                yield new TensorAddressAnyN(copy);
            }
        };
    }

    public static TensorAddress of(long ... labels) {
        return switch (labels.length) {
            case 0 -> of();
            case 1 -> of(labels[0]);
            case 2 -> of(labels[0], labels[1]);
            case 3 -> of(labels[0], labels[1], labels[2]);
            case 4 -> of(labels[0], labels[1], labels[2], labels[3]);
            default -> new TensorAddressAnyN(Arrays.copyOf(labels, labels.length));

        };
    }

    private static TensorAddress of(long label) {
        return new TensorAddressAny1(label);
    }

    private static TensorAddress of(long label0, long label1) {
        return new TensorAddressAny2(label0, label1);
    }

    public static TensorAddress of(long label0, long label1, long label2) {
        return new TensorAddressAny3(label0, label1, label2);
    }

    public static TensorAddress of(long label0, long label1, long label2, long label3) {
        return new TensorAddressAny4(label0, label1, label2, label3);
    }

    public static TensorAddress ofUnsafe(long ... labels) {
        return switch (labels.length) {
            case 0 -> of();
            case 1 -> of(labels[0]);
            case 2 -> of(labels[0], labels[1]);
            case 3 -> of(labels[0], labels[1], labels[2]);
            case 4 -> of(labels[0], labels[1], labels[2], labels[3]);
            default -> new TensorAddressAnyN(labels);
        };
    }

    private static long sanitize(long label) {
        if (label < Tensor.invalidIndex) {
            throw new IndexOutOfBoundsException("cell label " + label + " must be positive");
        }
        return label;
    }

}
