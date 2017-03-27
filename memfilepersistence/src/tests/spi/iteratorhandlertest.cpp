// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <set>
#include <vector>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/memfilepersistence/mapper/simplememfileiobuffer.h>
#include <tests/spi/memfiletestutils.h>
#include <tests/spi/simulatedfailurefile.h>
#include <tests/spi/options_builder.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/select/parser.h>

namespace storage {
namespace memfile {
namespace {
    spi::LoadType defaultLoadType(0, "default");
}

class IteratorHandlerTest : public SingleDiskMemFileTestUtils
{
    CPPUNIT_TEST_SUITE(IteratorHandlerTest);
    CPPUNIT_TEST(testCreateIterator);
    CPPUNIT_TEST(testSomeSlotsRemovedBetweenInvocations);
    CPPUNIT_TEST(testAllSlotsRemovedBetweenInvocations);
    CPPUNIT_TEST(testIterateMetadataOnly);
    CPPUNIT_TEST(testIterateHeadersOnly);
    CPPUNIT_TEST(testIterateLargeDocument);
    CPPUNIT_TEST(testDocumentsRemovedBetweenInvocations);
    CPPUNIT_TEST(testUnrevertableRemoveBetweenInvocations);
    CPPUNIT_TEST(testUnrevertableRemoveBetweenInvocationsIncludeRemoves);
    CPPUNIT_TEST(testMatchTimestampRangeDocAltered);
    CPPUNIT_TEST(testIterateAllVersions);
    CPPUNIT_TEST(testFieldSetFiltering);
    CPPUNIT_TEST(testIteratorInactiveOnException);
    CPPUNIT_TEST(testDocsCachedBeforeDocumentSelection);
    CPPUNIT_TEST(testTimestampRangeLimitedPrefetch);
    CPPUNIT_TEST(testCachePrefetchRequirements);
    CPPUNIT_TEST(testBucketEvictedFromCacheOnIterateException);
    CPPUNIT_TEST_SUITE_END();

public:
    void testCreateIterator();
    void testSomeSlotsRemovedBetweenInvocations();
    void testAllSlotsRemovedBetweenInvocations();
    void testIterateMetadataOnly();
    void testIterateHeadersOnly();
    void testIterateLargeDocument();
    void testDocumentsRemovedBetweenInvocations();
    void testUnrevertableRemoveBetweenInvocations();
    void testUnrevertableRemoveBetweenInvocationsIncludeRemoves();
    void testMatchTimestampRangeDocAltered();
    void testIterateAllVersions();
    void testFieldSetFiltering();
    void testIteratorInactiveOnException();
    void testDocsCachedBeforeDocumentSelection();
    void testTimestampRangeLimitedPrefetch();
    void testCachePrefetchRequirements();
    void testBucketEvictedFromCacheOnIterateException();

    void setUp();
    void tearDown();

    struct Chunk
    {
        std::vector<spi::DocEntry::UP> _entries;
    };

private:
    spi::Selection createSelection(const std::string& docSel) const;


    spi::CreateIteratorResult create(
            const spi::Bucket& b,
            const spi::Selection& sel,
            spi::IncludedVersions versions = spi::NEWEST_DOCUMENT_ONLY,
            const document::FieldSet& fieldSet = document::AllFields())
    {
        spi::Context context(defaultLoadType, spi::Priority(0),
                             spi::Trace::TraceLevel(0));
        return getPersistenceProvider().createIterator(b, fieldSet, sel,
                                                       versions, context);
    }

    typedef std::pair<Document::SP, spi::Timestamp> DocAndTimestamp;

    std::vector<DocAndTimestamp> feedDocs(size_t numDocs,
                                          uint32_t minSize = 110,
                                          uint32_t maxSize = 110);

    std::vector<Chunk> doIterate(spi::IteratorId id,
                                 uint64_t maxByteSize,
                                 size_t maxChunks = 0,
                                 bool allowEmptyResult = false);

    void verifyDocs(const std::vector<DocAndTimestamp>& wanted,
                    const std::vector<IteratorHandlerTest::Chunk>& chunks,
                    const std::set<vespalib::string>& removes
                    = std::set<vespalib::string>()) const;

