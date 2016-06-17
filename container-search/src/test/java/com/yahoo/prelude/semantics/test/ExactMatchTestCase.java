// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

/**
 * @author bratseth
 */
public class ExactMatchTestCase extends RuleBaseAbstractTestCase {

    public ExactMatchTestCase(String name) {
        super(name,"exactmatch.sr");
    }

    public void testCompleteMatch() {
        assertSemantics("AND primetime in no time","primetime notime");
    }

    /*
    public void testCompleteMatchWithNegative() {
        assertSemantics("AND primetime in no time ...fix",new Query(HttpRequest.fromString("?query=primetime+ANDNOT+regionexcl:us&type=adv")));
    }
    public void testCompleteMatchWithFilterAndNegative() {
        assertSemantics("AND primetime in no time ...fix",new Query(HttpRequest.fromString("?query=primetime+ANDNOT+regionexcl:us&type=adv&filter=%2Blang:en")));
    }
    */

}
