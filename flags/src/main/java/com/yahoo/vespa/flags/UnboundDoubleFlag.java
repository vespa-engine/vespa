// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;

/**
 * @author freva
 */
public class UnboundDoubleFlag extends UnboundFlagImpl<Double, DoubleFlag, UnboundDoubleFlag> {
    public UnboundDoubleFlag(FlagId id, double defaultValue) {
        this(id, defaultValue, new FetchVector());
    }

    public UnboundDoubleFlag(FlagId id, Double defaultValue, FetchVector defaultFetchVector) {
        super(id, defaultValue, defaultFetchVector,
                new SimpleFlagSerializer<>(DoubleNode::new, JsonNode::isFloatingPointNumber, JsonNode::asDouble),
                UnboundDoubleFlag::new, DoubleFlag::new);
    }
}
