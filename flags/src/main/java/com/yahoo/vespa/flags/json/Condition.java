// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.json.wire.WireCondition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * @author hakonhall
 */
public interface Condition extends Predicate<FetchVector> {
    enum Type {
        WHITELIST,
        BLACKLIST,
        RELATIONAL;

        public String toWire() { return name().toLowerCase(); }

        public static Type fromWire(String typeString) {
            for (Type type : values()) {
                if (type.name().equalsIgnoreCase(typeString)) {
                    return type;
                }
            }

            throw new IllegalArgumentException("Unknown type: '" + typeString + "'");
        }
    }

    class CreateParams {
        private final FetchVector.Dimension dimension;
        private final List<String> values;
        private final Optional<String> predicate;

        public CreateParams(FetchVector.Dimension dimension, List<String> values, Optional<String> predicate) {
            this.dimension = Objects.requireNonNull(dimension);
            this.values = Objects.requireNonNull(values);
            this.predicate = Objects.requireNonNull(predicate);
        }

        public FetchVector.Dimension dimension() { return dimension; }
        public List<String> values() { return values; }
        public Optional<String> predicate() { return predicate; }
    }

    static Condition fromWire(WireCondition wireCondition) {
        Objects.requireNonNull(wireCondition.type);
        Condition.Type type = Condition.Type.fromWire(wireCondition.type);

        Objects.requireNonNull(wireCondition.dimension);
        FetchVector.Dimension dimension = DimensionHelper.fromWire(wireCondition.dimension);

        List<String> values = wireCondition.values == null ? List.of() : wireCondition.values;
        Optional<String> predicate = Optional.ofNullable(wireCondition.predicate);

        var params = new CreateParams(dimension, values, predicate);

        switch (type) {
            case WHITELIST: return new WhitelistCondition(params);
            case BLACKLIST: return new BlacklistCondition(params);
            case RELATIONAL: return RelationalCondition.create(params);
        }

        throw new IllegalArgumentException("Unknown type '" + type + "'");
    }

    WireCondition toWire();
}
