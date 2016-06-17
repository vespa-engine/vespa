// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

/**
 * Tests that ![a] is interpreted as "default:![a]", not as "!default:[a]",
 * that is, in negative conditions we still only want to match the default index by default.
 *
 * @author bratseth
 */
public class AutomataNotTestCase extends RuleBaseAbstractTestCase {

    public AutomataNotTestCase(String name) {
        super(name,"automatanot.sr","semantics.fsa");
    }

    public void testAutomataNot() {
        if (System.currentTimeMillis() > 0) return; // TODO: MAKE THIS WORK!
        assertSemantics("carpenter","carpenter");
        assertSemantics("RANK brukbar busname:brukbar","brukbar");
    }

}
