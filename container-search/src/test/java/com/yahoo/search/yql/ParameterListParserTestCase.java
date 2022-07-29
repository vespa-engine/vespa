// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.prelude.query.WeightedSetItem;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class ParameterListParserTestCase {

    @Test
    void testMapParsing() {
        assertParsed("{}", Map.of());
        assertParsed("{a:12}", Map.of("a", 12));
        assertParsed("{'a':12}", Map.of("a", 12));
        assertParsed("{\"a\":12}", Map.of("a", 12));
        assertParsed("{a:12,b:13}", Map.of("a", 12, "b", 13));
        assertParsed("{a:12, b:13}", Map.of("a", 12, "b", 13));
        assertParsed("  { a:12, b:13} ", Map.of("a", 12, "b", 13));
        assertParsed("{a:12, 'b':13} ", Map.of("a", 12, "b", 13));
        assertParsed("{a:12,'b':13, \"c,}\": 14}", Map.of("a", 12, "b", 13, "c,}", 14));
    }

    @Test
    void testArrayParsing() {
        assertParsed("[]", Map.of());
        assertParsed("[[0,12]]", Map.of(0L, 12));
        assertParsed("[[0,12],[1,13]]", Map.of(0L, 12, 1L, 13));
        assertParsed("[[0,12], [1,13]]", Map.of(0L, 12, 1L, 13));
        assertParsed("  [ [0,12], [ 1,13]] ", Map.of(0L, 12, 1L, 13));
    }

    private void assertParsed(String string, Map<?, Integer> expected) {
        WeightedSetItem item = new WeightedSetItem("test");
        ParameterListParser.addItemsFromString(string, item);
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), item.getTokenWeight(entry.getKey()), "Key '" + entry.getKey() + "'");
        }
        assertEquals(expected.size(), item.getNumTokens(), "Token count is correct");
    }

}
