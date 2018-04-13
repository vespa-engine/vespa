// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * Tests anchoring
 *
 * @author bratseth
 */
public class AnchorTestCase extends RuleBaseAbstractTestCase {

    public AnchorTestCase() {
        super("anchor.sr");
    }

    @Test
    public void testSingleWordAnchoredBothSides() {
        assertSemantics("anchor", "word");
        assertSemantics("anchor", "anotherword");
        assertSemantics("notthisword", "notthisword");
        assertSemantics("AND word anotherword", "word anotherword");
    }

    @Test
    public void testMultiwordAnchored() {
        assertSemantics("anchor", "this is complete");
        assertSemantics("AND this is complete toomuch", "this is complete toomuch");
        assertSemantics("anchor", "a phrase");
        assertSemantics("anchor", "another phrase");
    }

    @Test
    public void testFirstAnchored() {
        assertSemantics("anchor","first");
        assertSemantics("AND anchor andmore","first andmore");
        assertSemantics("AND before first","before first");
        assertSemantics("AND before first andmore","before first andmore");
    }

    @Test
    public void testLastAnchored() {
        assertSemantics("anchor","last");
        assertSemantics("AND andmore anchor","andmore last");
        assertSemantics("AND last after","last after");
        assertSemantics("AND andmore last after","andmore last after");
    }

    @Test
    public void testFirstAndLastAnchored() {
        assertSemantics("AND anchor anchor","first last");
        assertSemantics("AND last first","last first");
        assertSemantics("AND anchor between anchor","first between last");
        assertSemantics("AND anchor last after","first last after");
        assertSemantics("AND before first anchor","before first last");
    }

}
