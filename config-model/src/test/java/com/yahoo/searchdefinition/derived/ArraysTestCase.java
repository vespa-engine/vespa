// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests array type deriving. Indexing statements over array
 * types is not yet supported, so this tests document type
 * configuration deriving only. Expand later.
 *
 * @author bratseth
 */
public class ArraysTestCase extends AbstractExportingTestCase {

    @Test
    public void testDocumentDeriving() throws IOException, ParseException {
        assertCorrectDeriving("arrays");
    }

}
