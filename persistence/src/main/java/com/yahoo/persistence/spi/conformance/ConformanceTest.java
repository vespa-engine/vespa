// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.conformance;

import com.yahoo.document.BucketId;
import com.yahoo.document.*;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.fieldset.AllFields;
import com.yahoo.document.fieldset.FieldSet;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.persistence.spi.*;
import com.yahoo.persistence.spi.result.*;
import junit.framework.TestCase;

import java.util.*;

public abstract class ConformanceTest extends TestCase {
    TestDocMan testDocMan = new TestDocMan();

    public interface PersistenceProviderFactory {
        public PersistenceProvider createProvider(DocumentTypeManager manager);
        public boolean supportsActiveState();
    }

    PersistenceProvider init(PersistenceProviderFactory factory) {
        return factory.createProvider(testDocMan);
    }

    // TODO: should invoke some form of destruction method on the provider after a test
    protected void doConformanceTest(PersistenceProviderFactory factory) throws Exception {
        testBasics(init(factory));
        testPut(init(factory));
        testRemove(init(factory));
        testGet(init(factory));
        testUpdate(init(factory));

        testListBuckets(init(factory));
        testBucketInfo(init(factory));
        testOrderIndependentBucketInfo(init(factory));
        testPutNewDocumentVersion(init(factory));
        testPutOlderDocumentVersion(init(factory));
        testPutDuplicate(init(factory));
        testDeleteBucket(init(factory));
        testSplitNormalCase(init(factory));
        testSplitTargetExists(init(factory));
        testJoinNormalCase(init(factory));
        testJoinTargetExists(init(factory));
        testJoinOneBucket(init(factory));

        testMaintain(init(factory));
        testGetModifiedBuckets(init(factory));

        if (factory.supportsActiveState()) {
            testBucketActivation(init(factory));
            testBucketActivationSplitAndJoin(init(factory));
        }

        testIterateAllDocs(init(factory));
        testIterateAllDocsNewestVersionOnly(init(factory));
        testIterateCreateIterator(init(factory));
        testIterateDestroyIterator(init(factory));
        testIterateWithUnknownId(init(factory));
        testIterateChunked(init(factory));
        testIterateMatchTimestampRange(init(factory));
        testIterateMaxByteSize(init(factory));
        testIterateExplicitTimestampSubset(init(factory));
        testIterateMatchSelection(init(factory));
        testIterateRemoves(init(factory));
        testIterationRequiringDocumentIdOnlyMatching(init(factory));
        testIterateAlreadyCompleted(init(factory));
        testIterateEmptyBucket(init(factory));

        testRemoveMerge(init(factory));
    }

    List<DocEntry> iterateBucket(PersistenceProvider spi, Bucket bucket, PersistenceProvider.IncludedVersions versions) throws Exception {
        List<DocEntry> ret = new ArrayList<DocEntry>();

        CreateIteratorResult iter = spi.createIterator(
                bucket,
                new AllFields(),
                new Selection("", 0, Long.MAX_VALUE),
                versions);

        assertFalse(iter.hasError());

        while (true) {
            IterateResult result = spi.iterate(iter.getIteratorId(), Long.MAX_VALUE);
            assertFalse(result.hasError());

            ret.addAll(result.getEntries());

            if (result.isCompleted()) {
                break;
            }
        }

        Collections.sort(ret);

        return ret;
    }

    void testBasicsIteration(PersistenceProvider provider, Bucket bucket, Document doc1, Document doc2, boolean includeRemoves) throws Exception {
        Selection selection = new Selection("true", 0, Long.MAX_VALUE);

        CreateIteratorResult iter = provider.createIterator(
                bucket,
                new AllFields(),
                selection,
                includeRemoves ? PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_OR_REMOVE : PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);

        assertTrue(!iter.hasError());

        IterateResult result = provider.iterate(iter.getIteratorId(), Long.MAX_VALUE);
        assertTrue(!result.hasError());
        assertTrue(result.isCompleted());
        assertEquals(new Result(), provider.destroyIterator(iter.getIteratorId()));

        long timeRemoveDoc1 = 0;
        long timeDoc1 = 0;
        long timeDoc2 = 0;

        for (DocEntry entry : result.getEntries()) {
            assertNotNull(entry.getDocumentId());

            if (entry.getDocumentId().equals(doc1.getId())) {
                assertTrue("Got removed document 1 when iterating without removes", includeRemoves);

                if (entry.getType() == DocEntry.Type.REMOVE_ENTRY) {
                    timeRemoveDoc1 = entry.getTimestamp();
                } else {
                    timeDoc1 = entry.getTimestamp();
                }
            } else if (entry.getDocumentId().equals(doc2.getId())) {
                assertEquals(DocEntry.Type.PUT_ENTRY, entry.getType());
                timeDoc2 = entry.getTimestamp();
            } else {
                assertFalse("Unknown document " + entry.getDocumentId(), false);
            }
        }

        assertEquals(2, timeDoc2);
        assertTrue(timeDoc1 == 0 || timeRemoveDoc1 != 0);
    }

