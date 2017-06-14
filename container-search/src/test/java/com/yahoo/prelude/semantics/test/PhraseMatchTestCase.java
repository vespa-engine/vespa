// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

/**
 * Tests that the phrase produced by an automata match can subsequently be replaced by an AND of the
 * same terms.
 *
 * @author bratseth
 */
public class PhraseMatchTestCase extends RuleBaseAbstractTestCase {

    public PhraseMatchTestCase(String name) {
        super(name,"phrasematch.sr","semantics.fsa");
    }

    public void testLiteralEquals() {
        if (1==1) return;     // TODO: Work in progress
        assertSemantics("AND retailer:digital retailer:camera","keyword:digital keyword:camera");
    }

}
