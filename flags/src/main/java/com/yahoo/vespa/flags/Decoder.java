// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Håkon Hallingstad
 */
@FunctionalInterface
public interface Decoder<T> {
    /** @throws RuntimeException when failing to decode value from string. */
    T decode(String value);

    /** A Decoder uniquely determines a Deserializer. */
    default Deserializer<T> deserializer() {
        return rawFlag -> {
            JsonNode jsonNode = rawFlag.asJsonNode();
            if (!jsonNode.isTextual())
                throw new IllegalArgumentException("Expected a text value but got " + jsonNode.getNodeType() + ": " + jsonNode);
            return decode(jsonNode.asText());
        };
    }
}
