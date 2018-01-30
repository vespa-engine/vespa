// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.component.Version;
import com.yahoo.document.*;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.idstring.IdString;
import com.yahoo.document.select.OrderingSpecification;
import com.yahoo.documentapi.messagebus.protocol.*;
import com.yahoo.messagebus.Routable;
import com.yahoo.text.Utf8;
import com.yahoo.vdslib.DocumentList;
import com.yahoo.vdslib.Entry;
import com.yahoo.vdslib.SearchResult;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class Messages50TestCase extends MessagesTestBase {

    @Override
    protected void registerTests(Map<Integer, RunnableTest> out) {
        // This list MUST mirror the list of routable factories from the DocumentProtocol constructor that support
        // version 5.0. When adding tests to this list, please KEEP THEM ORDERED alphabetically like they are now.
        out.put(DocumentProtocol.MESSAGE_BATCHDOCUMENTUPDATE, new testBatchDocumentUpdateMessage());
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
        out.put(DocumentProtocol.REPLY_BATCHDOCUMENTUPDATE, new testBatchDocumentUpdateReply());
        out.put(DocumentProtocol.REPLY_CREATEVISITOR, new testCreateVisitorReply());
        out.put(DocumentProtocol.REPLY_DESTROYVISITOR, new testDestroyVisitorReply());
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
        return new Version(5, 0);
    }

    @Override
    protected boolean shouldTestCoverage() {
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Tests
    //
    ////////////////////////////////////////////////////////////////////////////////

    private static int BASE_MESSAGE_LENGTH = 5;

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

        @Override
        public void run() {
            GetBucketListMessage msg = new GetBucketListMessage(new BucketId(16, 123));
            msg.setLoadType(loadTypes.getNameMap().get("foo"));
            assertEquals(BASE_MESSAGE_LENGTH + 12, serialize("GetBucketListMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (GetBucketListMessage)deserialize("GetBucketListMessage", DocumentProtocol.MESSAGE_GETBUCKETLIST, lang);
                assertEquals(new BucketId(16, 123), msg.getBucketId());
                assertEquals("foo", msg.getLoadType().getName());
            }
        }
    }


    public class testStatBucketMessage implements RunnableTest {

        @Override
        public void run() {
            StatBucketMessage msg = new StatBucketMessage(new BucketId(16, 123), "id.user=123");
            msg.setLoadType(null);
            assertEquals(BASE_MESSAGE_LENGTH + 27, serialize("StatBucketMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (StatBucketMessage)deserialize("StatBucketMessage", DocumentProtocol.MESSAGE_STATBUCKET, lang);
                assertEquals(new BucketId(16, 123), msg.getBucketId());
                assertEquals("id.user=123", msg.getDocumentSelection());
                assertEquals("default", msg.getLoadType().getName());
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

        @Override
        @SuppressWarnings("deprecation")
        public void run() {
            CreateVisitorMessage msg = new CreateVisitorMessage("SomeLibrary", "myvisitor", "newyork", "london");
            msg.setDocumentSelection("true and false or true");
            msg.getParameters().put("myvar", Utf8.toBytes("somevalue"));
            msg.getParameters().put("anothervar", Utf8.toBytes("34"));
            msg.getBuckets().add(new BucketId(16, 1234));
            msg.setVisitRemoves(true);
            msg.setVisitorOrdering(OrderingSpecification.DESCENDING);
            msg.setMaxBucketsPerVisitor(2);
            assertEquals(BASE_MESSAGE_LENGTH + 168, serialize("CreateVisitorMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (CreateVisitorMessage)deserialize("CreateVisitorMessage", DocumentProtocol.MESSAGE_CREATEVISITOR, lang);
                assertEquals("SomeLibrary", msg.getLibraryName());
                assertEquals("myvisitor", msg.getInstanceId());
                assertEquals("newyork", msg.getControlDestination());
                assertEquals("london", msg.getDataDestination());
                assertEquals("true and false or true", msg.getDocumentSelection());
                assertEquals(8, msg.getMaxPendingReplyCount());
                assertEquals(true, msg.getVisitRemoves());
                assertEquals(false, msg.getVisitInconsistentBuckets());
                assertEquals(1, msg.getBuckets().size());
                assertEquals(new BucketId(16, 1234), msg.getBuckets().iterator().next());
                assertEquals("somevalue", Utf8.toString(msg.getParameters().get("myvar")));
                assertEquals("34", Utf8.toString(msg.getParameters().get("anothervar")));
                assertEquals(OrderingSpecification.DESCENDING, msg.getVisitorOrdering());
                assertEquals(2, msg.getMaxBucketsPerVisitor());
            }

            msg.getBuckets().clear();

            assertEquals("CreateVisitorMessage(" +
                    "No buckets, " +
                    "selection 'true and false or true', " +
                    "bucket space 'default', " +
                    "library SomeLibrary, including removes, " +
                    "get fields: [all]" +
                    ")",
                    msg.toString());

            msg.getBuckets().add(new BucketId(16, 1234));

            assertEquals("CreateVisitorMessage(" +
                    "Bucket BucketId(0x40000000000004d2), " +
                    "selection 'true and false or true', " +
                    "bucket space 'default', " +
                    "library SomeLibrary, including removes, " +
                    "get fields: [all]" +
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
                    "bucket space 'default', " +
                    "library SomeLibrary, including removes, " +
                    "get fields: [all], " +
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
            reply.getVisitorStatistics().setSecondPassDocumentsReturned(456);
            reply.getVisitorStatistics().setSecondPassBytesReturned(789100);

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
                assertEquals(456, reply.getVisitorStatistics().getSecondPassDocumentsReturned());
                assertEquals(789100, reply.getVisitorStatistics().getSecondPassBytesReturned());
            }
        }
    }

    public class testDestroyVisitorReply implements RunnableTest {

        @Override
        public void run() {
            testVisitorReply("DestroyVisitorReply", DocumentProtocol.REPLY_DESTROYVISITOR);
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
            assertEquals("userdoc:scheme:1234:", msg.getDocuments().get(0).getDocument().getId().toString());
            assertEquals(1234, msg.getDocuments().get(0).getTimestamp());
            assertFalse(msg.getDocuments().get(0).isRemoveEntry());

            assertEquals(BASE_MESSAGE_LENGTH + 63, serialize("DocumentListMessage", msg));
            msg = (DocumentListMessage)deserialize("DocumentListMessage", DocumentProtocol.MESSAGE_DOCUMENTLIST, Language.JAVA);
            assertEquals("userdoc:scheme:1234:", msg.getDocuments().get(0).getDocument().getId().toString());
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
            try {
                FileInputStream stream = new FileInputStream(getPath("5-cpp-DocumentSummaryMessage-1.dat"));
                byte[] data = new byte[stream.available()];
                assertEquals(data.length, stream.read(data));

                Routable routable = decode(data);
                assertTrue(routable instanceof DocumentSummaryMessage);

                DocumentSummaryMessage msg = (DocumentSummaryMessage)routable;
                assertEquals(0, msg.getResult().getSummaryCount());

                stream = new FileInputStream(getPath("5-cpp-DocumentSummaryMessage-2.dat"));
                data = new byte[stream.available()];
                assertEquals(data.length, stream.read(data));

                routable = decode(data);
                assertTrue(routable instanceof DocumentSummaryMessage);

                msg = (DocumentSummaryMessage)routable;
                assertEquals(2, msg.getResult().getSummaryCount());
                com.yahoo.vdslib.DocumentSummary.Summary s = msg.getResult().getSummary(0);
                assertEquals("doc1", s.getDocId());
                byte[] b = s.getSummary();
                assertEquals(8, b.length);
                byte[] c = { 's', 'u', 'm', 'm', 'a', 'r', 'y', '1' };
                for (int i = 0; i < b.length; i++) {
                    assertEquals(c[i], b[i]);
                }

                s = msg.getResult().getSummary(1);
                assertEquals("aoc17", s.getDocId());
                b = s.getSummary();
                assertEquals(9, b.length);
                byte[] d = { 's', 'u', 'm', 'm', 'a', 'r', 'y', '4', '5' };
                for (int i = 0; i < b.length; i++) {
                    assertEquals(d[i], b[i]);
                }

                stream = new FileInputStream(getPath("5-cpp-DocumentSummaryMessage-3.dat"));
                data = new byte[stream.available()];
                assertEquals(data.length, stream.read(data));

                routable = decode(data);
                assertTrue(routable instanceof DocumentSummaryMessage);

                msg = (DocumentSummaryMessage)routable;
                assertEquals(2, msg.getResult().getSummaryCount());

                s = msg.getResult().getSummary(0);
                assertEquals("aoc17", s.getDocId());
                b = s.getSummary();
                assertEquals(9, b.length);
                byte[] e = { 's', 'u', 'm', 'm', 'a', 'r', 'y', '4', '5' };
                for (int i = 0; i < b.length; i++) {
                    assertEquals(e[i], b[i]);
                }

                s = msg.getResult().getSummary(1);
                assertEquals("doc1", s.getDocId());
                b = s.getSummary();
                assertEquals(8, b.length);
                byte[] f = { 's', 'u', 'm', 'm', 'a', 'r', 'y', '1' };
                for (int i = 0; i < b.length; i++) {
                    assertEquals(f[i], b[i]);
                }
            } catch (IOException e) {
                fail(e.toString());
            }
        }
    }


    public class testGetDocumentMessage implements RunnableTest {

        @Override
        @SuppressWarnings("deprecation")
        public void run() {
            GetDocumentMessage msg = new GetDocumentMessage(new DocumentId("doc:scheme:"));
            assertEquals(BASE_MESSAGE_LENGTH + 20, serialize("GetDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (GetDocumentMessage)deserialize("GetDocumentMessage", DocumentProtocol.MESSAGE_GETDOCUMENT, lang);
                assertEquals("doc:scheme:", msg.getDocumentId().toString());
            }
        }
    }


    public class testRemoveDocumentMessage implements RunnableTest {

        @Override
        public void run() {
            RemoveDocumentMessage msg = new RemoveDocumentMessage(new DocumentId("doc:scheme:"));
            assertEquals(BASE_MESSAGE_LENGTH + 16, serialize("RemoveDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (RemoveDocumentMessage)deserialize("RemoveDocumentMessage", DocumentProtocol.MESSAGE_REMOVEDOCUMENT, lang);
                assertEquals("doc:scheme:", msg.getDocumentId().toString());
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
            FileInputStream stream = new FileInputStream(getPath("5-cpp-SearchResultMessage-1.dat"));
            byte[] data = new byte[stream.available()];
            assertEquals(data.length, stream.read(data));

            Routable routable = decode(data);
            assertTrue(routable instanceof SearchResultMessage);

            SearchResultMessage msg = (SearchResultMessage)routable;
            assertEquals(0, msg.getResult().getHitCount());

            stream = new FileInputStream(getPath("5-cpp-SearchResultMessage-2.dat"));
            data = new byte[stream.available()];
            assertEquals(data.length, stream.read(data));

            routable = decode(data);
            assertTrue(routable instanceof SearchResultMessage);

            msg = (SearchResultMessage)routable;
            assertEquals(2, msg.getResult().getHitCount());
            com.yahoo.vdslib.SearchResult.Hit h = msg.getResult().getHit(0);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());
            h = msg.getResult().getHit(1);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());

            stream = new FileInputStream(getPath("5-cpp-SearchResultMessage-3.dat"));
            data = new byte[stream.available()];
            assertEquals(data.length, stream.read(data));

            routable = decode(data);
            assertTrue(routable instanceof SearchResultMessage);

            msg = (SearchResultMessage)routable;
            assertEquals(2, msg.getResult().getHitCount());
            h = msg.getResult().getHit(0);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());
            h = msg.getResult().getHit(1);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());

            stream = new FileInputStream(getPath("5-cpp-SearchResultMessage-4.dat"));
            data = new byte[stream.available()];
            assertEquals(data.length, stream.read(data));

            routable = decode(data);
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

    public class testPutDocumentMessage implements RunnableTest {

        @Override
        public void run() {
            PutDocumentMessage msg = new PutDocumentMessage(new DocumentPut(new Document(protocol.getDocumentTypeManager().getDocumentType("testdoc"), "doc:scheme:")));
            msg.setTimestamp(666);
            assertEquals(BASE_MESSAGE_LENGTH + 41, serialize("PutDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (PutDocumentMessage)deserialize("PutDocumentMessage", DocumentProtocol.MESSAGE_PUTDOCUMENT, lang);
                assertEquals("testdoc", msg.getDocumentPut().getDocument().getDataType().getName());
                assertEquals("doc:scheme:", msg.getDocumentPut().getDocument().getId().toString());
                assertEquals(666, msg.getTimestamp());
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
            DocumentUpdate update = new DocumentUpdate(docType, new DocumentId("doc:scheme:"));
            update.addFieldPathUpdate(new RemoveFieldPathUpdate(docType, "intfield", "testdoc.intfield > 0"));
            UpdateDocumentMessage msg = new UpdateDocumentMessage(update);
            msg.setNewTimestamp(777);
            msg.setOldTimestamp(666);

            assertEquals(BASE_MESSAGE_LENGTH + 89, serialize("UpdateDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (UpdateDocumentMessage)deserialize("UpdateDocumentMessage", DocumentProtocol.MESSAGE_UPDATEDOCUMENT, lang);
                assertEquals(update, msg.getDocumentUpdate());
                assertEquals(777, msg.getNewTimestamp());
                assertEquals(666, msg.getOldTimestamp());
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

    public class testBatchDocumentUpdateMessage implements RunnableTest {

        @Override
        public void run() {
            DocumentType docType = protocol.getDocumentTypeManager().getDocumentType("testdoc");
            BatchDocumentUpdateMessage msg = new BatchDocumentUpdateMessage(1234);

            {
                DocumentUpdate update = new DocumentUpdate(docType, new DocumentId("userdoc:footype:1234:foo"));
                update.addFieldPathUpdate(new RemoveFieldPathUpdate(docType, "intfield", "testdoc.intfield > 0"));
                msg.addUpdate(update);
            }
            {
                DocumentUpdate update = new DocumentUpdate(docType, new DocumentId("orderdoc(32,17):footype:1234:123456789:foo"));
                update.addFieldPathUpdate(new RemoveFieldPathUpdate(docType, "intfield", "testdoc.intfield > 0"));
                msg.addUpdate(update);
            }

            try {
                DocumentUpdate update = new DocumentUpdate(docType, new DocumentId("orderdoc:footype:5678:foo"));
                update.addFieldPathUpdate(new RemoveFieldPathUpdate(docType, "intfield", "testdoc.intfield > 0"));
                msg.addUpdate(update);
                fail();
            } catch (Exception e) {

            }

            try {
                DocumentUpdate update = new DocumentUpdate(docType, new DocumentId("groupdoc:footype:hable:foo"));
                update.addFieldPathUpdate(new RemoveFieldPathUpdate(docType, "intfield", "testdoc.intfield > 0"));
                msg.addUpdate(update);
                fail();
            } catch (Exception e) {

            }

            assertEquals(2, msg.getUpdates().size());

            assertEquals(BASE_MESSAGE_LENGTH + 202, serialize("BatchDocumentUpdateMessage", msg));

            for (Language lang : LANGUAGES) {
                msg = (BatchDocumentUpdateMessage)deserialize("BatchDocumentUpdateMessage", DocumentProtocol.MESSAGE_BATCHDOCUMENTUPDATE, lang);
                assertEquals(2, msg.getUpdates().size());
            }
        }
    }

    public class testBatchDocumentUpdateReply implements RunnableTest {

        @Override
        public void run() {
            BatchDocumentUpdateReply reply = new BatchDocumentUpdateReply();
            reply.setHighestModificationTimestamp(30);
            reply.getDocumentsNotFound().add(false);
            reply.getDocumentsNotFound().add(true);
            reply.getDocumentsNotFound().add(true);

            assertEquals(20, serialize("BatchDocumentUpdateReply", reply));

            for (Language lang : LANGUAGES) {
                BatchDocumentUpdateReply obj = (BatchDocumentUpdateReply)deserialize("BatchDocumentUpdateReply", DocumentProtocol.REPLY_BATCHDOCUMENTUPDATE, lang);
                assertNotNull(obj);
                assertEquals(30, obj.getHighestModificationTimestamp());
                assertEquals(3, obj.getDocumentsNotFound().size());
                assertFalse(obj.getDocumentsNotFound().get(0));
                assertTrue(obj.getDocumentsNotFound().get(1));
                assertTrue(obj.getDocumentsNotFound().get(2));
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
            FileInputStream stream = new FileInputStream(getPath("5-cpp-QueryResultMessage-1.dat"));
            byte[] data = new byte[stream.available()];
            assertEquals(data.length, stream.read(data));

            Routable routable = decode(data);
            assertTrue(routable instanceof QueryResultMessage);

            QueryResultMessage msg = (QueryResultMessage)routable;
            assertEquals(0, msg.getResult().getHitCount());

            stream = new FileInputStream(getPath("5-cpp-QueryResultMessage-2.dat"));
            data = new byte[stream.available()];
            assertEquals(data.length, stream.read(data));

            routable = decode(data);
            assertTrue(routable instanceof QueryResultMessage);

            msg = (QueryResultMessage)routable;
            assertEquals(2, msg.getResult().getHitCount());
            com.yahoo.vdslib.SearchResult.Hit h = msg.getResult().getHit(0);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());
            h = msg.getResult().getHit(1);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());

            stream = new FileInputStream(getPath("5-cpp-QueryResultMessage-3.dat"));
            data = new byte[stream.available()];
            assertEquals(data.length, stream.read(data));

            routable = decode(data);
            assertTrue(routable instanceof QueryResultMessage);

            msg = (QueryResultMessage)routable;
            assertEquals(2, msg.getResult().getHitCount());
            h = msg.getResult().getHit(0);
            assertEquals(109.0, h.getRank(), 1E-6);
            assertEquals("doc17", h.getDocId());
            h = msg.getResult().getHit(1);
            assertEquals(89.0, h.getRank(), 1E-6);
            assertEquals("doc1", h.getDocId());

            stream = new FileInputStream(getPath("5-cpp-QueryResultMessage-4.dat"));
            data = new byte[stream.available()];
            assertEquals(data.length, stream.read(data));

            routable = decode(data);
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
            GlobalId foo = new GlobalId(IdString.createIdString("doc:scheme:foo"));
            GlobalId bar = new GlobalId(IdString.createIdString("doc:scheme:bar"));

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
            GetDocumentReply reply = new GetDocumentReply(new Document(protocol.getDocumentTypeManager().getDocumentType("testdoc"), "doc:scheme:"));
            assertEquals(43, serialize("GetDocumentReply", reply));

            for (Language lang : LANGUAGES) {
                reply = (GetDocumentReply)deserialize("GetDocumentReply", DocumentProtocol.REPLY_GETDOCUMENT, lang);
                assertEquals("testdoc", reply.getDocument().getDataType().getName());
                assertEquals("doc:scheme:", reply.getDocument().getId().toString());
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

}
