// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a constant {@link String} value in a {@link GroupingExpression}.
 *
 * @author Simon Thoresen Hult
 */
public class StringValue extends ConstantValue<String> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The immutable value to assign to this.
     */
    public StringValue(String value) {
        super(value);
    }
}
