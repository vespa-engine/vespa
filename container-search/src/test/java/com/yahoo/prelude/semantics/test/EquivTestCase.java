// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

/**
 * @author bratseth
 */
public class EquivTestCase extends RuleBaseAbstractTestCase {

    public EquivTestCase() {
        super("equiv.sr");
    }

    @Test
    void testEquiv() {
        assertSemantics("EQUIV \"lord of the rings\" lotr", "lotr");
    }

    @Test
    void testEquivWithFollowingQuery() {
        assertSemantics("AND (EQUIV \"lord of the rings\" lotr) is a movie", "lotr is a movie");
    }

    @Test
    void testEquivWithPrecedingQuery() {
        assertSemantics("AND a movie is (EQUIV \"lord of the rings\" lotr)", "a movie is lotr");
    }

    @Test
    void testEquivWithSurroundingQuery() {
        assertSemantics("AND a movie is (EQUIV \"lord of the rings\" lotr) yes", "a movie is lotr yes");
    }

}
