// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * Tests numbers as conditions and productions
 *
 * @author bratseth
 */
public class NumbersTestCase extends RuleBaseAbstractTestCase {

    public NumbersTestCase() {
        super("numbers.sr");
    }

    @Test
    public void testNumbers() {
        assertSemantics("elite","1337");
        assertSemantics("1","one");
        assertSemantics("AND bort ned","opp");
        assertSemantics("AND kanoo knagg","foo bar");
        assertSemantics("AND 3 three","two 2");
        assertSemantics("AND 1 elite","one 1337");
    }


}