    void doTestUnrevertableRemoveBetweenInvocations(bool includeRemoves);
};

CPPUNIT_TEST_SUITE_REGISTRATION(IteratorHandlerTest);

void
IteratorHandlerTest::setUp()
{
    SingleDiskMemFileTestUtils::setUp();
}

void
IteratorHandlerTest::tearDown()
{
    SingleDiskMemFileTestUtils::tearDown();
}

spi::Selection
IteratorHandlerTest::createSelection(const std::string& docSel) const
{
    return spi::Selection(spi::DocumentSelection(docSel));
}

void
IteratorHandlerTest::testCreateIterator()
{
    spi::Bucket b(BucketId(16, 1234), spi::PartitionId(0));

    spi::CreateIteratorResult iter1(create(b, createSelection("true")));
    CPPUNIT_ASSERT_EQUAL(spi::IteratorId(1), iter1.getIteratorId());

    spi::CreateIteratorResult iter2(create(b, createSelection("true")));
    CPPUNIT_ASSERT_EQUAL(spi::IteratorId(2), iter2.getIteratorId());
}

std::vector<IteratorHandlerTest::Chunk>
IteratorHandlerTest::doIterate(spi::IteratorId id,
                               uint64_t maxByteSize,
                               size_t maxChunks,
                               bool allowEmptyResult)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    std::vector<Chunk> chunks;

    while (true) {
        spi::IterateResult result(getPersistenceProvider().iterate(
                id, maxByteSize, context));
        CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, result.getErrorCode());
        CPPUNIT_ASSERT(result.getEntries().size() > 0 || allowEmptyResult);

        chunks.push_back(Chunk{std::move(result.steal_entries())});
        if (result.isCompleted()
            || (maxChunks != 0 && chunks.size() >= maxChunks))
        {
            break;
        }
    }
    return chunks;
}

namespace {

size_t
getDocCount(const std::vector<IteratorHandlerTest::Chunk>& chunks)
{
    size_t count = 0;
    for (size_t i=0; i<chunks.size(); ++i) {
        count += chunks[i]._entries.size();
    }
    return count;
}

size_t
getRemoveEntryCount(const std::vector<spi::DocEntry::UP>& entries)
{
    size_t ret = 0;
    for (size_t i = 0; i < entries.size(); ++i) {
        if (entries[i]->isRemove()) {
            ++ret;
        }
    }
    return ret;
}

struct DocEntryIndirectTimestampComparator
{
    bool operator()(const spi::DocEntry::UP& e1,
                    const spi::DocEntry::UP& e2) const
    {
        return e1->getTimestamp() < e2->getTimestamp();
    }
};

std::vector<spi::DocEntry::UP>
getEntriesFromChunks(const std::vector<IteratorHandlerTest::Chunk>& chunks)
{
    std::vector<spi::DocEntry::UP> ret;
    for (size_t chunk = 0; chunk < chunks.size(); ++chunk) {
        for (size_t i = 0; i < chunks[chunk]._entries.size(); ++i) {
            ret.push_back(spi::DocEntry::UP(chunks[chunk]._entries[i]->clone()));
        }
    }
    std::sort(ret.begin(),
              ret.end(),
              DocEntryIndirectTimestampComparator());
    return ret;
}

const vespalib::LazyFile&
getFileHandle(const MemFile& mf1)
{
    return static_cast<const SimpleMemFileIOBuffer&>(
            mf1.getMemFileIO()).getFileHandle();
}

const LoggingLazyFile&
getLoggerFile(const MemFile& file)
{
    return dynamic_cast<const LoggingLazyFile&>(getFileHandle(file));
}

}

