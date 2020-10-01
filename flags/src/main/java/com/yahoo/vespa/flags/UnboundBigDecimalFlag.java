// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;

import java.math.BigDecimal;

/**
 * @author ogronnesby
 */
public class UnboundBigDecimalFlag extends UnboundFlagImpl<BigDecimal, BigDecimalFlag, UnboundBigDecimalFlag>  {
    public UnboundBigDecimalFlag(FlagId id, BigDecimal defaultValue, FetchVector defaultFetchVector) {
        super(id, defaultValue, defaultFetchVector,
                new SimpleFlagSerializer<>(DecimalNode::valueOf, JsonNode::isBigDecimal, JsonNode::decimalValue),
                UnboundBigDecimalFlag::new, BigDecimalFlag::new);
    }
}
