// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;

/**
 * @author hakonhall
 */
public class UnboundBooleanFlag extends UnboundFlagImpl<Boolean, BooleanFlag, UnboundBooleanFlag> {
    public UnboundBooleanFlag(FlagId id) {
        this(id, false);
    }

    public UnboundBooleanFlag(FlagId id, boolean defaultValue) {
        this(id, defaultValue, new FetchVector());
    }

    public UnboundBooleanFlag(FlagId id, boolean defaultValue, FetchVector defaultFetchVector) {
        super(id, defaultValue, defaultFetchVector,
                new SimpleFlagSerializer<>(BooleanNode::valueOf, JsonNode::isBoolean, JsonNode::asBoolean),
                UnboundBooleanFlag::new, BooleanFlag::new);
    }
}
