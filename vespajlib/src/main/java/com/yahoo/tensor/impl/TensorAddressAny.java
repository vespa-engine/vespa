// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.impl;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

/**
 * Parent of tensor address family centered around each dimension as int.
 * A positive number represents a numeric index usable as a direct addressing.
 * - 1 is representing an invalid/null address
 * Other negative numbers are an enumeration maintained in {@link Label}
 *
 * @author baldersheim
 */
abstract public class TensorAddressAny extends TensorAddress {
    public static TensorAddress of() {
        return TensorAddressEmpty.empty;
    }

    public static TensorAddress of(String label) {
        return new TensorAddressAny1(Label.of(label));
    }

    public static TensorAddress of(String label0, String label1) {
        return new TensorAddressAny2(Label.of(label0), Label.of(label1));
    }

    public static TensorAddress of(String label0, String label1, String label2) {
        return new TensorAddressAny3(Label.of(label0), Label.of(label1), Label.of(label2));
    }

    public static TensorAddress of(String label0, String label1, String label2, String label3) {
        return new TensorAddressAny4(Label.of(label0), Label.of(label1), Label.of(label2), Label.of(label3));
    }

    public static TensorAddress of(String[] labels) {
        return switch (labels.length) {
            case 0 -> of();
            case 1 -> of(labels[0]);
            case 2 -> of(labels[0], labels[1]);
            case 3 -> of(labels[0], labels[1], labels[2]);
            case 4 -> of(labels[0], labels[1], labels[2], labels[3]);
            default -> {
                var labelObjs = new Label[labels.length];
                for (int i = 0; i < labels.length; i++) {
                    labelObjs[i] = Label.of(labels[i]);
                }
                yield new TensorAddressAnyN(labelObjs);
            }
        };
    }

    public static TensorAddress of(int label) {
        return new TensorAddressAny1(Label.of(sanitize(label)));
    }

    public static TensorAddress of(int label0, int label1) {
        return new TensorAddressAny2(Label.of(sanitize(label0)), Label.of(sanitize(label1)));
    }

    public static TensorAddress of(int label0, int label1, int label2) {
        return new TensorAddressAny3(Label.of(sanitize(label0)), Label.of(sanitize(label1)), Label.of(sanitize(label2)));
    }

    public static TensorAddress of(int label0, int label1, int label2, int label3) {
        return new TensorAddressAny4(Label.of(sanitize(label0)), Label.of(sanitize(label1)), 
                Label.of(sanitize(label2)), Label.of(sanitize(label3)));
    }

    public static TensorAddress of(int ... labels) {
        return switch (labels.length) {
            case 0 -> of();
            case 1 -> of(labels[0]);
            case 2 -> of(labels[0], labels[1]);
            case 3 -> of(labels[0], labels[1], labels[2]);
            case 4 -> of(labels[0], labels[1], labels[2], labels[3]);
            default -> {
                var labelObjs = new Label[labels.length];
                for (int i = 0; i < labels.length; i++) {
                    labelObjs[i] = Label.of(sanitize(labels[i]));
                }
                yield new TensorAddressAnyN(labelObjs);
            }
        };
    }

    public static TensorAddress of(long label) {
        return new TensorAddressAny1(Label.of(sanitize(label)));
    }
    
    public static TensorAddress of(long label0, long label1) {
        return new TensorAddressAny2(Label.of(sanitize(label0)), Label.of(sanitize(label1)));
    }
    
    public static TensorAddress of(long label0, long label1, long label2) {
        return new TensorAddressAny3(Label.of(sanitize(label0)), Label.of(sanitize(label1)), Label.of(sanitize(label2)));
    }
    
    public static TensorAddress of(long label0, long label1, long label2, long label3) {
        return new TensorAddressAny4(Label.of(sanitize(label0)), Label.of(sanitize(label1)), 
                Label.of(sanitize(label2)), Label.of(sanitize(label3)));
    }
    
    public static TensorAddress of(long ... labels) {
        return switch (labels.length) {
            case 0 -> of();
            case 1 -> of(labels[0]);
            case 2 -> of(labels[0], labels[1]);
            case 3 -> of(labels[0], labels[1], labels[2]);
            case 4 -> of(labels[0], labels[1], labels[2], labels[3]);
            default -> {
                var labelObjs = new Label[labels.length];
                for (int i = 0; i < labels.length; i++) {
                    labelObjs[i] = Label.of(sanitize(labels[i]));
                }
                yield new TensorAddressAnyN(labelObjs);
            }
        };
    }
    
    
    private static TensorAddress of(Label label) {
        return new TensorAddressAny1(label);
    }
    
    private static TensorAddress of(Label label0, Label label1) {
        return new TensorAddressAny2(label0, label1);
    }
    
    private static TensorAddress of(Label label0, Label label1, Label label2) {
        return new TensorAddressAny3(label0, label1, label2);
    }
    
    private static TensorAddress of(Label label0, Label label1, Label label2, Label label3) {
        return new TensorAddressAny4(label0, label1, label2, label3);
    }
    
    public static TensorAddress of(Label ... labels) {
        return switch (labels.length) {
            case 0 -> of();
            case 1 -> of(labels[0]);
            case 2 -> of(labels[0], labels[1]);
            case 3 -> of(labels[0], labels[1], labels[2]);
            case 4 -> of(labels[0], labels[1], labels[2], labels[3]);
            default -> {
                var copy = new Label[labels.length];
                System.arraycopy(labels, 0, copy, 0, labels.length);
                yield  new TensorAddressAnyN(copy);
            }
        };
    }

    public static TensorAddress ofUnsafe(Label ... labels) {
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
