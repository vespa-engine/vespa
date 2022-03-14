// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.component.AbstractComponent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Basic in-memory flag source useful for testing
 *
 * Creates an empty flag source that will make all flags return their default values (from {@code value()}).
 * The with* methods can be used to set a different return value.
 *
 * @author freva
 */
public class InMemoryFlagSource extends AbstractComponent implements FlagSource {
    private final Map<FlagId, RawFlag> rawFlagsById = new ConcurrentHashMap<>();

    public InMemoryFlagSource withBooleanFlag(FlagId flagId, boolean value) {
        return withRawFlag(flagId, new UnboundBooleanFlag(flagId, value).serializer().serialize(value));
    }

    public InMemoryFlagSource withStringFlag(FlagId flagId, String value) {
        return withRawFlag(flagId, new UnboundStringFlag(flagId, value).serializer().serialize(value));
    }

    public InMemoryFlagSource withIntFlag(FlagId flagId, int value) {
        return withRawFlag(flagId, new UnboundIntFlag(flagId, value).serializer().serialize(value));
    }

    public InMemoryFlagSource withLongFlag(FlagId flagId, long value) {
        return withRawFlag(flagId, new UnboundLongFlag(flagId, value).serializer().serialize(value));
    }

    public InMemoryFlagSource withDoubleFlag(FlagId flagId, double value) {
        return withRawFlag(flagId, new UnboundDoubleFlag(flagId, value).serializer().serialize(value));
    }

    public <T> InMemoryFlagSource withJacksonFlag(FlagId flagId, T value, Class<T> jacksonClass) {
        return withRawFlag(flagId, new UnboundJacksonFlag<>(flagId, value, jacksonClass).serializer().serialize(value));
    }

    public <T> InMemoryFlagSource withListFlag(FlagId flagId, List<T> value, Class<T> elementClass) {
        return withRawFlag(flagId, new UnboundListFlag<T>(flagId, value, elementClass).serializer().serialize(value));
    }

    public InMemoryFlagSource removeFlag(FlagId flagId) {
        rawFlagsById.remove(flagId);
        return this;
    }

    private InMemoryFlagSource withRawFlag(FlagId flagId, RawFlag rawFlag) {
        rawFlagsById.put(flagId, rawFlag);
        return this;
    }

    @Override
    public Optional<RawFlag> fetch(FlagId id, FetchVector vector) {
        return Optional.ofNullable(rawFlagsById.get(id));
    }
}
