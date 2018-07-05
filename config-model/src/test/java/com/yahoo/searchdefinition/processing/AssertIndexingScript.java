// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.derived.IndexingScript;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public abstract class AssertIndexingScript {

    public static void assertIndexing(List<String> expected, Search search) {
        assertIndexing(expected, new IndexingScript(search).expressions());
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
            assertTrue("Unexpected: " + str, parsedExpected.remove(str));
        }
        assertTrue("Missing: " + parsedExpected.toString(), parsedExpected.isEmpty());
    }
}
