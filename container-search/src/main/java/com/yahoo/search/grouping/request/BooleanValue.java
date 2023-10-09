// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a constant {@link Boolean} value in a {@link GroupingExpression}.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class BooleanValue extends ConstantValue<Boolean> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The immutable value to assign to this.
     */
    public BooleanValue(Boolean value) {
        super(null, null, value);
    }

    private BooleanValue(String label, Integer level, Boolean value) {
        super(label, level, value);
    }

    @Override
    public BooleanValue copy() {
        return new BooleanValue(getLabel(), getLevelOrNull(), getValue());
    }

}
