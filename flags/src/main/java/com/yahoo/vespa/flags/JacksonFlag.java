// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Function;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakonhall
 */
public class JacksonFlag<T> implements Flag {
    private final static ObjectMapper mapper = new ObjectMapper();

    private final FlagId id;
    private final Class<T> jacksonClass;
    private final T defaultValue;
    private final FlagSource source;

    public static <T> Function<FlagSource, JacksonFlag<T>> createUnbound(String flagId, Class<T> jacksonClass, T defaultValue) {
        return createUnbound(new FlagId(flagId), jacksonClass, defaultValue);
    }

    public static <T> Function<FlagSource, JacksonFlag<T>> createUnbound(FlagId id, Class<T> jacksonClass, T defaultValue) {
        return source -> new JacksonFlag<>(id, jacksonClass, defaultValue, source);
    }

    public JacksonFlag(String flagId, Class<T> jacksonClass, T defaultValue, FlagSource source) {
        this(new FlagId(flagId), jacksonClass, defaultValue, source);
    }

    public JacksonFlag(FlagId id, Class<T> jacksonClass, T defaultValue, FlagSource source) {
        this.id = id;
        this.jacksonClass = jacksonClass;
        this.defaultValue = defaultValue;
        this.source = source;
    }

    @Override
    public FlagId id() {
        return id;
    }

    public T value() {
        return source.getString(id).map(string -> uncheck(() -> mapper.readValue(string, jacksonClass))).orElse(defaultValue);
    }

    @Override
    public String toString() {
        return "JacksonFlag{" +
                "id=" + id +
                ", jacksonClass=" + jacksonClass +
                ", defaultValue=" + defaultValue +
                ", source=" + source +
                '}';
    }
}
