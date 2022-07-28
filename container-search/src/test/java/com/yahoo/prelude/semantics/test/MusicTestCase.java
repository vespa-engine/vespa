// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

/**
 * Tests the rewriting in the semanticsearcher system test
 *
 * @author bratseth
 */
public class MusicTestCase {

    @Test
    void testMusic() {
        var tester = new RuleBaseTester("music.sr");
        tester.assertSemantics("AND song:together artist:youngbloods", "together by youngbloods");
    }

}