void
IteratorHandlerTest::verifyDocs(const std::vector<DocAndTimestamp>& wanted,
                                const std::vector<IteratorHandlerTest::Chunk>& chunks,
                                const std::set<vespalib::string>& removes) const
{
    std::vector<spi::DocEntry::UP> retrieved(
            getEntriesFromChunks(chunks));
    size_t removeCount = getRemoveEntryCount(retrieved);
    // Ensure that we've got the correct number of puts and removes
    CPPUNIT_ASSERT_EQUAL(removes.size(), removeCount);
    CPPUNIT_ASSERT_EQUAL(wanted.size(), retrieved.size() - removeCount);

    size_t wantedIdx = 0;
    for (size_t i = 0; i < retrieved.size(); ++i) {
        spi::DocEntry& entry(*retrieved[i]);
        if (entry.getDocument() != 0) {
            if (!(*wanted[wantedIdx].first == *entry.getDocument())) {
                std::ostringstream ss;
                ss << "Documents differ! Wanted:\n"
                   << wanted[wantedIdx].first->toString(true)
                   << "\n\nGot:\n"
                   << entry.getDocument()->toString(true);
                CPPUNIT_FAIL(ss.str());
            }
            CPPUNIT_ASSERT_EQUAL(wanted[wantedIdx].second, entry.getTimestamp());
            CPPUNIT_ASSERT_EQUAL(wanted[wantedIdx].first->serialize()->getLength()
                                 + sizeof(spi::DocEntry),
                                 size_t(entry.getSize()));
            ++wantedIdx;
        } else {
            // Remove-entry
            CPPUNIT_ASSERT(entry.getDocumentId() != 0);
            CPPUNIT_ASSERT_EQUAL(entry.getDocumentId()->getSerializedSize()
                                 + sizeof(spi::DocEntry),
                                 size_t(entry.getSize()));
            if (removes.find(entry.getDocumentId()->toString()) == removes.end()) {
                std::ostringstream ss;
                ss << "Got unexpected remove entry for document id "
                   << *entry.getDocumentId();
                CPPUNIT_FAIL(ss.str());
            }
        }
    }
}

// Feed numDocs documents, starting from timestamp 1000
std::vector<IteratorHandlerTest::DocAndTimestamp>
IteratorHandlerTest::feedDocs(size_t numDocs,
                              uint32_t minSize,
                              uint32_t maxSize)
{
    std::vector<DocAndTimestamp> docs;
    for (uint32_t i = 0; i < numDocs; ++i) {
        docs.push_back(
                DocAndTimestamp(
                        doPut(4,
                              framework::MicroSecTime(1000 + i),
                              minSize,
                              maxSize),
                        spi::Timestamp(1000 + i)));
    }
    flush(document::BucketId(16, 4));
    return docs;
}

void
IteratorHandlerTest::testSomeSlotsRemovedBetweenInvocations()
{
    std::vector<DocAndTimestamp> docs = feedDocs(100, 4096, 4096);

    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    spi::Selection sel(createSelection("true"));

    spi::CreateIteratorResult iter(create(b, sel));
    CPPUNIT_ASSERT(env()._cache.contains(b.getBucketId()));

    std::vector<Chunk> chunks = doIterate(iter.getIteratorId(), 10000, 25);
    CPPUNIT_ASSERT_EQUAL(size_t(25), chunks.size());

    {
        MemFilePtr file(getMemFile(b.getBucketId()));

        for (int i = 0 ; i < 2; ++i) {
            const MemSlot* slot = file->getSlotWithId(docs.front().first->getId());
            CPPUNIT_ASSERT(slot != 0);
            file->removeSlot(*slot);
            docs.erase(docs.begin());
        }
        file->flushToDisk();
    }

    std::vector<Chunk> chunks2 = doIterate(iter.getIteratorId(), 10000);
    CPPUNIT_ASSERT_EQUAL(size_t(24), chunks2.size());
    std::move(chunks2.begin(), chunks2.end(), std::back_inserter(chunks));

    verifyDocs(docs, chunks);

    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);

    // Bucket should not be evicted from cache during normal operation.
    CPPUNIT_ASSERT(env()._cache.contains(b.getBucketId()));
}

void
IteratorHandlerTest::testAllSlotsRemovedBetweenInvocations()
{
    std::vector<DocAndTimestamp> docs = feedDocs(100, 4096, 4096);

    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    spi::Selection sel(createSelection("true"));

    spi::CreateIteratorResult iter(create(b, sel));

    std::vector<Chunk> chunks = doIterate(iter.getIteratorId(), 1, 25);
    CPPUNIT_ASSERT_EQUAL(size_t(25), chunks.size());

    {
        MemFilePtr file(getMemFile(b.getBucketId()));

        for (int i = 0 ; i < 75; ++i) {
            const MemSlot* slot = file->getSlotWithId(docs[i].first->getId());
            CPPUNIT_ASSERT(slot != 0);
            file->removeSlot(*slot);
        }
        file->flushToDisk();
        docs.erase(docs.begin(), docs.begin() + 75);
    }

    std::vector<Chunk> chunks2 = doIterate(iter.getIteratorId(), 1, 0, true);
    CPPUNIT_ASSERT_EQUAL(size_t(0), getDocCount(chunks2));
    verifyDocs(docs, chunks);

    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
}

