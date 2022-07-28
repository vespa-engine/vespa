// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        assertSemantics("title:\"software engineer\"", "software engineer"); // Skip or when there is nothing else
    }

    @Test
    void testReplacing2() {
        assertSemantics("OR lotr \"lord of the rings\"", "lotr");
    }

}
