// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a raw value in a {@link GroupingExpression}.
 *
 * @author <a href="mailto:lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */
public class RawValue extends ConstantValue<RawBuffer> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The immutable value to assign to this.
     */
    public RawValue(RawBuffer value) {
        super(value);
    }
}