void
IteratorHandlerTest::testIterateMetadataOnly()
{
    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    std::vector<DocAndTimestamp> docs = feedDocs(10);

    CPPUNIT_ASSERT(
        doUnrevertableRemove(b.getBucketId(),
                             docs[docs.size() - 2].first->getId(),
                             Timestamp(1008)));

    CPPUNIT_ASSERT(
            doRemove(b.getBucketId(),
                     docs[docs.size() - 1].first->getId(),
                     framework::MicroSecTime(3001),
                     OperationHandler::PERSIST_REMOVE_IF_FOUND));

    flush(b.getBucketId());

    spi::Selection sel(createSelection("true"));
    spi::CreateIteratorResult iter(
            create(b, sel, spi::NEWEST_DOCUMENT_OR_REMOVE, document::NoFields()));

    std::vector<Chunk> chunks = doIterate(iter.getIteratorId(), 4096);
    std::vector<spi::DocEntry::UP> entries = getEntriesFromChunks(chunks);
    CPPUNIT_ASSERT_EQUAL(docs.size(), entries.size());
    std::vector<DocAndTimestamp>::const_iterator docIter(
            docs.begin());
    for (size_t i = 0; i < entries.size(); ++i, ++docIter) {
        const spi::DocEntry& entry = *entries[i];

        CPPUNIT_ASSERT(entry.getDocument() == 0);
        CPPUNIT_ASSERT(entry.getDocumentId() == 0);
        if (i == 9) {
            CPPUNIT_ASSERT(entry.isRemove());
            CPPUNIT_ASSERT_EQUAL(spi::Timestamp(3001), entry.getTimestamp());
        } else if (i == 8) {
            CPPUNIT_ASSERT(entry.isRemove());
            CPPUNIT_ASSERT_EQUAL(spi::Timestamp(1008), entry.getTimestamp());
        } else {
            CPPUNIT_ASSERT(!entry.isRemove());
            CPPUNIT_ASSERT_EQUAL(docIter->second, entry.getTimestamp());
        }
    }

    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
}

void
IteratorHandlerTest::testIterateHeadersOnly()
{
    std::vector<DocAndTimestamp> docs = feedDocs(20);
    // Remove all bodies.
    for (size_t i = 0; i < docs.size(); ++i) {
        clearBody(*docs[i].first);
    }

    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    spi::Selection sel(createSelection("true"));

    spi::CreateIteratorResult iter(create(b, sel, spi::NEWEST_DOCUMENT_ONLY,
                                          document::HeaderFields()));

    std::vector<Chunk> chunks = doIterate(iter.getIteratorId(), 1024);
    verifyDocs(docs, chunks);

    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
}

void
IteratorHandlerTest::testIterateLargeDocument()
{
    std::vector<DocAndTimestamp> docs = feedDocs(10, 10000, 10000);
    std::vector<DocAndTimestamp> largedoc;
    largedoc.push_back(docs.back());

    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    spi::Selection sel(createSelection("true"));

    spi::CreateIteratorResult iter(create(b, sel));

    std::vector<Chunk> chunks = doIterate(iter.getIteratorId(), 100, 1);
    verifyDocs(largedoc, chunks);

    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
}

void
IteratorHandlerTest::testDocumentsRemovedBetweenInvocations()
{
    int docCount = 100;
    std::vector<DocAndTimestamp> docs = feedDocs(docCount);

    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    spi::Selection sel(createSelection("true"));

    spi::CreateIteratorResult iter(create(b, sel));

    std::vector<Chunk> chunks = doIterate(iter.getIteratorId(), 1, 25);
    CPPUNIT_ASSERT_EQUAL(size_t(25), chunks.size());

    // Remove a subset of the documents. We should still get all the
    // original documents from the iterator, assuming no compactions.
    std::vector<DocumentId> removedDocs;
    std::vector<DocAndTimestamp> nonRemovedDocs;
    for (int i = 0; i < docCount; ++i) {
        if (i % 3 == 0) {
            removedDocs.push_back(docs[i].first->getId());
            CPPUNIT_ASSERT(doRemove(b.getBucketId(),
                                    removedDocs.back(),
                                    framework::MicroSecTime(2000 + i),
                                    OperationHandler::PERSIST_REMOVE_IF_FOUND));
        } else {
            nonRemovedDocs.push_back(docs[i]);
        }
    }
    flush(b.getBucketId());

    std::vector<Chunk> chunks2 = doIterate(iter.getIteratorId(), 1);
    CPPUNIT_ASSERT_EQUAL(size_t(75), chunks2.size());
    std::move(chunks2.begin(), chunks2.end(), std::back_inserter(chunks));

    verifyDocs(docs, chunks);

    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
}

