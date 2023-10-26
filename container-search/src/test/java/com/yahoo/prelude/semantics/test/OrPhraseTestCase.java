// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

/**
 * @author bratseth
 */
public class OrPhraseTestCase extends RuleBaseAbstractTestCase {

    public OrPhraseTestCase() {
        super("orphrase.sr");
    }

    @Test
    void testReplacing1() {
        assertSemantics("OR title:\"software engineer\" (AND new york)", "software engineer new york");
        assertSemantics("title:\"software engineer\"", "software engineer"); // Skip OR when there is nothing else
    }

    @Test
    void testReplacing2() {
        assertSemantics("OR \"lord of the rings\" lotr", "lotr");
    }

    @Test
    void testReplacing2WithFollowingQuery() {
        assertSemantics("AND (OR \"lord of the rings\" lotr) is a movie", "lotr is a movie");
    }

    @Test
    void testReplacing2WithPrecedingQuery() {
        assertSemantics("AND a movie is (OR \"lord of the rings\" lotr)", "a movie is lotr");
    }
    @Test
    void testReplacing2WithSurroundingQuery() {
        assertSemantics("AND a movie is (OR \"lord of the rings\" lotr) yes", "a movie is lotr yes");
    }

}
