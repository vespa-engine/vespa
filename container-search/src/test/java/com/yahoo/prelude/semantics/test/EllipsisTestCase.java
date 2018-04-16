// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * tersts the ellipsis rule base
 *
 * @author bratseth
 */
public class EllipsisTestCase extends RuleBaseAbstractTestCase {

    public EllipsisTestCase() {
        super("ellipsis.sr");
    }

    @Test
    public void testUnreferencedEllipsis() {
        assertSemantics("AND why is stench unpleasant about:stench","why is stench unpleasant");
        assertSemantics("AND why is the sky blue about:\"the sky\"","why is the sky blue");
        assertSemantics("AND why is aardwark almost always most relevant in dictionaries about:aardwark",
                        "why is aardwark almost always most relevant in dictionaries");
    }

    @Test
    public void testReferencedEllipsis() {
        assertSemantics("album:parade","parade album");
        assertSemantics("album:\"a sun came\"","a sun came album");
        assertSemantics("album:parade","parade cd");
        assertSemantics("album:\"a sun came\"","a sun came cd");
    }

    @Test
    public void testEllipsisInNamedCondition() {
        assertSemantics("AND name:\"a sun came\" product:video","buy a sun came");
        assertSemantics("AND name:stalker product:video","buy stalker video");
        assertSemantics("AND name:\"the usual suspects\" product:video","buy the usual suspects video");
    }

    @Test
    public void testMultipleEllipsis() {
        assertSemantics("AND from:paris to:texas","from paris to texas");
        assertSemantics("AND from:\"sao paulo\" to:\"real madrid\"","from sao paulo to real madrid");
        assertSemantics("AND from:\"from from\" to:oslo","from from from to oslo");
        assertSemantics("AND from:\"from from to\" to:koko","from from from to to koko"); // Matching is greedy left-right
    }

}