void
IteratorHandlerTest::doTestUnrevertableRemoveBetweenInvocations(bool includeRemoves)
{
    int docCount = 100;
    std::vector<DocAndTimestamp> docs = feedDocs(docCount);

    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    spi::Selection sel(createSelection("true"));
    spi::CreateIteratorResult iter(
            create(b, sel,
                   includeRemoves ?
                   spi::NEWEST_DOCUMENT_OR_REMOVE : spi::NEWEST_DOCUMENT_ONLY));

    std::vector<Chunk> chunks = doIterate(iter.getIteratorId(), 1, 25);
    CPPUNIT_ASSERT_EQUAL(size_t(25), chunks.size());

    // Remove a subset of the documents unrevertably.
    std::vector<DocumentId> removedDocs;
    std::vector<DocAndTimestamp> nonRemovedDocs;
    for (int i = 0; i < docCount - 25; ++i) {
        if (i < 10) {
            removedDocs.push_back(docs[i].first->getId());
            CPPUNIT_ASSERT(
                    doUnrevertableRemove(b.getBucketId(),
                                         removedDocs.back(),
                                         Timestamp(1000+i)));
        } else {
            nonRemovedDocs.push_back(docs[i]);
        }
    }
    flush(b.getBucketId());

    std::vector<Chunk> chunks2 = doIterate(iter.getIteratorId(), 1);
    std::vector<spi::DocEntry::UP> entries = getEntriesFromChunks(chunks2);
    if (!includeRemoves) {
        CPPUNIT_ASSERT_EQUAL(nonRemovedDocs.size(), chunks2.size());
        verifyDocs(nonRemovedDocs, chunks2);
    } else {
        CPPUNIT_ASSERT_EQUAL(size_t(75), entries.size());
        for (int i = 0; i < docCount - 25; ++i) {
            spi::DocEntry& entry(*entries[i]);
            if (i < 10) {
                CPPUNIT_ASSERT(entry.isRemove());
            } else {
                CPPUNIT_ASSERT(!entry.isRemove());
            }
        }
    }

    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
}

void
IteratorHandlerTest::testUnrevertableRemoveBetweenInvocations()
{
    doTestUnrevertableRemoveBetweenInvocations(false);
}

void
IteratorHandlerTest::testUnrevertableRemoveBetweenInvocationsIncludeRemoves()
{
    doTestUnrevertableRemoveBetweenInvocations(true);
}

