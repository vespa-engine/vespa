// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * @author bratseth
 */
public class ComparisonTestCase extends RuleBaseAbstractTestCase {

    public ComparisonTestCase() {
        super("comparison.sr");
    }

    /**
     * Tests that we can wriote rules which depends on the <i>same term</i> (java) being matched by two
     * different conditions (coffee, island)
     */
    @Test
    public void testNamedConditionReturnComparison() {
        // Not sufficient that both conditions are matched
        assertSemantics("AND borneo arabica island:borneo coffee:arabica","borneo arabica");

        // They must match the same word
        assertSemantics("AND java noise island:java coffee:java control:ambigous off","java noise");

        // Works also when there are other, not-equal matches
        assertSemantics("AND borneo arabica java island:borneo island:java coffee:arabica coffee:java control:ambigous off",
                        "borneo arabica java");
    }

    @Test
    public void testContainsAsSubstring() {
        assertSemantics("AND java island:java coffee:java control:ambigous off","java");
        assertSemantics("AND kanava island:kanava off","kanava");
        assertSemantics("AND borneo island:borneo","borneo");
    }

    @Test
    public void testAlphanumericComparison() {
        assertSemantics("a","a");
        assertSemantics("AND z highletter","z");
        assertSemantics("AND p highletter","p");
    }

}
