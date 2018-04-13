// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * @author bratseth
 */
public class ComparisonsTestCase extends RuleBaseAbstractTestCase {

    public ComparisonsTestCase() {
        super("comparisons.sr");
    }

    @Test
    public void testLiteralEquals() {
        assertSemantics("a","a");
        assertSemantics("RANK a foo:a","a&ranking=category");
        assertSemantics("a","a&ranking=somethingelse");
        assertSemantics("a","a&otherparam=category");
    }

}
