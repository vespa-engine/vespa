// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a constant {@link Long} value in a {@link GroupingExpression}.
 *
 * @author Simon Thoresen
 */
public class LongValue extends ConstantValue<Long> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value the immutable value to assign to this.
     */
    public LongValue(long value) {
        super(value);
    }

}
