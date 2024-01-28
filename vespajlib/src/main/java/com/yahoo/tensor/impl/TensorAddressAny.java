// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

import static com.yahoo.tensor.impl.Convert.safe2Int;
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
        return fromNumber((int)numericLabel(i));
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
        int[] labelsAsInt = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            labelsAsInt[i] = toNumber(labels[i]);
        }
        return ofUnsafe(labelsAsInt);
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
            case 1 -> new TensorAddressAny1(sanitize(labels[0]));
            case 2 -> new TensorAddressAny2(sanitize(labels[0]), sanitize(labels[1]));
            case 3 -> new TensorAddressAny3(sanitize(labels[0]), sanitize(labels[1]), sanitize(labels[2]));
            case 4 -> new TensorAddressAny4(sanitize(labels[0]), sanitize(labels[1]), sanitize(labels[2]), sanitize(labels[3]));
            default -> {
                for (int i = 0; i < labels.length; i++) {
                    sanitize(labels[i]);
                }
                yield new TensorAddressAnyN(labels);
            }
        };
    }

    public static TensorAddress of(long label) {
        return of(safe2Int(label));
    }

    public static TensorAddress of(long label0, long label1) {
        return of(safe2Int(label0), safe2Int(label1));
    }

    public static TensorAddress of(long label0, long label1, long label2) {
        return of(safe2Int(label0), safe2Int(label1), safe2Int(label2));
    }

    public static TensorAddress of(long label0, long label1, long label2, long label3) {
        return of(safe2Int(label0), safe2Int(label1), safe2Int(label2), safe2Int(label3));
    }

    public static TensorAddress of(long ... labels) {
        return switch (labels.length) {
            case 0 -> of();
            case 1 -> ofUnsafe(safe2Int(labels[0]));
            case 2 -> ofUnsafe(safe2Int(labels[0]), safe2Int(labels[1]));
            case 3 -> ofUnsafe(safe2Int(labels[0]), safe2Int(labels[1]), safe2Int(labels[2]));
            case 4 -> ofUnsafe(safe2Int(labels[0]), safe2Int(labels[1]), safe2Int(labels[2]), safe2Int(labels[3]));
            default -> {
                int[] labelsAsInt = new int[labels.length];
                for (int i = 0; i < labels.length; i++) {
                    labelsAsInt[i] = safe2Int(labels[i]);
                }
                yield of(labelsAsInt);
            }
        };
    }

    private static TensorAddress ofUnsafe(int label) {
        return new TensorAddressAny1(label);
    }

    private static TensorAddress ofUnsafe(int label0, int label1) {
        return new TensorAddressAny2(label0, label1);
    }

    private static TensorAddress ofUnsafe(int label0, int label1, int label2) {
        return new TensorAddressAny3(label0, label1, label2);
    }

    private static TensorAddress ofUnsafe(int label0, int label1, int label2, int label3) {
        return new TensorAddressAny4(label0, label1, label2, label3);
    }

    public static TensorAddress ofUnsafe(int ... labels) {
        return switch (labels.length) {
            case 0 -> of();
            case 1 -> ofUnsafe(labels[0]);
            case 2 -> ofUnsafe(labels[0], labels[1]);
            case 3 -> ofUnsafe(labels[0], labels[1], labels[2]);
            case 4 -> ofUnsafe(labels[0], labels[1], labels[2], labels[3]);
            default -> new TensorAddressAnyN(labels);
        };
    }

    private static int sanitize(int label) {
        if (label < Tensor.invalidIndex) {
            throw new IndexOutOfBoundsException("cell label " + label + " must be positive");
        }
        return label;
    }

}
