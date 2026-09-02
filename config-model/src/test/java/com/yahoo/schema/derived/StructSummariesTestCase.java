// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests deriving a configuration with summaries from array of struct
 *
 * @author arnej
 */
public class StructSummariesTestCase extends AbstractExportingTestCase {

    @Test
    void testStructSummariesDeriving() throws IOException, ParseException {
        assertCorrectDeriving("structsummaries", new TestProperties());
    }

}
