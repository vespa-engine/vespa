// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

/**
 * Tests a document processing where some processings fail with an exception
 *
 * @author Einar M. R. Rosenvinge
 */
public class FailingDocumentProcessingWithoutExceptionTestCase extends junit.framework.TestCase {

    /**
     * Tests chaining of some processors, and execution of the processors
     * on some documents
     */
    @Test
    public void testFailingProcessing() {
        // Set up service programmatically
        DocprocService service = new DocprocService("failing-no-exception");
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
        DocumentPut put1 = new DocumentPut(type, new DocumentId("doc:woexception:test:1"));
        DocumentPut put2 = new DocumentPut(type, new DocumentId("doc:woexception:test:2"));
        DocumentPut put3 = new DocumentPut(type, new DocumentId("doc:woexception:test:3"));

        // Process them
        service.process(put1);
        service.process(put2);
        service.process(put3);
        while (service.doWork()) {}

        // Verify
        assertEquals(new StringFieldValue("done 3"), put1.getDocument().getFieldValue("test"));
        assertEquals(new StringFieldValue("done 2"), put2.getDocument().getFieldValue("test")); // Due to PROCESSING_FAILED in 2
        assertEquals(new StringFieldValue("done 3"), put3.getDocument().getFieldValue("test"));
    }

    public static class SettingValueProcessor extends DocumentProcessor {

        private String value;

        public SettingValueProcessor(String value) {
            this.value = value;
        }

        @Override
        public Progress process(Processing processing) {
            for (DocumentOperation op : processing.getDocumentOperations()) {
                ((DocumentPut)op).getDocument().setFieldValue("test", value);
            }
            return Progress.DONE;
        }
    }

    public static class FailingProcessor extends SettingValueProcessor {

        public FailingProcessor(String name) {
            super(name);
        }

        @Override
        public Progress process(Processing processing) {
            super.process(processing);
            for (DocumentOperation op : processing.getDocumentOperations()) {
                if (op.getId().toString().equals("doc:woexception:test:2")) {
                    return DocumentProcessor.Progress.FAILED;
                }
            }
            return DocumentProcessor.Progress.DONE;
        }
    }

}
