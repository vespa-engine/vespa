// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.json.JsonFeedReader;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespaxmlparser.FeedReader;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;

import java.io.InputStream;

/**
 * Class for creating FeedReader based on dataFormat.
 * @author dybis
 */
public class FeedReaderFactory {

    /**
     * Creates FeedReader
     * @param inputStream source of feed data
     * @param docTypeManager handles the parsing of the document
     * @param dataFormat specifies the format
     * @return a feedreader
     */
    public FeedReader createReader(
            InputStream inputStream,
            DocumentTypeManager docTypeManager,
            FeedParams.DataFormat dataFormat)  {
        switch (dataFormat) {
            case XML_UTF8:
                try {
                    return new VespaXMLFeedReader(inputStream, docTypeManager);
                } catch (Exception e) {
                    throw new RuntimeException("Could not create VespaXMLFeedReader", e);
                }
            case JSON_UTF8:
                return new JsonFeedReader(inputStream, docTypeManager);
            default:
                throw new IllegalStateException("Can not create feed reader for format: " + dataFormat);
        }
    }

}
