// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests exporting with global-phase and ONNX models
 *
 * @author arnej
 */
public class GlobalPhaseOnnxModelsTestCase extends AbstractExportingTestCase {

    @Test
    void testModelInRankProfile() throws IOException, ParseException {
        assertCorrectDeriving("globalphase_onnx_inside");
    }

    @Test
    void testWithTokenFunctions() throws IOException, ParseException {
        assertCorrectDeriving("globalphase_token_functions");
    }

}
