// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.Optional;
import java.util.function.Function;

/**
 * An OptionalStringFlag is a flag which is either not set (empty), or set (String present).
 *
 * @author hakonhall
 */
public class OptionalStringFlag implements Flag {
    private final FlagId id;
    private final FlagSource source;

    public static Function<FlagSource, OptionalStringFlag> createUnbound(String flagId) {
        return createUnbound(new FlagId(flagId));
    }

    public static Function<FlagSource, OptionalStringFlag> createUnbound(FlagId id) {
        return source -> new OptionalStringFlag(id, source);
    }

    public OptionalStringFlag(String flagId, FlagSource source) {
        this(new FlagId(flagId), source);
    }

    public OptionalStringFlag(FlagId id, FlagSource source) {
        this.id = id;
        this.source = source;
    }

    @Override
    public FlagId id() {
        return id;
    }

    public StringFlag bindDefault(String defaultValue) {
        return new StringFlag(id, defaultValue, source);
    }

    public Optional<String> value() {
        return source.getString(id);
    }

    @Override
    public String toString() {
        return "OptionalStringFlag{" +
                "id=" + id +
                ", source=" + source +
                '}';
    }
}
