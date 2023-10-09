// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.collections.Pair;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.SimpleDocumentProcessor;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocumentProcessingHandlerBasicTestCase extends DocumentProcessingHandlerTestBase {
    private DocumentType type;

    public DocumentProcessingHandlerBasicTestCase() {
        this.type = new DocumentType("yalla");
        this.type.addField(new Field("blahblah", DataType.STRING));
    }

    @Test
    public void testPut() throws InterruptedException {
        Document document = new Document(getType(), "id:ns:yalla::balla");
        document.setFieldValue("blahblah", new StringFieldValue("This is a test."));
        PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(document));

        assertTrue(sendMessage("foobar", message));

        Message msg = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
        assertNotNull(msg);
        remoteServer.ackMessage(msg);
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);

        assertTrue((msg instanceof PutDocumentMessage));
        PutDocumentMessage put = (PutDocumentMessage) msg;
        Document outDoc = put.getDocumentPut().getDocument();

        assertEquals(document, outDoc);
        assertFalse(reply.hasErrors());
    }

    @Override
    public List<Pair<String,CallStack>> getCallStacks() {
        CallStack stack = new CallStack();
        stack.addLast(new TestDocumentProcessor());

        ArrayList<Pair<String, CallStack>> stacks = new ArrayList<>(1);
        stacks.add(new Pair<>("foobar", stack));
        return stacks;
    }

    @Override
    public DocumentType getType() {
        return type;
    }

    public static class TestDocumentProcessor extends SimpleDocumentProcessor {
    }
}
