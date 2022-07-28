// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.IndexingScript;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public abstract class AssertIndexingScript {

    public static void assertIndexing(List<String> expected, Schema schema) {
        assertIndexing(expected, new IndexingScript(schema).expressions());
    }

    public static void assertIndexing(List<String> expected, IndexingScript script) {
        assertIndexing(expected, script.expressions());
    }

    public static void assertIndexing(List<String> expected, Iterable<Expression> actual) {
        List<String> parsedExpected = new LinkedList<>();
        for (String str : expected) {
            try {
                parsedExpected.add(Expression.fromString(str).toString());
            } catch (ParseException e) {
                fail(e.getMessage());
            }
        }
        for (Expression actualExp : actual) {
            String str = actualExp.toString();
            assertTrue(parsedExpected.remove(str), "Unexpected: " + str);
        }
        assertTrue(parsedExpected.isEmpty(), "Missing: " + parsedExpected.toString());
    }
}
