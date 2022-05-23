// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.LongNode;

/**
 * @author hakonhall
 */
public class UnboundLongFlag extends UnboundFlagImpl<Long, LongFlag, UnboundLongFlag> {
    public UnboundLongFlag(FlagId id, long defaultValue) {
        this(id, defaultValue, new FetchVector());
    }

    public UnboundLongFlag(FlagId id, Long defaultValue, FetchVector defaultFetchVector) {
        super(id, defaultValue, defaultFetchVector,
                new SimpleFlagSerializer<>(LongNode::new, JsonNode::isIntegralNumber, JsonNode::asLong),
                UnboundLongFlag::new, LongFlag::new);
    }
}
