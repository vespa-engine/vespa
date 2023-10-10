// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;

import java.util.Arrays;

/**
 * Creates a context which supports array index based lookup.
 * This instance may be reused indefinitely for evaluations of a single
 * ranking expression, in a single thread at the time.
 *
 * @author bratseth
 */
public class ArrayContext extends AbstractArrayContext implements Cloneable {

    /** The current values set */
    private Value[] values;

    /**
     * Create a fast lookup context for an expression.
     * This instance should be reused indefinitely by a single thread.
     * This will fail if unknown values are attempted added.
     */
    public ArrayContext(RankingExpression expression) {
        this(expression, false);
    }

    public ArrayContext(RankingExpression expression, boolean ignoreUnknownValues) {
        this(expression, ignoreUnknownValues, defaultMissingValue);
    }

    public ArrayContext(RankingExpression expression, Value defaultValue) {
        this(expression, false, defaultValue);
    }

    /**
     * Create a fast lookup context for an expression.
     * This instance should be reused indefinitely by a single thread.
     *
     * @param expression the expression to create a context for
     * @param ignoreUnknownValues whether attempts to put values not present in this expression
     *                            should fail (false - the default), or be ignored (true)
     * @param missingValue the value to return if not set.
     */
    public ArrayContext(RankingExpression expression, boolean ignoreUnknownValues, Value missingValue) {
        super(expression, ignoreUnknownValues, missingValue);
        values = new Value[doubleValues().length];
        Arrays.fill(values, this.missingValue);
    }

    /**
     * Puts a value by name.
     * The value will be frozen if it isn't already.
     *
     * @throws IllegalArgumentException if the name is not present in the ranking expression this was created with, and
     *         ignoredUnknownValues is false
     */
    @Override
    public final void put(String name, Value value) {
        Integer index = nameToIndex().get(name);
        if (index == null) {
            if (ignoreUnknownValues())
                return;
            else
                throw new IllegalArgumentException("Value '" + name + "' is not known to " + this);
        }
        put(index, value);
    }

    /** Same as put(index,DoubleValue.frozen(value)) */
    public final void put(int index, double value) {
        put(index, DoubleValue.frozen(value));
    }

    /**
     * Puts a value by index.
     * The value will be frozen if it isn't already.
     */
    public final void put(int index, Value value) {
        values[index] = value.freeze();
        clearMissing(index);
        try {
            doubleValues()[index] = value.asDouble();
        }
        catch (UnsupportedOperationException e) {
            doubleValues()[index] = Double.NaN; // see getDouble below
        }
    }

    @Override
    public TensorType getType(Reference reference) {
        Integer index = nameToIndex().get(reference.toString());
        if (index == null) return null;
        return values[index].type();
    }

    /** Perform a slow lookup by name */
    @Override
    public Value get(String name) {
        Integer index = nameToIndex().get(name);
        if (index == null) return missingValue;
        return values[index];
    }

    /** Perform a fast lookup by index */
    @Override
    public final Value get(int index) {
        return values[index];
    }

    /** Perform a fast lookup directly of the value as a double. This is faster than get(index).asDouble() */
    @Override
    public final double getDouble(int index) {
        double value = doubleValues()[index];
        if (Double.isNaN(value) && ! isMissing(index))  // NaN is valid as a missing value
            throw new UnsupportedOperationException("Value at " + index + " has no double representation");
        return value;
    }

    /**
     * Creates a clone of this context suitable for evaluating against the same ranking expression
     * in a different thread (i.e, name name to index map, different value set.
     */
    public ArrayContext clone() {
        ArrayContext clone = (ArrayContext)super.clone();
        clone.values = new Value[nameToIndex().size()];
        Arrays.fill(clone.values, missingValue);
        return clone;
    }

}
