// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.function.Function;

/**
 * @author hakonhall
 */
public class StringFlag implements Flag {
    private final FlagId id;
    private final String defaultValue;
    private final FlagSource source;

    public static Function<FlagSource, StringFlag> createUnbound(String flagId, String defaultValue) {
        return createUnbound(new FlagId(flagId), defaultValue);
    }

    public static Function<FlagSource, StringFlag> createUnbound(FlagId id, String defaultValue) {
        return source -> new StringFlag(id, defaultValue, source);
    }

    public StringFlag(String flagId, String defaultValue, FlagSource source) {
        this(new FlagId(flagId), defaultValue, source);
    }

    public StringFlag(FlagId id, String defaultValue, FlagSource source) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.source = source;
    }

    @Override
    public FlagId id() {
        return id;
    }

    public String value() {
        return source.getString(id).orElse(defaultValue);
    }

    @Override
    public String toString() {
        return "StringFlag{" +
                "id=" + id +
                ", defaultValue='" + defaultValue + '\'' +
                ", source=" + source +
                '}';
    }
}
