// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

/**
 * Tests numeric terms
 *
 * @author bratseth
 */
public class NumericTermsTestCase extends RuleBaseAbstractTestCase {

    public NumericTermsTestCase(String name) {
        super(name,"numericterms.sr");
    }

    public void testNumericProduction() {
        assertSemantics("+restaurants -ycat2gc:96929265","restaurants");
    }

    public void testNumericConditionAndProduction() {
        assertSemantics("48","49");
    }

}