void
IteratorHandlerTest::testMatchTimestampRangeDocAltered()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketId bucketId(16, 4);
    document::StringFieldValue updateValue1("update1");
    document::StringFieldValue updateValue2("update2");

    Document::SP originalDoc = doPut(4, Timestamp(1234));

    {
        document::DocumentUpdate::SP update = createBodyUpdate(
                originalDoc->getId(), updateValue1);

        spi::UpdateResult result = doUpdate(bucketId, update, Timestamp(2345));
        CPPUNIT_ASSERT_EQUAL(1234, (int)result.getExistingTimestamp());
    }

    {
        document::DocumentUpdate::SP update = createBodyUpdate(
                originalDoc->getId(), updateValue2);

        spi::UpdateResult result = doUpdate(bucketId, update, Timestamp(3456));
        CPPUNIT_ASSERT_EQUAL(2345, (int)result.getExistingTimestamp());
    }

    CPPUNIT_ASSERT(
            doRemove(bucketId,
                     originalDoc->getId(),
                     Timestamp(4567),
                     OperationHandler::PERSIST_REMOVE_IF_FOUND));
    flush(bucketId);

    spi::Bucket b(bucketId, spi::PartitionId(0));

    {
        spi::Selection sel(createSelection("true"));
        sel.setFromTimestamp(spi::Timestamp(0));
        sel.setToTimestamp(spi::Timestamp(10));
        spi::CreateIteratorResult iter(create(b, sel));

        spi::IterateResult result(getPersistenceProvider().iterate(
                iter.getIteratorId(), 4096, context));
        CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, result.getErrorCode());
        CPPUNIT_ASSERT_EQUAL(size_t(0), result.getEntries().size());
        CPPUNIT_ASSERT(result.isCompleted());

        getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
    }

    {
        spi::Selection sel(createSelection("true"));
        sel.setFromTimestamp(spi::Timestamp(10000));
        sel.setToTimestamp(spi::Timestamp(20000));
        spi::CreateIteratorResult iter(create(b, sel));

        spi::IterateResult result(getPersistenceProvider().iterate(
                iter.getIteratorId(), 4096, context));
        CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, result.getErrorCode());
        CPPUNIT_ASSERT_EQUAL(size_t(0), result.getEntries().size());
        CPPUNIT_ASSERT(result.isCompleted());

        getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
    }

    {
        spi::Selection sel(createSelection("true"));
        sel.setFromTimestamp(spi::Timestamp(0));
        sel.setToTimestamp(spi::Timestamp(1234));
        spi::CreateIteratorResult iter(create(b, sel));

        spi::IterateResult result(getPersistenceProvider().iterate(
                iter.getIteratorId(), 4096, context));
        CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, result.getErrorCode());
        CPPUNIT_ASSERT_EQUAL(size_t(1), result.getEntries().size());
        CPPUNIT_ASSERT(result.isCompleted());

        const Document& receivedDoc(*result.getEntries()[0]->getDocument());
        if (!(*originalDoc == receivedDoc)) {
            std::ostringstream ss;
            ss << "Documents differ! Wanted:\n"
               << originalDoc->toString(true)
               << "\n\nGot:\n"
               << receivedDoc.toString(true);
            CPPUNIT_FAIL(ss.str());
        }

        getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
    }

    {
        spi::Selection sel(createSelection("true"));
        sel.setFromTimestamp(spi::Timestamp(0));
        sel.setToTimestamp(spi::Timestamp(2345));
        spi::CreateIteratorResult iter(create(b, sel));

        spi::IterateResult result(getPersistenceProvider().iterate(
                iter.getIteratorId(), 4096, context));
        CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, result.getErrorCode());
        CPPUNIT_ASSERT_EQUAL(size_t(1), result.getEntries().size());
        CPPUNIT_ASSERT(result.isCompleted());

        const Document& receivedDoc(*result.getEntries()[0]->getDocument());
        CPPUNIT_ASSERT(receivedDoc.getValue("content").get());
        CPPUNIT_ASSERT_EQUAL(updateValue1,
                             dynamic_cast<document::StringFieldValue&>(
                                     *receivedDoc.getValue(
                                             "content")));

        getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
    }

    {
        spi::Selection sel(createSelection("true"));
        sel.setFromTimestamp(spi::Timestamp(0));
        sel.setToTimestamp(spi::Timestamp(3456));
        spi::CreateIteratorResult iter(create(b, sel));

        spi::IterateResult result(getPersistenceProvider().iterate(
                iter.getIteratorId(), 4096, context));
        CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, result.getErrorCode());
        CPPUNIT_ASSERT_EQUAL(size_t(1), result.getEntries().size());
        CPPUNIT_ASSERT(result.isCompleted());

        const Document& receivedDoc(*result.getEntries()[0]->getDocument());
        CPPUNIT_ASSERT(receivedDoc.getValue("content").get());
        CPPUNIT_ASSERT_EQUAL(updateValue2,
                             dynamic_cast<document::StringFieldValue&>(
                                     *receivedDoc.getValue(
                                             "content")));

        getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
    }
}