    void testBasics(PersistenceProvider provider) throws Exception {
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));
        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        Document doc2 = testDocMan.createRandomDocumentAtLocation(0x01, 2);

        assertEquals(new Result(), provider.createBucket(bucket));
        assertEquals(new Result(), provider.put(bucket, 1, doc1));
        assertEquals(new Result(), provider.put(bucket, 2, doc2));

        assertEquals(new RemoveResult(true), provider.remove(bucket, 3, doc1.getId()));
        assertEquals(new Result(), provider.flush(bucket));

        testBasicsIteration(provider, bucket, doc1, doc2, false);
        testBasicsIteration(provider, bucket, doc1, doc2, true);
    }

    void testListBuckets(PersistenceProvider provider) {
        BucketId bucketId1 = new BucketId(8, 0x01);
        BucketId bucketId2 = new BucketId(8, 0x02);
        BucketId bucketId3 = new BucketId(8, 0x03);

        Bucket bucket1 = new Bucket((short)0, bucketId1);
        Bucket bucket2 = new Bucket((short)0, bucketId2);
        Bucket bucket3 = new Bucket((short)0, bucketId3);

        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        Document doc2 = testDocMan.createRandomDocumentAtLocation(0x02, 2);
        Document doc3 = testDocMan.createRandomDocumentAtLocation(0x03, 3);

        provider.createBucket(bucket1);
        provider.createBucket(bucket2);
        provider.createBucket(bucket3);

        provider.put(bucket1, 1, doc1);
        provider.flush(bucket1);

        provider.put(bucket2, 2, doc2);
        provider.flush(bucket2);

        provider.put(bucket3, 3, doc3);
        provider.flush(bucket3);

        BucketIdListResult result = provider.listBuckets((short)0);
        assertEquals(3, result.getBuckets().size());
        assertTrue(result.getBuckets().contains(bucketId1));
        assertTrue(result.getBuckets().contains(bucketId2));
        assertTrue(result.getBuckets().contains(bucketId3));
    }

    void testBucketInfo(PersistenceProvider provider) {
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        Document doc2 = testDocMan.createRandomDocumentAtLocation(0x01, 2);

        provider.createBucket(bucket);
        provider.put(bucket, 2, doc2);

        BucketInfo info = provider.getBucketInfo(bucket).getBucketInfo();
        provider.flush(bucket);

        assertEquals(1, info.getDocumentCount());
        assertTrue(info.getChecksum() != 0);

        provider.put(bucket, 3, doc1);
        BucketInfo info2 = provider.getBucketInfo(bucket).getBucketInfo();
        provider.flush(bucket);

        assertEquals(2, info2.getDocumentCount());
        assertTrue(info2.getChecksum() != 0);
        assertTrue(info.getChecksum() != info2.getChecksum());

        provider.put(bucket, 4, doc1);
        BucketInfo info3 = provider.getBucketInfo(bucket).getBucketInfo();
        provider.flush(bucket);

        assertEquals(2, info3.getDocumentCount());
        assertTrue(info3.getChecksum() != 0);
        assertTrue(info2.getChecksum() != info3.getChecksum());

        provider.remove(bucket, 5, doc1.getId());
        BucketInfo info4 = provider.getBucketInfo(bucket).getBucketInfo();
        provider.flush(bucket);

        assertEquals(1, info4.getDocumentCount());
        assertTrue(info4.getChecksum() != 0);
    }


    void testOrderIndependentBucketInfo(PersistenceProvider spi)
    {
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        Document doc2 = testDocMan.createRandomDocumentAtLocation(0x01, 2);
        spi.createBucket(bucket);

        int checksumOrdered = 0;

        {
            spi.put(bucket, 2, doc1);
            spi.put(bucket, 3, doc2);
            spi.flush(bucket);
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();

            checksumOrdered = info.getChecksum();
            assertTrue(checksumOrdered != 0);
        }

        spi.deleteBucket(bucket);
        spi.createBucket(bucket);
        assertEquals(0, spi.getBucketInfo(bucket).getBucketInfo().getChecksum());

        int checksumUnordered = 0;

        {
            // Swap order of puts
            spi.put(bucket, 3, doc2);
            spi.put(bucket, 2, doc1);
            spi.flush(bucket);
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();

            checksumUnordered = info.getChecksum();
            assertTrue(checksumUnordered != 0);
        }

        assertEquals(checksumOrdered, checksumUnordered);
    }

    void testPut(PersistenceProvider spi) {
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        spi.createBucket(bucket);

        assertEquals(new Result(), spi.put(bucket, 3, doc1));
        BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
        spi.flush(bucket);

        assertEquals(1, (int)info.getDocumentCount());
        assertTrue(info.getEntryCount() >= info.getDocumentCount());
        assertTrue(info.getChecksum() != 0);
        assertTrue(info.getDocumentSize() > 0);
        assertTrue(info.getUsedSize() >= info.getDocumentSize());
    }

    void testPutNewDocumentVersion(PersistenceProvider spi) {
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        Document doc2 = doc1.clone();


        doc2.setFieldValue("content", new StringFieldValue("hiho silver"));
        spi.createBucket(bucket);

        Result result = spi.put(bucket, 3, doc1);
        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            spi.flush(bucket);

            assertEquals(1, (int)info.getDocumentCount());
            assertTrue(info.getEntryCount() >= info.getDocumentCount());
            assertTrue(info.getChecksum() != 0);
            assertTrue(info.getDocumentSize() > 0);
            assertTrue(info.getUsedSize() >= info.getDocumentSize());
        }

        result = spi.put(bucket, 4, doc2);
        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            spi.flush(bucket);

            assertEquals(1, (int)info.getDocumentCount());
            assertTrue(info.getEntryCount() >= info.getDocumentCount());
            assertTrue(info.getChecksum() != 0);
            assertTrue(info.getDocumentSize() > 0);
            assertTrue(info.getUsedSize() >= info.getDocumentSize());
        }

        GetResult gr = spi.get(bucket, new AllFields(), doc1.getId());

        assertEquals(Result.ErrorType.NONE, gr.getErrorType());
        assertEquals(4, gr.getLastModifiedTimestamp());
        assertEquals(doc2, gr.getDocument());
    }

    void testPutOlderDocumentVersion(PersistenceProvider spi) {
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        Document doc2 = doc1.clone();
        doc2.setFieldValue("content", new StringFieldValue("hiho silver"));
        spi.createBucket(bucket);

        Result result = spi.put(bucket, 5, doc1);
        BucketInfo info1 = spi.getBucketInfo(bucket).getBucketInfo();
        spi.flush(bucket);
        {
            assertEquals(1, info1.getDocumentCount());
            assertTrue(info1.getEntryCount() >= info1.getDocumentCount());
            assertTrue(info1.getChecksum() != 0);
            assertTrue(info1.getDocumentSize() > 0);
            assertTrue(info1.getUsedSize() >= info1.getDocumentSize());
        }

        result = spi.put(bucket, 4, doc2);
        {
            BucketInfo info2 = spi.getBucketInfo(bucket).getBucketInfo();
            spi.flush(bucket);

            assertEquals(1, info2.getDocumentCount());
            assertTrue(info2.getEntryCount() >= info1.getDocumentCount());
            assertEquals(info1.getChecksum(), info2.getChecksum());
            assertEquals(info1.getDocumentSize(), info2.getDocumentSize());
            assertTrue(info2.getUsedSize() >= info1.getDocumentSize());
        }

        GetResult gr = spi.get(bucket, new AllFields(), doc1.getId());

        assertEquals(Result.ErrorType.NONE, gr.getErrorType());
        assertEquals(5, gr.getLastModifiedTimestamp());
        assertEquals(doc1, gr.getDocument());
    }

    void testPutDuplicate(PersistenceProvider spi) throws Exception {
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        spi.createBucket(bucket);
        assertEquals(new Result(), spi.put(bucket, 3, doc1));

        int checksum;
        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            spi.flush(bucket);
            assertEquals(1, (int)info.getDocumentCount());
            checksum = info.getChecksum();
        }
        assertEquals(new Result(), spi.put(bucket, 3, doc1));

        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            spi.flush(bucket);
            assertEquals(1, (int)info.getDocumentCount());
            assertEquals(checksum, info.getChecksum());
        }

        List<DocEntry> entries = iterateBucket(spi, bucket, PersistenceProvider.IncludedVersions.ALL_VERSIONS);
        assertEquals(1, entries.size());
    }


    void testRemove(PersistenceProvider spi) throws Exception {
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        spi.createBucket(bucket);

        Result result = spi.put(bucket, 3, doc1);

        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            spi.flush(bucket);

            assertEquals(1, (int)info.getDocumentCount());
            assertTrue(info.getChecksum() != 0);

            List<DocEntry> entries = iterateBucket(spi, bucket, PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);
            assertEquals(1, entries.size());
        }

        RemoveResult result2 = spi.removeIfFound(bucket, 5, doc1.getId());

        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            spi.flush(bucket);

            assertEquals(0, info.getDocumentCount());
            assertEquals(0, info.getChecksum());
            assertEquals(true, result2.wasFound());
        }

        assertEquals(0, iterateBucket(spi, bucket, PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY).size());
        assertEquals(1, iterateBucket(spi, bucket, PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_OR_REMOVE).size());

        RemoveResult result3 = spi.remove(bucket, 7, doc1.getId());

        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            spi.flush(bucket);

            assertEquals(0, (int)info.getDocumentCount());
            assertEquals(0, (int)info.getChecksum());
            assertEquals(false, result3.wasFound());
        }

        Result result4 = spi.put(bucket, 9, doc1);
        spi.flush(bucket);

        assertTrue(!result4.hasError());

        RemoveResult result5 = spi.remove(bucket, 9, doc1.getId());

        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            spi.flush(bucket);

            assertEquals(0, (int)info.getDocumentCount());
            assertEquals(0, (int)info.getChecksum());
            assertEquals(true, result5.wasFound());
            assertTrue(!result5.hasError());
        }

        GetResult getResult = spi.get(bucket, new AllFields(), doc1.getId());
        assertEquals(Result.ErrorType.NONE, getResult.getErrorType());
        assertEquals(0, getResult.getLastModifiedTimestamp());
        assertNull(getResult.getDocument());
    }

    void testRemoveMerge(PersistenceProvider spi) throws Exception {
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        DocumentId removeId = new DocumentId("userdoc:fraggle:1:rock");
        spi.createBucket(bucket);

        Result result = spi.put(bucket, 3, doc1);

        // Remove a document that does not exist
        {
            RemoveResult removeResult = spi.remove(bucket, 10, removeId);
            spi.flush(bucket);
            assertEquals(Result.ErrorType.NONE, removeResult.getErrorType());
            assertEquals(false, removeResult.wasFound());
        }
        // In a merge case, there might be multiple removes for the same document
        // if resending et al has taken place. These must all be added.
        {
            RemoveResult removeResult = spi.remove(bucket,
                    5,
                    removeId);
            spi.flush(bucket);
            assertEquals(Result.ErrorType.NONE, removeResult.getErrorType());
            assertEquals(false, removeResult.wasFound());
        }
        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();

            assertEquals(1, info.getDocumentCount());
            assertEquals(3, info.getEntryCount());
            assertTrue(info.getChecksum() != 0);
        }
        assertFalse(spi.flush(bucket).hasError());

        List<DocEntry> entries = iterateBucket(spi, bucket, PersistenceProvider.IncludedVersions.ALL_VERSIONS);
        // Remove entries should exist afterwards
        assertEquals(3, entries.size());
        for (int i = 2; i > 0; --i) {
            assertEquals((i == 2) ? 10 : 5, entries.get(i).getTimestamp());
            assertTrue(entries.get(i).getType() == DocEntry.Type.REMOVE_ENTRY);
            assertNotNull(entries.get(i).getDocumentId());
            assertEquals(removeId, entries.get(i).getDocumentId());
        }

        // Result tagged as document not found if CONVERT_PUT_TO_REMOVE flag is given and
        // timestamp does not exist, and PERSIST_NONEXISTING is not set

        // CONVERTED_REMOVE flag should be set if CONVERT_PUT_TO_REMOVE is set.

        // Trying to turn a remove without CONVERTED_REMOVE flag set into
        // unrevertable remove should work. (Should likely log warning, but we want
        // to do it anyways, in order to get bucket copies in sync if it happens)

        // Timestamps should not have been changed.

        // Verify that a valid and altered bucket info is returned on success
    }

    void testUpdate(PersistenceProvider spi) {
        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));
        spi.createBucket(bucket);

        DocumentType docType = testDocMan.getDocumentType("testdoctype1");

        DocumentUpdate update = new DocumentUpdate(docType, doc1.getId());
        FieldUpdate fieldUpdate = FieldUpdate.create(docType.getField("headerval"));
        fieldUpdate.addValueUpdate(AssignValueUpdate.createAssign(new IntegerFieldValue(42)));
        update.addFieldUpdate(fieldUpdate);

        {
            UpdateResult result = spi.update(bucket, 3, update);
            spi.flush(bucket);
            assertEquals(Result.ErrorType.NONE, result.getErrorType());
            assertEquals(0, result.getExistingTimestamp());
        }

        spi.put(bucket, 3, doc1);
        {
            UpdateResult result = spi.update(bucket, 4, update);
            spi.flush(bucket);

            assertEquals(Result.ErrorType.NONE, result.getErrorType());
            assertEquals(3, result.getExistingTimestamp());
        }

        {
            GetResult result = spi.get(bucket, new AllFields(), doc1.getId());

            assertEquals(Result.ErrorType.NONE, result.getErrorType());
            assertEquals(4, result.getLastModifiedTimestamp());
            assertEquals(new IntegerFieldValue(42), result.getDocument().getFieldValue("headerval"));
        }

        spi.remove(bucket, 5, doc1.getId());
        spi.flush(bucket);

        {
            GetResult result = spi.get(bucket, new AllFields(),  doc1.getId());

            assertEquals(Result.ErrorType.NONE, result.getErrorType());
            assertEquals(0, result.getLastModifiedTimestamp());
            assertNull(result.getDocument());
        }


        {
            UpdateResult result = spi.update(bucket, 6, update);
            spi.flush(bucket);

            assertEquals(Result.ErrorType.NONE, result.getErrorType());
            assertEquals(0, result.getExistingTimestamp());
        }
    }

    void testGet(PersistenceProvider spi) {
        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        spi.createBucket(bucket);

        {
            GetResult result = spi.get(bucket, new AllFields(), doc1.getId());

            assertEquals(Result.ErrorType.NONE, result.getErrorType());
            assertEquals(0, result.getLastModifiedTimestamp());
        }

        spi.put(bucket, 3, doc1);
        spi.flush(bucket);

        {
            GetResult result = spi.get(bucket, new AllFields(), doc1.getId());
            assertEquals(doc1, result.getDocument());
            assertEquals(3, result.getLastModifiedTimestamp());
        }

        spi.remove(bucket,
                4,
                doc1.getId());
        spi.flush(bucket);

        {
            GetResult result = spi.get(bucket, new AllFields(), doc1.getId());

            assertEquals(Result.ErrorType.NONE, result.getErrorType());
            assertEquals(0, result.getLastModifiedTimestamp());
        }
    }

    void
    testIterateCreateIterator(PersistenceProvider spi) throws Exception
    {

        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        spi.createBucket(bucket);

        CreateIteratorResult result = spi.createIterator(bucket, new AllFields(), new Selection("", 0, Long.MAX_VALUE), PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);
        assertEquals(Result.ErrorType.NONE, result.getErrorType());
        // Iterator ID 0 means invalid iterator, so cannot be returned
        // from a successful createIterator call.
        assertTrue(result.getIteratorId() != 0);

        spi.destroyIterator(result.getIteratorId());
    }

    void
    testIterateDestroyIterator(PersistenceProvider spi) throws Exception
    {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("", 0, Long.MAX_VALUE), PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);

        {
            IterateResult result = spi.iterate(iter.getIteratorId(), 1024);
            assertEquals(Result.ErrorType.NONE, result.getErrorType());
        }

        {
            Result destroyResult = spi.destroyIterator(iter.getIteratorId());
            assertTrue(!destroyResult.hasError());
        }
        // Iteration should now fail
        {
            IterateResult result = spi.iterate(iter.getIteratorId(), 1024);
            assertEquals(Result.ErrorType.PERMANENT_ERROR, result.getErrorType());
        }
        {
            Result destroyResult = spi.destroyIterator(iter.getIteratorId());
            assertTrue(!destroyResult.hasError());
        }
    }

    List<DocEntry> feedDocs(PersistenceProvider spi, Bucket bucket,
                            int numDocs,
                            int minSize,
                            int maxSize)
    {
        List<DocEntry> docs = new ArrayList<DocEntry>();

        for (int i = 0; i < numDocs; ++i) {
            Document doc = testDocMan.createRandomDocumentAtLocation(
                    bucket.getBucketId().getId(),
                    i,
                    minSize,
                    maxSize);
            Result result = spi.put(bucket, 1000 + i, doc);
            assertTrue(!result.hasError());
            docs.add(new DocEntry(1000 + i, doc));
        }
        assertEquals(new Result(), spi.flush(bucket));
        return docs;
    }

    void
    testIterateWithUnknownId(PersistenceProvider spi)
    {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        IterateResult result = spi.iterate(123, 1024);
        assertEquals(Result.ErrorType.PERMANENT_ERROR, result.getErrorType());
    }

    /**
     * Do a full bucket iteration, returning a vector of DocEntry chunks.
     */
    List<IterateResult> doIterate(PersistenceProvider spi,
                                  long id,
                                  long maxByteSize,
                                  int maxChunks)
    {
        List<IterateResult> chunks = new ArrayList<IterateResult>();

        while (true) {
            IterateResult result = spi.iterate(id, maxByteSize);
            assertFalse(result.hasError());

            assertTrue(result.getEntries().size() > 0);
            chunks.add(result);

            if (result.isCompleted()
                    || (maxChunks != 0 && chunks.size() >= maxChunks))
            {
                break;
            }
        }
        return chunks;
    }

    boolean containsDocument(List<IterateResult> chunks, Document doc) {
        for (IterateResult i : chunks) {
            for (DocEntry e : i.getEntries()) {
                if (e.getType() == DocEntry.Type.PUT_ENTRY && e.getDocument() != null && e.getDocument().equals(doc)) {
                    return true;
                }
            }
        }

        return false;
    }

    boolean containsRemove(List<IterateResult> chunks, String docId) {
        for (IterateResult i : chunks) {
            for (DocEntry e : i.getEntries()) {
                if (e.getType() == DocEntry.Type.REMOVE_ENTRY && e.getDocumentId() != null && e.getDocumentId().toString().equals(docId)) {
                    return true;
                }
            }
        }

        return false;
    }

    void verifyDocs(List<DocEntry> docs, List<IterateResult> chunks, List<String> removes) {
        int docCount = 0;
        int removeCount = 0;
        for (IterateResult result : chunks) {
            for (DocEntry e : result.getEntries()) {
                if (e.getType() == DocEntry.Type.PUT_ENTRY) {
                    ++docCount;
                } else {
                    ++removeCount;
                }
            }
        }

        assertEquals(docs.size(), docCount);

        for (DocEntry e : docs) {
            assertTrue(e.getDocument().toString(), containsDocument(chunks, e.getDocument()));
        }

        if (removes != null) {
            assertEquals(removes.size(), removeCount);

            for (String docId : removes) {
                assertTrue(docId, containsRemove(chunks, docId));
            }
        }
    }

    void verifyDocs(List<DocEntry> docs, List<IterateResult> chunks) {
        verifyDocs(docs, chunks, null);
    }


    void testIterateAllDocs(PersistenceProvider spi) throws Exception {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        List<DocEntry> docs = feedDocs(spi, b, 100, 110, 110);

        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("", 0, Long.MAX_VALUE), PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);

        List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 4096, 0);
        verifyDocs(docs, chunks);

        spi.destroyIterator(iter.getIteratorId());
    }

    void testIterateAllDocsNewestVersionOnly(PersistenceProvider spi) throws Exception {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        List<DocEntry> docs = feedDocs(spi, b, 100, 110, 110);
        List<DocEntry> newDocs = new ArrayList<DocEntry>();

        for (DocEntry e : docs) {
            Document newDoc = e.getDocument().clone();
            newDoc.setFieldValue("headerval", new IntegerFieldValue(5678 + (int)e.getTimestamp()));
            spi.put(b, 1000 + e.getTimestamp(), newDoc);
            newDocs.add(new DocEntry(1000 + e.getTimestamp(), newDoc));
        }

        spi.flush(b);

        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("", 0, Long.MAX_VALUE), PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);

        List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 4096, 0);

        verifyDocs(newDocs, chunks);

        spi.destroyIterator(iter.getIteratorId());
    }


    void testIterateChunked(PersistenceProvider spi) throws Exception
    {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        List<DocEntry> docs = feedDocs(spi, b, 100, 110, 110);
        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("", 0, Long.MAX_VALUE), PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);

        List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 1, 0);
        assertEquals(100, chunks.size());
        verifyDocs(docs, chunks);

        spi.destroyIterator(iter.getIteratorId());
    }

    void
    testIterateMaxByteSize(PersistenceProvider spi) throws Exception
    {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        List<DocEntry> docs = feedDocs(spi, b, 100, 4096, 4096);

        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("", 0, Long.MAX_VALUE), PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);

        // Docs are 4k each and iterating with max combined size of 10k.
        // Should receive no more than 3 docs in each chunk
        List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 10000, 0);
        assertTrue("Expected >= 33 chunks, got " + chunks.size(), chunks.size() >= 33);
        verifyDocs(docs, chunks);

        spi.destroyIterator(iter.getIteratorId());
    }

    void
    testIterateMatchTimestampRange(PersistenceProvider spi) throws Exception
    {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        List<DocEntry> docsToVisit = new ArrayList<DocEntry>();

        long fromTimestamp = 1010;
        long toTimestamp = 1060;

        for (int i = 0; i < 99; i++) {
            long timestamp = 1000 + i;

            Document doc = testDocMan.createRandomDocumentAtLocation(1, timestamp);

            spi.put(b, timestamp, doc);
            if (timestamp >= fromTimestamp && timestamp <= toTimestamp) {
                docsToVisit.add(new DocEntry(timestamp, doc));
            }
        }
        spi.flush(b);

        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("", fromTimestamp, toTimestamp),
                PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);

        List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 2048, 0);
        verifyDocs(docsToVisit, chunks);

        spi.destroyIterator(iter.getIteratorId());
    }

    void testIterateExplicitTimestampSubset(PersistenceProvider spi) throws Exception {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        List<DocEntry> docsToVisit = new ArrayList<DocEntry>();
        Set<Long> timestampsToVisit = new TreeSet<Long>();
        List<String> removes = new ArrayList<String>();


        for (int i = 0; i < 99; i++) {
            long timestamp = 1000 + i;
            Document doc = testDocMan.createRandomDocumentAtLocation(1, timestamp, 110, 110);

            spi.put(b, timestamp, doc);
            if (timestamp % 3 == 0) {
                docsToVisit.add(new DocEntry(timestamp, doc));
                timestampsToVisit.add(timestamp);
            }
        }

        assertTrue(spi.remove(b, 2000, docsToVisit.get(0).getDocument().getId()).wasFound());
        spi.flush(b);

        timestampsToVisit.add(2000l);
        removes.add(docsToVisit.get(0).getDocument().getId().toString());
        timestampsToVisit.remove(docsToVisit.get(0).getTimestamp());
        docsToVisit.remove(docsToVisit.get(0));

        // When selecting a timestamp subset, we should ignore IncludedVersions, and return all matches regardless.
        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection(timestampsToVisit),
                PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);

        List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 2048, 0);

        verifyDocs(docsToVisit, chunks, removes);

        spi.destroyIterator(iter.getIteratorId());
    }

    void testIterateRemoves(PersistenceProvider spi) throws Exception {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        int docCount = 10;
        List<DocEntry> docs = feedDocs(spi, b, docCount, 100, 100);
        List<String> removedDocs = new ArrayList<String>();
        List<DocEntry> nonRemovedDocs = new ArrayList<DocEntry>();

        for (int i = 0; i < docCount; ++i) {
            if (i % 3 == 0) {
                removedDocs.add(docs.get(i).getDocument().getId().toString());
                assertTrue(spi.remove(b, 2000 + i, docs.get(i).getDocument().getId()).wasFound());
            } else {
                nonRemovedDocs.add(docs.get(i));
            }
        }
        spi.flush(b);

        // First, test iteration without removes
        {
            CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("", 0, Long.MAX_VALUE),
                    PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);

            List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 4096, 0);
            verifyDocs(nonRemovedDocs, chunks);
            spi.destroyIterator(iter.getIteratorId());
        }

        {
            CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("", 0, Long.MAX_VALUE),
                    PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_OR_REMOVE);

            List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 4096, 0);
            verifyDocs(nonRemovedDocs, chunks, removedDocs);
            spi.destroyIterator(iter.getIteratorId());
        }
    }

    void testIterateMatchSelection(PersistenceProvider spi) throws Exception
    {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        List<DocEntry> docsToVisit = new ArrayList<DocEntry>();

        for (int i = 0; i < 99; i++) {
            Document doc = testDocMan.createRandomDocumentAtLocation(1, 1000 + i, 110, 110);
            doc.setFieldValue("headerval", new IntegerFieldValue(i));

            spi.put(b, 1000 + i, doc);
            if ((i % 3) == 0) {
                docsToVisit.add(new DocEntry(1000 + i, doc));
            }
        }
        spi.flush(b);

        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("testdoctype1.headerval % 3 == 0", 0, Long.MAX_VALUE),
                PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_ONLY);

        List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 2048, 0);
        verifyDocs(docsToVisit, chunks);

        spi.destroyIterator(iter.getIteratorId());
    }

    void testIterationRequiringDocumentIdOnlyMatching(PersistenceProvider spi) throws Exception
    {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        feedDocs(spi, b, 100, 100, 100);
        DocumentId removedId = new DocumentId("userdoc:blarg:1:unknowndoc");

        // Document does not already exist, remove should create a
        // remove entry for it regardless.
        assertFalse(spi.remove(b, 2000, removedId).wasFound());
        spi.flush(b);

        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("id == '" + removedId.toString() + "'", 0, Long.MAX_VALUE),
                PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_OR_REMOVE);

        List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 4096, 0);
        List<String> removes = new ArrayList<String>();
        List<DocEntry> docs = new ArrayList<DocEntry>();

        removes.add(removedId.toString());
        verifyDocs(docs, chunks, removes);

        spi.destroyIterator(iter.getIteratorId());
    }

    void testIterateAlreadyCompleted(PersistenceProvider spi) throws Exception
    {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        List<DocEntry> docs = feedDocs(spi, b, 10, 100, 100);
        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("", 0, Long.MAX_VALUE),
                PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_OR_REMOVE);

        List<IterateResult> chunks = doIterate(spi, iter.getIteratorId(), 4096, 0);
        verifyDocs(docs, chunks);

        IterateResult result = spi.iterate(iter.getIteratorId(), 4096);
        assertEquals(Result.ErrorType.NONE, result.getErrorType());
        assertEquals(0, result.getEntries().size());
        assertTrue(result.isCompleted());

        spi.destroyIterator(iter.getIteratorId());
    }

    void testIterateEmptyBucket(PersistenceProvider spi) throws Exception
    {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);

        CreateIteratorResult iter = spi.createIterator(b, new AllFields(), new Selection("", 0, Long.MAX_VALUE),
                PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_OR_REMOVE);

        IterateResult result = spi.iterate(iter.getIteratorId(), 4096);
        assertEquals(Result.ErrorType.NONE, result.getErrorType());
        assertEquals(0, result.getEntries().size());
        assertTrue(result.isCompleted());

        spi.destroyIterator(iter.getIteratorId());
    }

    void testDeleteBucket(PersistenceProvider spi) throws Exception
    {
        Bucket b = new Bucket((short)0, new BucketId(8, 0x1));
        spi.createBucket(b);
        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);

        spi.put(b, 3, doc1);
        spi.flush(b);

        spi.deleteBucket(b);
        GetResult result = spi.get(b, new AllFields(), doc1.getId());

        assertEquals(Result.ErrorType.NONE, result.getErrorType());
        assertEquals(0, result.getLastModifiedTimestamp());
    }


    void testSplitNormalCase(PersistenceProvider spi)
    {
        Bucket bucketA = new Bucket((short)0, new BucketId(3, 0x2));
        Bucket bucketB = new Bucket((short)0, new BucketId(3, 0x6));

        Bucket bucketC = new Bucket((short)0, new BucketId(2, 0x2));
        spi.createBucket(bucketC);

        for (int i = 0; i < 10; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, i);
            spi.put(bucketC, i + 1, doc1);
        }

        for (int i = 10; i < 20; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            spi.put(bucketC, i + 1, doc1);
        }

        spi.flush(bucketC);

        spi.split(bucketC, bucketA, bucketB);
        testSplitNormalCasePostCondition(spi, bucketA, bucketB, bucketC);
        /*if (_factory->hasPersistence()) {
            spi.reset();
            document::TestDocMan testDocMan2;
            spi = getSpi(*_factory, testDocMan2);
            testSplitNormalCasePostCondition(spi, bucketA, bucketB, bucketC,
                    testDocMan2);
        }*/
    }


    void testSplitNormalCasePostCondition(PersistenceProvider spi, Bucket bucketA,
                                          Bucket bucketB, Bucket bucketC)
    {
        assertEquals(10, spi.getBucketInfo(bucketA).getBucketInfo().
                getDocumentCount());
        assertEquals(10, spi.getBucketInfo(bucketB).getBucketInfo().
                getDocumentCount());

        FieldSet fs = new AllFields();
        for (int i = 0; i < 10; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, i);
            assertTrue(spi.get(bucketA, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketC, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketB, fs, doc1.getId()).hasDocument());
        }

        for (int i = 10; i < 20; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            assertTrue(spi.get(bucketB, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketA, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketC, fs, doc1.getId()).hasDocument());
        }
    }

    void testSplitTargetExists(PersistenceProvider spi) throws Exception
    {
        Bucket bucketA = new Bucket((short)0, new BucketId(3, 0x2));
        Bucket bucketB = new Bucket((short)0, new BucketId(3, 0x6));
        spi.createBucket(bucketB);

        Bucket bucketC = new Bucket((short)0, new BucketId(2, 0x2));
        spi.createBucket(bucketC);

        for (int i = 0; i < 10; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, i);
            spi.put(bucketC, i + 1, doc1);
        }

        spi.flush(bucketC);

        for (int i = 10; i < 20; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            spi.put(bucketB, i + 1, doc1);
        }
        spi.flush(bucketB);
        assertTrue(!spi.getBucketInfo(bucketB).getBucketInfo().isActive());

        for (int i = 10; i < 20; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            spi.put(bucketC, i + 1, doc1);
        }
        spi.flush(bucketC);

        for (int i = 20; i < 25; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            spi.put(bucketB, i + 1, doc1);
        }

        spi.flush(bucketB);

        spi.split(bucketC, bucketA, bucketB);
        testSplitTargetExistsPostCondition(spi, bucketA, bucketB, bucketC);
        /*if (_factory->hasPersistence()) {
            spi.reset();
            document::TestDocMan testDocMan2;
            spi = getSpi(*_factory, testDocMan2);
            testSplitTargetExistsPostCondition(spi, bucketA, bucketB, bucketC,
                    testDocMan2);
        }*/
    }


    void testSplitTargetExistsPostCondition(PersistenceProvider spi, Bucket bucketA,
                                            Bucket bucketB, Bucket bucketC)
    {
        assertEquals(10, spi.getBucketInfo(bucketA).getBucketInfo().
                getDocumentCount());
        assertEquals(15, spi.getBucketInfo(bucketB).getBucketInfo().
                getDocumentCount());

        FieldSet fs = new AllFields();
        for (int i = 0; i < 10; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, i);
            assertTrue(spi.get(bucketA, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketC, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketB, fs, doc1.getId()).hasDocument());
        }

        for (int i = 10; i < 25; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            assertTrue(spi.get(bucketB, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketA, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketC, fs, doc1.getId()).hasDocument());
        }
    }

    void testJoinNormalCase(PersistenceProvider spi) throws Exception
    {
        Bucket bucketA = new Bucket((short)0, new BucketId(3, 0x02));
        spi.createBucket(bucketA);

        Bucket bucketB = new Bucket((short)0, new BucketId(3, 0x06));
        spi.createBucket(bucketB);

        Bucket bucketC = new Bucket((short)0, new BucketId(2, 0x02));

        for (int i = 0; i < 10; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, i);
            spi.put(bucketA, i + 1, doc1);
        }

        spi.flush(bucketA);

        for (int i = 10; i < 20; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            spi.put(bucketB, i + 1, doc1);
        }

        spi.flush(bucketB);

        spi.join(bucketA, bucketB, bucketC);
        testJoinNormalCasePostCondition(spi, bucketA, bucketB, bucketC);
        /*if (_factory->hasPersistence()) {
            spi.reset();
            document::TestDocMan testDocMan2;
            spi = getSpi(*_factory, testDocMan2);
            testJoinNormalCasePostCondition(spi, bucketA, bucketB, bucketC,
                    testDocMan2);
        }*/
    }

    void testJoinNormalCasePostCondition(PersistenceProvider spi, Bucket bucketA,
                                         Bucket bucketB, Bucket bucketC)
    {
        assertEquals(20, spi.getBucketInfo(bucketC).
                getBucketInfo().getDocumentCount());

        FieldSet fs = new AllFields();
        for (int i = 0; i < 10; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, i);
            assertTrue(spi.get(bucketC, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketA, fs, doc1.getId()).hasDocument());
        }

        for (int i = 10; i < 20; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            assertTrue(spi.get(bucketC, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketB, fs, doc1.getId()).hasDocument());
        }
    }

    void testJoinTargetExists(PersistenceProvider spi) throws Exception
    {
        Bucket bucketA = new Bucket((short)0, new BucketId(3, 0x02));
        spi.createBucket(bucketA);

        Bucket bucketB = new Bucket((short)0, new BucketId(3, 0x06));
        spi.createBucket(bucketB);

        Bucket bucketC = new Bucket((short)0, new BucketId(2, 0x02));
        spi.createBucket(bucketC);

        for (int i = 0; i < 10; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, i);
            spi.put(bucketA, i + 1, doc1);
        }

        spi.flush(bucketA);

        for (int i = 10; i < 20; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            spi.put(bucketB, i + 1, doc1);
        }
        spi.flush(bucketB);

        for (int i = 20; i < 30; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            spi.put(bucketC, i + 1, doc1);
        }
        spi.flush(bucketC);

        spi.join(bucketA, bucketB, bucketC);
        testJoinTargetExistsPostCondition(spi, bucketA, bucketB, bucketC);
        /*if (_factory->hasPersistence()) {
            spi.reset();
            document::TestDocMan testDocMan2;
            spi = getSpi(*_factory, testDocMan2);
            testJoinTargetExistsPostCondition(spi, bucketA, bucketB, bucketC,
                    testDocMan2);
        }*/
    }

    void testJoinTargetExistsPostCondition(PersistenceProvider spi, Bucket bucketA,
                                           Bucket bucketB, Bucket bucketC)
    {
        assertEquals(30, spi.getBucketInfo(bucketC).getBucketInfo().
                getDocumentCount());

        FieldSet fs = new AllFields();
        for (int i = 0; i < 10; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, i);
            assertTrue(spi.get(bucketC, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketA, fs, doc1.getId()).hasDocument());
        }

        for (int i = 10; i < 20; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            assertTrue(spi.get(bucketC, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketB, fs, doc1.getId()).hasDocument());
        }

        for (int i = 20; i < 30; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x06, i);
            assertTrue(spi.get(bucketC, fs, doc1.getId()).hasDocument());
        }
    }

    void testJoinOneBucket(PersistenceProvider spi) throws Exception
    {
        Bucket bucketA = new Bucket((short)0, new BucketId(3, 0x02));
        spi.createBucket(bucketA);

        Bucket bucketB = new Bucket((short)0, new BucketId(3, 0x06));
        Bucket bucketC = new Bucket((short)0, new BucketId(2, 0x02));

        for (int i = 0; i < 10; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, i);
            spi.put(bucketA, i + 1, doc1);
        }
        spi.flush(bucketA);

        spi.join(bucketA, bucketB, bucketC);
        testJoinOneBucketPostCondition(spi, bucketA, bucketC);
        /*if (_factory->hasPersistence()) {
            spi.reset();
            document::TestDocMan testDocMan2;
            spi = getSpi(*_factory, testDocMan2);
            testJoinOneBucketPostCondition(spi, bucketA, bucketC, testDocMan2);
        }*/
    }

    void testJoinOneBucketPostCondition(PersistenceProvider spi, Bucket bucketA, Bucket bucketC)
    {
        assertEquals(10, spi.getBucketInfo(bucketC).getBucketInfo().
                getDocumentCount());

        FieldSet fs = new AllFields();
        for (int i = 0; i < 10; ++i) {
            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, i);
            assertTrue(spi.get(bucketC, fs, doc1.getId()).hasDocument());
            assertTrue(!spi.get(bucketA, fs, doc1.getId()).hasDocument());
        }
    }


    void testMaintain(PersistenceProvider spi) throws Exception {
        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);

        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));
        spi.createBucket(bucket);

        spi.put(bucket, 3, doc1);
        spi.flush(bucket);

        assertEquals(Result.ErrorType.NONE,
                     spi.maintain(bucket, PersistenceProvider.MaintenanceLevel.LOW).getErrorType());
    }

    void testGetModifiedBuckets(PersistenceProvider spi) throws Exception {
        assertEquals(0, spi.getModifiedBuckets().getBuckets().size());
    }

    void testBucketActivation(PersistenceProvider spi) throws Exception {
        Bucket bucket = new Bucket((short)0, new BucketId(8, 0x01));

        spi.createBucket(bucket);
        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            assertTrue(!info.isActive());
        }

        spi.setActiveState(bucket, BucketInfo.ActiveState.ACTIVE);
        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            assertTrue(info.isActive());
        }

        spi.setActiveState(bucket, BucketInfo.ActiveState.NOT_ACTIVE);
        {
            BucketInfo info = spi.getBucketInfo(bucket).getBucketInfo();
            assertTrue(!info.isActive());
        }
    }

    void testBucketActivationSplitAndJoin(PersistenceProvider spi) throws Exception
    {
        Bucket bucketA = new Bucket((short)0, new BucketId(3, 0x02));
        Bucket bucketB = new Bucket((short)0, new BucketId(3, 0x06));
        Bucket bucketC = new Bucket((short)0, new BucketId(2, 0x02));
        Document doc1 = testDocMan.createRandomDocumentAtLocation(0x02, 1);
        Document doc2 = testDocMan.createRandomDocumentAtLocation(0x06, 2);

        spi.createBucket(bucketC);
        spi.put(bucketC, 1, doc1);
        spi.put(bucketC, 2, doc2);
        spi.flush(bucketC);

        spi.setActiveState(bucketC, BucketInfo.ActiveState.ACTIVE);
        assertTrue(spi.getBucketInfo(bucketC).getBucketInfo().isActive());
        spi.split(bucketC, bucketA, bucketB);
        assertTrue(spi.getBucketInfo(bucketA).getBucketInfo().isActive());
        assertTrue(spi.getBucketInfo(bucketB).getBucketInfo().isActive());
        assertTrue(!spi.getBucketInfo(bucketC).getBucketInfo().isActive());

        spi.setActiveState(bucketA, BucketInfo.ActiveState.NOT_ACTIVE);
        spi.setActiveState(bucketB, BucketInfo.ActiveState.NOT_ACTIVE);
        spi.join(bucketA, bucketB, bucketC);
        assertTrue(!spi.getBucketInfo(bucketA).getBucketInfo().isActive());
        assertTrue(!spi.getBucketInfo(bucketB).getBucketInfo().isActive());
        assertTrue(!spi.getBucketInfo(bucketC).getBucketInfo().isActive());

        spi.split(bucketC, bucketA, bucketB);
        assertTrue(!spi.getBucketInfo(bucketA).getBucketInfo().isActive());
        assertTrue(!spi.getBucketInfo(bucketB).getBucketInfo().isActive());
        assertTrue(!spi.getBucketInfo(bucketC).getBucketInfo().isActive());

        spi.setActiveState(bucketA, BucketInfo.ActiveState.ACTIVE);
        spi.join(bucketA, bucketB, bucketC);
        assertTrue(!spi.getBucketInfo(bucketA).getBucketInfo().isActive());
        assertTrue(!spi.getBucketInfo(bucketB).getBucketInfo().isActive());
        assertTrue(spi.getBucketInfo(bucketC).getBucketInfo().isActive());
    }
