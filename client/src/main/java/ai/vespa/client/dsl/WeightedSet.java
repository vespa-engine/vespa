// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.Map;

public class WeightedSet extends QueryChain {

    private final String fieldName;
    private final Map<String, Integer> weightedSet;

    WeightedSet(String fieldName, Map<String, Integer> weightedSet) {
        this.fieldName = fieldName;
        this.weightedSet = weightedSet;
        this.nonEmpty = true;
    }

    @Override
    public Select getSelect() {
        return sources.select;
    }

    @Override
    public String toString() {
        return "weightedSet(" + fieldName + ", " + Q.toJson(weightedSet) + ")";
    }

    @Override
    boolean hasPositiveSearchField(String fieldName) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

    @Override
    boolean hasNegativeSearchField(String fieldName) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

    @Override
    boolean hasNegativeSearchField(String fieldName, Object value) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

}