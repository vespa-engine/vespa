// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscreteSequenceTest {

    @Test
    void renders_token_strings_when_present() {
        var sequence = new DiscreteSequence(List.of(1, 2, 3), List.of("<cb0_1>", "<cb1_2>", "<cb2_3>"));

        assertEquals("<cb0_1> <cb1_2> <cb2_3>", sequence.asText(" "));
    }

    @Test
    void falls_back_to_numeric_tokens() {
        var sequence = DiscreteSequence.of(List.of(1, 2, 3));

        assertEquals("1,2,3", sequence.asText(","));
    }
}
