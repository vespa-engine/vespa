// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a raw value in a {@link GroupingExpression}.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class RawValue extends ConstantValue<RawBuffer> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The immutable value to assign to this.
     */
    public RawValue(RawBuffer value) {
        super(null, null, value);
    }

    private RawValue(String label, Integer level, RawBuffer value) {
        super(label, level, value);
    }

    @Override
    public RawValue copy() {
        return new RawValue(getLabel(), getLevelOrNull(), getValue().clone());
    }

}
