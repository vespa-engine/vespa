package com.yahoo.searchlib.rankingexpression.evaluation;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.tensor.TensorType;

/**
 * The type of a ranking expression value - either a double or a tensor.
 *
 * @author bratseth
 */
public class ValueType {

    private static final ValueType doubleValueType = new ValueType(TensorType.empty);

    private final TensorType tensorType;

    private ValueType(TensorType tensorType) {
        this.tensorType = tensorType;
    }

    /** Returns true if this is the double type */
    public boolean isDouble() { return tensorType.rank() == 0; }

    /** The type of this as a tensor type. The double type is the empty tensor type (rank 0) */
    public TensorType tensorType() { return tensorType; }

    /** Returns the type representing a double */
    public static ValueType doubleType() { return doubleValueType; }

    /** Returns a type representing the given tensor type */
    public static ValueType of(TensorType type) { return new ValueType(type); }

}
