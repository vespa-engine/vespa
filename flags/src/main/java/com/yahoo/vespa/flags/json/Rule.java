// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.JsonNodeRawFlag;
import com.yahoo.vespa.flags.RawFlag;
import com.yahoo.vespa.flags.json.wire.WireRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author hakonhall
 */
public class Rule {
    private final List<Condition> andConditions;
    private final Optional<RawFlag> valueToApply;

    public Rule(Optional<RawFlag> valueToApply, Condition... andConditions) {
        this(valueToApply, List.of(andConditions));
    }

    public Rule(Optional<RawFlag> valueToApply, List<Condition> andConditions) {
        this.andConditions = List.copyOf(andConditions);
        this.valueToApply = valueToApply;
    }

    public List<Condition> conditions() {
        return andConditions;
    }

    /** Returns true if all the conditions satisfy the given fetch vector */
    public boolean match(FetchVector fetchVector) {
        return andConditions.stream().allMatch(condition -> condition.test(fetchVector));
    }

    /**
     * Returns true if all the conditions on dimensions set in the fetch vector are satisfied.
     * Conditions on dimensions not specified in the given fetch vector are ignored.
     */
    public boolean partialMatch(FetchVector fetchVector) {
        return andConditions.stream()
                .allMatch(condition -> !fetchVector.hasDimension(condition.dimension()) || condition.test(fetchVector));
    }

    /**
     * Returns a copy of this rule without those conditions that can be resolved by the fetch vector.  Returns empty
     * if any of those conditions are false.
     */
    public Optional<Rule> partialResolve(FetchVector fetchVector) {
        List<Condition> newConditions = new ArrayList<>();
        for (var condition : andConditions) {
            if (fetchVector.hasDimension(condition.dimension())) {
                if (!condition.test(fetchVector)) {
                    return Optional.empty();
                }
            } else {
                newConditions.add(condition);
            }
        }

        return Optional.of(new Rule(valueToApply, newConditions));
    }

    public Optional<RawFlag> getValueToApply() {
        return valueToApply;
    }

    public WireRule toWire() {
        WireRule wireRule = new WireRule();

        if (!andConditions.isEmpty()) {
            wireRule.andConditions = andConditions.stream().map(Condition::toWire).toList();
        }

        wireRule.value = valueToApply.map(RawFlag::asJsonNode).orElse(null);

        return wireRule;
    }

    public static Rule fromWire(WireRule wireRule) {
        List<Condition> conditions = wireRule.andConditions == null ?
                List.of() :
                wireRule.andConditions.stream().map(Condition::fromWire).toList();
        Optional<RawFlag> value = wireRule.value == null || wireRule.value.isNull() ?
                                  Optional.empty() :
                                  Optional.of(JsonNodeRawFlag.fromJsonNode(wireRule.value));
        return new Rule(value, conditions);
    }

    @Override
    public String toString() {
        return "Rule{" +
               "andConditions=" + andConditions +
               ", valueToApply=" + valueToApply +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rule rule = (Rule) o;
        return andConditions.equals(rule.andConditions) && valueToApply.equals(rule.valueToApply);
    }

    @Override
    public int hashCode() {
        return Objects.hash(andConditions, valueToApply);
    }
}
