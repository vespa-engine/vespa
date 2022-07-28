// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

/**
 * Tests a case reported by tularam
 *
 * @author bratseth
 */
public class NoStemmingTestCase extends RuleBaseAbstractTestCase {

    public NoStemmingTestCase() {
        super("nostemming.sr");
    }

    /** Should rewrite correctly */
    @Test
    void testCorrectRewriting1() {
        assertSemantics("+(AND i:arts i:sciences TRUE) -i:b", "i:as -i:b");
    }

    /** Should rewrite correctly too */
    @Test
    void testCorrectRewriting2() {
        assertSemantics("+(AND i:arts i:sciences i:crafts TRUE) -i:b", "i:asc -i:b");
    }

    /** Should not rewrite */
    @Test
    void testNoRewriting() {
        assertSemantics("+i:a -i:s", "i:a -i:s");
    }

}
