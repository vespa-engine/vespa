// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.component.Version;
import com.yahoo.document.BucketId;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.GlobalId;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.idstring.IdString;
import com.yahoo.messagebus.Routable;
import com.yahoo.text.Utf8;
import com.yahoo.vdslib.SearchResult;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Messages80TestCase extends MessagesTestBase {

    @Override
    protected void registerTests(Map<Integer, RunnableTest> out) {
        out.put(DocumentProtocol.MESSAGE_CREATEVISITOR,   new CreateVisitorMessageTest());
        out.put(DocumentProtocol.MESSAGE_DESTROYVISITOR,  new DestroyVisitorMessageTest());
        out.put(DocumentProtocol.MESSAGE_DOCUMENTLIST,    new DocumentListMessageTest());
        out.put(DocumentProtocol.MESSAGE_EMPTYBUCKETS,    new EmptyBucketsMessageTest());
        out.put(DocumentProtocol.MESSAGE_GETBUCKETLIST,   new GetBucketListMessageTest());
        out.put(DocumentProtocol.MESSAGE_GETBUCKETSTATE,  new GetBucketStateMessageTest());
        out.put(DocumentProtocol.MESSAGE_GETDOCUMENT,     new GetDocumentMessageTest());
        out.put(DocumentProtocol.MESSAGE_MAPVISITOR,      new MapVisitorMessageTest());
        out.put(DocumentProtocol.MESSAGE_PUTDOCUMENT,     new PutDocumentMessageTest());
        out.put(DocumentProtocol.MESSAGE_QUERYRESULT,     new QueryResultMessageTest());
        out.put(DocumentProtocol.MESSAGE_REMOVEDOCUMENT,  new RemoveDocumentMessageTest());
        out.put(DocumentProtocol.MESSAGE_REMOVELOCATION,  new RemoveLocationMessageTest());
        out.put(DocumentProtocol.MESSAGE_STATBUCKET,      new StatBucketMessageTest());
        out.put(DocumentProtocol.MESSAGE_UPDATEDOCUMENT,  new UpdateDocumentMessageTest());
        out.put(DocumentProtocol.MESSAGE_VISITORINFO,     new VisitorInfoMessageTest());
        out.put(DocumentProtocol.REPLY_CREATEVISITOR,     new CreateVisitorReplyTest());
        out.put(DocumentProtocol.REPLY_DESTROYVISITOR,    new DestroyVisitorReplyTest());
        out.put(DocumentProtocol.REPLY_DOCUMENTIGNORED,   new DocumentIgnoredReplyTest());
        out.put(DocumentProtocol.REPLY_DOCUMENTLIST,      new DocumentListReplyTest());
        out.put(DocumentProtocol.REPLY_EMPTYBUCKETS,      new EmptyBucketsReplyTest());
        out.put(DocumentProtocol.REPLY_GETBUCKETLIST,     new GetBucketListReplyTest());
        out.put(DocumentProtocol.REPLY_GETBUCKETSTATE,    new GetBucketStateReplyTest());
        out.put(DocumentProtocol.REPLY_GETDOCUMENT,       new GetDocumentReplyTest());
        out.put(DocumentProtocol.REPLY_MAPVISITOR,        new MapVisitorReplyTest());
        out.put(DocumentProtocol.REPLY_PUTDOCUMENT,       new PutDocumentReplyTest());
        out.put(DocumentProtocol.REPLY_QUERYRESULT,       new QueryResultReplyTest());
        out.put(DocumentProtocol.REPLY_REMOVEDOCUMENT,    new RemoveDocumentReplyTest());
        out.put(DocumentProtocol.REPLY_REMOVELOCATION,    new RemoveLocationReplyTest());
        out.put(DocumentProtocol.REPLY_STATBUCKET,        new StatBucketReplyTest());
        out.put(DocumentProtocol.REPLY_UPDATEDOCUMENT,    new UpdateDocumentReplyTest());
        out.put(DocumentProtocol.REPLY_VISITORINFO,       new VisitorInfoReplyTest());
        out.put(DocumentProtocol.REPLY_WRONGDISTRIBUTION, new WrongDistributionReplyTest());
    }

    @Override
    protected Version version() {
        return new Version(8, 310);
    }

    @Override
    protected boolean shouldTestCoverage() {
        return true;
    }

    private static void forEachLanguage(Consumer<Language> fun) {
        for (var lang : MessagesTestBase.LANGUAGES) {
            fun.accept(lang);
        }
    }

    class GetDocumentMessageTest implements RunnableTest {
        @Override
        public void run() {
            var msg = new GetDocumentMessage(new DocumentId("id:ns:testdoc::"), "foo bar");
            serialize("GetDocumentMessage", msg);
            forEachLanguage((lang) -> {
                var msg2 = (GetDocumentMessage)deserialize("GetDocumentMessage", DocumentProtocol.MESSAGE_GETDOCUMENT, lang);
                assertEquals("id:ns:testdoc::", msg2.getDocumentId().toString());
                assertEquals("foo bar", msg2.getFieldSet());
            });
        }
    }

    class GetDocumentReplyTest implements RunnableTest {
        @Override
        public void run() {
            testDocumentReturnedCase();
            testEmptyCase();
        }

        void testDocumentReturnedCase() {
            var reply = new GetDocumentReply(new Document(protocol.getDocumentTypeManager().getDocumentType("testdoc"), "id:ns:testdoc::"));
            reply.setLastModified(1234567L);
            serialize("GetDocumentReply", reply);
            forEachLanguage((lang) -> {
                var reply2 = (GetDocumentReply)deserialize("GetDocumentReply", DocumentProtocol.REPLY_GETDOCUMENT, lang);
                assertEquals(1234567L, reply2.getLastModified());
                var doc = reply2.getDocument();
                assertNotNull(doc);
                assertEquals("testdoc", doc.getDataType().getName());
                assertEquals("id:ns:testdoc::", doc.getId().toString());
                assertNotNull(doc.getLastModified());
                assertEquals(1234567L, doc.getLastModified().longValue());
            });
        }

        void testEmptyCase() {
            var reply = new GetDocumentReply(null);
            serialize("GetDocumentReply-empty", reply);
            forEachLanguage((lang) -> {
                var reply2 = (GetDocumentReply)deserialize("GetDocumentReply-empty", DocumentProtocol.REPLY_GETDOCUMENT, lang);
                assertEquals(0L, reply2.getLastModified());
                assertNull(reply2.getDocument());
            });
        }
    }

    private static final String CONDITION_STRING = "There's just one condition";

    class PutDocumentMessageTest implements RunnableTest {

        void verifyCreateIfNonExistentFlag() {
            var msg = new PutDocumentMessage(new DocumentPut(new Document(protocol.getDocumentTypeManager().getDocumentType("testdoc"), "id:ns:testdoc::")));
            msg.setCreateIfNonExistent(true);
            serialize("PutDocumentMessage-create", msg);
            forEachLanguage((lang) -> {
                var decoded = (PutDocumentMessage)deserialize("PutDocumentMessage-create", DocumentProtocol.MESSAGE_PUTDOCUMENT, lang);
                assertTrue(decoded.getCreateIfNonExistent());
                assertEquals(decoded.getDocumentPut(), decoded.getDocumentPut());
            });
        }

        @Override
        public void run() {
            var msg = new PutDocumentMessage(new DocumentPut(new Document(protocol.getDocumentTypeManager().getDocumentType("testdoc"), "id:ns:testdoc::")));
            msg.setTimestamp(666);
            msg.setCondition(new TestAndSetCondition(CONDITION_STRING));
            serialize("PutDocumentMessage", msg);

            forEachLanguage((lang) -> {
                var deserializedMsg = (PutDocumentMessage)deserialize("PutDocumentMessage", DocumentProtocol.MESSAGE_PUTDOCUMENT, lang);
                var deserializedDoc = deserializedMsg.getDocumentPut().getDocument();
                assertNotNull(deserializedDoc);
                assertEquals(msg.getDocumentPut().getDocument().getDataType().getName(), deserializedDoc.getDataType().getName());
                assertEquals(msg.getDocumentPut().getDocument().getId().toString(), deserializedDoc.getId().toString());
                assertEquals(msg.getTimestamp(), deserializedMsg.getTimestamp());
                assertEquals(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
                assertFalse(deserializedMsg.getCreateIfNonExistent());
            });
            verifyCreateIfNonExistentFlag();
        }
    }

    class PutDocumentReplyTest implements RunnableTest {
        @Override
        public void run() {
            var reply = new WriteDocumentReply(DocumentProtocol.REPLY_PUTDOCUMENT);
            reply.setHighestModificationTimestamp(30);
            serialize("PutDocumentReply", reply);
            forEachLanguage((lang) -> {
                var obj = (WriteDocumentReply)deserialize("PutDocumentReply", DocumentProtocol.REPLY_PUTDOCUMENT, lang);
                assertEquals(30, obj.getHighestModificationTimestamp());
            });
        }
    }

    class UpdateDocumentMessageTest implements RunnableTest {

        UpdateDocumentMessage makeUpdateWithCreateIfMissing(boolean createIfMissing) {
            var docType = protocol.getDocumentTypeManager().getDocumentType("testdoc");
            var update = new DocumentUpdate(docType, new DocumentId("id:ns:testdoc::"));
            update.addFieldPathUpdate(new RemoveFieldPathUpdate(docType, "intfield", "testdoc.intfield > 0"));
            update.setCreateIfNonExistent(createIfMissing);

            var msg = new UpdateDocumentMessage(update);
            msg.setNewTimestamp(777);
            msg.setOldTimestamp(666);
            msg.setCondition(new TestAndSetCondition(CONDITION_STRING));
            return msg;
        }

        void testLegacyCreateIfMissingFlagCanBeDeserializedFromDocumentUpdate() {
            // Legacy binary files were created _prior_ to the createIfMissing flag being
            // written as part of the serialization process.
            forEachLanguage((lang) -> {
                var msg = (UpdateDocumentMessage) deserialize(
                        "UpdateDocumentMessage-legacy-no-create-if-missing",
                        DocumentProtocol.MESSAGE_UPDATEDOCUMENT, lang);
                assertFalse(msg.createIfMissing());

                msg = (UpdateDocumentMessage) deserialize(
                        "UpdateDocumentMessage-legacy-with-create-if-missing",
                        DocumentProtocol.MESSAGE_UPDATEDOCUMENT, lang);
                assertTrue(msg.createIfMissing());
            });
        }

        void checkDeserialization(Language lang, String name, boolean expectedCreate) {
            var msg = (UpdateDocumentMessage) deserialize(name, DocumentProtocol.MESSAGE_UPDATEDOCUMENT, lang);
            assertEquals(expectedCreate, msg.createIfMissing());
        };

        void testCreateIfMissingFlagIsPropagated() {
            serialize("UpdateDocumentMessage-no-create-if-missing",   makeUpdateWithCreateIfMissing(false));
            serialize("UpdateDocumentMessage-with-create-if-missing", makeUpdateWithCreateIfMissing(true));

            forEachLanguage((lang) -> {
                checkDeserialization(lang, "UpdateDocumentMessage-no-create-if-missing",   false);
                checkDeserialization(lang, "UpdateDocumentMessage-with-create-if-missing", true);
            });
        }

        void testAllUpdateFieldsArePropagated() {
            var docType = protocol.getDocumentTypeManager().getDocumentType("testdoc");
            var update = new DocumentUpdate(docType, new DocumentId("id:ns:testdoc::"));
            update.addFieldPathUpdate(new RemoveFieldPathUpdate(docType, "intfield", "testdoc.intfield > 0"));

            var msg = new UpdateDocumentMessage(update);
            msg.setNewTimestamp(777);
            msg.setOldTimestamp(666);
            msg.setCondition(new TestAndSetCondition(CONDITION_STRING));

            serialize("UpdateDocumentMessage", msg);

            forEachLanguage((lang) -> {
                var deserializedMsg = (UpdateDocumentMessage)deserialize("UpdateDocumentMessage", DocumentProtocol.MESSAGE_UPDATEDOCUMENT, lang);
                assertEquals(msg.getDocumentUpdate(), deserializedMsg.getDocumentUpdate());
                assertEquals(msg.getNewTimestamp(), deserializedMsg.getNewTimestamp());
                assertEquals(msg.getOldTimestamp(), deserializedMsg.getOldTimestamp());
                assertEquals(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
            });
        }

        @Override
        public void run() {
            testAllUpdateFieldsArePropagated();
            testLegacyCreateIfMissingFlagCanBeDeserializedFromDocumentUpdate();
            testCreateIfMissingFlagIsPropagated();
        }
    }

    class UpdateDocumentReplyTest implements RunnableTest {
        @Override
        public void run() {
            var reply = new UpdateDocumentReply();
            reply.setHighestModificationTimestamp(30);
            reply.setWasFound(true);
            serialize("UpdateDocumentReply", reply);
            forEachLanguage((lang) -> {
                var obj = (UpdateDocumentReply)deserialize("UpdateDocumentReply", DocumentProtocol.REPLY_UPDATEDOCUMENT, lang);
                assertNotNull(obj);
                assertEquals(30, reply.getHighestModificationTimestamp());
                assertTrue(obj.wasFound());
            });
        }
    }

    class RemoveDocumentMessageTest implements RunnableTest {
        @Override
        public void run() {
            var msg = new RemoveDocumentMessage(new DocumentId("id:ns:testdoc::"));
            msg.setCondition(new TestAndSetCondition(CONDITION_STRING));
            serialize("RemoveDocumentMessage", msg);
            forEachLanguage((lang) -> {
                var deserializedMsg = (RemoveDocumentMessage)deserialize("RemoveDocumentMessage", DocumentProtocol.MESSAGE_REMOVEDOCUMENT, lang);
                assertEquals(msg.getDocumentId().toString(), deserializedMsg.getDocumentId().toString());
                assertEquals(msg.getCondition(), deserializedMsg.getCondition());
            });
        }
    }

    class RemoveDocumentReplyTest implements RunnableTest {
        @Override
        public void run() {
            var reply = new RemoveDocumentReply();
            reply.setHighestModificationTimestamp(30);
            reply.setWasFound(true);
            serialize("RemoveDocumentReply", reply);
            forEachLanguage((lang) -> {
                var obj = (RemoveDocumentReply)deserialize("RemoveDocumentReply", DocumentProtocol.REPLY_REMOVEDOCUMENT, lang);
                assertNotNull(obj);
                assertEquals(30, obj.getHighestModificationTimestamp());
                assertTrue(obj.wasFound());
            });
        }
    }

    class RemoveLocationMessageTest implements RunnableTest {
        @Override
        public void run() {
            var msg = new RemoveLocationMessage("id.group == \"mygroup\"", "bjarne");
            serialize("RemoveLocationMessage", msg);
            forEachLanguage((lang) -> {
                var msg2 = (RemoveLocationMessage)deserialize("RemoveLocationMessage", DocumentProtocol.MESSAGE_REMOVELOCATION, lang);
                assertEquals("id.group == \"mygroup\"", msg2.getDocumentSelection());
                assertEquals("bjarne", msg2.getBucketSpace());
            });
        }
    }

    class RemoveLocationReplyTest implements RunnableTest {
        @Override
        public void run() {
            testDocumentReply("RemoveLocationReply", DocumentProtocol.REPLY_REMOVELOCATION);
        }
    }

    class CreateVisitorMessageTest implements RunnableTest {
        private static final String BUCKET_SPACE = "bjarne";

        @Override
        public void run() {
            var msg = new CreateVisitorMessage("SomeLibrary", "myvisitor", "newyork", "london");
            msg.setDocumentSelection("true and false or true");
            msg.getParameters().put("myvar", Utf8.toBytes("somevalue"));
            msg.getParameters().put("anothervar", Utf8.toBytes("34"));
            msg.getBuckets().add(new BucketId(16, 1234));
            msg.setVisitRemoves(true);
            msg.setVisitInconsistentBuckets(true);
            msg.setFieldSet("foo bar");
            msg.setMaxBucketsPerVisitor(2);
            msg.setBucketSpace(BUCKET_SPACE);
            msg.setMaxPendingReplyCount(12);

            serialize("CreateVisitorMessage", msg);

            forEachLanguage((lang) -> {
                var msg2 = (CreateVisitorMessage)deserialize("CreateVisitorMessage", DocumentProtocol.MESSAGE_CREATEVISITOR, lang);
                assertEquals("SomeLibrary", msg2.getLibraryName());
                assertEquals("myvisitor", msg2.getInstanceId());
                assertEquals("newyork", msg2.getControlDestination());
                assertEquals("london", msg2.getDataDestination());
                assertEquals("true and false or true", msg2.getDocumentSelection());
                assertEquals(12, msg2.getMaxPendingReplyCount());
                assertTrue(msg2.getVisitRemoves());
                assertEquals("foo bar", msg2.getFieldSet());
                assertTrue(msg2.getVisitInconsistentBuckets());
                assertEquals(1, msg2.getBuckets().size());
                assertEquals(new BucketId(16, 1234), msg2.getBuckets().iterator().next());
                assertEquals(2, msg2.getParameters().size());
                assertEquals("somevalue", Utf8.toString(msg2.getParameters().get("myvar")));
                assertEquals("34", Utf8.toString(msg2.getParameters().get("anothervar")));
                assertEquals(2, msg2.getMaxBucketsPerVisitor());
                assertEquals(BUCKET_SPACE, msg2.getBucketSpace());
            });
        }
    }

    class CreateVisitorReplyTest implements RunnableTest {
        @Override
        public void run() {
            var reply = new CreateVisitorReply(DocumentProtocol.REPLY_CREATEVISITOR);
            reply.setLastBucket(new BucketId(16, 123));
            reply.getVisitorStatistics().setBucketsVisited(3);
            reply.getVisitorStatistics().setDocumentsVisited(1000);
            reply.getVisitorStatistics().setBytesVisited(1024000);
            reply.getVisitorStatistics().setDocumentsReturned(123);
            reply.getVisitorStatistics().setBytesReturned(512000);

            serialize("CreateVisitorReply", reply);

            forEachLanguage((lang) -> {
                var reply2 = (CreateVisitorReply)deserialize("CreateVisitorReply", DocumentProtocol.REPLY_CREATEVISITOR, lang);
                assertNotNull(reply2);
                assertEquals(new BucketId(16, 123), reply2.getLastBucket());
                assertEquals(3, reply2.getVisitorStatistics().getBucketsVisited());
                assertEquals(1000, reply2.getVisitorStatistics().getDocumentsVisited());
                assertEquals(1024000, reply2.getVisitorStatistics().getBytesVisited());
                assertEquals(123, reply2.getVisitorStatistics().getDocumentsReturned());
                assertEquals(512000, reply2.getVisitorStatistics().getBytesReturned());
            });
        }
    }

    class DestroyVisitorMessageTest implements RunnableTest {
        @Override
        public void run() {
            var msg = new DestroyVisitorMessage("myvisitor");
            serialize("DestroyVisitorMessage", msg);
            forEachLanguage((lang) -> {
                var msg2 = (DestroyVisitorMessage)deserialize("DestroyVisitorMessage", DocumentProtocol.MESSAGE_DESTROYVISITOR, lang);
                assertEquals("myvisitor", msg2.getInstanceId());
            });
        }
    }

    class DestroyVisitorReplyTest implements RunnableTest {
        @Override
        public void run() {
            testVisitorReply("DestroyVisitorReply", DocumentProtocol.REPLY_DESTROYVISITOR);
        }
    }

    class MapVisitorMessageTest implements RunnableTest {
        @Override
        public void run() {
            var msg = new MapVisitorMessage();
            msg.getData().put("foo", "3");
            msg.getData().put("bar", "5");
            serialize("MapVisitorMessage", msg);
            forEachLanguage((lang) -> {
                var msg2 = (MapVisitorMessage) deserialize("MapVisitorMessage", DocumentProtocol.MESSAGE_MAPVISITOR, lang);
                assertEquals(2, msg2.getData().size());
                assertEquals("3", msg2.getData().get("foo"));
                assertEquals("5", msg2.getData().get("bar"));
            });
        }
    }

    class MapVisitorReplyTest implements RunnableTest {
        @Override
        public void run() {
            testVisitorReply("MapVisitorReply", DocumentProtocol.REPLY_MAPVISITOR);
        }
    }

    class QueryResultMessageTest implements RunnableTest {
        @Override
        public void run() throws Exception {
            test_result_with_match_features();

            Routable routable = deserialize("QueryResultMessage-1", DocumentProtocol.MESSAGE_QUERYRESULT, Language.CPP);
            assertTrue(routable instanceof QueryResultMessage);

            QueryResultMessage msg = (QueryResultMessage)routable;
            assertEquals(0, msg.getResult().getHitCount());

            routable = deserialize("QueryResultMessage-2", DocumentProtocol.MESSAGE_QUERYRESULT, Language.CPP);
            assertTrue(routable instanceof QueryResultMessage);

            msg = (QueryResultMessage)routable;
            assertEquals(2, msg.getResult().getHitCount());
            com.yahoo.vdslib.SearchResult.Hit h = msg.getResult().getHit(0);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());
            assertFalse(h.getMatchFeatures().isPresent());
            h = msg.getResult().getHit(1);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());
            assertFalse(h.getMatchFeatures().isPresent());

            routable = deserialize("QueryResultMessage-3", DocumentProtocol.MESSAGE_QUERYRESULT, Language.CPP);
            assertTrue(routable instanceof QueryResultMessage);

            msg = (QueryResultMessage)routable;
            assertEquals(2, msg.getResult().getHitCount());
            h = msg.getResult().getHit(0);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());
            assertFalse(h.getMatchFeatures().isPresent());
            h = msg.getResult().getHit(1);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());
            assertFalse(h.getMatchFeatures().isPresent());

            routable = deserialize("QueryResultMessage-4", DocumentProtocol.MESSAGE_QUERYRESULT, Language.CPP);
            assertTrue(routable instanceof QueryResultMessage);

            msg = (QueryResultMessage)routable;
            assertEquals(3, msg.getResult().getHitCount());
            h = msg.getResult().getHit(0);
            assertTrue(h instanceof SearchResult.HitWithSortBlob);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());
            byte[] b = ((SearchResult.HitWithSortBlob)h).getSortBlob();
            assertEqualsData(new byte[] { 's', 'o', 'r', 't', 'd', 'a', 't', 'a', '2' }, b);

            h = msg.getResult().getHit(1);
            assertTrue(h instanceof SearchResult.HitWithSortBlob);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());
            b = ((SearchResult.HitWithSortBlob)h).getSortBlob();
            assertEqualsData(new byte[] { 's', 'o', 'r', 't', 'd', 'a', 't', 'a', '1' }, b);

            h = msg.getResult().getHit(2);
            assertTrue(h instanceof SearchResult.HitWithSortBlob);
            assertEquals(90.0, h.getRank(), 1E-6);
            assertEquals("doc18", h.getDocId());
            b = ((SearchResult.HitWithSortBlob)h).getSortBlob();
            assertEqualsData(new byte[] { 's', 'o', 'r', 't', 'd', 'a', 't', 'a', '3' }, b);
        }

        void assertEqualsData(byte[] exp, byte[] act) {
            assertEquals(exp.length, act.length);
            for (int i = 0; i < exp.length; ++i) {
                assertEquals(exp[i], act[i]);
            }
        }

        void test_result_with_match_features() {
            Routable routable = deserialize("QueryResultMessage-6", DocumentProtocol.MESSAGE_QUERYRESULT, Language.CPP);
            assertTrue(routable instanceof QueryResultMessage);

            var msg = (QueryResultMessage)routable;
            assertEquals(2, msg.getResult().getHitCount());

            var h = msg.getResult().getHit(0);
            assertTrue(h instanceof SearchResult.Hit);
            assertEquals(7.0, h.getRank(), 1E-6);
            assertEquals("doc2", h.getDocId());
            assertTrue(h.getMatchFeatures().isPresent());
            var mf = h.getMatchFeatures().get();
            assertEquals(12.0, mf.field("foo").asDouble(), 1E-6);
            assertEqualsData(new byte[] { 'T', 'h', 'e', 'r', 'e' }, mf.field("bar").asData());

            h = msg.getResult().getHit(1);
            assertTrue(h instanceof SearchResult.Hit);
            assertEquals(5.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());
            assertTrue(h.getMatchFeatures().isPresent());
            mf = h.getMatchFeatures().get();
            assertEquals(1.0, mf.field("foo").asDouble(), 1E-6);
            assertEqualsData(new byte[] { 'H', 'i' }, mf.field("bar").asData());
        }
    }

    class QueryResultReplyTest implements RunnableTest {
        @Override
        public void run() {
            testVisitorReply("QueryResultReply", DocumentProtocol.REPLY_QUERYRESULT);
        }
    }

    class VisitorInfoMessageTest implements RunnableTest {
        @Override
        public void run() {
            var msg = new VisitorInfoMessage();
            msg.getFinishedBuckets().add(new BucketId(16, 1));
            msg.getFinishedBuckets().add(new BucketId(16, 2));
            msg.getFinishedBuckets().add(new BucketId(16, 4));
            msg.setErrorMessage("error message: \u00e6\u00c6\u00f8\u00d8\u00e5\u00c5\u00f6\u00d6");

            serialize("VisitorInfoMessage", msg);

            forEachLanguage((lang) -> {
                var msg2 = (VisitorInfoMessage)deserialize("VisitorInfoMessage", DocumentProtocol.MESSAGE_VISITORINFO, lang);
                assertTrue(msg2.getFinishedBuckets().contains(new BucketId(16, 1)));
                assertTrue(msg2.getFinishedBuckets().contains(new BucketId(16, 2)));
                assertTrue(msg2.getFinishedBuckets().contains(new BucketId(16, 4)));
                assertEquals("error message: \u00e6\u00c6\u00f8\u00d8\u00e5\u00c5\u00f6\u00d6", msg2.getErrorMessage());
            });
        }
    }

    class VisitorInfoReplyTest implements RunnableTest {
        @Override
        public void run() {
            testVisitorReply("VisitorInfoReply", DocumentProtocol.REPLY_VISITORINFO);
        }
    }

    class DocumentListMessageTest implements RunnableTest {
        @Override
        public void run() {
            var msg = new DocumentListMessage();
            msg.setBucketId(new BucketId(17, 1234));
            var doc = new Document(protocol.getDocumentTypeManager().getDocumentType("testdoc"), "id:scheme:testdoc:n=1234:1");
            msg.getDocuments().add(new DocumentListEntry(doc, 1234, true));

            serialize("DocumentListMessage", msg);
            forEachLanguage((lang) -> {
                var msg2 = (DocumentListMessage) deserialize("DocumentListMessage", DocumentProtocol.MESSAGE_DOCUMENTLIST, lang);
                assertEquals(new BucketId(17, 1234), msg2.getBucketId());
                assertEquals(1, msg2.getDocuments().size());
                var entry = msg2.getDocuments().get(0);
                assertEquals("id:scheme:testdoc:n=1234:1", entry.getDocument().getId().toString());
                assertEquals(1234, entry.getTimestamp());
                assertTrue(entry.isRemoveEntry());
            });
        }
    }

    class DocumentListReplyTest implements RunnableTest {
        @Override
        public void run() {
            testVisitorReply("DocumentListReply", DocumentProtocol.REPLY_DOCUMENTLIST);
        }
    }

    class EmptyBucketsMessageTest implements RunnableTest {
        @Override
        public void run() {
            var bids = new ArrayList<BucketId>();
            for (int i = 0; i < 13; ++i) {
                bids.add(new BucketId(16, i));
            }
            var ebm = new EmptyBucketsMessage(bids);
            serialize("EmptyBucketsMessage", ebm);
            forEachLanguage((lang) -> {
                var ebm2 = (EmptyBucketsMessage)deserialize("EmptyBucketsMessage", DocumentProtocol.MESSAGE_EMPTYBUCKETS, lang);
                assertEquals(13, ebm2.getBucketIds().size());
                for (int i = 0; i < 13; ++i) {
                    assertEquals(new BucketId(16, i), ebm2.getBucketIds().get(i));
                }
            });
        }
    }

    class EmptyBucketsReplyTest implements RunnableTest {
        @Override
        public void run() {
            testVisitorReply("EmptyBucketsReply", DocumentProtocol.REPLY_EMPTYBUCKETS);
        }
    }

    class GetBucketListMessageTest implements RunnableTest {
        private static final String BUCKET_SPACE = "beartato";

        @Override
        public void run() {
            var msg = new GetBucketListMessage(new BucketId(16, 123));
            msg.setBucketSpace(BUCKET_SPACE);
            serialize("GetBucketListMessage", msg);
            forEachLanguage((lang) -> {
                var msg2 = (GetBucketListMessage)deserialize("GetBucketListMessage", DocumentProtocol.MESSAGE_GETBUCKETLIST, lang);
                assertEquals(new BucketId(16, 123), msg2.getBucketId());
                assertEquals(BUCKET_SPACE, msg2.getBucketSpace());
            });
        }
    }

    class GetBucketListReplyTest implements RunnableTest {
        @Override
        public void run() {
            var reply = new GetBucketListReply();
            reply.getBuckets().add(new GetBucketListReply.BucketInfo(new BucketId(16, 123), "foo"));
            reply.getBuckets().add(new GetBucketListReply.BucketInfo(new BucketId(17, 1123), "bar"));
            reply.getBuckets().add(new GetBucketListReply.BucketInfo(new BucketId(18, 11123), "zoink"));

            serialize("GetBucketListReply", reply);

            forEachLanguage((lang) -> {
                var reply2 = (GetBucketListReply)deserialize("GetBucketListReply", DocumentProtocol.REPLY_GETBUCKETLIST, lang);
                assertEquals(3, reply2.getBuckets().size());
                assertEquals(reply2.getBuckets().get(0), new GetBucketListReply.BucketInfo(new BucketId(16, 123), "foo"));
                assertEquals(reply2.getBuckets().get(1), new GetBucketListReply.BucketInfo(new BucketId(17, 1123), "bar"));
                assertEquals(reply2.getBuckets().get(2), new GetBucketListReply.BucketInfo(new BucketId(18, 11123), "zoink"));
            });
        }
    }

    class GetBucketStateMessageTest implements RunnableTest {
        @Override
        public void run() {
            var msg = new GetBucketStateMessage(new BucketId(16, 666));
            serialize("GetBucketStateMessage", msg);

            forEachLanguage((lang) -> {
                var msg2 = (GetBucketStateMessage)deserialize("GetBucketStateMessage", DocumentProtocol.MESSAGE_GETBUCKETSTATE, lang);
                assertEquals(16, msg2.getBucketId().getUsedBits());
                assertEquals(4611686018427388570L, msg2.getBucketId().getId());
            });
        }
    }

    class GetBucketStateReplyTest implements RunnableTest {
        @Override
        public void run() {
            var foo = new GlobalId(IdString.createIdString("id:ns:testdoc::foo"));
            var bar = new GlobalId(IdString.createIdString("id:ns:testdoc::bar"));
            var baz = new DocumentId("id:ns:testdoc::baz");

            var reply = new GetBucketStateReply();
            var state = new ArrayList<DocumentState>(3);
            state.add(new DocumentState(foo, 777, false));
            state.add(new DocumentState(bar, 888, true));
            state.add(new DocumentState(baz, 999, false));
            reply.setBucketState(state);

            serialize("GetBucketStateReply", reply);

            forEachLanguage((lang) -> {
                var reply2 = (GetBucketStateReply)deserialize("GetBucketStateReply", DocumentProtocol.REPLY_GETBUCKETSTATE, lang);
                assertEquals(3, reply2.getBucketState().size());

                assertEquals(777, reply2.getBucketState().get(0).getTimestamp());
                assertEquals(foo, reply2.getBucketState().get(0).getGid());
                assertFalse(reply2.getBucketState().get(0).hasDocId());
                assertFalse(reply2.getBucketState().get(0).isRemoveEntry());

                assertEquals(888, reply2.getBucketState().get(1).getTimestamp());
                assertEquals(bar, reply2.getBucketState().get(1).getGid());
                assertFalse(reply2.getBucketState().get(1).hasDocId());
                assertTrue(reply2.getBucketState().get(1).isRemoveEntry());

                assertEquals(999, reply2.getBucketState().get(2).getTimestamp());
                assertTrue(reply2.getBucketState().get(2).hasDocId());
                assertEquals(new GlobalId(baz.getGlobalId()), reply2.getBucketState().get(2).getGid());
                assertEquals(baz, reply2.getBucketState().get(2).getDocId());
                assertFalse(reply2.getBucketState().get(2).isRemoveEntry());
            });
        }
    }

    class StatBucketMessageTest implements RunnableTest {
        private static final String BUCKET_SPACE = "andrei";

        @Override
        public void run() {
            var msg = new StatBucketMessage(new BucketId(16, 123), "id.user=123");
            msg.setBucketSpace(BUCKET_SPACE);
            serialize("StatBucketMessage", msg);
            forEachLanguage((lang) -> {
                var msg2 = (StatBucketMessage)deserialize("StatBucketMessage", DocumentProtocol.MESSAGE_STATBUCKET, lang);
                assertEquals(new BucketId(16, 123), msg2.getBucketId());
                assertEquals("id.user=123", msg2.getDocumentSelection());
                assertEquals(BUCKET_SPACE, msg2.getBucketSpace());
            });
        }
    }

    class StatBucketReplyTest implements RunnableTest {
        @Override
        public void run() {
            var msg = new StatBucketReply();
            msg.setResults("These are the votes of the Norwegian jury");
            serialize("StatBucketReply", msg);
            forEachLanguage((lang) -> {
                var msg2 = (StatBucketReply)deserialize("StatBucketReply", DocumentProtocol.REPLY_STATBUCKET, lang);
                assertEquals("These are the votes of the Norwegian jury", msg2.getResults());
            });
        }
    }

    class WrongDistributionReplyTest implements RunnableTest {
        @Override
        public void run() {
            var reply = new WrongDistributionReply("distributor:3 storage:2");
            serialize("WrongDistributionReply", reply);
            forEachLanguage((lang) -> {
                var reply2 = (WrongDistributionReply)deserialize("WrongDistributionReply", DocumentProtocol.REPLY_WRONGDISTRIBUTION, lang);
                assertEquals("distributor:3 storage:2", reply2.getSystemState());
            });
        }
    }

    class DocumentIgnoredReplyTest implements RunnableTest {
        @Override
        public void run() {
            var reply = new DocumentIgnoredReply();
            serialize("DocumentIgnoredReply", reply);
            forEachLanguage((lang) -> {
                var reply2 = (DocumentIgnoredReply)deserialize("DocumentIgnoredReply", DocumentProtocol.REPLY_DOCUMENTIGNORED, lang);
                assertNotNull(reply2);
            });
        }
    }

    private void testDocumentReply(String filename, int type) {
        var reply = new DocumentReply(type);
        serialize(filename, reply);

        forEachLanguage((lang) -> {
            var reply2 = (DocumentReply)deserialize(filename, type, lang);
            assertNotNull(reply2);
        });
    }

    private void testVisitorReply(String filename, int type) {
        VisitorReply reply = new VisitorReply(type);
        serialize(filename, reply);

        forEachLanguage((lang) -> {
            var reply2 = (VisitorReply)deserialize(filename, type, lang);
            assertNotNull(reply2);
        });
    }

}
