// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.flags.json.Rule;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Basic in-memory flag source useful for testing
 *
 * @author freva
 */
public class InMemoryFlagSource implements FlagSource {
    private final Map<FlagId, FlagData> flagDataById = new ConcurrentHashMap<>();

    public InMemoryFlagSource withFlag(FlagId flagId) {
        flagDataById.put(flagId, new FlagData(flagId));
        return this;
    }

    public InMemoryFlagSource withDoubleFlag(FlagId flagId, double value) {
        return withRawFlag(flagId, new DoubleNode(value));
    }

    private InMemoryFlagSource withRawFlag(FlagId flagId, JsonNode rawFlag) {
        Rule rule = new Rule(Optional.of(JsonNodeRawFlag.fromJsonNode(rawFlag)));
        flagDataById.put(flagId, new FlagData(flagId, new FetchVector(), rule));
        return this;
    }

    @Override
    public Optional<RawFlag> fetch(FlagId id, FetchVector vector) {
        return Optional.ofNullable(flagDataById.get(id))
                .flatMap(flagData -> flagData.resolve(vector));
    }
}
