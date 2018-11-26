// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.function.Function;

/**
 * @author hakonhall
 */
public class IntFlag implements Flag {
    private final FlagId id;
    private final int defaultValue;
    private final FlagSource source;

    public static Function<FlagSource, IntFlag> createUnbound(String flagId, int defaultValue) {
        return createUnbound(new FlagId(flagId), defaultValue);
    }

    public static Function<FlagSource, IntFlag> createUnbound(FlagId id, int defaultValue) {
        return source -> new IntFlag(id, defaultValue, source);
    }

    public IntFlag(String flagId, int defaultValue, FlagSource source) {
        this(new FlagId(flagId), defaultValue, source);
    }

    public IntFlag(FlagId id, int defaultValue, FlagSource source) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.source = source;
    }

    @Override
    public FlagId id() {
        return id;
    }

    public int value() {
        return source.getString(id).map(String::trim).map(Integer::parseInt).orElse(defaultValue);
    }

    @Override
    public String toString() {
        return "IntFlag{" +
                "id=" + id +
                ", defaultValue=" + defaultValue +
                ", source=" + source +
                '}';
    }
}
