// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import com.google.gson.Gson;

import java.util.List;
import java.util.Map;

public final class Q {

    static Gson gson = new Gson();
    private static Sources SELECT_ALL_FROM_SOURCES_ALL = new Sources(new Select("*"), "*");

    public static Select select(String fieldName) {
        return new Select(fieldName);
    }

    public static Select select(String fieldName, String... others) {
        return new Select(fieldName, others);
    }

    public static Field p(String fieldName) {
        return SELECT_ALL_FROM_SOURCES_ALL.where(fieldName);
    }

    public static Query p(QueryChain query) {
        return new Query(SELECT_ALL_FROM_SOURCES_ALL, query);
    }

    public static Query p() {
        return new Query(SELECT_ALL_FROM_SOURCES_ALL);
    }

    public static Rank rank(Query query, Query... ranks) {
        return new Rank(query, ranks);
    }

    public static UserInput ui(String value) {
        return new UserInput(value);
    }

    public static UserInput ui(Annotation a, String value) {
        return new UserInput(a, value);
    }

    public static UserInput ui(String index, String value) {
        return ui(A.defaultIndex(index), value);
    }

    public static DotProduct dotPdt(String field, Map<String, Integer> weightedSet) {
        return new DotProduct(field, weightedSet);
    }

    public static WeightedSet wtdSet(String field, Map<String, Integer> weightedSet) {
        return new WeightedSet(field, weightedSet);
    }

    public static NonEmpty nonEmpty(Query query) {
        return new NonEmpty(query);
    }

    public static Wand wand(String field, Map<String, Integer> weightedSet) {
        return new Wand(field, weightedSet);
    }

    public static Wand wand(String field, List<List<Integer>> numericRange) {
        return new Wand(field, numericRange);
    }

    public static WeakAnd weakand(String field, Query query) {
        return new WeakAnd(field, query);
    }
}
