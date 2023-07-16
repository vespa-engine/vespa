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
