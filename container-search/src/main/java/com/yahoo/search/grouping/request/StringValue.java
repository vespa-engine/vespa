// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a constant {@link String} value in a {@link GroupingExpression}.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class StringValue extends ConstantValue<String> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The immutable value to assign to this.
     */
    public StringValue(String value) {
        super(null, null, value);
    }

    private StringValue(String label, Integer level, String value) {
        super(label, level, value);
    }

    @Override
    public StringValue copy() {
        return new StringValue(getLabel(), getLevelOrNull(), getValue());
    }

}
