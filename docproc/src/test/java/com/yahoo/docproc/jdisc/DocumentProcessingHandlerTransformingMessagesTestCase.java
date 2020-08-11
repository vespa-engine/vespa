// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentReply;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
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
    }

    private void put() throws InterruptedException {
        {
            PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(new Document(getType(), "id:nodocstatus:foo::put:to:put")));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(PutDocumentMessage.class));
            PutDocumentMessage outputMsg = (PutDocumentMessage)result;
            assertThat(outputMsg.getDocumentPut().getDocument().getFieldValue("foostring").toString(), is("banana"));
        }
        {
            PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(new Document(getType(), "id:nodocstatus:foo::put:to:remove")));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(RemoveDocumentMessage.class));
            RemoveDocumentMessage outputMsg = (RemoveDocumentMessage)result;
            assertThat(outputMsg.getDocumentId().toString(), is("id:nodocstatus:foo::put:to:remove"));
        }
        {
            PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(new Document(getType(), "id:nodocstatus:foo::put:to:update")));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(UpdateDocumentMessage.class));
            UpdateDocumentMessage outputMsg = (UpdateDocumentMessage)result;
            assertThat(outputMsg.getDocumentUpdate().getId().toString(), is("id:nodocstatus:foo::put:to:update"));
        }
        {
            PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(new Document(getType(), "id:nodocstatus:foo::put:to:nothing")));
            assertTrue(sendMessage(FOOBAR, message));
            Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
            assertNotNull(reply);
            assertThat(reply, instanceOf(DocumentReply.class));
            assertFalse(reply.hasErrors());
        }
    }

    private void remove() throws InterruptedException {
        {
            RemoveDocumentMessage message = new RemoveDocumentMessage(new DocumentId("id:nodocstatus:foo::remove:to:put"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(PutDocumentMessage.class));
            PutDocumentMessage outputMsg = (PutDocumentMessage)result;
            assertThat(outputMsg.getDocumentPut().getDocument().getId().toString(), is("id:nodocstatus:foo::remove:to:put"));
        }

        {
            RemoveDocumentMessage message = new RemoveDocumentMessage(new DocumentId("id:nodocstatus:foo::remove:to:remove"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(RemoveDocumentMessage.class));
            RemoveDocumentMessage outputMsg = (RemoveDocumentMessage)result;
            assertThat(outputMsg.getDocumentId().toString(), is("id:nodocstatus:foo::remove:to:remove"));
        }
        {
            RemoveDocumentMessage message = new RemoveDocumentMessage(new DocumentId("id:nodocstatus:foo::remove:to:update"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(UpdateDocumentMessage.class));
            UpdateDocumentMessage outputMsg = (UpdateDocumentMessage)result;
            assertThat(outputMsg.getDocumentUpdate().getId().toString(), is("id:nodocstatus:foo::remove:to:update"));
        }
        {
            RemoveDocumentMessage message = new RemoveDocumentMessage(new DocumentId("id:nodocstatus:foo::remove:to:nothing"));
            assertTrue(sendMessage(FOOBAR, message));
            Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
            assertNotNull(reply);
            assertThat(reply, instanceOf(DocumentReply.class));
            assertFalse(reply.hasErrors());
        }
    }

    private void update() throws InterruptedException {
        {
            UpdateDocumentMessage message = new UpdateDocumentMessage(new DocumentUpdate(getType(), "id:nodocstatus:foo::update:to:put"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(PutDocumentMessage.class));
            PutDocumentMessage outputMsg = (PutDocumentMessage)result;
            assertThat(outputMsg.getDocumentPut().getDocument().getId().toString(), is("id:nodocstatus:foo::update:to:put"));
        }

        {
            UpdateDocumentMessage message = new UpdateDocumentMessage(new DocumentUpdate(getType(), "id:nodocstatus:foo::update:to:remove"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(RemoveDocumentMessage.class));
            RemoveDocumentMessage outputMsg = (RemoveDocumentMessage)result;
            assertThat(outputMsg.getDocumentId().toString(), is("id:nodocstatus:foo::update:to:remove"));
        }
        {
            UpdateDocumentMessage message = new UpdateDocumentMessage(new DocumentUpdate(getType(), "id:nodocstatus:foo::update:to:update"));
            Routable result = sendMessageAndGetResult(message);
            assertThat(result, instanceOf(UpdateDocumentMessage.class));
            UpdateDocumentMessage outputMsg = (UpdateDocumentMessage)result;
            assertThat(outputMsg.getDocumentUpdate().getId().toString(), is("id:nodocstatus:foo::update:to:update"));
        }
        {
            UpdateDocumentMessage message = new UpdateDocumentMessage(new DocumentUpdate(getType(), "id:nodocstatus:foo::update:to:nothing"));
            assertTrue(sendMessage(FOOBAR, message));
            Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
            assertNotNull(reply);
            assertThat(reply, instanceOf(DocumentReply.class));
            assertFalse(reply.hasErrors());
        }
    }

    public class TransformingDocumentProcessor extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            ListIterator<DocumentOperation> it = processing.getDocumentOperations().listIterator();
            while (it.hasNext()) {
                DocumentOperation op = it.next();
                String id = op.getId().toString();
                if ("id:nodocstatus:foo::put:to:put".equals(id)) {
                    Document doc = ((DocumentPut)op).getDocument();
                    doc.setFieldValue("foostring", new StringFieldValue("banana"));
                } else if ("id:nodocstatus:foo::put:to:remove".equals(id)) {
                    it.set(new DocumentRemove(new DocumentId(id)));
                } else if ("id:nodocstatus:foo::put:to:update".equals(id)) {
                    it.set(new DocumentUpdate(getType(), id));
                } else if ("id:nodocstatus:foo::put:to:nothing".equals(id)) {
                    it.remove();
                } else if ("id:nodocstatus:foo::remove:to:put".equals(id)) {
                    it.set(new DocumentPut(getType(), op.getId()));
                } else if ("id:nodocstatus:foo::remove:to:remove".equals(id)) {
                    //nada
                } else if ("id:nodocstatus:foo::remove:to:update".equals(id)) {
                    it.set(new DocumentUpdate(getType(), id));
                } else if ("id:nodocstatus:foo::remove:to:nothing".equals(id)) {
                    it.remove();
                } else if ("id:nodocstatus:foo::update:to:put".equals(id)) {
                    it.set(new DocumentPut(getType(), op.getId()));
                } else if ("id:nodocstatus:foo::update:to:remove".equals(id)) {
                    it.set(new DocumentRemove(new DocumentId(id)));
                } else if ("id:nodocstatus:foo::update:to:update".equals(id)) {
                    //nada
                } else if ("id:nodocstatus:foo::update:to:nothing".equals(id)) {
                    it.remove();
                } else if ("id:12345:6789:multiop:nodocstatus:keep:this".equals(id)) {
                    //nada
                }
            }
            return Progress.DONE;
        }
    }
}
