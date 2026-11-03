// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.prelude.query.NumericInItem;
import com.yahoo.prelude.query.StringInItem;
import com.yahoo.prelude.query.WeightedSetItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void testStringTokenListParsing() {
        assertStringTokens("", List.of());
        assertStringTokens("a", List.of("a"));
        assertStringTokens("a,b", List.of("a", "b"));
        assertStringTokens("a, b", List.of("a", "b"));
        assertStringTokens("'a', \"b\"", List.of("a", "b"));
        assertStringTokens("'a,b', c", List.of("a,b", "c"));
    }

    /** A JSON array, as produced by passing the parameter as an array in a POSTed JSON query. */
    @Test
    void testStringTokenListParsingOfJsonArray() {
        assertStringTokens("[]", List.of());
        assertStringTokens("[ ]", List.of());
        assertStringTokens("[a]", List.of("a"));
        assertStringTokens("[a, b]", List.of("a", "b"));
        assertStringTokens("[\"a\", \"b\"]", List.of("a", "b"));
        assertStringTokens("[ \"a\" , 'b' ]", List.of("a", "b"));
        assertStringTokens("[\"a,b\", c]", List.of("a,b", "c"));
        assertThrows(IllegalArgumentException.class, () -> assertStringTokens("[a, b", List.of()));
    }

    @Test
    void testNumericTokenListParsing() {
        assertNumericTokens("1", List.of(1L));
        assertNumericTokens("1,2", List.of(1L, 2L));
        assertNumericTokens("1, 2", List.of(1L, 2L));
    }

    /** A JSON array, as produced by passing the parameter as an array in a POSTed JSON query. */
    @Test
    void testNumericTokenListParsingOfJsonArray() {
        assertNumericTokens("[]", List.of());
        assertNumericTokens("[ ]", List.of());
        assertNumericTokens("[1]", List.of(1L));
        assertNumericTokens("[1, 2]", List.of(1L, 2L));
        assertNumericTokens("[ 1 , 2 ]", List.of(1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> assertNumericTokens("[1, 2", List.of()));
    }

    private void assertStringTokens(String string, List<String> expected) {
        StringInItem item = new StringInItem("test");
        ParameterListParser.addStringTokensFromString(string, item);
        assertEquals(Set.copyOf(expected), Set.copyOf(item.getTokens()), "Tokens of '" + string + "'");
    }

    private void assertNumericTokens(String string, List<Long> expected) {
        NumericInItem item = new NumericInItem("test");
        ParameterListParser.addNumericTokensFromString(string, item);
        assertEquals(Set.copyOf(expected), Set.copyOf(item.getTokens()), "Tokens of '" + string + "'");
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
