// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.component.Version;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.json.wire.WireCondition;

import java.util.function.Predicate;

/**
 * @author hakonhall
 */
public class RelationalCondition implements Condition {
    private final RelationalPredicate relationalPredicate;
    private final Predicate<String> predicate;
    private final FetchVector.Dimension dimension;

    public static RelationalCondition create(CreateParams params) {
        if (!params.values().isEmpty()) {
            throw new IllegalArgumentException(RelationalCondition.class.getSimpleName() +
                    " does not support the 'values' field");
        }

        String predicate = params.predicate().orElseThrow(() ->
                new IllegalArgumentException(RelationalCondition.class.getSimpleName() +
                        " requires the predicate field in the condition"));
        RelationalPredicate relationalPredicate = RelationalPredicate.fromWire(predicate);

        switch (params.dimension()) {
            case VESPA_VERSION:
                final Version rightVersion = Version.fromString(relationalPredicate.rightOperand());
                Predicate<String> p = (String leftString) -> {
                    Version leftVersion = Version.fromString(leftString);
                    return relationalPredicate.operator().evaluate(leftVersion, rightVersion);
                };
                return new RelationalCondition(relationalPredicate, p, params.dimension());
            default:
                throw new IllegalArgumentException(RelationalCondition.class.getSimpleName() +
                        " not supported for dimension " + FetchVector.Dimension.VESPA_VERSION.name());
        }
    }

    private RelationalCondition(RelationalPredicate relationalPredicate, Predicate<String> predicate,
                                FetchVector.Dimension dimension) {
        this.relationalPredicate = relationalPredicate;
        this.predicate = predicate;
        this.dimension = dimension;
    }

    @Override
    public Type type() {
        return Type.RELATIONAL;
    }

    @Override
    public FetchVector.Dimension dimension() {
        return dimension;
    }

    @Override
    public CreateParams toCreateParams() {
        return new CreateParams(dimension).withPredicate(relationalPredicate.toWire());
    }

    @Override
    public boolean test(FetchVector fetchVector) {
        return fetchVector.getValue(dimension).map(predicate::test).orElse(false);
    }

    @Override
    public WireCondition toWire() {
        var condition = new WireCondition();
        condition.type = Condition.Type.RELATIONAL.toWire();
        condition.dimension = DimensionHelper.toWire(dimension);
        condition.predicate = relationalPredicate.toWire();
        return condition;
    }
}
