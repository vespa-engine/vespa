// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * @author bratseth
 */
public class OrPhraseTestCase extends RuleBaseAbstractTestCase {

    public OrPhraseTestCase() {
        super("orphrase.sr");
    }

    @Test
    public void testReplacing1() {
        assertSemantics("OR (AND new york) title:\"software engineer\"","software engineer new york");
        assertSemantics("title:\"software engineer\"","software engineer"); // Skip or when there is nothing else
    }

    @Test
    public void testReplacing2() {
        assertSemantics("OR lotr \"lord of the rings\"","lotr");
    }

}
