// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ConstantValidatorTest {

    @Test
    void ensure_that_valid_ranking_constants_do_not_fail() {
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/ranking_constants_ok/").create();
    }

    @Test
    void ensure_that_failing_ranking_constants_fails() {
        try {
            new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/ranking_constants_fail/").create();
            fail();
        } catch (IllegalArgumentException e) {
            String[] lines = e.getMessage().split("\n");
            assertStartsWith("constant(constant_tensor_2) tensor(x[6]): file:tensors/constant_tensor_2.json: Tensor label is not a string", lines[1]);
            assertStartsWith("constant(constant_tensor_3) tensor(cpp{},d{}): file:tensors/constant_tensor_3.json: Tensor dimension 'cd' does not exist", lines[2]);
            assertStartsWith("constant(constant_tensor_4) tensor(x{},y{}): file:tensors/constant_tensor_4.json: Tensor dimension 'z' does not exist", lines[3]);
        }
    }

    private void assertStartsWith(String prefix, String value) {
        assertEquals(prefix, value.substring(0, prefix.length()));
    }

}