void
IteratorHandlerTest::testIterateAllVersions()
{
    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    std::vector<DocAndTimestamp> docs;

    Document::SP originalDoc(createRandomDocumentAtLocation(
                                     4, 1001, 110, 110));

    doPut(originalDoc, framework::MicroSecTime(1001), 0);

    document::StringFieldValue updateValue1("update1");
    {
        document::DocumentUpdate::SP update = createBodyUpdate(
                originalDoc->getId(), updateValue1);

        spi::UpdateResult result = doUpdate(b.getBucketId(), update, Timestamp(2345));
        CPPUNIT_ASSERT_EQUAL(1001, (int)result.getExistingTimestamp());
    }
    flush(b.getBucketId());

    Document::SP updatedDoc(new Document(*originalDoc));
    updatedDoc->setValue("content", document::StringFieldValue("update1"));
    docs.push_back(DocAndTimestamp(originalDoc, spi::Timestamp(1001)));
    docs.push_back(DocAndTimestamp(updatedDoc, spi::Timestamp(2345)));

    spi::Selection sel(createSelection("true"));
    spi::CreateIteratorResult iter(create(b, sel, spi::ALL_VERSIONS));

    std::vector<Chunk> chunks = doIterate(iter.getIteratorId(), 4096);
    verifyDocs(docs, chunks);

    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
}

void
IteratorHandlerTest::testFieldSetFiltering()
{
    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    Document::SP doc(createRandomDocumentAtLocation(
                             4, 1001, 110, 110));
    doc->setValue(doc->getField("headerval"), document::IntFieldValue(42));
    doc->setValue(doc->getField("hstringval"),
                  document::StringFieldValue("groovy, baby!"));
    doc->setValue(doc->getField("content"),
                  document::StringFieldValue("fancy content"));
    doPut(doc, framework::MicroSecTime(1001), 0);
    flush(b.getBucketId());

    document::FieldSetRepo repo;
    spi::Selection sel(createSelection("true"));
    spi::CreateIteratorResult iter(
            create(b, sel, spi::NEWEST_DOCUMENT_ONLY,
                   *repo.parse(*getTypeRepo(), "testdoctype1:hstringval,content")));
    std::vector<spi::DocEntry::UP> entries(
            getEntriesFromChunks(doIterate(iter.getIteratorId(), 4096)));
    CPPUNIT_ASSERT_EQUAL(size_t(1), entries.size());
    CPPUNIT_ASSERT_EQUAL(std::string("content: fancy content\n"
                                     "hstringval: groovy, baby!\n"),
                         stringifyFields(*entries[0]->getDocument()));
}

void
IteratorHandlerTest::testIteratorInactiveOnException()
{
    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    feedDocs(10);

    env()._cache.clear();

    simulateIoErrorsForSubsequentlyOpenedFiles(IoErrors().afterReads(1));

    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    spi::CreateIteratorResult iter(create(b, createSelection("true")));
    spi::IterateResult result(getPersistenceProvider().iterate(
                                      iter.getIteratorId(), 100000, context));
    CPPUNIT_ASSERT(result.hasError());
    // Check that iterator is marked as inactive
    const SharedIteratorHandlerState& state(
            getPersistenceProvider().getIteratorHandler().getState());
    CPPUNIT_ASSERT(state._iterators.find(iter.getIteratorId().getValue())
                   != state._iterators.end());
    CPPUNIT_ASSERT(state._iterators.find(iter.getIteratorId().getValue())
                   ->second.isActive() == false);

    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
}

void
IteratorHandlerTest::testDocsCachedBeforeDocumentSelection()
{
    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    std::vector<DocAndTimestamp> docs = feedDocs(100, 4096, 4096);

    env()._cache.clear();
    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options).maximumReadThroughGap(1024*1024).build());
    env()._lazyFileFactory = std::unique_ptr<Environment::LazyFileFactory>(
            new LoggingLazyFile::Factory());

    spi::Selection sel(createSelection("id.user=4"));
    spi::CreateIteratorResult iter(create(b, sel, spi::NEWEST_DOCUMENT_ONLY,
                                          document::BodyFields()));

    std::vector<Chunk> chunks = doIterate(iter.getIteratorId(), 4096);
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
    {
        MemFilePtr file(getMemFile(b.getBucketId()));
        // Should have 3 read ops; metadata, (precached) headers and bodies
        CPPUNIT_ASSERT_EQUAL(size_t(3),
                             getLoggerFile(*file).operations.size());
    }
}

