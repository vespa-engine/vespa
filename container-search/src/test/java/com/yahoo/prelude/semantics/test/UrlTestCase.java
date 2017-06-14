// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

/**
 * Tests working with url indexes
 *
 * @author bratseth
 */
public class UrlTestCase extends RuleBaseAbstractTestCase {

    public UrlTestCase(String name) {
        super(name,"url.sr");
    }

    public void testFromDefaultToUrlIndex() {
        assertSemantics("fromurl:\"youtube com\"","youtube.com");
    }


}
