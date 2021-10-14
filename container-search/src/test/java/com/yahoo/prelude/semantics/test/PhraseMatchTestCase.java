// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests that the phrase produced by an automata match can subsequently be replaced by an AND of the
 * same terms.
 *
 * @author bratseth
 */
public class PhraseMatchTestCase extends RuleBaseAbstractTestCase {

    public PhraseMatchTestCase() {
        super("phrasematch.sr", "semantics.fsa");
    }

    @Test
    @Ignore // TODO: Work in progress
    public void testLiteralEquals() {
        assertSemantics("AND retailer:digital retailer:camera","keyword:digital keyword:camera");
    }

    @Test
    public void testMatchingPhrase() {
        assertSemantics("OR (AND iphone 7) i7", "iphone 7");
    }

}
