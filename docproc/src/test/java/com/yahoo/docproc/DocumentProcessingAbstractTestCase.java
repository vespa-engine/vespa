// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.StringFieldValue;

import static org.junit.Assert.assertEquals;

/**
 * Convenience superclass of document processor test cases
 *
 * @author bratseth
 */
public abstract class DocumentProcessingAbstractTestCase {

    /**
     * Asserts that a document processing service works
     */
    protected void assertProcessingWorks(DocprocService service) {
        // Create documents
        DocumentType type = new DocumentType("test");
        type.addField("test", DataType.STRING);
        DocumentPut put1 = new DocumentPut(type, new DocumentId("doc:test:test:1"));
        DocumentPut put2 = new DocumentPut(type, new DocumentId("doc:test:test:2"));
        DocumentPut put3 = new DocumentPut(type, new DocumentId("doc:test:test:3"));

        // Process them
        service.process(put1);
        service.process(put2);
        service.process(put3);
        while (service.doWork()) {}

        // Verify
        assertEquals(new StringFieldValue("done"), put1.getDocument().getFieldValue("test"));
        assertEquals(new StringFieldValue("done"), put2.getDocument().getFieldValue("test"));
        assertEquals(new StringFieldValue("done"), put3.getDocument().getFieldValue("test"));
    }

    public static class TestDocumentProcessor1 extends DocumentProcessor {
        @Override
        public Progress process(Processing processing) {
            for (DocumentOperation op : processing.getDocumentOperations()) {
                if (processing.getVariable("processor1") == null) {
                    processing.setVariable("processor1", "called");
                    return Progress.LATER;
                }
                processing.setVariable("processor1", "calledTwice");
            }
            return Progress.DONE;
        }
    }

    public static class TestDocumentProcessor2 extends DocumentProcessor {
        @Override
        public Progress process(Processing processing) {
            for (DocumentOperation op : processing.getDocumentOperations()) {
                assertEquals("calledTwice", processing.getVariable("processor1"));
                processing.setVariable("processor2", "called");
            }
            return Progress.DONE;
        }
    }

    public static class TestDocumentProcessor3 extends DocumentProcessor {
        @Override
        public Progress process(Processing processing) {
            for (DocumentOperation op : processing.getDocumentOperations()) {
                assertEquals("called", processing.getVariable("processor2"));
                if (processing.getVariable("processor3") == null) {
                    processing.setVariable("processor3", "called");
                    return Progress.LATER;
                }
                ((DocumentPut)op).getDocument().setFieldValue("test", new StringFieldValue("done"));
            }
            return Progress.DONE;
        }
    }

}
