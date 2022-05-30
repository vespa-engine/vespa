// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;

/**
 * @author hakonhall
 */
public class UnboundIntFlag extends UnboundFlagImpl<Integer, IntFlag, UnboundIntFlag> {
    public UnboundIntFlag(FlagId id, int defaultValue) {
        this(id, defaultValue, new FetchVector());
    }

    public UnboundIntFlag(FlagId id, int defaultValue, FetchVector defaultFetchVector) {
        super(id, defaultValue, defaultFetchVector,
                new SimpleFlagSerializer<>(IntNode::new, JsonNode::isIntegralNumber, JsonNode::asInt),
                UnboundIntFlag::new, IntFlag::new);
    }
}
