// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests streaming search configuration deriving for structs
 *
 * @author  bratseth
 */
public class StreamingStructTestCase extends AbstractExportingTestCase {

    @Test
    void testStreamingStruct() throws IOException, ParseException {
        assertCorrectDeriving("streamingstruct");
    }

    @Test
    void testStreamingStructExplicitDefaultSummaryClass() throws IOException, ParseException {
        assertCorrectDeriving("streamingstructdefault");
    }

}
