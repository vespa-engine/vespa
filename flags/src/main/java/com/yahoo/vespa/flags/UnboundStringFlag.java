// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author hakonhall
 */
public class UnboundStringFlag extends UnboundFlagImpl<String, StringFlag, UnboundStringFlag> {
    public UnboundStringFlag(FlagId id, String defaultValue) {
        this(id, defaultValue, new FetchVector());
    }

    public UnboundStringFlag(FlagId id, String defaultValue, FetchVector defaultFetchVector) {
        this(id, defaultValue, defaultFetchVector,
             new SimpleFlagSerializer<>(TextNode::new, JsonNode::isTextual, JsonNode::asText));
    }

    public UnboundStringFlag(FlagId id, String defaultValue, Predicate<String> validator) {
        this(id, defaultValue, new FetchVector(), validator);
    }

    public UnboundStringFlag(FlagId id, String defaultValue, FetchVector fetchVector, Predicate<String> validator) {
        this(id, defaultValue, fetchVector,
             new SimpleFlagSerializer<>(stringValue -> {
                                            if (!validator.test(stringValue))
                                                throw new IllegalArgumentException("Invalid value: '" + stringValue + "'");
                                            return new TextNode(stringValue);
                                        },
                                        JsonNode::isTextual, JsonNode::asText));
    }

    public UnboundStringFlag(FlagId id, String defaultValue, FetchVector defaultFetchVector,
                             FlagSerializer<String> serializer) {
        super(id, defaultValue, defaultFetchVector, serializer, UnboundStringFlag::new, StringFlag::new);
    }
}
