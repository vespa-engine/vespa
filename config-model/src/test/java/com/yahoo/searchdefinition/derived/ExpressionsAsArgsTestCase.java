// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests correct deriving of expressions as arguments to functions.
 *
 * @author lesters
 */
public class ExpressionsAsArgsTestCase extends AbstractExportingTestCase {

    @Test
    public void testDocumentDeriving() throws IOException, ParseException {
        assertCorrectDeriving("function_arguments");
        assertCorrectDeriving("function_arguments_with_expressions");
    }

}



