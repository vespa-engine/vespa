// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

/**
 * Test a case reported by Alibaba
 *
 * @author bratseth
 */
public class AlibabaTestCase extends RuleBaseAbstractTestCase {

    public AlibabaTestCase(String name) {
        super(name,"alibaba.sr");
    }

    public void testNumberReplacement() {
        assertSemantics("AND nokia 3100","3100");
    }

    public void testRuleFollowingNumber() {
        assertSemantics("lenovo","legend");
    }

    public void testCombinedNumberAndRegular1() {
        assertSemantics("AND lenovo nokia 3100","legend 3100");
    }

    public void testCombinedNumberAndRegular2() {
        assertSemantics("AND nokia 3100 lenovo","3100 legend");
    }

}
