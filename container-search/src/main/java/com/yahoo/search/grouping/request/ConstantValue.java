// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a constant value in a {@link GroupingExpression}. Because it does not operate on any input,
 * this expression type can be used at any input level (see {@link GroupingExpression#resolveLevel(int)}). All supported
 * data types are represented as subclasses of this.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
@SuppressWarnings("rawtypes")
public abstract class ConstantValue<T extends Comparable> extends GroupingExpression {

    private final T value;

    protected ConstantValue(T value) {
        super(asImage(value));
        this.value = value;
    }

    /**
     * Returns the constant value of this.
     *
     * @return The value.
     */
    public T getValue() {
        return value;
    }
}
