// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.Map;

public class WeightedSet extends QueryChain {

    private String fieldName;
    private Map<String, Integer> weightedSet;

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
        return "weightedSet(" + fieldName + ", " + Q.gson.toJson(weightedSet) + ")";
    }

    @Override
    boolean hasPositiveSearchField(String fieldName) {
        // TODO: implementation
        return false;
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        // TODO: implementation
        return false;
    }

    @Override
    boolean hasNegativeSearchField(String fieldName) {
        // TODO: implementation
        return false;
    }

    @Override
    boolean hasNegativeSearchField(String fieldName, Object value) {
        // TODO: implementation
        return false;
    }
}
