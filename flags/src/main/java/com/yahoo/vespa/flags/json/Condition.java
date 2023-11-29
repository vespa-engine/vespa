// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.Dimension;
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
        private final Dimension dimension;
        private List<String> values = List.of();
        private Optional<String> predicate = Optional.empty();

        public CreateParams(Dimension dimension) { this.dimension = Objects.requireNonNull(dimension); }

        public CreateParams withValues(String... values) { return withValues(List.of(values)); }
        public CreateParams withValues(List<String> values) {
            this.values = List.copyOf(values);
            return this;
        }

        public CreateParams withPredicate(String predicate) {
            this.predicate = Optional.of(predicate);
            return this;
        }

        public Dimension dimension() { return dimension; }
        public List<String> values() { return values; }
        public Optional<String> predicate() { return predicate; }

        public Condition createAs(Condition.Type type) {
            switch (type) {
                case WHITELIST: return WhitelistCondition.create(this);
                case BLACKLIST: return BlacklistCondition.create(this);
                case RELATIONAL: return RelationalCondition.create(this);
            }

            throw new IllegalArgumentException("Unknown type '" + type + "'");
        }
    }

    static Condition fromWire(WireCondition wireCondition) {
        Objects.requireNonNull(wireCondition.type);
        Condition.Type type = Condition.Type.fromWire(wireCondition.type);

        Objects.requireNonNull(wireCondition.dimension);
        Dimension dimension = Dimension.fromWire(wireCondition.dimension);
        var params = new CreateParams(dimension);

        if (wireCondition.values != null) {
            params.withValues(wireCondition.values);
        }

        if (wireCondition.predicate != null) {
            params.withPredicate(wireCondition.predicate);
        }

        return params.createAs(type);
    }

    Condition.Type type();

    Dimension dimension();

    CreateParams toCreateParams();

    WireCondition toWire();
}
