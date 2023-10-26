// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a constant {@link Long} value in a {@link GroupingExpression}.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class LongValue extends ConstantValue<Long> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value the immutable value to assign to this.
     */
    public LongValue(long value) {
        super(null, null, value);
    }

    private LongValue(String label, Integer level, Long value) {
        super(label, level, value);
    }

    @Override
    public LongValue copy() {
        return new LongValue(getLabel(), getLevelOrNull(), getValue());
    }

}
