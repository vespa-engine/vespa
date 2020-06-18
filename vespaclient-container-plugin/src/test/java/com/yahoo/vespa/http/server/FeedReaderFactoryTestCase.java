package com.yahoo.vespa.http.server;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.http.client.config.FeedParams;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FeedReaderFactoryTestCase {
    FeedReaderFactory ffr = new FeedReaderFactory();
    DocumentTypeManager manager = new DocumentTypeManager();

    private InputStream createStream(String s) {
        return new ByteArrayInputStream(Utf8.toBytes(s));
    }

    @Test
    public void testXmlException() {
        try {
            ffr.createReader(createStream("Some malformed xml"), manager, FeedParams.DataFormat.XML_UTF8);
            fail();
        } catch (RuntimeException e) {
            assertEquals("Could not create VespaXMLFeedReader. First characters are: 'Some malformed xml'", e.getMessage());
        }
    }
}
