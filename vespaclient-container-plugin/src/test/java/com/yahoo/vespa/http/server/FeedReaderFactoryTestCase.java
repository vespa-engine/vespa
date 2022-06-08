// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.text.Utf8;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FeedReaderFactoryTestCase {
    DocumentTypeManager manager = new DocumentTypeManager();

    private InputStream createStream(String s) {
        return new ByteArrayInputStream(Utf8.toBytes(s));
    }

    @Test
    public void testXmlExceptionWithDebug() {
        try {
            new FeedReaderFactory(true).createReader(createStream("Some malformed xml"), manager, FeedParams.DataFormat.XML_UTF8);
            fail();
        } catch (RuntimeException e) {
            assertEquals("Could not create VespaXMLFeedReader. First characters are: 'Some malformed xml'", e.getMessage());
        }
    }
    @Test
    public void testXmlException() {
        try {
            new FeedReaderFactory(false).createReader(createStream("Some malformed xml"), manager, FeedParams.DataFormat.XML_UTF8);
            fail();
        } catch (RuntimeException e) {
            assertEquals("Could not create VespaXMLFeedReader.", e.getMessage());
        }
    }
}
