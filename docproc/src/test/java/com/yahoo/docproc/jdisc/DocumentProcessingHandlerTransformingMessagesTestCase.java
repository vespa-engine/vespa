// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.collections.Pair;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.*;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.documentapi.messagebus.protocol.*;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.Routable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocumentProcessingHandlerTransformingMessagesTestCase extends DocumentProcessingHandlerTestBase {

    private static final String FOOBAR = "foobar";
    private final DocumentType type;

    public DocumentProcessingHandlerTransformingMessagesTestCase() {
        type = new DocumentType("foo");
        type.addField("foostring", DataType.STRING);
    }

    @Override
    public List<Pair<String, CallStack>> getCallStacks() {
        CallStack stack = new CallStack();
        stack.addLast(new TransformingDocumentProcessor());

        ArrayList<Pair<String, CallStack>> stacks = new ArrayList<>(1);
        stacks.add(new Pair<>(FOOBAR, stack));
        return stacks;
    }

    @Override
    public DocumentType getType() {
        return type;
    }

    private Routable sendMessageAndGetResult(DocumentMessage message) throws InterruptedException {
        assertTrue(sendMessage(FOOBAR, message));

        Message result = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(result);
        remoteServer.ackMessage(result);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);
        assertFalse(reply.hasErrors());

        return result;
    }

    @Test
    public void testAllMessages() throws InterruptedException {
        put();
        remove();
        update();
        batchDocumentUpdate();
    }

    private void put() throws InterruptedException {
        {
            PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(new Document(getType(), "doc:nodocstatus:put:to:put")));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(PutDocumentMessage.class));
            PutDocumentMessage outputMsg = (PutDocumentMessage)result;
            assertThat(outputMsg.getDocumentPut().getDocument().getFieldValue("foostring").toString(), is("banana"));
        }
        {
            PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(new Document(getType(), "doc:nodocstatus:put:to:remove")));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(RemoveDocumentMessage.class));
            RemoveDocumentMessage outputMsg = (RemoveDocumentMessage)result;
            assertThat(outputMsg.getDocumentId().toString(), is("doc:nodocstatus:put:to:remove"));
        }
        {
            PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(new Document(getType(), "doc:nodocstatus:put:to:update")));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(UpdateDocumentMessage.class));
            UpdateDocumentMessage outputMsg = (UpdateDocumentMessage)result;
            assertThat(outputMsg.getDocumentUpdate().getId().toString(), is("doc:nodocstatus:put:to:update"));
        }
        {
            PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(new Document(getType(), "doc:nodocstatus:put:to:nothing")));
            assertTrue(sendMessage(FOOBAR, message));
            Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
            assertNotNull(reply);
            assertThat(reply, instanceOf(DocumentReply.class));
            assertFalse(reply.hasErrors());
        }
    }

    private void remove() throws InterruptedException {
        {
            RemoveDocumentMessage message = new RemoveDocumentMessage(new DocumentId("doc:nodocstatus:remove:to:put"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(PutDocumentMessage.class));
            PutDocumentMessage outputMsg = (PutDocumentMessage)result;
            assertThat(outputMsg.getDocumentPut().getDocument().getId().toString(), is("doc:nodocstatus:remove:to:put"));
        }

        {
            RemoveDocumentMessage message = new RemoveDocumentMessage(new DocumentId("doc:nodocstatus:remove:to:remove"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(RemoveDocumentMessage.class));
            RemoveDocumentMessage outputMsg = (RemoveDocumentMessage)result;
            assertThat(outputMsg.getDocumentId().toString(), is("doc:nodocstatus:remove:to:remove"));
        }
        {
            RemoveDocumentMessage message = new RemoveDocumentMessage(new DocumentId("doc:nodocstatus:remove:to:update"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(UpdateDocumentMessage.class));
            UpdateDocumentMessage outputMsg = (UpdateDocumentMessage)result;
            assertThat(outputMsg.getDocumentUpdate().getId().toString(), is("doc:nodocstatus:remove:to:update"));
        }
        {
            RemoveDocumentMessage message = new RemoveDocumentMessage(new DocumentId("doc:nodocstatus:remove:to:nothing"));
            assertTrue(sendMessage(FOOBAR, message));
            Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
            assertNotNull(reply);
            assertThat(reply, instanceOf(DocumentReply.class));
            assertFalse(reply.hasErrors());
        }
    }

    private void update() throws InterruptedException {
        {
            UpdateDocumentMessage message = new UpdateDocumentMessage(new DocumentUpdate(getType(), "doc:nodocstatus:update:to:put"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(PutDocumentMessage.class));
            PutDocumentMessage outputMsg = (PutDocumentMessage)result;
            assertThat(outputMsg.getDocumentPut().getDocument().getId().toString(), is("doc:nodocstatus:update:to:put"));
        }

        {
            UpdateDocumentMessage message = new UpdateDocumentMessage(new DocumentUpdate(getType(), "doc:nodocstatus:update:to:remove"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(RemoveDocumentMessage.class));
            RemoveDocumentMessage outputMsg = (RemoveDocumentMessage)result;
            assertThat(outputMsg.getDocumentId().toString(), is("doc:nodocstatus:update:to:remove"));
        }
        {
            UpdateDocumentMessage message = new UpdateDocumentMessage(new DocumentUpdate(getType(), "doc:nodocstatus:update:to:update"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(UpdateDocumentMessage.class));
            UpdateDocumentMessage outputMsg = (UpdateDocumentMessage)result;
            assertThat(outputMsg.getDocumentUpdate().getId().toString(), is("doc:nodocstatus:update:to:update"));
        }
        {
            UpdateDocumentMessage message = new UpdateDocumentMessage(new DocumentUpdate(getType(), "doc:nodocstatus:update:to:nothing"));
            assertTrue(sendMessage(FOOBAR, message));
            Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
            assertNotNull(reply);
            assertThat(reply, instanceOf(DocumentReply.class));
            assertFalse(reply.hasErrors());
        }
    }

    private void batchDocumentUpdate() throws InterruptedException {
        DocumentUpdate doc1 = new DocumentUpdate(getType(), new DocumentId("userdoc:test:12345:batch:nodocstatus:keep:this"));
        DocumentUpdate doc2 = new DocumentUpdate(getType(), new DocumentId("userdoc:test:12345:batch:nodocstatus:skip:this"));

        Field testField = getType().getField("foostring");
        doc1.addFieldUpdate(FieldUpdate.createAssign(testField, new StringFieldValue("1 not yet processed")));
        doc2.addFieldUpdate(FieldUpdate.createAssign(testField, new StringFieldValue("2 not yet processed")));

        BatchDocumentUpdateMessage message = new BatchDocumentUpdateMessage(12345);
        message.addUpdate(doc1);
        message.addUpdate(doc2);

        Routable result = sendMessageAndGetResult(message);
        assertThat(result, instanceOf(UpdateDocumentMessage.class));
        DocumentUpdate outputUpd = ((UpdateDocumentMessage)result).getDocumentUpdate();
        assertThat(outputUpd.getId().toString(), is("userdoc:test:12345:batch:nodocstatus:keep:this"));
    }

    public class TransformingDocumentProcessor extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            ListIterator<DocumentOperation> it = processing.getDocumentOperations().listIterator();
            while (it.hasNext()) {
                DocumentOperation op = it.next();
                String id = op.getId().toString();
                if ("doc:nodocstatus:put:to:put".equals(id)) {
                    Document doc = ((DocumentPut)op).getDocument();
                    doc.setFieldValue("foostring", new StringFieldValue("banana"));
                } else if ("doc:nodocstatus:put:to:remove".equals(id)) {
                    it.set(new DocumentRemove(new DocumentId(id)));
                } else if ("doc:nodocstatus:put:to:update".equals(id)) {
                    it.set(new DocumentUpdate(getType(), id));
                } else if ("doc:nodocstatus:put:to:nothing".equals(id)) {
                    it.remove();
                } else if ("doc:nodocstatus:remove:to:put".equals(id)) {
                    it.set(new DocumentPut(getType(), op.getId()));
                } else if ("doc:nodocstatus:remove:to:remove".equals(id)) {
                    //nada
                } else if ("doc:nodocstatus:remove:to:update".equals(id)) {
                    it.set(new DocumentUpdate(getType(), id));
                } else if ("doc:nodocstatus:remove:to:nothing".equals(id)) {
                    it.remove();
                } else if ("doc:nodocstatus:update:to:put".equals(id)) {
                    it.set(new DocumentPut(getType(), op.getId()));
                } else if ("doc:nodocstatus:update:to:remove".equals(id)) {
                    it.set(new DocumentRemove(new DocumentId(id)));
                } else if ("doc:nodocstatus:update:to:update".equals(id)) {
                    //nada
                } else if ("doc:nodocstatus:update:to:nothing".equals(id)) {
                    it.remove();
                } else if ("userdoc:12345:6789:multiop:nodocstatus:keep:this".equals(id)) {
                    //nada
                } else if ("userdoc:12345:6789:multiop:nodocstatus:skip:this".equals(id)) {
                    it.remove();
                } else if ("userdoc:test:12345:batch:nodocstatus:keep:this".equals(id)) {
                    //nada
                } else if ("userdoc:test:12345:batch:nodocstatus:skip:this".equals(id)) {
                    it.remove();
                }
            }
            return Progress.DONE;
        }
    }
}
