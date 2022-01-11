// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * Tests stemming.
 *
 * @author bratseth
 */
public class StemmingTestCase {

    @Test
    public void testRewritingDueToStemmingInQuery() {
        var tester = new RuleBaseTester("stemming.sr");
        tester.assertSemantics("+(AND i:vehicle TRUE) -i:s", "i:cars -i:s");
    }

    @Test
    public void testNoRewritingDueToStemmingInQueryWhenStemmingDisabled() {
        var tester = new RuleBaseTester("stemming-none.sr");
        tester.assertSemantics("+i:cars -i:s", "i:cars -i:s");
    }

    @Test
    public void testRewritingDueToStemmingInRule() {
        var tester = new RuleBaseTester("stemming.sr");
        tester.assertSemantics("+(AND i:animal TRUE) -i:s", "i:horse -i:s");
    }

    @Test
    public void testNoRewritingDueToStemmingInRuleWhenStemmingDisabled() {
        var tester = new RuleBaseTester("stemming-none.sr");
        tester.assertSemantics("+i:horse -i:s", "i:horse -i:s");
    }

    @Test
    public void testRewritingDueToExactMatch() {
        var tester = new RuleBaseTester("stemming.sr");
        tester.assertSemantics("+(AND i:arts i:sciences TRUE) -i:s", "i:as -i:s");
    }

    @Test
    public void testEnglishStemming() {
        var tester = new RuleBaseTester("stemming.sr");
        tester.assertSemantics("i:drive", "i:going");
    }

    @Test
    public void testFrenchStemming() {
        var tester = new RuleBaseTester("stemming-french.sr");
        tester.assertSemantics("i:going", "i:going");
    }

}
