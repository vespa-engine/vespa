// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

/**
 * @author bratseth
 */
public class SynonymTestCase {

    @Test
    void testReplacingBySynonyms() {
        var tester = new RuleBaseTester("synonyms.sr");
        tester.assertSemantics("EQUIV index1:foo index1:baz index1:bar", "index1:foo");
        tester.assertSemantics("EQUIV index1:foo index1:baz index1:bar", "index1:bar");
        tester.assertSemantics("EQUIV index1:foo index1:baz index1:bar", "index1:baz");
        tester.assertSemantics("EQUIV index1:word index1:\"a phrase\"", "index1:word");
        tester.assertSemantics("EQUIV index1:word index1:\"a phrase\"", "index1:a index1:phrase");
        tester.assertSemantics("index1:other", "index1:other");
    }

    @Test
    void testAddingSynonyms() {
        var tester = new RuleBaseTester("synonyms.sr");
        tester.assertSemantics("EQUIV index2:foo index2:baz index2:bar", "index2:foo");
        tester.assertSemantics("EQUIV index2:bar index2:foo index2:baz", "index2:bar");
        tester.assertSemantics("EQUIV index2:baz index2:foo index2:bar", "index2:baz");
        tester.assertSemantics("EQUIV index2:word index2:\"a phrase\"", "index2:word");
        tester.assertSemantics("EQUIV index2:\"a phrase\" index2:word", "index2:a index2:phrase");
        tester.assertSemantics("index2:other", "index2:other");
    }

}
