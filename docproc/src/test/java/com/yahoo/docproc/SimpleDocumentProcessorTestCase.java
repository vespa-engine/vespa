// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.docproc.impl.DocprocService;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.idstring.IdIdString;
import com.yahoo.jdisc.Metric;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Einar M R Rosenvinge
 */
public class SimpleDocumentProcessorTestCase {

    private static DocprocService setupDocprocService(SimpleDocumentProcessor processor, Metric metric) {
        CallStack stack = new CallStack("default", metric);
        stack.addLast(processor);
        DocprocService service = new DocprocService("default");
        service.setCallStack(stack);
        service.setInService(true);
        return service;
    }

    private static Processing getProcessing(DocumentOperation... operations) {
        Processing processing = new Processing();

        for (DocumentOperation op : operations) {
            processing.addDocumentOperation(op);
        }

        return processing;
    }

    private static DocumentType createTestType() {
        DocumentType type = new DocumentType("foobar");
        type.addField("title", DataType.STRING);
        return type;
    }

    @Test
    public void requireThatProcessingMultipleOperationsWork() {
        DocumentType type = createTestType();

        Processing p = getProcessing(new DocumentPut(type, "id:this:foobar::is:a:document"),
                                     new DocumentUpdate(type, "id:this:foobar::is:an:update"),
                                     new DocumentRemove(new DocumentId("id:this:foobar::is:a:remove")));

        MockMetric metric = new MockMetric();
        DocprocService service = setupDocprocService(new VerySimpleDocumentProcessor(), metric);
        service.getExecutor().process(p);

        assertEquals(3, p.getDocumentOperations().size());
        assertTrue(p.getDocumentOperations().get(0) instanceof DocumentPut);
        assertEquals("processed", ((DocumentPut) p.getDocumentOperations().get(0)).getDocument().getFieldValue("title").getWrappedValue());
        assertTrue(p.getDocumentOperations().get(1) instanceof DocumentUpdate);
        assertTrue(p.getDocumentOperations().get(2) instanceof DocumentRemove);
        assertEquals("id:foobar:foobar::12345", p.getDocumentOperations().get(2).getId().toString());
        assertEquals(Map.of(Map.of("chain", "default", "documenttype", "foobar"), 3.0),
                     metric.metrics().get("documents_processed"));
    }

    @Test
    public void requireThatProcessingSingleOperationWorks() {
        DocumentType type = createTestType();

        Processing p = getProcessing(new DocumentPut(type, "id:this:foobar::is:a:document"));
        DocprocService service = setupDocprocService(new VerySimpleDocumentProcessor(), new MockMetric());
        service.getExecutor().process(p);

        assertEquals(1, p.getDocumentOperations().size());
        assertTrue(p.getDocumentOperations().get(0) instanceof DocumentPut);
        assertEquals("processed", ((DocumentPut) p.getDocumentOperations().get(0)).getDocument().getFieldValue("title").getWrappedValue());
    }

    @Test
    public void requireThatThrowingTerminatesIteration() {
        DocumentType type = createTestType();

        Processing p = getProcessing(new DocumentPut(type, "id:this:foobar::is:a:document"),
                                     new DocumentRemove(new DocumentId("id:this:foobar::is:a:remove")),
                                     new DocumentPut(type, "id:this:foobar::is:a:document2"));

        DocprocService service = setupDocprocService(new SimpleDocumentProcessorThrowingOnRemovesAndUpdates(), new MockMetric());
        try {
            service.getExecutor().process(p);
        } catch (RuntimeException re) {
            //ok
        }

        assertEquals(3, p.getDocumentOperations().size());
        assertTrue(p.getDocumentOperations().get(0) instanceof DocumentPut);
        assertEquals("processed", ((DocumentPut) p.getDocumentOperations().get(0)).getDocument().getFieldValue("title").getWrappedValue());
        assertTrue(p.getDocumentOperations().get(1) instanceof DocumentRemove);
        assertEquals("id:this:foobar::is:a:remove", p.getDocumentOperations().get(1).getId().toString());
        assertTrue(p.getDocumentOperations().get(2) instanceof DocumentPut);
        assertNull(((DocumentPut) p.getDocumentOperations().get(2)).getDocument().getFieldValue("title"));


    }

    public static class VerySimpleDocumentProcessor extends SimpleDocumentProcessor {

        @Override
        public void process(DocumentPut put) {
            put.getDocument().setFieldValue("title", new StringFieldValue("processed"));
        }

        @Override
        public void process(DocumentRemove remove) {
            remove.getId().setId(new IdIdString("foobar", "foobar", "", "12345"));
        }

        @Override
        public void process(DocumentUpdate update) {
            update.clearFieldUpdates();
        }

    }

    public static class SimpleDocumentProcessorThrowingOnRemovesAndUpdates extends SimpleDocumentProcessor {

        @Override
        public void process(DocumentPut put) {
            put.getDocument().setFieldValue("title", new StringFieldValue("processed"));
        }

        @Override
        public void process(DocumentRemove remove) {
            throw new RuntimeException("oh no.");
        }

        @Override
        public void process(DocumentUpdate update) {
            throw new RuntimeException("oh no.");
        }

    }

}
