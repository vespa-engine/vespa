// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.function.Function;

/**
 * @author hakonhall
 */
public class LongFlag implements Flag {
    private final FlagId id;
    private final long defaultValue;
    private final FlagSource source;

    public static Function<FlagSource, LongFlag> createUnbound(String flagId, int defaultValue) {
        return createUnbound(new FlagId(flagId), defaultValue);
    }

    public static Function<FlagSource, LongFlag> createUnbound(FlagId id, int defaultValue) {
        return source -> new LongFlag(id, defaultValue, source);
    }

    public LongFlag(String flagId, long defaultValue, FlagSource source) {
        this(new FlagId(flagId), defaultValue, source);
    }

    public LongFlag(FlagId id, long defaultValue, FlagSource source) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.source = source;
    }

    @Override
    public FlagId id() {
        return id;
    }

    public long value() {
        return source.getString(id).map(String::trim).map(Long::parseLong).orElse(defaultValue);
    }

    @Override
    public String toString() {
        return "LongFlag{" +
                "id=" + id +
                ", defaultValue=" + defaultValue +
                ", source=" + source +
                '}';
    }
}
