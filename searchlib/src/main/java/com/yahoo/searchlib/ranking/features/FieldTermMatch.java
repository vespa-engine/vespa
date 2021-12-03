// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features;

import com.yahoo.api.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates the fieldTermMatch features
 *
 * @author bratseth
 */
@Beta
public class FieldTermMatch {

    /**
     * Computes the fieldTermMatch features:
     * <ul>
     *     <li><code>firstPosition</code> - the position of the first occurrence of this query term in this index field</li>
     *     <li><code>occurrences</code> - the position of the first occurrence of this query term in this index field</li>
     * </ul>
     * @param queryTerm the term to return these features for
     * @param field the field value to compute over, assumed to be a space-separated string of tokens
     * @return a features object containing the two values described above
     */
    public static Features compute(String queryTerm, String field) {
        Map<String, Value> features = new HashMap<>();

        String[] tokens = field.split(" ");

        int occurrences = 0;
        int firstPosition = 1000000;
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].equals(queryTerm)) {
                if (occurrences == 0)
                    firstPosition = i;
                occurrences++;
            }
        }
        features.put("firstPosition", new DoubleValue(firstPosition));
        features.put("occurrences", new DoubleValue(occurrences));
        return new Features(features);
    }

}
