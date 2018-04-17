// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Einar M R Rosenvinge
 */
public class CallbackTestCase {

    private DocumentPut put1;
    private DocumentPut put2;
    private List<DocumentOperation> operations = new ArrayList<>(2);
    DocprocService service;

    @Before
    public void setUp() {
        service = new DocprocService("callback");
        service.setCallStack(new CallStack().addNext(new TestCallbackDp()));
        service.setInService(true);

        // Create documents
        DocumentType type = new DocumentType("test");
        type.addField("status", DataType.STRING);
        put1 = new DocumentPut(type, new DocumentId("doc:callback:test:1"));
        put2 = new DocumentPut(type, new DocumentId("doc:callback:test:2"));
        operations.add(new DocumentPut(type, new DocumentId("doc:callback:test:3")));
        operations.add(new DocumentPut(type, new DocumentId("doc:callback:test:4")));
    }

    @Test
    public void testProcessingWithCallbackSingleDoc() {
        ProcessingEndpoint drecv = new TestProcessingEndpoint();

        service.process(put1, drecv);
        while (service.doWork()) { }

        assertEquals(new StringFieldValue("received"), put1.getDocument().getFieldValue("status"));
    }

    @Test
    public void testProcessingWithCallbackMultipleDocs() {
        ProcessingEndpoint drecv = new TestProcessingEndpoint();

        service.process(toProcessing(operations), drecv);
        while (service.doWork()) { }

        assertEquals(new StringFieldValue("received"), ((DocumentPut) operations.get(0)).getDocument().getFieldValue("status"));
        assertEquals(new StringFieldValue("received"), ((DocumentPut) operations.get(1)).getDocument().getFieldValue("status"));
    }

    private Processing toProcessing(List<DocumentOperation> documentOperations) {
        Processing processing = new Processing();
        for (DocumentOperation op : documentOperations)
            processing.addDocumentOperation(op);
        return processing;
    }

    @Test
    public void testProcessingWithCallbackProcessing() {
        ProcessingEndpoint drecv = new TestProcessingEndpoint();

        Processing processing = new Processing("default", put2, service.getCallStack());

        service.process(processing, drecv);
        while (service.doWork()) { }

        assertEquals(new StringFieldValue("received"), put2.getDocument().getFieldValue("status"));
    }

    public class TestProcessingEndpoint implements ProcessingEndpoint {
        public void processingDone(Processing processing) {
            for (DocumentOperation op : processing.getDocumentOperations()) {
                ((DocumentPut)op).getDocument().setFieldValue("status", new StringFieldValue("received"));
            }
        }

        public void processingFailed(Processing processing, Exception exception) {
            //do nothing here for now
        }
    }

    public static class TestCallbackDp extends com.yahoo.docproc.SimpleDocumentProcessor {
    }

}
