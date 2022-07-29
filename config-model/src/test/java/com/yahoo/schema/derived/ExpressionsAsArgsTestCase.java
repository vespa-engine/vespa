// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests correct deriving of expressions as arguments to functions.
 *
 * @author lesters
 */
public class ExpressionsAsArgsTestCase extends AbstractExportingTestCase {

    @Test
    void testDocumentDeriving() throws IOException, ParseException {
        assertCorrectDeriving("function_arguments");
        assertCorrectDeriving("function_arguments_with_expressions");
    }

}



