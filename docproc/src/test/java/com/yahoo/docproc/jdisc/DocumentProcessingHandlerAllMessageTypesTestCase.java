// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.collections.Pair;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * @author Einar M R Rosenvinge
 */
public class DocumentProcessingHandlerAllMessageTypesTestCase extends DocumentProcessingHandlerTestBase {

    private static final String FOOBAR = "foobar";
    private final DocumentType type;

    public DocumentProcessingHandlerAllMessageTypesTestCase() {
        this.type = new DocumentType("baz");
        this.type.addField(new Field("blahblah", DataType.STRING));
        this.type.addField(new Field("defaultWait", DataType.INT));
        this.type.addField(new Field("customWait", DataType.INT));
        type.addFieldSets(Collections.singletonMap(DocumentType.DOCUMENT, Arrays.asList("blahblah", "defaultWait", "customWait")));
    }

    @Test
    public void testMessages() throws InterruptedException {
        get();
        put();
        remove();
        update();
    }

    private void get() throws InterruptedException {
        GetDocumentMessage message = new GetDocumentMessage(new DocumentId("id:this:baz::is:a:test"), "fieldset?");

        assertTrue(sendMessage(FOOBAR, message));

        Message result = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(result);
        remoteServer.ackMessage(result);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);

        assertTrue(result instanceof GetDocumentMessage);

        assertFalse(reply.hasErrors());
    }


    private void put() throws InterruptedException {
        Document document = new Document(getType(), "id:ns:baz::foo");
        document.setFieldValue("blahblah", new StringFieldValue("This is a test."));
        PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(document));

        assertTrue(sendMessage(FOOBAR, message));

        Message result = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(result);
        remoteServer.ackMessage(result);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);

        assertTrue(result instanceof PutDocumentMessage);
        PutDocumentMessage outputMsg = (PutDocumentMessage) result;
        assertEquals("THIS IS A TEST.", ((StringFieldValue) outputMsg.getDocumentPut().getDocument().getFieldValue("blahblah")).getString());

        assertFalse(reply.hasErrors());
    }

    private void remove() throws InterruptedException {
        RemoveDocumentMessage message = new RemoveDocumentMessage(new DocumentId("id:ns:baz::12345:6789"));

        assertTrue(sendMessage(FOOBAR, message));

        Message result = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(result);
        remoteServer.ackMessage(result);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);

        assertTrue(result instanceof RemoveDocumentMessage);
        RemoveDocumentMessage outputMsg = (RemoveDocumentMessage) result;
        assertEquals("id:ns:baz::12345:6789", outputMsg.getDocumentId().toString());

        assertFalse(reply.hasErrors());
    }

    private void update() throws InterruptedException {
        DocumentUpdate documentUpdate = new DocumentUpdate(getType(), "id:ns:baz::foo");
        UpdateDocumentMessage message = new UpdateDocumentMessage(documentUpdate);

        assertTrue(sendMessage(FOOBAR, message));

        Message result = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(result);
        remoteServer.ackMessage(result);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);

        assertTrue(result instanceof UpdateDocumentMessage);
        UpdateDocumentMessage outputMsg = (UpdateDocumentMessage) result;
        assertEquals("id:ns:baz::foo", outputMsg.getDocumentUpdate().getId().toString());

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
