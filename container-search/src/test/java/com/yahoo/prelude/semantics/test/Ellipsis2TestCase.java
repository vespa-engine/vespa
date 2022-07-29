// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

/**
 * tersts the ellipsis rule base
 *
 * @author bratseth
 */
public class Ellipsis2TestCase extends RuleBaseAbstractTestCase {

    public Ellipsis2TestCase() {
        super("ellipsis2.sr");
    }

    @Test
    void testUnreferencedEllipsis() {
        assertSemantics("AND a b c someindex:\"a b c\"", "a b c");
    }

}
