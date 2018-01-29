package com.yahoo.searchlib.rankingexpression.evaluation;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.tensor.TensorType;

import java.util.Optional;

/**
 * The type of a ranking expression value - either a double or a tensor.
 *
 * @author bratseth
 */
public class ValueType {

    private static final ValueType doubleValueType = new ValueType(Optional.empty());

    private final Optional<TensorType> tensorType;

    private ValueType(Optional<TensorType> type) {
        this.tensorType = type;
    }

    /** Returns true if this is a double type */
    public boolean isDouble() { return ! tensorType.isPresent(); }

    /** Returns true if this is a tensor type */
    public boolean isTensor() { return tensorType.isPresent(); }

    /** The specific tensor type of this, or empty if this is not a tensor type */
    public Optional<TensorType> tensorType() { return tensorType; }

    /** Returns the type representing a double */
    public static ValueType doubleType() { return doubleValueType; }

    /** Returns a type representing the given tensor type */
    public static ValueType tensorType(TensorType type) { return new ValueType(Optional.of(type)); }

}