//
//            void testRemoveEntry()
//            {
//                if (!_factory->supportsRemoveEntry()) {
//                    return;
//                }
//                document::TestDocMan testDocMan;
//                _factory->clear();
//                PersistenceProvider::UP spi(getSpi(*_factory, testDocMan));
//
//            Bucket bucket(document::BucketId(8, 0x01), PartitionId(0));
//            Document doc1 = testDocMan.createRandomDocumentAtLocation(0x01, 1);
//            Document doc2 = testDocMan.createRandomDocumentAtLocation(0x01, 2);
//            spi.createBucket(bucket);
//
//            spi.put(bucket, 3, doc1);
//            spi.flush(bucket);
//            BucketInfo info1 = spi.getBucketInfo(bucket).getBucketInfo();
//
//            {
//                spi.put(bucket, 4, doc2);
//                spi.flush(bucket);
//                spi.removeEntry(bucket, 4);
//                spi.flush(bucket);
//                BucketInfo info2 = spi.getBucketInfo(bucket).getBucketInfo();
//                assertEquals(info1, info2);
//            }
//
//            // Test case where there exists a previous version of the document.
//            {
//                spi.put(bucket, 5, doc1);
//                spi.flush(bucket);
//                spi.removeEntry(bucket, 5);
//                spi.flush(bucket);
//                BucketInfo info2 = spi.getBucketInfo(bucket).getBucketInfo();
//                assertEquals(info1, info2);
//            }
//
//            // Test case where the newest document version after removeEntrying is a remove.
//            {
//                spi.remove(bucket, 6, doc1.getId());
//                spi.flush(bucket);
//                BucketInfo info2 = spi.getBucketInfo(bucket).getBucketInfo();
//                assertEquals(0, info2.getDocumentCount());
//
//                spi.put(bucket, 7, doc1);
//                spi.flush(bucket);
//                spi.removeEntry(bucket, 7);
//                spi.flush(bucket);
//                BucketInfo info3 = spi.getBucketInfo(bucket).getBucketInfo();
//                assertEquals(info2, info3);
//            }
//            }
//
}

