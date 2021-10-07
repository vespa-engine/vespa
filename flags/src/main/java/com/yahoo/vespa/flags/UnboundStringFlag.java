// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import javax.annotation.concurrent.Immutable;

/**
 * @author hakonhall
 */
@Immutable
public class UnboundStringFlag extends UnboundFlagImpl<String, StringFlag, UnboundStringFlag> {
    public UnboundStringFlag(FlagId id, String defaultValue) {
        this(id, defaultValue, new FetchVector());
    }

    public UnboundStringFlag(FlagId id, String defaultValue, FetchVector defaultFetchVector) {
        super(id, defaultValue, defaultFetchVector,
                new SimpleFlagSerializer<>(TextNode::new, JsonNode::isTextual, JsonNode::asText),
                UnboundStringFlag::new, StringFlag::new);
    }
}
