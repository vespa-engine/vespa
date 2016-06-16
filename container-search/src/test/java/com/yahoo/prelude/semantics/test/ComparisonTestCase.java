// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

/**
 * @author bratseth
 */
public class ComparisonTestCase extends RuleBaseAbstractTestCase {

    public ComparisonTestCase(String name) {
        super(name,"comparison.sr");
    }

    /**
     * Tests that we can wriote rules which depends on the <i>same term</i> (java) being matched by two
     * different conditions (coffee, island)
     */
    public void testNamedConditionReturnComparison() {
        // Not sufficient that both conditions are matched
        assertSemantics("AND borneo arabica island:borneo coffee:arabica","borneo arabica");

        // They must match the same word
        assertSemantics("AND java noise island:java coffee:java control:ambigous off","java noise");

        // Works also when there are other, not-equal matches
        assertSemantics("AND borneo arabica java island:borneo island:java coffee:arabica coffee:java control:ambigous off",
                        "borneo arabica java");
    }

    public void testContainsAsSubstring() {
        assertSemantics("AND java island:java coffee:java control:ambigous off","java");
        assertSemantics("AND kanava island:kanava off","kanava");
        assertSemantics("AND borneo island:borneo","borneo");
    }

    public void testAlphanumericComparison() {
        assertSemantics("a","a");
        assertSemantics("AND z highletter","z");
        assertSemantics("AND p highletter","p");
    }

}
