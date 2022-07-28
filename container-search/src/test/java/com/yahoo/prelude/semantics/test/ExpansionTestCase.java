// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

public class ExpansionTestCase extends RuleBaseAbstractTestCase {

    public ExpansionTestCase() {
        super("expansion.sr");
    }

    @Test
    void testOrExpansion() {
        assertSemantics("OR or1 or2 or3", "or1");
    }

    @Test
    void testEquivExpansion1() {
        assertSemantics("EQUIV equiv1 equiv2 equiv3", "equiv1");
    }

    @Test
    void testEquivExpansion2() {
        assertSemantics("EQUIV testfield:e1 testfield:e2 testfield:e3", "testfield:foo");
    }

    @Test
    void testEquivExpansion3() {
        assertSemantics("AND testfield:e1 testfield:e2 testfield:e3 testfield:e1 testfield:e2 testfield:e3",
                "testfield:foo testfield:bar");
    }

}
