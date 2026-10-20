// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

import java.util.List;
import java.util.stream.Collectors;

/** Keeps the first grouping row with each distinct key tuple in a document. */
@Beta
public class OncePerDocumentPredicate extends FilterExpression {
    private final List<GroupingExpression> keys;

    public OncePerDocumentPredicate(List<GroupingExpression> keys) {
        if (keys.isEmpty()) throw new IllegalArgumentException("onceperdoc requires at least one key expression");
        for (var key : keys) {
            key.visit(expression -> {
                if (expression instanceof AggregatorNode)
                    throw new IllegalArgumentException("onceperdoc keys cannot contain aggregators");
            });
        }
        this.keys = List.copyOf(keys);
    }

    public List<GroupingExpression> getKeys() { return keys; }

    @Override
    public String toString() {
        return "onceperdoc(" + keys.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public OncePerDocumentPredicate copy() {
        return new OncePerDocumentPredicate(keys.stream().map(GroupingExpression::copy).toList());
    }
}
