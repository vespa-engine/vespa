// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.vespa.http.server.FeedParams;
import com.yahoo.vespa.http.server.FeedReaderFactory;

import java.io.InputStream;

/**
 * For creating MockReader of innput stream.
 * @author dybis
 */
public class MockFeedReaderFactory extends FeedReaderFactory {

    public MockFeedReaderFactory() {
        super(true);
    }

    @Override
    public FeedReader createReader(
            InputStream inputStream,
            DocumentTypeManager docTypeManager,
            FeedParams.DataFormat dataFormat) {
        try {
            return new MockReader(inputStream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