void
IteratorHandlerTest::testTimestampRangeLimitedPrefetch()
{
    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    // Feed docs with timestamp range [1000, 1100)
    feedDocs(100, 4096, 4096);

    env()._cache.clear();
    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options).maximumReadThroughGap(512).build());
    env()._lazyFileFactory = std::unique_ptr<Environment::LazyFileFactory>(
            new LoggingLazyFile::Factory());

    spi::Selection sel(createSelection("id.user=4"));
    sel.setFromTimestamp(spi::Timestamp(1050));
    sel.setToTimestamp(spi::Timestamp(1059));
    spi::CreateIteratorResult iter(create(b, sel, spi::NEWEST_DOCUMENT_ONLY,
                                          document::BodyFields()));
    std::vector<Chunk> chunks = doIterate(iter.getIteratorId(), 4096);
    CPPUNIT_ASSERT_EQUAL(size_t(10), getDocCount(chunks));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().destroyIterator(iter.getIteratorId(), context);
    // Iterate over all slots, ensuring that only those that fall within the
    // timestamp range have actually been cached.
    {
        MemFilePtr file(getMemFile(b.getBucketId()));
        // Should have 3 read ops; metadata, (precached) headers and bodies
        CPPUNIT_ASSERT_EQUAL(size_t(3),
                             getLoggerFile(*file).operations.size());
        for (size_t i = 0; i < file->getSlotCount(); ++i) {
            const MemSlot& slot((*file)[i]);
            if (slot.getTimestamp() >= Timestamp(1050)
                && slot.getTimestamp() <= Timestamp(1059))
            {
                CPPUNIT_ASSERT(file->partAvailable(slot, HEADER));
                CPPUNIT_ASSERT(file->partAvailable(slot, BODY));
            } else {
                CPPUNIT_ASSERT(!file->partAvailable(slot, HEADER));
                CPPUNIT_ASSERT(!file->partAvailable(slot, BODY));
            }
        }
    }
}

void
IteratorHandlerTest::testCachePrefetchRequirements()
{
    document::select::Parser parser(
            env().repo(), env()._bucketFactory);
    {
        // No prefetch required.
        // NOTE: since stuff like id.user=1234 won't work, we have to handle
        // that explicitly in createIterator based on the assumption that a
        // non-empty document selection at _least_ requires header to be read.
        std::unique_ptr<document::select::Node> sel(
                parser.parse("true"));
        CachePrefetchRequirements req(
                CachePrefetchRequirements::createFromSelection(env().repo(),
                *sel));
        CPPUNIT_ASSERT(!req.isHeaderPrefetchRequired());
        CPPUNIT_ASSERT(!req.isBodyPrefetchRequired());
    }

    {
        // Header prefetch required.
        std::unique_ptr<document::select::Node> sel(
                parser.parse("testdoctype1.hstringval='blarg'"));
        CachePrefetchRequirements req(
                CachePrefetchRequirements::createFromSelection(env().repo(),
                *sel));
        CPPUNIT_ASSERT(req.isHeaderPrefetchRequired());
        CPPUNIT_ASSERT(!req.isBodyPrefetchRequired());
    }

    {
        // Body prefetch required.
        std::unique_ptr<document::select::Node> sel(
                parser.parse("testdoctype1.content='foobar'"));
        CachePrefetchRequirements req(
                CachePrefetchRequirements::createFromSelection(env().repo(),
                *sel));
        CPPUNIT_ASSERT(!req.isHeaderPrefetchRequired());
        CPPUNIT_ASSERT(req.isBodyPrefetchRequired());
    }
}

void
IteratorHandlerTest::testBucketEvictedFromCacheOnIterateException()
{
    spi::Bucket b(BucketId(16, 4), spi::PartitionId(0));
    feedDocs(10);
    env()._cache.clear();

    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    spi::CreateIteratorResult iter(create(b, createSelection("true")));
    simulateIoErrorsForSubsequentlyOpenedFiles(IoErrors().afterReads(1));
    spi::IterateResult result(getPersistenceProvider().iterate(
                                      iter.getIteratorId(), 100000, context));
    CPPUNIT_ASSERT(result.hasError());

    // This test is actually a bit disingenuous since calling iterate will
    // implicitly invoke maintain() on an IO exception, which will subsequently
    // evict the bucket due to the exception happening again in its context.
    CPPUNIT_ASSERT(!env()._cache.contains(b.getBucketId()));
}

}
}
