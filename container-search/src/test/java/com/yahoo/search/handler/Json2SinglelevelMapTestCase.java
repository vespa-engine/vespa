// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.yql.MinimalQueryInserter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Json2SinglelevelMapTestCase {
    @Test
    void testDecodeString() {
        Map<String, String> m = new Json2SingleLevelMap(new ByteArrayInputStream("{\"yql\":\"text\", \"f1\":7.3, \"i1\":7, \"t\":true, \"f\":false,  \"n\":null, \"a\":[0.786, 0.193]}".getBytes(StandardCharsets.UTF_8))).parse();
        assertEquals(7, m.size());
        assertTrue(m.containsKey("yql"));
        assertTrue(m.containsKey("f1"));
        assertTrue(m.containsKey("i1"));
        assertTrue(m.containsKey("t"));
        assertTrue(m.containsKey("f"));
        assertTrue(m.containsKey("n"));
        assertTrue(m.containsKey("a"));
        assertEquals("text", m.get("yql"));
        assertEquals("7.3", m.get("f1"));
        assertEquals("7", m.get("i1"));
        assertEquals("true", m.get("t"));
        assertEquals("false", m.get("f"));
        assertEquals("null", m.get("n"));
        assertEquals("[0.786, 0.193]", m.get("a"));
    }

    @Test
    void testThatWeAllowSingleQuotes() {
        Map<String, String> m = new Json2SingleLevelMap(new ByteArrayInputStream("{'yql':'text'}".getBytes(StandardCharsets.UTF_8))).parse();
        assertTrue(m.containsKey("yql"));
        assertEquals("text", m.get("yql"));
    }

    /** A parameter which is a JSON object is dotted out, and read back as sub-properties when substituted. */
    @Test
    void testParameterSubstitutionOfAJsonObject() {
        Map<String, String> m = parse("""
                                      {
                                        "yql": "select * from sources * where wand(terms, @q_terms)",
                                        "q_terms": { "7128": 34, "2622": 18 }
                                      }
                                      """);
        assertEquals("34", m.get("q_terms.7128"));
        assertEquals("18", m.get("q_terms.2622"));
        assertEquals("select * from sources * where wand(terms, {\"2622\": 18, \"7128\": 34})",
                     yqlOf(m));
    }

    /** A parameter which is a JSON array is kept verbatim, and parsed as an array when substituted. */
    @Test
    void testParameterSubstitutionOfAJsonArray() {
        Map<String, String> m = parse("""
                                      {
                                        "yql": "select * from sources * where wand(terms, @q_terms)",
                                        "q_terms": [ [7128, 34], [2622, 18] ]
                                      }
                                      """);
        assertEquals("[ [7128, 34], [2622, 18] ]", m.get("q_terms"));
        assertEquals("select * from sources * where wand(terms, {\"2622\": 18, \"7128\": 34})",
                     yqlOf(m));
    }

    private static Map<String, String> parse(String json) {
        return new Json2SingleLevelMap(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))).parse();
    }

    private static String yqlOf(Map<String, String> requestMap) {
        Query query = new Query.Builder().setRequestMap(requestMap).build();
        Result result = new Execution(new Chain<Searcher>(new MinimalQueryInserter()),
                                      Execution.Context.createContextStub()).search(query);
        assertNull(result.hits().getError());
        return query.yqlRepresentation();
    }

}
