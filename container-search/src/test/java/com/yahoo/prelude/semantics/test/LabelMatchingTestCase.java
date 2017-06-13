// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import java.io.IOException;

import com.yahoo.prelude.semantics.parser.ParseException;

/**
 * Tests label-dependent matching
 *
 * @author bratseth
 */
public class LabelMatchingTestCase extends RuleBaseAbstractTestCase {

    public LabelMatchingTestCase(String name) {
        super(name,"labelmatching.sr");
    }

    /** Tests that matching with no label matches the default label (index) only */
    public void testDefaultLabelMatching() throws IOException, ParseException {
        assertSemantics("matched:term","term");
        assertSemantics("alabel:term","alabel:term");

        assertSemantics("AND term2 hit","term2");
        assertSemantics("alabel:term2","alabel:term2");
    }

    public void testSpecificLabelMatchingInConditionReference() throws IOException, ParseException {
        assertSemantics("+dcattitle:restaurants -dcat:hotel","dcattitle:restaurants");
    }

    public void testSpecificlabelMatchingInNestedCondition() throws IOException, ParseException {
        assertSemantics("three","foo:one");
        assertSemantics("three","foo:two");
        assertSemantics("bar:one","bar:one");
        assertSemantics("bar:two","bar:two");
        assertSemantics("foo:three","foo:three");
        assertSemantics("one","one");
        assertSemantics("two","two");
        assertSemantics("AND three three","foo:one foo:two");
        assertSemantics("AND bar:one bar:two","bar:one bar:two");
    }

}
