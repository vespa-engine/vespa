// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
public class ElementCompletenessTestCase {

    private static final double delta = 0.0001;

    @Test
    public void testElementCompleteness1() {
        Map<String, Integer> query = createQuery();
        ElementCompleteness.Item[] items = createField(1);

        Features f = ElementCompleteness.compute(query, items);
        assertEquals(0.26111111111111107, f.get("completeness").asDouble(), delta);
        assertEquals(1.0, f.get("fieldCompleteness").asDouble(), delta);
        assertEquals(0.2222222222222222, f.get("queryCompleteness").asDouble(), delta);
        assertEquals(3.0, f.get("elementWeight").asDouble(), delta);
    }

    @Test
    public void testElementCompleteness2() {
        Map<String, Integer> query = createQuery();
        ElementCompleteness.Item[] items = createField(2);

        Features f = ElementCompleteness.compute(query, items);
        assertEquals(0.975, f.get("completeness").asDouble(), delta);
        assertEquals(0.5, f.get("fieldCompleteness").asDouble(), delta);
        assertEquals(1.0, f.get("queryCompleteness").asDouble(), delta);
        assertEquals(4.0, f.get("elementWeight").asDouble(), delta);
    }

    @Test
    public void testElementCompleteness3() {
        Map<String, Integer> query = createQuery();
        ElementCompleteness.Item[] items = createField(3);

        Features f = ElementCompleteness.compute(query, items);
        assertEquals(1.0, f.get("completeness").asDouble(), delta);
        assertEquals(1.0, f.get("fieldCompleteness").asDouble(), delta);
        assertEquals(1.0, f.get("queryCompleteness").asDouble(), delta);
        assertEquals(5.0, f.get("elementWeight").asDouble(), delta);
    }

    @Test
    public void testElementCompletenessNoMatches() {
        ElementCompleteness.Item[] items = createField(3);

        Features f = ElementCompleteness.compute(new HashMap<String, Integer>(), items);
        assertEquals(0.0, f.get("completeness").asDouble(), delta);
        assertEquals(0.0, f.get("fieldCompleteness").asDouble(), delta);
        assertEquals(0.0, f.get("queryCompleteness").asDouble(), delta);
        assertEquals(0.0, f.get("elementWeight").asDouble(), delta);
    }

    private Map<String, Integer> createQuery() {
        Map<String, Integer> query = new HashMap<>();
        query.put("a", 100);
        query.put("b", 150);
        query.put("c", 200);
        return query;
    }

    private ElementCompleteness.Item[] createField(int size) {
        ElementCompleteness.Item[] items = new ElementCompleteness.Item[size];
        if (size > 0) items[0] = new ElementCompleteness.Item("a", 3); // qc: 100/450=0.22, fc: 1.0, c: 0.611
        if (size > 1) items[1] = new ElementCompleteness.Item("a b c d e f", 4); // qc: 1.0, fc: 0.5, c: 0.75
        if (size > 2) items[2] = new ElementCompleteness.Item("a b c", 5);
        return items;
    }

}
