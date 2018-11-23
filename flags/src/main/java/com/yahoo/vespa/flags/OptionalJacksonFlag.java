// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.function.Function;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakonhall
 */
public class OptionalJacksonFlag<T> implements Flag {
    private final static ObjectMapper mapper = new ObjectMapper();

    private final FlagId id;
    private final Class<T> jacksonClass;
    private final FlagSource source;

    public static <T> Function<FlagSource, OptionalJacksonFlag<T>> createUnbound(String flagId, Class<T> jacksonClass) {
        return createUnbound(new FlagId(flagId), jacksonClass);
    }

    public static <T> Function<FlagSource, OptionalJacksonFlag<T>> createUnbound(FlagId id, Class<T> jacksonClass) {
        return source -> new OptionalJacksonFlag<>(id, jacksonClass, source);
    }

    public OptionalJacksonFlag(String flagId, Class<T> jacksonClass, FlagSource source) {
        this(new FlagId(flagId), jacksonClass, source);
    }

    public OptionalJacksonFlag(FlagId id, Class<T> jacksonClass, FlagSource source) {
        this.id = id;
        this.jacksonClass = jacksonClass;
        this.source = source;
    }

    @Override
    public FlagId id() {
        return id;
    }

    public Optional<T> value() {
        return source.getString(id).map(string -> uncheck(() -> mapper.readValue(string, jacksonClass)));
    }

    public JacksonFlag<T> withDefault(T defaultValue) {
        return new JacksonFlag<T>(id, jacksonClass, defaultValue, source);
    }

    @Override
    public String toString() {
        return "OptionalJacksonFlag{" +
                "id=" + id +
                ", jacksonClass=" + jacksonClass +
                ", source=" + source +
                '}';
    }
}
