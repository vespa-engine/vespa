// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

/**
 * @author Simon Thoresen Hult
 */
public class Predicates {

    public static Conjunction and(Predicate... operands) {
        return new Conjunction(operands);
    }

    public static Disjunction or(Predicate... operands) {
        return new Disjunction(operands);
    }

    public static Negation not(Predicate operand) {
        return new Negation(operand);
    }

    public static BooleanPredicate value(boolean value) {
        return new BooleanPredicate(value);
    }

    public static FeatureBuilder feature(String key) {
        return new FeatureBuilder(key);
    }

    public static class FeatureBuilder {

        private final String key;

        public FeatureBuilder(String key) {
            this.key = key;
        }

        public FeatureRange lessThan(long toExclusive) {
            return new FeatureRange(key, null, toExclusive - 1);
        }

        public FeatureRange lessThanOrEqualTo(long toInclusive) {
            return new FeatureRange(key, null, toInclusive);
        }

        public FeatureRange greaterThan(long fromExclusive) {
            return new FeatureRange(key, fromExclusive + 1, null);
        }

        public FeatureRange greaterThanOrEqualTo(long fromInclusive) {
            return new FeatureRange(key, fromInclusive, null);
        }

        public FeatureRange inRange(long fromInclusive, long toInclusive) {
            return new FeatureRange(key, fromInclusive, toInclusive);
        }

        public Negation notInRange(long fromInclusive, long toInclusive) {
            return new Negation(new FeatureRange(key, fromInclusive, toInclusive));
        }

        public FeatureSet inSet(String... values) {
            return new FeatureSet(key, values);
        }

        public Negation notInSet(String... values) {
            return new Negation(new FeatureSet(key, values));
        }
    }

}
