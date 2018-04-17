// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests a document processing where some processings fail with an exception
 *
 * @author bratseth
 */
public class FailingDocumentProcessingTestCase {

    /**
     * Tests chaining of some processors, and execution of the processors
     * on some documents
     */
    @Test
    public void testFailingProcessing() {
        // Set up service programmatically
        DocprocService service = new DocprocService("failing");
        DocumentProcessor first = new SettingValueProcessor("done 1");
        DocumentProcessor second = new FailingProcessor("done 2");
        DocumentProcessor third = new SettingValueProcessor("done 3");
        service.setCallStack(new CallStack().addLast(first).addLast(second).addLast(third));
        service.setInService(true);

        assertProcessingWorks(service);
    }

    protected void assertProcessingWorks(DocprocService service) {
        // Create documents
        DocumentType type = new DocumentType("test");
        type.addField("test", DataType.STRING);
        DocumentPut put1 = new DocumentPut(type, new DocumentId("doc:failing:test:1"));
        DocumentPut put2 = new DocumentPut(type, new DocumentId("doc:failing:test:2"));
        DocumentPut put3 = new DocumentPut(type, new DocumentId("doc:failing:test:3"));

        // Process them
        service.process(put1);
        service.process(put2);
        service.process(put3);
        while (service.doWork()) {}

        // Verify
        assertEquals(new StringFieldValue("done 3"), put1.getDocument().getFieldValue("test"));
        assertEquals(new StringFieldValue("done 2"), put2.getDocument().getFieldValue("test")); // Due to exception in 2
        assertEquals(new StringFieldValue("done 3"), put3.getDocument().getFieldValue("test"));
    }

    public static class SettingValueProcessor extends SimpleDocumentProcessor {

        private String value;

        public SettingValueProcessor(String value) {
            this.value = value;
        }

        @Override
        public void process(DocumentPut put) {
            put.getDocument().setFieldValue("test", value);
        }
    }

    public static class FailingProcessor extends SettingValueProcessor {

        public FailingProcessor(String name) {
            super(name);
        }

        @Override
        public void process(DocumentPut put) {
            super.process(put);
            if (put.getId().toString().equals("doc:failing:test:2")) {
                throw new HandledProcessingException("Failed at receiving document test:2");
            }
        }
    }

}
