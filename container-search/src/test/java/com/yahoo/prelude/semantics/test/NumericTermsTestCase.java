// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

/**
 * Tests numeric terms
 *
 * @author bratseth
 */
public class NumericTermsTestCase extends RuleBaseAbstractTestCase {

    public NumericTermsTestCase() {
        super("numericterms.sr");
    }

    @Test
    void testNumericProduction() {
        assertSemantics("+restaurants -ycat2gc:96929265", "restaurants");
    }

    @Test
    void testNumericConditionAndProduction() {
        assertSemantics("48", "49");
    }

}
