// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.container.StatisticsConfig;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.idstring.UserDocIdString;
import com.yahoo.statistics.StatisticsImpl;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class SimpleDocumentProcessorTestCase {

    private static DocprocService setupDocprocService(SimpleDocumentProcessor processor) {
        CallStack stack = new CallStack("default", new StatisticsImpl(new StatisticsConfig(new StatisticsConfig.Builder())), new NullMetric());
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

    @Test
    public void requireThatProcessingMultipleOperationsWork() {
        DocumentType type = new DocumentType("foobar");
        type.addField("title", DataType.STRING);

        Processing p = getProcessing(new DocumentPut(type, "doc:this:is:a:document"),
                                     new DocumentUpdate(type, "doc:this:is:an:update"),
                                     new DocumentRemove(new DocumentId("doc:this:is:a:remove")));

        DocprocService service = setupDocprocService(new VerySimpleDocumentProcessor());
        service.getExecutor().process(p);

        assertThat(p.getDocumentOperations().size(), is(3));
        assertThat(p.getDocumentOperations().get(0) instanceof DocumentPut, is(true));
        assertThat(((DocumentPut) p.getDocumentOperations().get(0)).getDocument().getFieldValue("title").getWrappedValue(),
                   is("processed"));
        assertThat(p.getDocumentOperations().get(1) instanceof DocumentUpdate, is(true));
        assertThat(p.getDocumentOperations().get(2) instanceof DocumentRemove, is(true));
        assertThat(p.getDocumentOperations().get(2).getId().toString(),
                   is("userdoc:foobar:1234:something"));
    }

    @Test
    public void requireThatProcessingSingleOperationWorks() {
        DocumentType type = new DocumentType("foobar");
        type.addField("title", DataType.STRING);

        Processing p = getProcessing(new DocumentPut(type, "doc:this:is:a:document"));
        DocprocService service = setupDocprocService(new VerySimpleDocumentProcessor());
        service.getExecutor().process(p);

        assertThat(p.getDocumentOperations().size(), is(1));
        assertThat(p.getDocumentOperations().get(0) instanceof DocumentPut, is(true));
        assertThat(((DocumentPut) p.getDocumentOperations().get(0)).getDocument().getFieldValue("title").getWrappedValue(),
                   is("processed"));
    }

    @Test
    public void requireThatThrowingTerminatesIteration() {
        DocumentType type = new DocumentType("foobar");
        type.addField("title", DataType.STRING);

        Processing p = getProcessing(new DocumentPut(type, "doc:this:is:a:document"),
                                     new DocumentRemove(new DocumentId("doc:this:is:a:remove")),
                                     new DocumentPut(type, "doc:this:is:a:document2"));

        DocprocService service = setupDocprocService(new SimpleDocumentProcessorThrowingOnRemovesAndUpdates());
        try {
            service.getExecutor().process(p);
        } catch (RuntimeException re) {
            //ok
        }

        assertThat(p.getDocumentOperations().size(), is(3));
        assertThat(p.getDocumentOperations().get(0) instanceof DocumentPut, is(true));
        assertThat(((DocumentPut) p.getDocumentOperations().get(0)).getDocument().getFieldValue("title").getWrappedValue(),
                   is("processed"));
        assertThat(p.getDocumentOperations().get(1) instanceof DocumentRemove, is(true));
        assertThat(p.getDocumentOperations().get(1).getId().toString(),
                   is("doc:this:is:a:remove"));
        assertThat(p.getDocumentOperations().get(2) instanceof DocumentPut, is(true));
        assertThat(((DocumentPut) p.getDocumentOperations().get(2)).getDocument().getFieldValue("title"),
                   nullValue());


    }

    public static class VerySimpleDocumentProcessor extends SimpleDocumentProcessor {

        @Override
        public void process(DocumentPut put) {
            put.getDocument().setFieldValue("title", new StringFieldValue("processed"));
        }

        @Override
        public void process(DocumentRemove remove) {
            remove.getId().setId(new UserDocIdString("foobar", 1234L, "something"));
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
