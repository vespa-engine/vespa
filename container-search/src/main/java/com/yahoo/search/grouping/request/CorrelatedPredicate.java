// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Keeps grouping rows whose variables match a labeled correlated query. */
public class CorrelatedPredicate extends FilterExpression {
    private final String label;
    private final Map<String, AttributeValue> bindings;

    public CorrelatedPredicate(String label, Map<String, AttributeValue> bindings) {
        this.label = Objects.requireNonNull(label);
        if (label.isBlank() || bindings.isEmpty())
            throw new IllegalArgumentException("correlated requires a label and variable/attribute pairs");
        if (bindings.keySet().stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("correlated requires non-blank variable names");
        this.bindings = Map.copyOf(bindings);
    }

    public String getLabel() { return label; }
    public Map<String, AttributeValue> getBindings() { return bindings; }

    @Override
    public String toString() {
        return "correlated(" + GroupingExpression.asImage(label) + ", " + new TreeMap<>(bindings).entrySet().stream()
                .map(binding -> GroupingExpression.asImage(binding.getKey()) + ", " + binding.getValue())
                .collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public CorrelatedPredicate copy() {
        var copied = new TreeMap<String, AttributeValue>();
        bindings.forEach((variable, field) -> copied.put(variable, field.copy()));
        return new CorrelatedPredicate(label, copied);
    }
}
