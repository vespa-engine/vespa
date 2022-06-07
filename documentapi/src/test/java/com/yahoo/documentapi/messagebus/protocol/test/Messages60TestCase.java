// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.component.Version;
import com.yahoo.document.BucketId;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.GlobalId;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.idstring.IdString;
import com.yahoo.documentapi.messagebus.protocol.CreateVisitorMessage;
import com.yahoo.documentapi.messagebus.protocol.CreateVisitorReply;
import com.yahoo.documentapi.messagebus.protocol.DestroyVisitorMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentIgnoredReply;
import com.yahoo.documentapi.messagebus.protocol.DocumentListMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.DocumentReply;
import com.yahoo.documentapi.messagebus.protocol.DocumentState;
import com.yahoo.documentapi.messagebus.protocol.DocumentSummaryMessage;
import com.yahoo.documentapi.messagebus.protocol.EmptyBucketsMessage;
import com.yahoo.documentapi.messagebus.protocol.GetBucketListMessage;
import com.yahoo.documentapi.messagebus.protocol.GetBucketListReply;
import com.yahoo.documentapi.messagebus.protocol.GetBucketStateMessage;
import com.yahoo.documentapi.messagebus.protocol.GetBucketStateReply;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentReply;
import com.yahoo.documentapi.messagebus.protocol.MapVisitorMessage;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.QueryResultMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentReply;
import com.yahoo.documentapi.messagebus.protocol.RemoveLocationMessage;
import com.yahoo.documentapi.messagebus.protocol.SearchResultMessage;
import com.yahoo.documentapi.messagebus.protocol.StatBucketMessage;
import com.yahoo.documentapi.messagebus.protocol.StatBucketReply;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentReply;
import com.yahoo.documentapi.messagebus.protocol.VisitorInfoMessage;
import com.yahoo.documentapi.messagebus.protocol.VisitorReply;
import com.yahoo.documentapi.messagebus.protocol.WriteDocumentReply;
import com.yahoo.documentapi.messagebus.protocol.WrongDistributionReply;
import com.yahoo.messagebus.Routable;
import com.yahoo.text.Utf8;
import com.yahoo.vdslib.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 * @author Vegard Sjonfjell
 */

public class Messages60TestCase extends MessagesTestBase {
    @Override
    protected void registerTests(Map<Integer, RunnableTest> out) {
        // This list MUST mirror the list of routable factories from the DocumentProtocol constructor that support
        // version 6. When adding tests to this list, please KEEP THEM ORDERED alphabetically like they are now.
        out.put(DocumentProtocol.MESSAGE_CREATEVISITOR, new testCreateVisitorMessage());
        out.put(DocumentProtocol.MESSAGE_DESTROYVISITOR, new testDestroyVisitorMessage());
        out.put(DocumentProtocol.MESSAGE_DOCUMENTLIST, new testDocumentListMessage());
        out.put(DocumentProtocol.MESSAGE_DOCUMENTSUMMARY, new testDocumentSummaryMessage());
        out.put(DocumentProtocol.MESSAGE_EMPTYBUCKETS, new testEmptyBucketsMessage());
        out.put(DocumentProtocol.MESSAGE_GETBUCKETLIST, new testGetBucketListMessage());
        out.put(DocumentProtocol.MESSAGE_GETBUCKETSTATE, new testGetBucketStateMessage());
        out.put(DocumentProtocol.MESSAGE_GETDOCUMENT, new testGetDocumentMessage());
        out.put(DocumentProtocol.MESSAGE_MAPVISITOR, new testMapVisitorMessage());
        out.put(DocumentProtocol.MESSAGE_PUTDOCUMENT, new testPutDocumentMessage());
        out.put(DocumentProtocol.MESSAGE_QUERYRESULT, new testQueryResultMessage());
        out.put(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, new testRemoveDocumentMessage());
        out.put(DocumentProtocol.MESSAGE_REMOVELOCATION, new testRemoveLocationMessage());
        out.put(DocumentProtocol.MESSAGE_SEARCHRESULT, new testSearchResultMessage());
        out.put(DocumentProtocol.MESSAGE_STATBUCKET, new testStatBucketMessage());
        out.put(DocumentProtocol.MESSAGE_UPDATEDOCUMENT, new testUpdateDocumentMessage());
        out.put(DocumentProtocol.MESSAGE_VISITORINFO, new testVisitorInfoMessage());
        out.put(DocumentProtocol.REPLY_CREATEVISITOR, new testCreateVisitorReply());
        out.put(DocumentProtocol.REPLY_DESTROYVISITOR, new testDestroyVisitorReply());
        out.put(DocumentProtocol.REPLY_DOCUMENTIGNORED, new testDocumentIgnoredReply());
        out.put(DocumentProtocol.REPLY_DOCUMENTLIST, new testDocumentListReply());
        out.put(DocumentProtocol.REPLY_DOCUMENTSUMMARY, new testDocumentSummaryReply());
        out.put(DocumentProtocol.REPLY_EMPTYBUCKETS, new testEmptyBucketsReply());
        out.put(DocumentProtocol.REPLY_GETBUCKETLIST, new testGetBucketListReply());
        out.put(DocumentProtocol.REPLY_GETBUCKETSTATE, new testGetBucketStateReply());
        out.put(DocumentProtocol.REPLY_GETDOCUMENT, new testGetDocumentReply());
        out.put(DocumentProtocol.REPLY_MAPVISITOR, new testMapVisitorReply());
        out.put(DocumentProtocol.REPLY_PUTDOCUMENT, new testPutDocumentReply());
        out.put(DocumentProtocol.REPLY_QUERYRESULT, new testQueryResultReply());
        out.put(DocumentProtocol.REPLY_REMOVEDOCUMENT, new testRemoveDocumentReply());
        out.put(DocumentProtocol.REPLY_REMOVELOCATION, new testRemoveLocationReply());
        out.put(DocumentProtocol.REPLY_SEARCHRESULT, new testSearchResultReply());
        out.put(DocumentProtocol.REPLY_STATBUCKET, new testStatBucketReply());
        out.put(DocumentProtocol.REPLY_UPDATEDOCUMENT, new testUpdateDocumentReply());
        out.put(DocumentProtocol.REPLY_VISITORINFO, new testVisitorInfoReply());
        out.put(DocumentProtocol.REPLY_WRONGDISTRIBUTION, new testWrongDistributionReply());
    }

