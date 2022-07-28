// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.collections.Pair;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class GeminiTestCase extends AbstractExportingTestCase {

    @Test
    void testRanking2() throws IOException, ParseException {
        DerivedConfiguration c = assertCorrectDeriving("gemini2");
        RawRankProfile p = c.getRankProfileList().getRankProfiles().get("test");
        Map<String, String> ranking = removePartKeySuffixes(asMap(p.configProperties()));
        assertEquals("attribute(right)", resolve(lookup("toplevel", ranking), ranking));
    }

    private Map<String, String> asMap(List<Pair<String, String>> properties) {
        Map<String, String> map = new HashMap<>();
        for (Pair<String, String> property : properties)
            map.put(property.getFirst(), property.getSecond());
        return map;
    }

    private Map<String, String> removePartKeySuffixes(Map<String, String> p) {
        Map<String, String> pWithoutSuffixes = new HashMap<>();
        for (Map.Entry<String, String> entry : p.entrySet())
            pWithoutSuffixes.put(removePartSuffix(entry.getKey()), entry.getValue());
        return pWithoutSuffixes;
    }

    private String removePartSuffix(String s) {
        int partIndex = s.indexOf(".part");
        if (partIndex <= 0) return s;
        return s.substring(0, partIndex);
    }

    /**
     * Recursively resolves references to other ranking expressions - rankingExpression(name) -
     * and replaces the reference by the expression
     */
    private String resolve(String expression, Map<String, String> ranking) {
        int referenceStartIndex;
        while ((referenceStartIndex = expression.indexOf("rankingExpression(")) >= 0) {
            int referenceEndIndex = expression.indexOf(")", referenceStartIndex);
            expression = expression.substring(0, referenceStartIndex) +
                         resolve(lookup(expression.substring(referenceStartIndex + "rankingExpression(".length(), referenceEndIndex), ranking), ranking) +
                         expression.substring(referenceEndIndex + 1);
        }
        return expression;
    }

    private String lookup(String expressionName, Map<String, String> ranking) {
        String value = ranking.get("rankingExpression(" + expressionName + ").rankingScript");
        if (value == null) {
            return expressionName;
        }
        return value;
    }

}
