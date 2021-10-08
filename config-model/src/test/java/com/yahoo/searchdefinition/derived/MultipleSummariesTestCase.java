// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests deriving a configuration with multiple summaries
 *
 * @author  bratseth
 */
public class MultipleSummariesTestCase extends AbstractExportingTestCase {
    @Test
    @Ignore
    public void testMultipleSummaries() throws IOException, ParseException {
        assertCorrectDeriving("multiplesummaries");
    }
}
