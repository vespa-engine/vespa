// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import com.google.gson.Gson;

import java.util.List;
import java.util.Map;

/**
 * Helper class for generating Vespa search queries
 * https://docs.vespa.ai/en/reference/query-language-reference.html
 */
public final class Q {

    static Gson gson = new Gson();
    private static Sources SELECT_ALL_FROM_SOURCES_ALL = new Sources(new Select("*"), "*");

    public static Select select(String fieldName) { return new Select(fieldName);
    }

    public static Select select(String fieldName, String... others) {
        return new Select(fieldName, others);
    }

    /**
     * P represents "parentheses", used for generated a query in the parentheses.
     *
     * @param fieldName the field name
     * @return the field
     */
    public static Field p(String fieldName) {
        return SELECT_ALL_FROM_SOURCES_ALL.where(fieldName);
    }

    /**
     * P represents "parentheses", used for generated a query in the parentheses.
     *
     * @param query the query
     * @return the query
     */
    public static Query p(QueryChain query) {
        return new Query(SELECT_ALL_FROM_SOURCES_ALL, query);
    }

    /**
     * P represents "parentheses", used for generated a query in the parentheses.
     * This method generates an empty query
     *
     * @return the empty query
     */
    public static Query p() {
        return new Query(SELECT_ALL_FROM_SOURCES_ALL);
    }

    /**
     * Rank rank.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#rank
     *
     * @param query the query
     * @param ranks the ranks
     * @return the rank query
     */
    public static Rank rank(Query query, Query... ranks) {
        return new Rank(query, ranks);
    }

    /**
     * UI represents "userInput".
     * https://docs.vespa.ai/en/reference/query-language-reference.html#userinput
     *
     * @param value the value
     * @return the user input query
     */
    public static UserInput ui(String value) {
        return new UserInput(value);
    }

    /**
     * userInput with an annotation.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#userinput
     *
     * @param a     the a
     * @param value the value
     * @return the user input query
     */
    public static UserInput ui(Annotation a, String value) {
        return new UserInput(a, value);
    }

    /**
     * A convenience method to generate userInput with default index annotation.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#userinput
     *
     * @param index the index
     * @param value the value
     * @return the user input query
     */
    public static UserInput ui(String index, String value) {
        return ui(A.defaultIndex(index), value);
    }

    /**
     * dotPdt represents "dotProduct".
     * https://docs.vespa.ai/en/reference/query-language-reference.html#dotproduct
     *
     * @param field       the field
     * @param weightedSet the weighted set
     * @return the dot product query
     */
    public static DotProduct dotPdt(String field, Map<String, Integer> weightedSet) {
        return new DotProduct(field, weightedSet);
    }

    /**
     * wtdSet represents "weightedSet".
     * https://docs.vespa.ai/en/reference/query-language-reference.html#weightedset
     *
     * @param field       the field
     * @param weightedSet the weighted set
     * @return the weighted set query
     */
    public static WeightedSet wtdSet(String field, Map<String, Integer> weightedSet) {
        return new WeightedSet(field, weightedSet);
    }

    /**
     * NonEmpty non empty.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#nonempty
     *
     * @param query the query
     * @return the non empty query
     */
    public static NonEmpty nonEmpty(Query query) {
        return new NonEmpty(query);
    }

    /**
     * Wand wand.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#wand
     *
     * @param field       the field
     * @param weightedSet the weighted set
     * @return the wand query
     */
    public static Wand wand(String field, Map<String, Integer> weightedSet) {
        return new Wand(field, weightedSet);
    }

    /**
     * Wand wand.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#wand
     *
     * @param field        the field
     * @param numericRange the numeric range
     * @return the wand query
     */
    public static Wand wand(String field, List<List<Integer>> numericRange) {
        return new Wand(field, numericRange);
    }

    /**
     * Weakand weak and.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#weakand
     *
     * @param field the field
     * @param query the query
     * @return the weak and query
     */
    public static WeakAnd weakand(String field, Query query) {
        return new WeakAnd(field, query);
    }
}
