// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features;

import com.yahoo.api.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Calculates the elementCompleteness features
 *
 * @author bratseth
 */
public class ElementCompleteness {

    /** Hardcoded to default for now */
    private static final double fieldCompletenessImportance	= 0.05;

    /**
     * Computes the following elementCompleteness features:
     * <ul>
     *     <li><code>completeness</code>
     *     <li><code>fieldCompleteness</code>
     *     <li><code>queryCompleteness</code>
     *     <li><code>elementWeight</code>
     * </ul>
     *
     * @param queryTerms the query terms with associated weights to compute over
     * @param field a set of weighted field values, where each is taken to be a space-separated string of tokens
     * @return a features object containing the values listed above
     */
    public static Features compute(Map<String, Integer> queryTerms, Item[] field) {
        double completeness = 0;
        double fieldCompleteness = 0;
        double queryCompleteness = 0;
        double elementWeight = 0;

        double queryTermWeightSum = sum(queryTerms.values());

        for (Item item : field) {
            String[] itemTokens =item.value().split(" ");
            int matchCount = 0;
            int matchWeightSum = 0;
            for (String token : itemTokens) {
                Integer weight = queryTerms.get(token);
                if (weight == null) continue;
                matchCount++;
                matchWeightSum += weight;
            }
            double itemFieldCompleteness = (double)matchCount / itemTokens.length;
            double itemQueryCompleteness = matchWeightSum / queryTermWeightSum;
            double itemCompleteness =
                    fieldCompletenessImportance * itemFieldCompleteness +
                    (1 - fieldCompletenessImportance) * itemQueryCompleteness;
            if (itemCompleteness > completeness) {
                completeness = itemCompleteness;
                fieldCompleteness = itemFieldCompleteness;
                queryCompleteness = itemQueryCompleteness;
                elementWeight = item.weight();
            }
        }

        Map<String, Value> features = new HashMap<>();
        features.put("completeness", new DoubleValue(completeness));
        features.put("fieldCompleteness", new DoubleValue(fieldCompleteness));
        features.put("queryCompleteness", new DoubleValue(queryCompleteness));
        features.put("elementWeight", new DoubleValue(elementWeight));
        return new Features(features);
    }

    private static int sum(Collection<Integer> integers) {
        int sum = 0;
        for (int integer : integers)
            sum += integer;
        return sum;
    }

    public static class Item {

        private final String value;
        private final double weight;

        public Item(String value, double weight) {
            this.value = value;
            this.weight = weight;
        }

        public String value() { return value; }
        public double weight() { return weight; }

    }

}
