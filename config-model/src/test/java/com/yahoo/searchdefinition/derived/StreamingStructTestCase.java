// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Tests VSM configuration deriving for structs
 *
 * @author  bratseth
 */
public class StreamingStructTestCase extends AbstractExportingTestCase {

    @Test
    public void testStreamingStruct() throws IOException, ParseException {
        assertCorrectDeriving("streamingstruct");
    }

    @Test
    public void testStreamingStructExplicitDefaultSummaryClass() throws IOException, ParseException {
        assertCorrectDeriving("streamingstructdefault");
    }

}
