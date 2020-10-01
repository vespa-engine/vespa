// Copyright 2020 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.math.BigDecimal;

/**
 * @author ogronnesby
 */
public class BigDecimalFlag extends FlagImpl<BigDecimal, BigDecimalFlag> {
    public BigDecimalFlag(FlagId id, BigDecimal defaultValue, FetchVector fetchVector, FlagSerializer<BigDecimal> serializer, FlagSource source) {
        super(id, defaultValue, fetchVector, serializer, source, BigDecimalFlag::new);
    }

    public BigDecimal value() { return boxedValue(); }
}
