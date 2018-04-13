// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * @author bratseth
 */
public class NotTestCase extends RuleBaseAbstractTestCase {

    public NotTestCase() {
        super("not.sr");
    }

    @Test
    public void testLiteralEquals() {
        assertSemantics("RANK a foo:a","a");
        assertSemantics("a","a&ranking=category");
        assertSemantics("RANK a foo:a","a&ranking=somethingelse");
        assertSemantics("RANK a foo:a","a&otherparam=category");
    }

}
