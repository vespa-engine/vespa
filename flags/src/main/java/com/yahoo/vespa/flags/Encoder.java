// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.node.TextNode;

/**
 * @author Håkon Hallingstad
 */
@FunctionalInterface
public interface Encoder<T> {
    /** @throws RuntimeException when failing to encode value to string. */
    String encode(T value);

    /** An Encoder uniquely determines a Serializer. */
    default Serializer<T> serializer() {
        return value -> JsonNodeRawFlag.fromJsonNode(new TextNode(encode(value)));
    }
}
