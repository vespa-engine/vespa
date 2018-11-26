// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.function.Function;

/**
 * A FeatureFlag defaults to false (but see {@link #defaultToTrue()}).
 *
 * @author hakonhall
 */
public class FeatureFlag implements Flag {
    private final boolean defaultValue;
    private final FlagId id;
    private final FlagSource source;

    public static Function<FlagSource, FeatureFlag> createUnbound(String flagId, boolean defaultValue) {
        return createUnbound(new FlagId(flagId), defaultValue);
    }

    public static Function<FlagSource, FeatureFlag> createUnbound(FlagId id, boolean defaultValue) {
        return source -> new FeatureFlag(id, defaultValue, source);
    }

    public FeatureFlag(String flagId, FlagSource source) {
        this(new FlagId(flagId), source);
    }

    public FeatureFlag(FlagId id, FlagSource source) {
        this(id, false, source);
    }

    private FeatureFlag(FlagId id, boolean defaultValue, FlagSource source) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.source = source;
    }

    public FeatureFlag defaultToTrue() {
        return new FeatureFlag(id, true, source);
    }

    @Override
    public FlagId id() {
        return id;
    }

    public boolean value() {
        return source.getString(id).map(FeatureFlag::booleanFromString).orElse(defaultValue);
    }

    private static boolean booleanFromString(String string) {
        String canonicalString = string.trim().toLowerCase();
        if (canonicalString.equals("true")) {
            return true;
        } else if (canonicalString.equals("false")) {
            return false;
        }

        throw new IllegalArgumentException("Unable to convert to true or false: '" + string + "'");
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
