// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    // No equiv: Not optimal, but not wrong either
    @Test
    void testEquivExpansion3() {
        assertSemantics("AND testfield:e1 testfield:e2 testfield:e3 testfield:e1 testfield:e2 testfield:e3",
                        "testfield:foo testfield:bar");
    }

    /** EQUIV production should preserve query structure, not collapse into a root-level EQUIV. */
    @Test
    void testEquivPreservesQueryStructure() {
        assertSemantics("AND red running (EQUIV equiv1 equiv2 equiv3)", "red running equiv1");
    }

    /** EQUIV at the start of a multi-term query should also preserve structure. */
    @Test
    void testEquivPreservesQueryStructureMatchFirst() {
        assertSemantics("AND (EQUIV equiv1 equiv2 equiv3) is great", "equiv1 is great");
    }

    /** EQUIV in the middle of a multi-term query should also preserve structure. */
    @Test
    void testEquivPreservesQueryStructureMatchMiddle() {
        assertSemantics("AND foo (EQUIV equiv1 equiv2 equiv3) bar", "foo equiv1 bar");
    }

    /** Cascading EQUIV: rule 1 produces cascade2, rule 2 matches it and adds cascade3 to the same EQUIV. */
    @Test
    void testEquivCascading() {
        assertSemantics("EQUIV cascade1 cascade2 cascade3", "cascade1");
    }

    /** Cascading EQUIV in a multi-term query should preserve structure. */
    @Test
    void testEquivCascadingPreservesQueryStructure() {
        assertSemantics("AND foo (EQUIV cascade1 cascade2 cascade3) bar", "foo cascade1 bar");
    }

}
