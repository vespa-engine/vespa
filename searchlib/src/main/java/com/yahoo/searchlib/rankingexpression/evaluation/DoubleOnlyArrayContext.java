// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.tensor.TensorType;

/**
 * A variant of an array context variant which supports faster binding of variables but slower lookup
 * from non-gbdt-optimized ranking expressions.
 *
 * @author bratseth
 */
public class DoubleOnlyArrayContext extends AbstractArrayContext {

    /**
     * Create a fast lookup context for an expression.
     * This instance should be reused indefinitely by a single thread.
     * This will fail if unknown values are attempted added.
     */
    public DoubleOnlyArrayContext(RankingExpression expression) {
        this(expression, false);
    }

    /**
     * Create a fast lookup context for an expression.
     * This instance should be reused indefinitely by a single thread.
     *
     * @param expression the expression to create a context for
     * @param ignoreUnknownValues whether attempts to put values not present in this expression
     *                            should fail (false - the default), or be ignored (true)
     */
    public DoubleOnlyArrayContext(RankingExpression expression, boolean ignoreUnknownValues) {
        super(expression, ignoreUnknownValues);
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
        doubleValues()[index] = value;
    }

    /** Puts a value by index. */
    public final void put(int index, Value value) {
        try {
            put(index, value.asDouble());
        }
        catch (UnsupportedOperationException e) {
            throw new IllegalArgumentException("This context only supports doubles, not " + value);
        }
    }

    @Override
    public TensorType getType(String name) { return TensorType.empty; }

    /** Perform a slow lookup by name */
    @Override
    public Value get(String name) {
        Integer index = nameToIndex().get(name);
        if (index==null) return DoubleValue.zero;
        return new DoubleValue(getDouble(index));
    }

    /** Perform a faster lookup by index */
    @Override
    public final Value get(int index) {
        return new DoubleValue(getDouble(index));
    }

    /**
     * Creates a clone of this context suitable for evaluating against the same ranking expression
     * in a different thread (i.e, name name to index map, different value set.
     */
    public DoubleOnlyArrayContext clone() {
        return (DoubleOnlyArrayContext)super.clone();
    }

}