//
//// Get number of puts and removes across all chunks (i.e. all entries)
//            size_t
//            getDocCount(const std::vector<Chunk>& chunks)
//            {
//                size_t count = 0;
//                for (size_t i=0; i<chunks.size(); ++i) {
//                    count += chunks[i]._entries.size();
//                }
//                return count;
//            }
//
//            size_t
//            getRemoveEntryCount(const std::vector<spi::DocEntry::UP>& entries)
//            {
//                size_t ret = 0;
//                for (size_t i = 0; i < entries.size(); ++i) {
//                    if (entries[i]->isRemove()) {
//                        ++ret;
//                    }
//                }
//                return ret;
//            }
//
//            List<DocEntry>
//                    getEntriesFromChunks(const std::vector<Chunk>& chunks)
//            {
//                std::vector<spi::DocEntry::UP> ret;
//                for (size_t chunk = 0; chunk < chunks.size(); ++chunk) {
//                    for (size_t i = 0; i < chunks[chunk]._entries.size(); ++i) {
//                        ret.push_back(chunks[chunk]._entries[i]);
//                    }
//                }
//                std::sort(ret.begin(),
//                    ret.end(),
//                    DocEntryIndirectTimestampComparator());
//                return ret;
//            }
//
//
//
//            spi.destroyIterator(iter.getIteratorId());
//            std::sort(ret.begin(),
//                    ret.end(),
//                    DocEntryIndirectTimestampComparator());
//            return ret;
//            }
//
//            void
//            verifyDocs(const std::vector<DocAndTimestamp>& wanted,
//            const std::vector<Chunk>& chunks,
//            const std::set<string>& removes = std::set<string>())
//            {
//                List<DocEntry> retrieved(
//                    getEntriesFromChunks(chunks));
//                size_t removeCount = getRemoveEntryCount(retrieved);
//                // Ensure that we've got the correct number of puts and removes
//                assertEquals(removes.size(), removeCount);
//                assertEquals(wanted.size(), retrieved.size() - removeCount);
//
//                size_t wantedIdx = 0;
//                for (size_t i = 0; i < retrieved.size(); ++i) {
//                    DocEntry& entry(*retrieved[i]);
//                    if (entry.getDocumentOperation() != 0) {
//                        if (!(*wanted[wantedIdx].doc == *entry.getDocumentOperation())) {
//                            std::ostringstream ss;
//                            ss << "Documents differ! Wanted:\n"
//                                    << wanted[wantedIdx].doc->toString(true)
//                                    << "\n\nGot:\n"
//                                    << entry.getDocumentOperation()->toString(true);
//                            CPPUNIT_FAIL(ss.str());
//                        }
//                        assertEquals(wanted[wantedIdx].timestamp, entry.getTimestamp());
//                        size_t serSize = wanted[wantedIdx].doc->serialize()->getLength();
//                        assertEquals(serSize + sizeof(DocEntry), size_t(entry.getSize()));
//                        assertEquals(serSize, size_t(entry.getDocumentSize()));
//                        ++wantedIdx;
//                    } else {
//                        // Remove-entry
//                        assertTrue(entry.getDocumentId() != 0);
//                        size_t serSize = entry.getDocumentId()->getSerializedSize();
//                        assertEquals(serSize + sizeof(DocEntry), size_t(entry.getSize()));
//                        assertEquals(serSize, size_t(entry.getDocumentSize()));
//                        if (removes.find(entry.getDocumentId()->toString()) == removes.end()) {
//                            std::ostringstream ss;
//                            ss << "Got unexpected remove entry for document id "
//                                    << *entry.getDocumentId();
//                            CPPUNIT_FAIL(ss.str());
//                        }
//                    }
//                }
//            }
//
//// Feed numDocs documents, starting from timestamp 1000
//
//            }  // namespace
//
//
//





//            void detectAndTestOptionalBehavior() {
//                // Report if implementation supports setting bucket size info.
//
//                // Report if joining same bucket on multiple partitions work.
//                // (Where target equals one of the sources). (If not supported service
//                // layer must die if a bucket is found during init on multiple partitions)
//                // Test functionality if it works.
//            }
//
//
//        } // spi
//    } // storage
//
//
//}
