// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * @author bratseth
 */
public class WeightingTestCase extends RuleBaseAbstractTestCase {

    public WeightingTestCase() {
        super("weighting.sr");
    }

    @Test
    public void testWeighting() {
        assertSemantics("foo!150","foo");
        assertSemantics("AND foo!150 snip","foo snip");
        assertSemantics("AND foo!150 bar","foo bar");
        assertSemantics("AND bar!57 foo","bar foo");
        assertSemantics("AND foo!150 fu","foo fu");
        assertSemantics("AND foo!150 bar kanoo boat!237","foo bar kanoo");
    }

}
