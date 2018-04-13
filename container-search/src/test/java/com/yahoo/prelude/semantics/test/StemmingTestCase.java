// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * Tests a case reported by tularam
 *
 * @author bratseth
 */
public class StemmingTestCase extends RuleBaseAbstractTestCase {

    public StemmingTestCase() {
        super("stemming.sr");
    }

    @Test
    public void testRewritingDueToStemmingInQuery() {
        assertSemantics("+i:vehicle -i:s","i:cars -i:s");
    }

    @Test
    public void testRewritingDueToStemmingInRule() {
        assertSemantics("+i:animal -i:s","i:horse -i:s");
    }

    @Test
    public void testRewritingDueToExactMatch() {
        assertSemantics("+(AND i:arts i:sciences) -i:s","i:as -i:s");
    }

    @Test
    public void testNoRewritingBecauseShortWordsAreNotStemmed() {
        assertSemantics("+i:a -i:s","i:a -i:s");
    }

}
