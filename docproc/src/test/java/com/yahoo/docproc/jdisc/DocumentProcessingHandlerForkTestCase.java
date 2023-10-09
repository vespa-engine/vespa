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
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.WriteDocumentReply;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.Trace;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocumentProcessingHandlerForkTestCase extends DocumentProcessingHandlerTestBase {

    private static final String TOMANYALLINSAMEBUCKET = "tomanyallinsamebucket";
    private static final String TOMANYSOMEINSAMEBUCKET = "tomanysomeinsamebucket";
    private static final String TOMANY = "many";
    private static final String TOONE = "toone";
    private static final String TOZERO = "tozero";
    private static final String TOMULTIPLY = "multiply";
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

    private Trace processDocument(String chain, String id, int numAcks) throws InterruptedException {
        assertTrue(sendMessage(chain, createPutDocumentMessage(id)));
        for (int i = 0; i < numAcks; i++) {
            Message remoteMsg = remoteServer.awaitMessage(60, TimeUnit.SECONDS);
            assertTrue(remoteMsg instanceof PutDocumentMessage);
            remoteMsg.getTrace().trace(1, "remoteServer.ack(" + id + ")");
            remoteServer.ackMessage(remoteMsg);
        }
        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertNotNull(reply);
        assertFalse(reply.hasErrors());
        return reply.getTrace();
    }

    private int countMatches(String text, String pattern) {
        int count = 0;
        for (int fromIndex = text.indexOf(pattern); fromIndex >= 0; fromIndex = text.indexOf(pattern, fromIndex+1)) {
            count++;
        }
        return count;
    }
    private int countSequencerMessages(String text) {
        return countMatches(text, "Sequencer sending message with sequence id");
    }
    boolean containsSequenceId(String trace, long sequence) {
        return trace.contains("Sequencer sending message with sequence id '" + sequence + "'.");
    }
    private void testNoExtraSequencingOfNormalOp() throws InterruptedException {
        String trace = processDocument(TOMULTIPLY, "id:ns:baz::noop", 1).toString();
        assertTrue(containsSequenceId(trace, 553061876));
        assertEquals(1, countSequencerMessages(trace));
    }
    private void testSequencingWhenChangingId() throws InterruptedException {
        String trace = processDocument(TOMULTIPLY, "id:ns:baz::transform", 1).toString();
        assertTrue(containsSequenceId(trace, 2033581295));
        assertTrue(containsSequenceId(trace, -633118987));
        assertEquals(2, countSequencerMessages(trace));
    }
    private void testSequencingWhenAddingOtherId() throws InterruptedException {
        String trace = processDocument(TOMULTIPLY, "id:ns:baz::append", 2).toString();
        assertTrue(containsSequenceId(trace, -334982203));
        assertTrue(containsSequenceId(trace, -633118987));
        assertEquals(2, countSequencerMessages(trace));
    }
    private void testSequencingWhenAddingSameId() throws InterruptedException {
        String trace = processDocument(TOMULTIPLY, "id:ns:baz::append_same", 2).toString();
        assertTrue(containsSequenceId(trace, 1601327001));
        assertEquals(3, countSequencerMessages(trace));
        assertEquals(3, countMatches(trace, "Sequencer sending message with sequence id '1601327001'."));
    }

    @Test
    public void testSequencing() throws InterruptedException {
        testNoExtraSequencingOfNormalOp();
        testSequencingWhenChangingId();
        testSequencingWhenAddingOtherId();
        testSequencingWhenAddingSameId();
    }

    private void putToManyAllInSameBucket() throws InterruptedException {
        assertPutMessages(createPutDocumentMessage(), TOMANYALLINSAMEBUCKET,
                          "id:123456:baz:n=11111:foo:er:bra",
                          "id:123456:baz:n=11111:foo:trallala",
                          "id:123456:baz:n=11111:foo:a");
    }

    private void putToManySomeInSameBucket() throws InterruptedException {
        assertPutMessages(createPutDocumentMessage(), TOMANYSOMEINSAMEBUCKET,
                          "id:123456:baz:n=7890:bar:er:bra",
                          "id:foo:baz::er:ja",
                          "id:567890:baz:n=1234:a",
                          "id:foo:baz::hahahhaa",
                          "id:123456:baz:n=7890:a:a",
                          "id:foo:baz::aa",
                          "id:567890:baz:n=1234:bar:ala",
                          "id:foo:baz::sdfgsaa",
                          "id:123456:baz:n=7890:bar:tralsfa",
                          "id:foo:baz::dfshaa");
    }

    private void putToFourPuts() throws InterruptedException {
        assertPutMessages(createPutDocumentMessage(), TOMANY,
                          "id:foo:baz::er:bra",
                          "id:foo:baz::er:ja",
                          "id:foo:baz::hahahhaa",
                          "id:foo:baz::trallala");
    }

    private void putToOne() throws InterruptedException {
        assertPutMessages(createPutDocumentMessage(), TOONE,
                          "id:ns:baz::bar");
    }

    private void putToZero() throws InterruptedException {
        assertTrue(sendMessage(TOZERO, createPutDocumentMessage()));

        Reply reply = driver.client().awaitReply(60, TimeUnit.SECONDS);
        assertTrue(reply instanceof WriteDocumentReply);
        assertFalse(reply.hasErrors());
    }

    @Override
    protected List<Pair<String, CallStack>> getCallStacks() {
        ArrayList<Pair<String, CallStack>> stacks = new ArrayList<>(6);
        stacks.add(new Pair<>(TOMANYALLINSAMEBUCKET, new CallStack().addLast(new OneToManyDocumentsAllInSameBucketProcessor())));
        stacks.add(new Pair<>(TOMANYSOMEINSAMEBUCKET, new CallStack().addLast(new OneToManyDocumentsSomeInSameBucketProcessor())));
        stacks.add(new Pair<>(TOMANY, new CallStack().addLast(new OneToManyDocumentsProcessor())));
        stacks.add(new Pair<>(TOONE, new CallStack().addLast(new OneToOneDocumentsProcessor())));
        stacks.add(new Pair<>(TOZERO, new CallStack().addLast(new OneToZeroDocumentsProcessor())));
        stacks.add(new Pair<>(TOMULTIPLY, new CallStack().addLast(new MultiplyProcessor())));
        return stacks;
    }

    protected PutDocumentMessage createPutDocumentMessage() {
        return createPutDocumentMessage("id:ns:baz::bar");
    }
    protected PutDocumentMessage createPutDocumentMessage(String id) {
        Document document = new Document(getType(), id);
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

    public static class OneToOneDocumentsProcessor extends DocumentProcessor {

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
            operations.add(new DocumentPut(type, "id:foo:baz::er:bra"));
            operations.add(new DocumentPut(type, "id:foo:baz::er:ja"));
            operations.add(new DocumentPut(type, "id:foo:baz::trallala"));
            operations.add(new DocumentPut(type, "id:foo:baz::hahahhaa"));
            return Progress.DONE;
        }
    }

    public static class OneToZeroDocumentsProcessor extends DocumentProcessor {

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
            operations.add(new DocumentPut(type, "id:123456:baz:n=7890:bar:er:bra"));
            operations.add(new DocumentPut(type, "id:foo:baz::er:ja"));
            operations.add(new DocumentPut(type, "id:567890:baz:n=1234:a"));
            operations.add(new DocumentPut(type, "id:foo:baz::hahahhaa"));
            operations.add(new DocumentPut(type, "id:123456:baz:n=7890:a:a"));
            operations.add(new DocumentPut(type, "id:foo:baz::aa"));
            operations.add(new DocumentPut(type, "id:567890:baz:n=1234:bar:ala"));
            operations.add(new DocumentPut(type, "id:foo:baz::sdfgsaa"));
            operations.add(new DocumentPut(type, "id:123456:baz:n=7890:bar:tralsfa"));
            operations.add(new DocumentPut(type, "id:foo:baz::dfshaa"));
            return Progress.DONE;
        }

    }

    public class OneToManyDocumentsAllInSameBucketProcessor extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            List<DocumentOperation> docs = processing.getDocumentOperations();
            docs.clear();
            docs.add(new DocumentPut(type, "id:123456:baz:n=11111:foo:er:bra"));
            docs.add(new DocumentPut(type, "id:123456:baz:n=11111:foo:trallala"));
            docs.add(new DocumentPut(type, "id:123456:baz:n=11111:foo:a"));
            return Progress.DONE;
        }

    }

    public class MultiplyProcessor extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            List<DocumentOperation> docs = processing.getDocumentOperations();
            assertEquals(1, docs.size());
            DocumentId id = docs.get(0).getId();
            if ("id:ns:baz::transform".equals(id.toString())) {
                docs.clear();
                docs.add(new DocumentPut(type, "id:ns:baz::appended"));
            } else if ("id:ns:baz::append".equals(id.toString())) {
                docs.add(new DocumentPut(type, "id:ns:baz::appended"));
            } else if ("id:ns:baz::append_same".equals(id.toString())) {
                docs.add(new DocumentPut(type, "id:ns:baz::append_same"));

            }
            return Progress.DONE;
        }

    }

}
