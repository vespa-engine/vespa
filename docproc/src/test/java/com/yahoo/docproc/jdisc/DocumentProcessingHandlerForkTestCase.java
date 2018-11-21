// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.collections.Pair;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.*;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.WriteDocumentReply;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocumentProcessingHandlerForkTestCase extends DocumentProcessingHandlerTestBase {

    private static final String TOMANYALLINSAMEBUCKET = "tomanyallinsamebucket";
    private static final String TOMANYSOMEINSAMEBUCKET = "tomanysomeinsamebucket";
    private static final String TOMANY = "many";
    private static final String TOONE = "toone";
    private static final String TOZERO = "tozero";
    private final DocumentType type;

    public DocumentProcessingHandlerForkTestCase() {
        this.type = new DocumentType("baz");
        this.type.addField(new Field("blahblah", DataType.STRING));
    }

    @Override
    public DocumentType getType() {
        return type;
    }

    @Test
    public void testMessages() throws InterruptedException {
        putToFourPuts();
        putToManyAllInSameBucket();
        putToManySomeInSameBucket();
        putToOne();
        putToZero();
    }

    private void putToManyAllInSameBucket() throws InterruptedException {
        assertPutMessages(createPutDocumentMessage(), TOMANYALLINSAMEBUCKET,
                          "userdoc:123456:11111:foo:er:bra",
                          "userdoc:123456:11111:foo:trallala",
                          "userdoc:123456:11111:foo:a");
    }

    private void putToManySomeInSameBucket() throws InterruptedException {
        assertPutMessages(createPutDocumentMessage(), TOMANYSOMEINSAMEBUCKET,
                          "userdoc:123456:7890:bar:er:bra",
                          "doc:foo:bar:er:ja",
                          "userdoc:567890:1234:a",
                          "doc:foo:bar:hahahhaa",
                          "userdoc:123456:7890:a:a",
                          "doc:foo:bar:aa",
                          "userdoc:567890:1234:bar:ala",
                          "doc:foo:bar:sdfgsaa",
                          "userdoc:123456:7890:bar:tralsfa",
                          "doc:foo:bar:dfshaa");
    }

    private void putToFourPuts() throws InterruptedException {
        assertPutMessages(createPutDocumentMessage(), TOMANY,
                          "doc:foo:bar:er:bra",
                          "doc:foo:bar:er:ja",
                          "doc:foo:bar:hahahhaa",
                          "doc:foo:bar:trallala");
    }

    private void putToOne() throws InterruptedException {
        assertPutMessages(createPutDocumentMessage(), TOONE,
                          "doc:baz:bar");
    }

    private void putToZero() throws InterruptedException {
        assertTrue(sendMessage(TOZERO, createPutDocumentMessage()));

        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertTrue(reply instanceof WriteDocumentReply);
        assertFalse(reply.hasErrors());
    }

    @Override
    protected List<Pair<String, CallStack>> getCallStacks() {
        ArrayList<Pair<String, CallStack>> stacks = new ArrayList<>(5);
        stacks.add(new Pair<>(TOMANYALLINSAMEBUCKET, new CallStack().addLast(new OneToManyDocumentsAllInSameBucketProcessor())));
        stacks.add(new Pair<>(TOMANYSOMEINSAMEBUCKET, new CallStack().addLast(new OneToManyDocumentsSomeInSameBucketProcessor())));
        stacks.add(new Pair<>(TOMANY, new CallStack().addLast(new OneToManyDocumentsProcessor())));
        stacks.add(new Pair<>(TOONE, new CallStack().addLast(new OneToOneDocumentsProcessor())));
        stacks.add(new Pair<>(TOZERO, new CallStack().addLast(new OneToZeroDocumentsProcessor())));
        return stacks;
    }

    protected PutDocumentMessage createPutDocumentMessage() {
        Document document = new Document(getType(), "doc:baz:bar");
        document.setFieldValue("blahblah", new StringFieldValue("This is a test."));
        return new PutDocumentMessage(new DocumentPut(document));
    }

    private void assertPutMessages(DocumentMessage msg, String route, String... expected) throws InterruptedException {
        msg.getTrace().setLevel(9);
        assertTrue(sendMessage(route, msg));

        String[] actual = new String[expected.length];
        for (int i = 0; i < expected.length; ++i) {
            Message remoteMsg = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
            assertTrue(remoteMsg instanceof PutDocumentMessage);
            remoteMsg.getTrace().trace(1, "remoteServer.ack(" + expected[i] + ")");
            remoteServer.ackMessage(remoteMsg);
            actual[i] = ((PutDocumentMessage)remoteMsg).getDocumentPut().getDocument().getId().toString();
        }
        assertNull(remoteServer.awaitMessage(100, TimeUnit.MILLISECONDS));

        Arrays.sort(expected);
        Arrays.sort(actual);
        assertArrayEquals(expected, actual);

        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);
        assertFalse(reply.hasErrors());
        String trace = reply.getTrace().toString();
        for (String documentId : expected) {
            assertTrue("missing trace for document '" + documentId + "'\n" + trace,
                       trace.contains("remoteServer.ack(" + documentId + ")"));
        }
        if (expected.length == 1) {
            assertFalse("unexpected fork in trace for single document\n" + trace,
                        trace.contains("<fork>"));
        } else {
            assertTrue("missing fork in trace for " + expected.length + " split\n" + trace,
                       trace.contains("<fork>"));
        }
    }

    public class OneToOneDocumentsProcessor extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            return Progress.DONE;
        }
    }

    public class OneToManyDocumentsProcessor extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            List<DocumentOperation> operations = processing.getDocumentOperations();
            operations.clear();
            operations.add(new DocumentPut(type, "doc:foo:bar:er:bra"));
            operations.add(new DocumentPut(type, "doc:foo:bar:er:ja"));
            operations.add(new DocumentPut(type, "doc:foo:bar:trallala"));
            operations.add(new DocumentPut(type, "doc:foo:bar:hahahhaa"));
            return Progress.DONE;
        }
    }

    public class OneToZeroDocumentsProcessor extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            processing.getDocumentOperations().clear();
            return Progress.DONE;
        }
    }

    public class OneToManyDocumentsSomeInSameBucketProcessor extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            List<DocumentOperation> operations = processing.getDocumentOperations();
            operations.clear();
            operations.add(new DocumentPut(type, "userdoc:123456:7890:bar:er:bra"));
            operations.add(new DocumentPut(type, "doc:foo:bar:er:ja"));
            operations.add(new DocumentPut(type, "userdoc:567890:1234:a"));
            operations.add(new DocumentPut(type, "doc:foo:bar:hahahhaa"));
            operations.add(new DocumentPut(type, "userdoc:123456:7890:a:a"));
            operations.add(new DocumentPut(type, "doc:foo:bar:aa"));
            operations.add(new DocumentPut(type, "userdoc:567890:1234:bar:ala"));
            operations.add(new DocumentPut(type, "doc:foo:bar:sdfgsaa"));
            operations.add(new DocumentPut(type, "userdoc:123456:7890:bar:tralsfa"));
            operations.add(new DocumentPut(type, "doc:foo:bar:dfshaa"));
            return Progress.DONE;
        }

    }

    public class OneToManyDocumentsAllInSameBucketProcessor extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            List<DocumentOperation> docs = processing.getDocumentOperations();
            docs.clear();
            docs.add(new DocumentPut(type, "userdoc:123456:11111:foo:er:bra"));
            docs.add(new DocumentPut(type, "userdoc:123456:11111:foo:trallala"));
            docs.add(new DocumentPut(type, "userdoc:123456:11111:foo:a"));
            return Progress.DONE;
        }

    }

}
