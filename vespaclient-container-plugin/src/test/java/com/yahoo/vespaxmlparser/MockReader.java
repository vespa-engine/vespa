// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.vespa.http.server.MetaStream;
import com.yahoo.vespa.http.server.util.ByteLimitedInputStream;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader.Operation;

import java.io.InputStream;
import java.lang.reflect.Field;

/**
 * Mock for ExternalFeedTestCase which had to override package private methods.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class MockReader implements FeedReader {

    MetaStream stream;
    boolean finished = false;

    public MockReader(InputStream stream) throws Exception {
        this.stream = getMetaStream(stream);
    }

    private static MetaStream getMetaStream(InputStream stream) {
        if (stream instanceof MetaStream) {
            return (MetaStream) stream;
        }
        if (!(stream instanceof ByteLimitedInputStream)) {
            throw new IllegalStateException("Given unknown stream type.");
        }
        //Ooooooo this is so ugly
        try {
            ByteLimitedInputStream byteLimitedInputStream = (ByteLimitedInputStream) stream;
            Field f = byteLimitedInputStream.getClass().getDeclaredField("wrappedStream"); //NoSuchFieldException
            f.setAccessible(true);
            return (MetaStream) f.get(byteLimitedInputStream);
        } catch (Exception e) {
            throw new IllegalStateException("Implementation of ByteLimitedInputStream has changed.", e);
        }
    }

    @Override
    public FeedOperation read() throws Exception {
        Operation operation = new Operation();
        if (finished) {
            return operation;
        }

        byte whatToDo = stream.getNextOperation();
        DocumentId id = new DocumentId("id:banana:banana::doc1");
        DocumentType docType = new DocumentType("banana");
        switch (whatToDo) {
        case 0:
            finished = true;
            break;
        case 1:
            Document doc = new Document(docType, id);
            operation.setDocument(doc);
            break;
        case 2:
            operation.setRemove(id);
            break;
        case 3:
            operation.setDocumentUpdate(new DocumentUpdate(docType, id));
            break;
        case 4:
            throw new RuntimeException("boom");
        }
        return operation;
    }

}
