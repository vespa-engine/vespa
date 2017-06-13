// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.StringFieldValue;

/**
 * Tests a document processing where some processings fail permanently
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M. R. Rosenvinge</a>
 */
public class FailingPermanentlyDocumentProcessingTestCase extends junit.framework.TestCase {

    public FailingPermanentlyDocumentProcessingTestCase(String name) {
        super(name);
    }

    /**
     * Tests chaining of some processors, and execution of the processors
     * on some documents
     */
    public void testFailingProcessing() {
        // Set up service programmatically
        DocprocService service = new DocprocService("failing-permanently");
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
        DocumentPut put1 = new DocumentPut(type, new DocumentId("doc:permanentfailure:test:1"));
        DocumentPut put2 = new DocumentPut(type, new DocumentId("doc:permanentfailure:test:2"));
        DocumentPut put3 = new DocumentPut(type, new DocumentId("doc:permanentfailure:test:3"));

        // Process them
        service.process(put1);
        service.process(put2);
        service.process(put3);
        while (service.doWork()) {}

        // Verify
        assertEquals(new StringFieldValue("done 3"), put1.getDocument().getFieldValue("test"));
        assertEquals(new StringFieldValue("done 2"), put2.getDocument().getFieldValue("test")); // Due to PERMANENT_FAILURE in 2
        assertNull(put3.getDocument().getFieldValue("test"));  //service is disabled now
        assertFalse(service.isInService());

        service.setInService(true);
        while (service.doWork()) {}

        assertEquals(new StringFieldValue("done 3"), put3.getDocument().getFieldValue("test"));
        assertTrue(service.isInService());
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
                if (op.getId().toString().equals("doc:permanentfailure:test:2")) {
                    return DocumentProcessor.Progress.PERMANENT_FAILURE;
                }
            }
            return DocumentProcessor.Progress.DONE;
        }
    }

}
