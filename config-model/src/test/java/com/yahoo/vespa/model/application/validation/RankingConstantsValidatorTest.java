// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;

import static com.yahoo.vespa.model.application.validation.RankingConstantsValidator.TensorValidationException;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RankingConstantsValidatorTest {

    @Test
    public void ensure_that_valid_ranking_constants_do_not_fail() {
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/ranking_constants_ok/").create();
    }

    @Test
    public void ensure_that_failing_ranking_constants_fails() {
        try {
            new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/ranking_constants_fail/").create();
            fail();
        } catch (TensorValidationException e) {
            assertTrue(e.getMessage().contains("Ranking constant 'constant_tensor_2' (tensors/constant_tensor_2.json): Tensor label is not a string (VALUE_NUMBER_INT)"));
            assertTrue(e.getMessage().contains("Ranking constant 'constant_tensor_3' (tensors/constant_tensor_3.json): Tensor dimension 'cd' does not exist"));
            assertTrue(e.getMessage().contains("Ranking constant 'constant_tensor_4' (tensors/constant_tensor_4.json): Tensor dimension 'z' does not exist"));
        }
    }

}
