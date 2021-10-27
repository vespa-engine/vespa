// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.json.wire.WireCondition;

import java.util.List;

/**
 * @author hakonhall
 */
public abstract class ListCondition implements Condition {
    private final Condition.Type type;
    private final FetchVector.Dimension dimension;
    private final List<String> values;
    private final boolean isWhitelist;

    protected ListCondition(Type type, CreateParams params) {
        this.type = type;
        this.dimension = params.dimension();
        this.values = List.copyOf(params.values());
        this.isWhitelist = type == Type.WHITELIST;

        if (params.predicate().isPresent()) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " does not support the 'predicate' field");
        }
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public FetchVector.Dimension dimension() {
        return dimension;
    }

    @Override
    public CreateParams toCreateParams() {
        return new CreateParams(dimension).withValues(values);
    }

    @Override
    public boolean test(FetchVector fetchVector) {
        boolean listContainsValue = fetchVector.getValue(dimension).map(values::contains).orElse(false);
        return isWhitelist == listContainsValue;
    }

    @Override
    public WireCondition toWire() {
        var condition = new WireCondition();
        condition.type = type.toWire();
        condition.dimension = DimensionHelper.toWire(dimension);
        condition.values = values.isEmpty() ? null : values;
        return condition;
    }
}
