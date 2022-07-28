// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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

    // TODO: Work in progress
    @Test
    @Disabled
    void testLiteralEquals() {
        assertSemantics("AND retailer:digital retailer:camera", "keyword:digital keyword:camera");
    }

    @Test
    void testMatchingPhrase() {
        assertSemantics("OR (AND iphone 7) i7", "iphone 7");
    }

}
