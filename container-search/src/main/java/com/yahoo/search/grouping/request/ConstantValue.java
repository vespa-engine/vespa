// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a constant value in a {@link GroupingExpression}. Because it does not operate on any input,
 * this expression type can be used at any input level (see {@link GroupingExpression#resolveLevel(int)}). All supported
 * data types are represented as subclasses of this.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
@SuppressWarnings("rawtypes")
public abstract class ConstantValue<T extends Comparable> extends GroupingExpression {

    private final T value;

    protected ConstantValue(String label, Integer level, T value) {
        super(asImage(value), label, level);
        this.value = value;
    }

    @Override
    public abstract ConstantValue copy();

    /** Returns the constant value of this */
    public T getValue() {
        return value;
    }

}
