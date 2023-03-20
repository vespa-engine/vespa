// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests constants in rank-profile
 *
 * @author arnej
 */
public class SmallConstantsTestCase extends AbstractExportingTestCase {

    @Test
    void testScalarInRankProfile() throws IOException, ParseException {
        assertCorrectDeriving("scalar_constant");
    }

    @Test
    void testVectorInRankProfile() throws IOException, ParseException {
        assertCorrectDeriving("vector_constant");
    }

}
