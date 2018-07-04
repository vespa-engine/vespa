// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This class is used in {@link Group} instances where the identifying expression evaluated to a {@link Double}.
 *
 * @author Simon Thoresen Hult
 */
public class DoubleId extends ValueGroupId<Double> {

    /**
     * Constructs a new instance of this class.
     *
     * @param value The identifying double.
     */
    public DoubleId(Double value) {
        super("double", value);
    }
}
