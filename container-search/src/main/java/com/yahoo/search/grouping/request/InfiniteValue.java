// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an infinite value in a {@link GroupingExpression}.
 *
 * @author <a href="mailto:lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */
public class InfiniteValue extends ConstantValue<Infinite> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The immutable value to assign to this.
     */
    public InfiniteValue(Infinite value) {
        super(value);
    }
}
