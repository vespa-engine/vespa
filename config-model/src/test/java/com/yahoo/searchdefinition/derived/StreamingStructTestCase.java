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
        // Tests an issue for mail in Vespa 4.1; specific overrides of default summary class
        assertCorrectDeriving("streamingstructdefault");
    }

    @Test
    public void testStreamingStructDocumentsonlyDeriving() throws IOException {
        String root = "src/test/derived/streamingstruct/";
        String temp = "temp/documentderiver/";
        new File(temp).mkdir();
        Deriver.deriveDocuments(Arrays.asList(root + "streamingstruct.sd"), temp);
        assertEqualFiles(root + "/onlydoc/documentmanager.cfg",
                         temp + "/documentmanager.cfg");
    }

}
