// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.json.wire.WireCondition;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author hakonhall
 */
public class Condition implements Predicate<FetchVector> {
    public enum Type { WHITELIST, BLACKLIST }

    private final Type type;
    private final FetchVector.Dimension dimension;
    private final List<String> values;

    public Condition(Type type, FetchVector.Dimension dimension, String... values) {
        this(type, dimension, Arrays.asList(values));
    }

    public Condition(Type type, FetchVector.Dimension dimension, List<String> values) {
        this.type = type;
        this.dimension = dimension;
        this.values = values;
    }

    @Override
    public boolean test(FetchVector vector) {
        boolean isMember = vector.getValue(dimension).filter(values::contains).isPresent();

        switch (type) {
            case WHITELIST: return isMember;
            case BLACKLIST: return !isMember;
            default: throw new IllegalArgumentException("Unknown type " + type);
        }
    }

    public static Condition fromWire(WireCondition wireCondition) {
        Objects.requireNonNull(wireCondition.type);
        Type type = Type.valueOf(wireCondition.type.toUpperCase());

        Objects.requireNonNull(wireCondition.dimension);
        FetchVector.Dimension dimension = DimensionHelper.fromWire(wireCondition.dimension);

        List<String> values = wireCondition.values == null ? List.of() : wireCondition.values;

        return new Condition(type, dimension, values);
    }

    public WireCondition toWire() {
        WireCondition wire = new WireCondition();
        wire.type = type.name().toLowerCase();
        wire.dimension = DimensionHelper.toWire(dimension);
        wire.values = values.isEmpty() ? null : values;
        return wire;
    }
}
