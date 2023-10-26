// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A {@link RawFlag} represents the typeless flag value, possibly partially deserialized.
 *
 * @author hakonhall
 */
public interface RawFlag {
    JsonNode asJsonNode();
    String asJson();
}
