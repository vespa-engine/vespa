// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * Tests that using rule bases containing cjk characters work
 *
 * @author bratseth
 */
public class CJKTestCase extends RuleBaseAbstractTestCase {

    public CJKTestCase() {
        super("cjk.sr");
    }

    @Test
    public void testIt() {
        assertSemantics("\u7d22a","a\u7d22");
        assertSemantics("\u7d22a","\u7d22a");
        assertSemantics("brand:\u7d22\u5c3c","\u7d22\u5c3c");
        assertSemantics("brand:\u60e0\u666e","\u60e0\u666e");
        assertSemantics("brand:\u4f73\u80fd","\u4f73\u80fd");
        assertSemantics("AND brand:\u4f73\u80fd \u7d22a","\u4f73\u80fd a\u7d22");
        assertSemantics("\u4f73\u80fd\u7d22\u5c3c","\u4f73\u80fd\u7d22\u5c3c");
    }

}
