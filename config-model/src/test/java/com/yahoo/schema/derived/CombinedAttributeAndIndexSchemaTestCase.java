// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests deriving a configuration with multiple summaries
 *
 * @author bratseth
 */
public class CombinedAttributeAndIndexSchemaTestCase extends AbstractExportingTestCase {

    @Test
    void testMultipleSummaries() throws IOException, ParseException {
        assertCorrectDeriving("combinedattributeandindexsearch");
    }

}
