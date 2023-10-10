// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an infinite value in a {@link GroupingExpression}.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class InfiniteValue extends ConstantValue<Infinite> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The immutable value to assign to this.
     */
    public InfiniteValue(Infinite value) {
        super(null, null, value);
    }

    private InfiniteValue(String label, Integer level, Infinite value) {
        super(label, level, value);
    }

    @Override
    public InfiniteValue copy() {
        return new InfiniteValue(getLabel(), getLevelOrNull(), getValue());
    }

}
