// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

public class RangesTestCase extends RuleBaseAbstractTestCase {

    public RangesTestCase() {
        super("ranges.sr");
    }

    @Test
    void testPrice() {
        assertSemantics("AND shoes price:<5000",
                        "shoes under 5000");
    }

}
