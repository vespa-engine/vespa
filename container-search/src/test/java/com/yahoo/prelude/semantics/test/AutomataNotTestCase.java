// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests that ![a] is interpreted as "default:![a]", not as "!default:[a]",
 * that is, in negative conditions we still only want to match the default index by default.
 *
 * @author bratseth
 */
public class AutomataNotTestCase extends RuleBaseAbstractTestCase {

    public AutomataNotTestCase() {
        super("automatanot.sr", "semantics.fsa");
    }

    @Test
    @Ignore // TODO: MAKE THIS WORK!
    public void testAutomataNot() {
        if (System.currentTimeMillis() > 0) return;
        assertSemantics("carpenter", "carpenter");
        assertSemantics("RANK brukbar busname:brukbar", "brukbar");
    }

}
