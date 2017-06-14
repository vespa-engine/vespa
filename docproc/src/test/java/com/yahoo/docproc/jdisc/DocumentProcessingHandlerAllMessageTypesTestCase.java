// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.collections.Pair;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.*;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.documentapi.messagebus.protocol.BatchDocumentUpdateMessage;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.vdslib.DocumentList;
import com.yahoo.vdslib.Entry;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocumentProcessingHandlerAllMessageTypesTestCase extends DocumentProcessingHandlerTestBase {

    private static final String FOOBAR = "foobar";
    private final DocumentType type;

    public DocumentProcessingHandlerAllMessageTypesTestCase() {
        this.type = new DocumentType("baz");
        this.type.addField(new Field("blahblah", DataType.STRING));
        this.type.addField(new Field("defaultWait", DataType.INT));
        this.type.addField(new Field("customWait", DataType.INT));
    }

    @Test
    public void testMessages() throws InterruptedException {
        get();
        put();
        remove();
        update();
        batchDocumentUpdate();
    }

    private void get() throws InterruptedException {
        GetDocumentMessage message = new GetDocumentMessage(new DocumentId("doc:this:is:a:test"), "fieldset?");

        assertTrue(sendMessage(FOOBAR, message));

        Message result = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(result);
        remoteServer.ackMessage(result);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);

        assertThat(result, instanceOf(GetDocumentMessage.class));

        assertFalse(reply.hasErrors());
    }


    private void put() throws InterruptedException {
        Document document = new Document(getType(), "doc:baz:foo");
        document.setFieldValue("blahblah", new StringFieldValue("This is a test."));
        PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(document));

        assertTrue(sendMessage(FOOBAR, message));

        Message result = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(result);
        remoteServer.ackMessage(result);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);

        assertThat(result, instanceOf(PutDocumentMessage.class));
        PutDocumentMessage outputMsg = (PutDocumentMessage) result;
        assertThat(((StringFieldValue) outputMsg.getDocumentPut().getDocument().getFieldValue("blahblah")).getString(), is("THIS IS A TEST."));

        assertFalse(reply.hasErrors());
    }

    private void remove() throws InterruptedException {
        RemoveDocumentMessage message = new RemoveDocumentMessage(new DocumentId("doc:12345:6789"));

        assertTrue(sendMessage(FOOBAR, message));

        Message result = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(result);
        remoteServer.ackMessage(result);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);

        assertThat(result, instanceOf(RemoveDocumentMessage.class));
        RemoveDocumentMessage outputMsg = (RemoveDocumentMessage) result;
        assertThat(outputMsg.getDocumentId().toString(), is("doc:12345:6789"));

        assertFalse(reply.hasErrors());
    }

    private void update() throws InterruptedException {
        DocumentUpdate documentUpdate = new DocumentUpdate(getType(), "doc:baz:foo");
        UpdateDocumentMessage message = new UpdateDocumentMessage(documentUpdate);

        assertTrue(sendMessage(FOOBAR, message));

        Message result = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(result);
        remoteServer.ackMessage(result);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);

        assertThat(result, instanceOf(UpdateDocumentMessage.class));
        UpdateDocumentMessage outputMsg = (UpdateDocumentMessage) result;
        assertThat(outputMsg.getDocumentUpdate().getId().toString(), is("doc:baz:foo"));

        assertFalse(reply.hasErrors());
    }

    private void batchDocumentUpdate() throws InterruptedException {
        DocumentUpdate doc1 = new DocumentUpdate(getType(), new DocumentId("userdoc:test:12345:multi:1"));
        DocumentUpdate doc2 = new DocumentUpdate(getType(), new DocumentId("userdoc:test:12345:multi:2"));

        Field testField = getType().getField("blahblah");
        doc1.addFieldUpdate(FieldUpdate.createAssign(testField, new StringFieldValue("1 not yet processed")));
        doc2.addFieldUpdate(FieldUpdate.createAssign(testField, new StringFieldValue("2 not yet processed")));

        BatchDocumentUpdateMessage message = new BatchDocumentUpdateMessage(12345);
        message.addUpdate(doc1);
        message.addUpdate(doc2);

        assertTrue(sendMessage(FOOBAR, message));

        Message remote1 = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertTrue(remote1 instanceof UpdateDocumentMessage);
        remoteServer.ackMessage(remote1);
        assertNull(driver.client().awaitReply(100, TimeUnit.MILLISECONDS));

        Message remote2 = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertTrue(remote2 instanceof UpdateDocumentMessage);
        remoteServer.ackMessage(remote2);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);
        assertFalse(reply.hasErrors());
    }

    @Override
    public List<Pair<String,CallStack>> getCallStacks() {
        CallStack stack = new CallStack();
        stack.addLast(new YallaDocumentProcessor());
        stack.addLast(new WaitingDefaultDocumentProcessor());
        stack.addLast(new WaitingCustomDocumentProcessor());

        ArrayList<Pair<String, CallStack>> stacks = new ArrayList<>(1);
        stacks.add(new Pair<>(FOOBAR, stack));
        return stacks;
    }

    @Override
    public DocumentType getType() {
        return type;
    }

    private static class YallaDocumentProcessor extends DocumentProcessor {
        @Override
        public Progress process(Processing processing) {
            for (DocumentOperation op : processing.getDocumentOperations()) {
                if (op instanceof DocumentPut) {
                    Document doc = ((DocumentPut) op).getDocument();
                    for (Field f : doc.getDataType().fieldSet()) {
                        FieldValue val = doc.getFieldValue(f);
                        if (val instanceof StringFieldValue) {
                            StringFieldValue sf = (StringFieldValue) val;
                            doc.setFieldValue(f, new StringFieldValue(sf.getString().toUpperCase()));
                        }
                    }
                }
                //don't touch updates or removes
            }
            return Progress.DONE;
        }
    }

    private static abstract class WaitingDocumentProcessor extends DocumentProcessor {
        protected Progress laterProgress;
        protected String waitKey;

        @Override
        public Progress process(Processing processing) {
            for (DocumentOperation op : processing.getDocumentOperations()) {
                if (op instanceof DocumentPut) {
                    Document doc = ((DocumentPut) op).getDocument();
                    if (doc.getFieldValue(waitKey) == null) {
                        System.out.println(this.getClass().getSimpleName() + ": returning LATER for " + doc);
                        doc.setFieldValue(waitKey, new IntegerFieldValue(1));
                        return laterProgress;
                    }
                }
                //don't touch updates or removes
            }
            return Progress.DONE;
        }
    }

    private static class WaitingDefaultDocumentProcessor extends WaitingDocumentProcessor {
        private WaitingDefaultDocumentProcessor() {
            laterProgress = Progress.LATER;
            waitKey = "defaultWait";
        }
    }

    private static class WaitingCustomDocumentProcessor extends WaitingDocumentProcessor {
        private WaitingCustomDocumentProcessor() {
            laterProgress = Progress.later(60);
            waitKey = "customWait";
        }
    }
}
