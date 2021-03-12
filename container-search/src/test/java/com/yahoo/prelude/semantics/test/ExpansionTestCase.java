// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

public class ExpansionTestCase extends RuleBaseAbstractTestCase {

    public ExpansionTestCase() {
        super("expansion.sr");
    }

    @Test
    public void testOrExpansion() {
        assertSemantics("OR or1 or2 or3", "or1");
    }

    @Test
    public void testEquivExpansion() {
        assertSemantics("EQUIV equiv1 equiv2 equiv3", "equiv1");
    }

}
