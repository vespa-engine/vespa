// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * tersts the ellipsis rule base
 *
 * @author bratseth
 */
public class MatchAllTestCase extends RuleBaseAbstractTestCase {

    public MatchAllTestCase() {
        super("matchall.sr");
    }

    @Test
    public void testMatchAll() {
        assertSemantics("RANK a normtitle:a","a");
        assertSemantics("RANK (AND a b) normtitle:\"a b\"","a b");
        assertSemantics("RANK (AND a a b a) normtitle:\"a a b a\"","a a b a");
    }

    @Test
    public void testMatchAllFilterIsIgnored() {
        assertSemantics("RANK a |b normtitle:a","a&filter=b");
        assertSemantics("RANK (AND a b) |b |c normtitle:\"a b\"","a b&filter=b c");
        assertSemantics("RANK (AND a a b a) |a |a |c |d |b normtitle:\"a a b a\"","a a b a&filter=a a c d b");
    }

}
