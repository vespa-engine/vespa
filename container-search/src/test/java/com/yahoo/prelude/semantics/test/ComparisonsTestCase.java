// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

/**
 * @author bratseth
 */
public class ComparisonsTestCase extends RuleBaseAbstractTestCase {

    public ComparisonsTestCase(String name) {
        super(name,"comparisons.sr");
    }

    public void testLiteralEquals() {
        assertSemantics("a","a");
        assertSemantics("RANK a foo:a","a&ranking=category");
        assertSemantics("a","a&ranking=somethingelse");
        assertSemantics("a","a&otherparam=category");
    }

}
