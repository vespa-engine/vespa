// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.function.Function;

/**
 * A FeatureFlag is true only if set (enabled).
 *
 * @author hakonhall
 */
public class FeatureFlag implements Flag {
    private final FlagId id;
    private final FlagSource source;

    public static Function<FlagSource, FeatureFlag> createUnbound(String flagId) {
        return createUnbound(new FlagId(flagId));
    }

    public static Function<FlagSource, FeatureFlag> createUnbound(FlagId flagId) {
        return source -> new FeatureFlag(flagId, source);
    }

    public FeatureFlag(String flagId, FlagSource source) {
        this(new FlagId(flagId), source);
    }

    public FeatureFlag(FlagId id, FlagSource source) {
        this.id = id;
        this.source = source;
    }

    @Override
    public FlagId id() {
        return id;
    }

    public boolean isSet() {
        return source.hasFeature(id);
    }

    @Override
    public String toString() {
        return "FeatureFlag{" +
                "id=" + id +
                ", source=" + source +
                '}';
    }
}