    @Override
    protected Version version() {
        return new Version(6, 221, 0);
    }

    @Override
    protected boolean shouldTestCoverage() {
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Tests
    //
    ////////////////////////////////////////////////////////////////////////////////

    protected static int BASE_MESSAGE_LENGTH = 5;

    public class testRemoveLocationMessage implements RunnableTest {

        @Override
        public void run() {
            {
                RemoveLocationMessage msg = new RemoveLocationMessage("id.group == \"mygroup\"");
                assertEquals(BASE_MESSAGE_LENGTH + 29, serialize("RemoveLocationMessage", msg));

                for (Language lang : LANGUAGES) {
                    msg = (RemoveLocationMessage)deserialize("RemoveLocationMessage", DocumentProtocol.MESSAGE_REMOVELOCATION, lang);
                    assertEquals("id.group == \"mygroup\"", msg.getDocumentSelection());
                }
            }
        }
    }

    public class testGetBucketListMessage implements RunnableTest {

        private static final String BUCKET_SPACE = "beartato";

        @Override
        public void run() {
            GetBucketListMessage msg = new GetBucketListMessage(new BucketId(16, 123));
            msg.setBucketSpace(BUCKET_SPACE);
            assertEquals(BASE_MESSAGE_LENGTH + 12 + serializedLength(BUCKET_SPACE), serialize("GetBucketListMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (GetBucketListMessage)deserialize("GetBucketListMessage", DocumentProtocol.MESSAGE_GETBUCKETLIST, lang);
                assertEquals(new BucketId(16, 123), msg.getBucketId());
                assertEquals(BUCKET_SPACE, msg.getBucketSpace());
            }
        }
    }

    public class testStatBucketMessage implements RunnableTest {

        private static final String BUCKET_SPACE = "andrei";

        @Override
        public void run() {
            StatBucketMessage msg = new StatBucketMessage(new BucketId(16, 123), "id.user=123");
            msg.setBucketSpace(BUCKET_SPACE);
            assertEquals(BASE_MESSAGE_LENGTH + 27 + serializedLength(BUCKET_SPACE), serialize("StatBucketMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (StatBucketMessage)deserialize("StatBucketMessage", DocumentProtocol.MESSAGE_STATBUCKET, lang);
                assertEquals(new BucketId(16, 123), msg.getBucketId());
                assertEquals("id.user=123", msg.getDocumentSelection());
                assertEquals(BUCKET_SPACE, msg.getBucketSpace());
            }
        }
    }

    public class testGetBucketStateMessage implements RunnableTest {

        @Override
        public void run() {
            GetBucketStateMessage msg = new GetBucketStateMessage(new BucketId(16, 666));
            assertEquals(BASE_MESSAGE_LENGTH + 12, serialize("GetBucketStateMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (GetBucketStateMessage)deserialize("GetBucketStateMessage", DocumentProtocol.MESSAGE_GETBUCKETSTATE, lang);
                assertEquals(16, msg.getBucketId().getUsedBits());
                assertEquals(4611686018427388570l, msg.getBucketId().getId());
            }
        }
    }

    public class testCreateVisitorMessage implements RunnableTest {

        private static final String BUCKET_SPACE = "bjarne";

        @Override
        public void run() {
            CreateVisitorMessage msg = new CreateVisitorMessage("SomeLibrary", "myvisitor", "newyork", "london");
            msg.setDocumentSelection("true and false or true");
            msg.getParameters().put("myvar", Utf8.toBytes("somevalue"));
            msg.getParameters().put("anothervar", Utf8.toBytes("34"));
            msg.getBuckets().add(new BucketId(16, 1234));
            msg.setVisitRemoves(true);
            msg.setFieldSet("foo bar");
            msg.setMaxBucketsPerVisitor(2);
            msg.setBucketSpace(BUCKET_SPACE);
            assertEquals(BASE_MESSAGE_LENGTH + 178 + serializedLength(BUCKET_SPACE), serialize("CreateVisitorMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (CreateVisitorMessage)deserialize("CreateVisitorMessage", DocumentProtocol.MESSAGE_CREATEVISITOR, lang);
                assertEquals("SomeLibrary", msg.getLibraryName());
                assertEquals("myvisitor", msg.getInstanceId());
                assertEquals("newyork", msg.getControlDestination());
                assertEquals("london", msg.getDataDestination());
                assertEquals("true and false or true", msg.getDocumentSelection());
                assertEquals(8, msg.getMaxPendingReplyCount());
                assertEquals(true, msg.getVisitRemoves());
                assertEquals("foo bar", msg.getFieldSet());
                assertEquals(false, msg.getVisitInconsistentBuckets());
                assertEquals(1, msg.getBuckets().size());
                assertEquals(new BucketId(16, 1234), msg.getBuckets().iterator().next());
                assertEquals("somevalue", Utf8.toString(msg.getParameters().get("myvar")));
                assertEquals("34", Utf8.toString(msg.getParameters().get("anothervar")));
                assertEquals(2, msg.getMaxBucketsPerVisitor());
                assertEquals(BUCKET_SPACE, msg.getBucketSpace());
            }

            msg.getBuckets().clear();

            assertEquals("CreateVisitorMessage(" +
                            "No buckets, " +
                            "selection 'true and false or true', " +
                            "bucket space 'bjarne', " +
                            "library SomeLibrary, including removes, " +
                            "get fields: foo bar" +
                            ")",
                    msg.toString());

            msg.getBuckets().add(new BucketId(16, 1234));

            assertEquals("CreateVisitorMessage(" +
                            "Bucket BucketId(0x40000000000004d2), " +
                            "selection 'true and false or true', " +
                            "bucket space 'bjarne', " +
                            "library SomeLibrary, including removes, " +
                            "get fields: foo bar" +
                            ")",
                    msg.toString());

            msg.getBuckets().add(new BucketId(16, 1235));
            msg.getBuckets().add(new BucketId(16, 1236));
            msg.getBuckets().add(new BucketId(16, 1237));
            msg.getBuckets().add(new BucketId(16, 1238));
            msg.setFromTimestamp(10001);
            msg.setToTimestamp(20002);
            msg.setVisitInconsistentBuckets(true);
            assertEquals("CreateVisitorMessage(" +
                            "5 buckets: BucketId(0x40000000000004d2) BucketId(0x40000000000004d3) BucketId(0x40000000000004d4) ..., " +
                            "time 10001-20002, " +
                            "selection 'true and false or true', " +
                            "bucket space 'bjarne', " +
                            "library SomeLibrary, including removes, " +
                            "get fields: foo bar, " +
                            "visit inconsistent buckets" +
                            ")",
                    msg.toString());
        }
    }

    public class testCreateVisitorReply implements RunnableTest {

        @Override
        public void run() {
            CreateVisitorReply reply = new CreateVisitorReply(DocumentProtocol.REPLY_CREATEVISITOR);
            reply.setLastBucket(new BucketId(16, 123));
            reply.getVisitorStatistics().setBucketsVisited(3);
            reply.getVisitorStatistics().setDocumentsVisited(1000);
            reply.getVisitorStatistics().setBytesVisited(1024000);
            reply.getVisitorStatistics().setDocumentsReturned(123);
            reply.getVisitorStatistics().setBytesReturned(512000);

            assertEquals(65, serialize("CreateVisitorReply", reply));

            for (Language lang : LANGUAGES) {
                reply = (CreateVisitorReply)deserialize("CreateVisitorReply", DocumentProtocol.REPLY_CREATEVISITOR, lang);
                assertNotNull(reply);
                assertEquals(new BucketId(16, 123), reply.getLastBucket());
                assertEquals(3, reply.getVisitorStatistics().getBucketsVisited());
                assertEquals(1000, reply.getVisitorStatistics().getDocumentsVisited());
                assertEquals(1024000, reply.getVisitorStatistics().getBytesVisited());
                assertEquals(123, reply.getVisitorStatistics().getDocumentsReturned());
                assertEquals(512000, reply.getVisitorStatistics().getBytesReturned());
            }
        }
    }

    public class testDestroyVisitorReply implements RunnableTest {

        @Override
        public void run() {
            testVisitorReply("DestroyVisitorReply", DocumentProtocol.REPLY_DESTROYVISITOR);
        }
    }

    public class testDocumentIgnoredReply implements RunnableTest {

        @Override
        public void run() {
            DocumentIgnoredReply reply = new DocumentIgnoredReply();
            assertEquals(BASE_MESSAGE_LENGTH, serialize("DocumentIgnoredReply", reply));

            for (Language lang : LANGUAGES) {
                reply = (DocumentIgnoredReply)deserialize("DocumentIgnoredReply", DocumentProtocol.REPLY_DOCUMENTIGNORED, lang);
            }
        }
    }

    public class testDocumentListReply implements RunnableTest {

        @Override
        public void run() {
            testVisitorReply("DocumentListReply", DocumentProtocol.REPLY_DOCUMENTLIST);
        }
    }

    public class testDocumentSummaryReply implements RunnableTest {

        @Override
        public void run() {
            testVisitorReply("DocumentSummaryReply", DocumentProtocol.REPLY_DOCUMENTSUMMARY);
        }
    }

    public class testEmptyBucketsReply implements RunnableTest {

        @Override
        public void run() {
            testVisitorReply("EmptyBucketsReply", DocumentProtocol.REPLY_EMPTYBUCKETS);
        }
    }

    public class testDestroyVisitorMessage implements RunnableTest {

        @Override
        public void run() {
            DestroyVisitorMessage msg = new DestroyVisitorMessage("myvisitor");
            assertEquals(BASE_MESSAGE_LENGTH + 17, serialize("DestroyVisitorMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (DestroyVisitorMessage)deserialize("DestroyVisitorMessage", DocumentProtocol.MESSAGE_DESTROYVISITOR, lang);
                assertEquals("myvisitor", msg.getInstanceId());
            }
        }
    }

    public class testDocumentListMessage implements RunnableTest {

        @Override
        public void run() {
            DocumentListMessage msg = (DocumentListMessage)deserialize("DocumentListMessage", DocumentProtocol.MESSAGE_DOCUMENTLIST, Language.CPP);
            assertEquals("id:scheme:testdoc:n=1234:1", msg.getDocuments().get(0).getDocument().getId().toString());
            assertEquals(1234, msg.getDocuments().get(0).getTimestamp());
            assertFalse(msg.getDocuments().get(0).isRemoveEntry());

            assertEquals(BASE_MESSAGE_LENGTH + 69, serialize("DocumentListMessage", msg));
            msg = (DocumentListMessage)deserialize("DocumentListMessage", DocumentProtocol.MESSAGE_DOCUMENTLIST, Language.JAVA);
            assertEquals("id:scheme:testdoc:n=1234:1", msg.getDocuments().get(0).getDocument().getId().toString());
            assertEquals(1234, msg.getDocuments().get(0).getTimestamp());
            assertFalse(msg.getDocuments().get(0).isRemoveEntry());

        }
    }

    public class testEmptyBucketsMessage implements RunnableTest {

        @Override
        public void run() {
            List<BucketId> bids = new ArrayList<>();
            for (int i = 0; i < 13; ++i) {
                bids.add(new BucketId(16, i));
            }

            EmptyBucketsMessage ebm = new EmptyBucketsMessage(bids);
            assertEquals(BASE_MESSAGE_LENGTH + 112, serialize("EmptyBucketsMessage", ebm));
            for (Language lang : LANGUAGES) {
                ebm = (EmptyBucketsMessage)deserialize("EmptyBucketsMessage", DocumentProtocol.MESSAGE_EMPTYBUCKETS, lang);
                for (int i = 0; i < 13; ++i) {
                    assertEquals(new BucketId(16, i), ebm.getBucketIds().get(i));
                }
            }
        }
    }

    public class testDocumentSummaryMessage implements RunnableTest {

        @Override
        public void run() {
            Routable routable = deserialize("DocumentSummaryMessage-1", DocumentProtocol.MESSAGE_DOCUMENTSUMMARY, Language.CPP);
            assertTrue(routable instanceof DocumentSummaryMessage);

            DocumentSummaryMessage msg = (DocumentSummaryMessage) routable;
            assertEquals(0, msg.getResult().getSummaryCount());

            routable = deserialize("DocumentSummaryMessage-2", DocumentProtocol.MESSAGE_DOCUMENTSUMMARY, Language.CPP);
            assertTrue(routable instanceof DocumentSummaryMessage);

            msg = (DocumentSummaryMessage) routable;
            assertEquals(2, msg.getResult().getSummaryCount());
            com.yahoo.vdslib.DocumentSummary.Summary s = msg.getResult().getSummary(0);
            assertEquals("doc1", s.getDocId());
            byte[] b = s.getSummary();
            assertEquals(8, b.length);
            byte[] c = {'s', 'u', 'm', 'm', 'a', 'r', 'y', '1'};
            for (int i = 0; i < b.length; i++) {
                assertEquals(c[i], b[i]);
            }

            s = msg.getResult().getSummary(1);
            assertEquals("aoc17", s.getDocId());
            b = s.getSummary();
            assertEquals(9, b.length);
            byte[] d = {'s', 'u', 'm', 'm', 'a', 'r', 'y', '4', '5'};
            for (int i = 0; i < b.length; i++) {
                assertEquals(d[i], b[i]);
            }
            routable = deserialize("DocumentSummaryMessage-3", DocumentProtocol.MESSAGE_DOCUMENTSUMMARY, Language.CPP);
            assertTrue(routable instanceof DocumentSummaryMessage);

            msg = (DocumentSummaryMessage) routable;
            assertEquals(2, msg.getResult().getSummaryCount());

            s = msg.getResult().getSummary(0);
            assertEquals("aoc17", s.getDocId());
            b = s.getSummary();
            assertEquals(9, b.length);
            byte[] e = {'s', 'u', 'm', 'm', 'a', 'r', 'y', '4', '5'};
            for (int i = 0; i < b.length; i++) {
                assertEquals(e[i], b[i]);
            }

            s = msg.getResult().getSummary(1);
            assertEquals("doc1", s.getDocId());
            b = s.getSummary();
            assertEquals(8, b.length);
            byte[] f = {'s', 'u', 'm', 'm', 'a', 'r', 'y', '1'};
            for (int i = 0; i < b.length; i++) {
                assertEquals(f[i], b[i]);
            }
        }
    }


    public class testGetDocumentMessage implements RunnableTest {

        @Override
        public void run() {
            GetDocumentMessage msg = new GetDocumentMessage(new DocumentId("id:ns:testdoc::"), "foo bar");
            assertEquals(BASE_MESSAGE_LENGTH + 31, serialize("GetDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (GetDocumentMessage)deserialize("GetDocumentMessage", DocumentProtocol.MESSAGE_GETDOCUMENT, lang);
                assertEquals("id:ns:testdoc::", msg.getDocumentId().toString());
                assertEquals("foo bar", msg.getFieldSet());
            }
        }
    }


    public class testRemoveDocumentMessage implements RunnableTest {

        @Override
        public void run() {
            final RemoveDocumentMessage msg = new RemoveDocumentMessage(new DocumentId("id:ns:testdoc::"));
            msg.setCondition(new TestAndSetCondition(CONDITION_STRING));

            assertEquals(BASE_MESSAGE_LENGTH + 20 + serializedLength(msg.getCondition().getSelection()), serialize("RemoveDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                final RemoveDocumentMessage deserializedMsg = (RemoveDocumentMessage)deserialize("RemoveDocumentMessage", DocumentProtocol.MESSAGE_REMOVEDOCUMENT, lang);
                assertEquals(deserializedMsg.getDocumentId().toString(), msg.getDocumentId().toString());
            }
        }
    }

    public class testMapVisitorMessage implements RunnableTest {

        @Override
        public void run() {
            MapVisitorMessage msg = (MapVisitorMessage)deserialize("MapVisitorMessage", DocumentProtocol.MESSAGE_MAPVISITOR, Language.CPP);
            assertEquals("3", msg.getData().get("foo"));
            assertEquals("5", msg.getData().get("bar"));

            assertEquals(BASE_MESSAGE_LENGTH + 32, serialize("MapVisitorMessage", msg));

            msg = (MapVisitorMessage)deserialize("MapVisitorMessage", DocumentProtocol.MESSAGE_MAPVISITOR, Language.JAVA);
            assertEquals("3", msg.getData().get("foo"));
            assertEquals("5", msg.getData().get("bar"));
        }
    }


    public class testVisitorInfoMessage implements RunnableTest {

        @Override
        public void run() {
            VisitorInfoMessage msg = new VisitorInfoMessage();
            msg.getFinishedBuckets().add(new BucketId(16, 1));
            msg.getFinishedBuckets().add(new BucketId(16, 2));
            msg.getFinishedBuckets().add(new BucketId(16, 4));
            msg.setErrorMessage("error message: \u00e6\u00c6\u00f8\u00d8\u00e5\u00c5\u00f6\u00d6");
            assertEquals(BASE_MESSAGE_LENGTH + 67, serialize("VisitorInfoMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (VisitorInfoMessage)deserialize("VisitorInfoMessage", DocumentProtocol.MESSAGE_VISITORINFO, lang);
                assertTrue(msg.getFinishedBuckets().contains(new BucketId(16, 1)));
                assertTrue(msg.getFinishedBuckets().contains(new BucketId(16, 2)));
                assertTrue(msg.getFinishedBuckets().contains(new BucketId(16, 4)));
                assertEquals("error message: \u00e6\u00c6\u00f8\u00d8\u00e5\u00c5\u00f6\u00d6", msg.getErrorMessage());
            }
        }
    }

    public class testSearchResultMessage implements RunnableTest {

        @Override
        public void run() throws Exception {
            Routable routable = deserialize("SearchResultMessage-1", DocumentProtocol.MESSAGE_SEARCHRESULT, Language.CPP);
            assertTrue(routable instanceof SearchResultMessage);

            SearchResultMessage msg = (SearchResultMessage)routable;
            assertEquals(0, msg.getResult().getHitCount());

            routable = deserialize("SearchResultMessage-2", DocumentProtocol.MESSAGE_SEARCHRESULT, Language.CPP);
            assertTrue(routable instanceof SearchResultMessage);

            msg = (SearchResultMessage)routable;
            assertEquals(2, msg.getResult().getHitCount());
            com.yahoo.vdslib.SearchResult.Hit h = msg.getResult().getHit(0);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());
            h = msg.getResult().getHit(1);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());

            routable = deserialize("SearchResultMessage-3", DocumentProtocol.MESSAGE_SEARCHRESULT, Language.CPP);
            assertTrue(routable instanceof SearchResultMessage);

            msg = (SearchResultMessage)routable;
            assertEquals(2, msg.getResult().getHitCount());
            h = msg.getResult().getHit(0);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());
            h = msg.getResult().getHit(1);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());

            routable = deserialize("SearchResultMessage-4", DocumentProtocol.MESSAGE_SEARCHRESULT, Language.CPP);
            assertTrue(routable instanceof SearchResultMessage);

            msg = (SearchResultMessage)routable;
            assertEquals(3, msg.getResult().getHitCount());
            h = msg.getResult().getHit(0);
            assertTrue(h instanceof SearchResult.HitWithSortBlob);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());
            byte[] b = ((SearchResult.HitWithSortBlob)h).getSortBlob();
            assertEquals(9, b.length);
            byte[] e = { 's', 'o', 'r', 't', 'd', 'a', 't', 'a', '2' };
            for (int i = 0; i < b.length; i++) {
                assertEquals(e[i], b[i]);
            }
            h = msg.getResult().getHit(1);
            assertTrue(h instanceof SearchResult.HitWithSortBlob);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());
            b = ((SearchResult.HitWithSortBlob)h).getSortBlob();
            assertEquals(9, b.length);
            byte[] d = { 's', 'o', 'r', 't', 'd', 'a', 't', 'a', '1' };
            for (int i = 0; i < b.length; i++) {
                assertEquals(d[i], b[i]);
            }
            h = msg.getResult().getHit(2);
            assertTrue(h instanceof SearchResult.HitWithSortBlob);
            assertEquals(90.0, h.getRank(), 1E-6);
            assertEquals("doc18", h.getDocId());
            b = ((SearchResult.HitWithSortBlob)h).getSortBlob();
            assertEquals(9, b.length);
            byte[] c = { 's', 'o', 'r', 't', 'd', 'a', 't', 'a', '3' };
            for (int i = 0; i < b.length; i++) {
                assertEquals(c[i], b[i]);
            }
        }
    }

    private static String CONDITION_STRING = "There's just one condition";

    public class testPutDocumentMessage implements RunnableTest {

        @Override
        public void run() {
            PutDocumentMessage msg = new PutDocumentMessage(new DocumentPut(new Document(protocol.getDocumentTypeManager().getDocumentType("testdoc"), "id:ns:testdoc::")));

            msg.setTimestamp(666);
            msg.setCondition(new TestAndSetCondition(CONDITION_STRING));

            assertEquals(BASE_MESSAGE_LENGTH + 45 + serializedLength(msg.getCondition().getSelection()), serialize("PutDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                final PutDocumentMessage deserializedMsg = (PutDocumentMessage)deserialize("PutDocumentMessage", DocumentProtocol.MESSAGE_PUTDOCUMENT, lang);
                assertEquals(msg.getDocumentPut().getDocument().getDataType().getName(), deserializedMsg.getDocumentPut().getDocument().getDataType().getName());
                assertEquals(msg.getDocumentPut().getDocument().getId().toString(), deserializedMsg.getDocumentPut().getDocument().getId().toString());
                assertEquals(msg.getTimestamp(), deserializedMsg.getTimestamp());
                assertEquals(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
            }
        }
    }

    public class testPutDocumentReply implements RunnableTest {

        @Override
        public void run() {
            WriteDocumentReply reply = new WriteDocumentReply(DocumentProtocol.REPLY_PUTDOCUMENT);
            reply.setHighestModificationTimestamp(30);

            assertEquals(13, serialize("PutDocumentReply", reply));

            for (Language lang : LANGUAGES) {
                WriteDocumentReply obj = (WriteDocumentReply)deserialize("PutDocumentReply", DocumentProtocol.REPLY_PUTDOCUMENT, lang);
                assertNotNull(obj);
                assertEquals(30, obj.getHighestModificationTimestamp());
            }
        }
    }

    public class testUpdateDocumentMessage implements RunnableTest {

        @Override
        public void run() {
            DocumentType docType = protocol.getDocumentTypeManager().getDocumentType("testdoc");
            DocumentUpdate update = new DocumentUpdate(docType, new DocumentId("id:ns:testdoc::"));
            update.addFieldPathUpdate(new RemoveFieldPathUpdate(docType, "intfield", "testdoc.intfield > 0"));

            final UpdateDocumentMessage msg = new UpdateDocumentMessage(update);
            msg.setNewTimestamp(777);
            msg.setOldTimestamp(666);
            msg.setCondition(new TestAndSetCondition(CONDITION_STRING));

            assertEquals(BASE_MESSAGE_LENGTH + 93 + serializedLength(msg.getCondition().getSelection()), serialize("UpdateDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                final UpdateDocumentMessage deserializedMsg = (UpdateDocumentMessage) deserialize("UpdateDocumentMessage", DocumentProtocol.MESSAGE_UPDATEDOCUMENT, lang);
                assertEquals(msg.getDocumentUpdate(), deserializedMsg.getDocumentUpdate());
                assertEquals(msg.getNewTimestamp(), deserializedMsg.getNewTimestamp());
                assertEquals(msg.getOldTimestamp(), deserializedMsg.getOldTimestamp());
                assertEquals(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
            }
        }
    }

    public class testUpdateDocumentReply implements RunnableTest {

        @Override
        public void run() {
            UpdateDocumentReply reply = new UpdateDocumentReply();
            reply.setHighestModificationTimestamp(30);
            reply.setWasFound(false);

            assertEquals(14, serialize("UpdateDocumentReply", reply));

            for (Language lang : LANGUAGES) {
                UpdateDocumentReply obj = (UpdateDocumentReply)deserialize("UpdateDocumentReply", DocumentProtocol.REPLY_UPDATEDOCUMENT, lang);
                assertNotNull(obj);
                assertEquals(30, reply.getHighestModificationTimestamp());
                assertEquals(false, obj.wasFound());
            }
        }
    }

    public class testVisitorInfoReply implements RunnableTest {

        @Override
        public void run() {
            testVisitorReply("VisitorInfoReply", DocumentProtocol.REPLY_VISITORINFO);
        }
    }

    public class testWrongDistributionReply implements RunnableTest {

        @Override
        public void run() {
            WrongDistributionReply reply = new WrongDistributionReply("distributor:3 storage:2");
            assertEquals(32, serialize("WrongDistributionReply", reply));

            for (Language lang : LANGUAGES) {
                reply = (WrongDistributionReply)deserialize("WrongDistributionReply", DocumentProtocol.REPLY_WRONGDISTRIBUTION, lang);
                assertEquals("distributor:3 storage:2", reply.getSystemState());
            }
        }
    }

    public class testRemoveDocumentReply implements RunnableTest {

        @Override
        public void run() {
            RemoveDocumentReply reply = new RemoveDocumentReply();
            reply.setHighestModificationTimestamp(30);
            reply.setWasFound(false);

            assertEquals(14, serialize("RemoveDocumentReply", reply));

            for (Language lang : LANGUAGES) {
                RemoveDocumentReply obj = (RemoveDocumentReply)deserialize("RemoveDocumentReply", DocumentProtocol.REPLY_REMOVEDOCUMENT, lang);
                assertNotNull(obj);
                assertEquals(30, obj.getHighestModificationTimestamp());
                assertEquals(false, obj.wasFound());
            }
        }
    }

    public class testRemoveLocationReply implements RunnableTest {

        @Override
        public void run() {
            testDocumentReply("RemoveLocationReply", DocumentProtocol.REPLY_REMOVELOCATION);
        }
    }

    public class testSearchResultReply implements RunnableTest {

        @Override
        public void run() {
            testVisitorReply("SearchResultReply", DocumentProtocol.REPLY_SEARCHRESULT);
        }
    }

    public class testStatBucketReply implements RunnableTest {

        @Override
        public void run() {
            StatBucketReply msg = new StatBucketReply();
            msg.setResults("These are the votes of the Norwegian jury");

            assertEquals(50, serialize("StatBucketReply", msg));

            for (Language lang : LANGUAGES) {
                msg = (StatBucketReply)deserialize("StatBucketReply", DocumentProtocol.REPLY_STATBUCKET, lang);
                assertEquals("These are the votes of the Norwegian jury", msg.getResults());
            }
        }
    }

    public class testQueryResultReply implements RunnableTest {

        @Override
        public void run() {
            testVisitorReply("QueryResultReply", DocumentProtocol.REPLY_QUERYRESULT);
        }
    }

    public class testQueryResultMessage implements RunnableTest {

        @Override
        public void run() throws Exception {
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
            h = msg.getResult().getHit(1);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());

            routable = deserialize("QueryResultMessage-3", DocumentProtocol.MESSAGE_QUERYRESULT, Language.CPP);
            assertTrue(routable instanceof QueryResultMessage);

            msg = (QueryResultMessage)routable;
            assertEquals(2, msg.getResult().getHitCount());
            h = msg.getResult().getHit(0);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());
            h = msg.getResult().getHit(1);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());

            routable = deserialize("QueryResultMessage-4", DocumentProtocol.MESSAGE_QUERYRESULT, Language.CPP);
            assertTrue(routable instanceof QueryResultMessage);

            msg = (QueryResultMessage)routable;
            assertEquals(3, msg.getResult().getHitCount());
            h = msg.getResult().getHit(0);
            assertTrue(h instanceof SearchResult.HitWithSortBlob);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());
            byte[] b = ((SearchResult.HitWithSortBlob)h).getSortBlob();
            assertEquals(9, b.length);
            byte[] e = { 's', 'o', 'r', 't', 'd', 'a', 't', 'a', '2' };
            for (int i = 0; i < b.length; i++) {
                assertEquals(e[i], b[i]);
            }
            h = msg.getResult().getHit(1);
            assertTrue(h instanceof SearchResult.HitWithSortBlob);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());
            b = ((SearchResult.HitWithSortBlob)h).getSortBlob();
            assertEquals(9, b.length);
            byte[] d = { 's', 'o', 'r', 't', 'd', 'a', 't', 'a', '1' };
            for (int i = 0; i < b.length; i++) {
                assertEquals(d[i], b[i]);
            }
            h = msg.getResult().getHit(2);
            assertTrue(h instanceof SearchResult.HitWithSortBlob);
            assertEquals(90.0, h.getRank(), 1E-6);
            assertEquals("doc18", h.getDocId());
            b = ((SearchResult.HitWithSortBlob)h).getSortBlob();
            assertEquals(9, b.length);
            byte[] c = { 's', 'o', 'r', 't', 'd', 'a', 't', 'a', '3' };
            for (int i = 0; i < b.length; i++) {
                assertEquals(c[i], b[i]);
            }
        }
    }

    public class testGetBucketListReply implements RunnableTest {

        public void run() {
            GetBucketListReply reply = new GetBucketListReply();
            reply.getBuckets().add(new GetBucketListReply.BucketInfo(new BucketId(16, 123), "foo"));
            reply.getBuckets().add(new GetBucketListReply.BucketInfo(new BucketId(17, 1123), "bar"));
            reply.getBuckets().add(new GetBucketListReply.BucketInfo(new BucketId(18, 11123), "zoink"));

            assertEquals(56, serialize("GetBucketListReply", reply));

            for (Language lang : LANGUAGES) {
                reply = (GetBucketListReply)deserialize("GetBucketListReply", DocumentProtocol.REPLY_GETBUCKETLIST, lang);
                assertEquals(reply.getBuckets().get(0), new GetBucketListReply.BucketInfo(new BucketId(16, 123), "foo"));
                assertEquals(reply.getBuckets().get(1), new GetBucketListReply.BucketInfo(new BucketId(17, 1123), "bar"));
                assertEquals(reply.getBuckets().get(2), new GetBucketListReply.BucketInfo(new BucketId(18, 11123), "zoink"));
            }
        }
    }

    public class testGetBucketStateReply implements RunnableTest {

        public void run() {
            GlobalId foo = new GlobalId(IdString.createIdString("id:ns:testdoc::foo"));
            GlobalId bar = new GlobalId(IdString.createIdString("id:ns:testdoc::bar"));

            GetBucketStateReply reply = new GetBucketStateReply();
            List<DocumentState> state = new ArrayList<>(2);
            state.add(new DocumentState(foo, 777, false));
            state.add(new DocumentState(bar, 888, true));
            reply.setBucketState(state);
            assertEquals(53, serialize("GetBucketStateReply", reply));

            for (Language lang : LANGUAGES) {
                reply = (GetBucketStateReply)deserialize("GetBucketStateReply", DocumentProtocol.REPLY_GETBUCKETSTATE, lang);
                assertEquals(777, reply.getBucketState().get(0).getTimestamp());
                assertEquals(foo, reply.getBucketState().get(0).getGid());
                assertEquals(false, reply.getBucketState().get(0).isRemoveEntry());
                assertEquals(888, reply.getBucketState().get(1).getTimestamp());
                assertEquals(bar, reply.getBucketState().get(1).getGid());
                assertEquals(true, reply.getBucketState().get(1).isRemoveEntry());
            }
        }
    }

    public class testGetDocumentReply implements RunnableTest {

        public void run() {
            GetDocumentReply reply = new GetDocumentReply(new Document(protocol.getDocumentTypeManager().getDocumentType("testdoc"), "id:ns:testdoc::"));
            assertEquals(47, serialize("GetDocumentReply", reply));

            for (Language lang : LANGUAGES) {
                reply = (GetDocumentReply)deserialize("GetDocumentReply", DocumentProtocol.REPLY_GETDOCUMENT, lang);
                assertEquals("testdoc", reply.getDocument().getDataType().getName());
                assertEquals("id:ns:testdoc::", reply.getDocument().getId().toString());
            }
        }
    }

    public class testMapVisitorReply implements RunnableTest {

        public void run() {
            testVisitorReply("MapVisitorReply", DocumentProtocol.REPLY_MAPVISITOR);
        }
    }

    protected void testDocumentReply(String filename, int type) {
        DocumentReply reply = new DocumentReply(type);
        assertEquals(5, serialize(filename, reply));

        for (Language lang : LANGUAGES) {
            reply = (DocumentReply)deserialize(filename, type, lang);
            assertNotNull(reply);
        }
    }

    protected void testVisitorReply(String filename, int type) {
        VisitorReply reply = new VisitorReply(type);
        assertEquals(5, serialize(filename, reply));

        for (Language lang : LANGUAGES) {
            reply = (VisitorReply)deserialize(filename, type, lang);
            assertNotNull(reply);
        }
    }

    static int serializedLength(String str) {
        return 4 + str.length();
    }
}
