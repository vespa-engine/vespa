// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/memfilepersistence/memfile/memfile.h>
#include <tests/spi/memfiletestutils.h>
#include <tests/spi/logginglazyfile.h>
#include <tests/spi/options_builder.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/memfilepersistence/memfile/memfilecompactor.h>
#include <vespa/memfilepersistence/mapper/simplememfileiobuffer.h>
#include <vespa/vespalib/util/exceptions.h>
#include <limits>

namespace storage {
namespace memfile {

struct MemFileTest : public SingleDiskMemFileTestUtils
{
    typedef MemFileCompactor::SlotList SlotList;

    /**
     * Feed a document whose ID is deterministically generated from `seed` to
     * bucket (16, 4) at time `timestamp`.
     */
    document::DocumentId feedDocument(
            uint64_t seed,
            uint64_t timestamp,
            uint32_t headerSize = 0,
            uint32_t minBodySize = 10,
            uint32_t maxBodySize = 100);

    /**
     * Feed n instances of documents with the same ID to bucket (16, 4) using
     * a timestamp range of [1000, 1000+n).
     */
    void feedSameDocNTimes(uint32_t n);

    void setMaxDocumentVersionsOption(uint32_t n);

    std::vector<Types::Timestamp> compactWithVersionLimit(uint32_t maxVersions);

    void testCompactRemoveDoublePut();
    void testCompactPutRemove();
    void testCompactGidCollision();
    void testCompactGidCollisionAndNot();
    void testCompactWithMemFile();
    void testCompactCombined();
    void testCompactDifferentPuts();
    void testNoCompactionWhenDocumentVersionsWithinLimit();
    void testCompactWhenDocumentVersionsExceedLimit();
    void testCompactLimit1KeepsNewestVersionOnly();
    void testCompactionOptionsArePropagatedFromConfig();
    void testZeroDocumentVersionConfigIsCorrected();
    void testResizeToFreeSpace();
    void testNoFileWriteOnNoOpCompaction();
    void testCacheSize();
    void testClearCache();
    void testGetSlotsByTimestamp();
    void testCacheInconsistentSlot();
    void testEnsureCached();
    void testAddSlotWhenDiskFull();
    void testGetSerializedSize();
    void testGetBucketInfo();
    void testCopySlotsPreservesLocationSharing();
    void testFlushingToNonExistingFileAlwaysRunsCompaction();
    void testOrderDocSchemeDocumentsCanBeAddedToFile();

    CPPUNIT_TEST_SUITE(MemFileTest);
    CPPUNIT_TEST(testCompactRemoveDoublePut);
    CPPUNIT_TEST(testCompactPutRemove);
    CPPUNIT_TEST(testCompactGidCollision);
    CPPUNIT_TEST(testCompactGidCollisionAndNot);
    CPPUNIT_TEST(testCompactWithMemFile);
    CPPUNIT_TEST(testCompactCombined);
    CPPUNIT_TEST(testCompactDifferentPuts);
    CPPUNIT_TEST(testNoCompactionWhenDocumentVersionsWithinLimit);
    CPPUNIT_TEST(testCompactWhenDocumentVersionsExceedLimit);
    CPPUNIT_TEST(testCompactLimit1KeepsNewestVersionOnly);
    CPPUNIT_TEST(testCompactionOptionsArePropagatedFromConfig);
    CPPUNIT_TEST(testZeroDocumentVersionConfigIsCorrected);
    CPPUNIT_TEST(testNoFileWriteOnNoOpCompaction);
    CPPUNIT_TEST(testCacheSize);
    CPPUNIT_TEST(testClearCache);
    CPPUNIT_TEST(testGetSlotsByTimestamp);
    CPPUNIT_TEST(testEnsureCached);
    CPPUNIT_TEST(testResizeToFreeSpace);
    CPPUNIT_TEST(testAddSlotWhenDiskFull);
    CPPUNIT_TEST(testGetSerializedSize);
    CPPUNIT_TEST(testGetBucketInfo);
    CPPUNIT_TEST(testCopySlotsPreservesLocationSharing);
    CPPUNIT_TEST(testFlushingToNonExistingFileAlwaysRunsCompaction);
    CPPUNIT_TEST(testOrderDocSchemeDocumentsCanBeAddedToFile);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemFileTest);

/**
 * Slots should actually be the same pointer. Use this assert to do correct
 * check, and still print content of slots on failure.
 */
#define ASSERT_SLOT_EQUAL(slotptra, slotptrb) \
{ \
    CPPUNIT_ASSERT(slotptra != 0); \
    CPPUNIT_ASSERT(slotptrb != 0); \
    std::ostringstream slotdiff; \
    slotdiff << "Expected: " << *slotptra << ", but got " << *slotptrb; \
    CPPUNIT_ASSERT_EQUAL_MSG(slotdiff.str(), slotptra, slotptrb); \
}

namespace {

framework::MicroSecTime sec(uint64_t n) {
    return framework::MicroSecTime(n * 1000000ULL);
}

/**
 * Utility functions for tests to call to do compacting, such that the
 * tests themselves are not bound to the current interface.
 *
 * Also, this function translates second time to microsecond time.
 */
MemFileTest::SlotList getSlotsToRemove(
        const MemFile& file, uint64_t currentTime,
        uint64_t revertTime, uint64_t keepRemoveTime)
{
    MemFileCompactor compactor(
            sec(currentTime),
            CompactionOptions()
                .maxDocumentVersions(
                    std::numeric_limits<uint32_t>::max())
                .revertTimePeriod(sec(revertTime))
                .keepRemoveTimePeriod(sec(keepRemoveTime)));
    return compactor.getSlotsToRemove(file);
}

class AutoFlush
{
public:
    AutoFlush(MemFilePtr& ptr) : _ptr(ptr) {}
    ~AutoFlush() { _ptr->flushToDisk(); }
private:
    MemFilePtr& _ptr;
};

}

document::DocumentId
MemFileTest::feedDocument(
        uint64_t seed,
        uint64_t timestamp,
        uint32_t headerSize,
        uint32_t minDocSize,
        uint32_t maxDocSize) {
    document::Document::SP doc(createRandomDocumentAtLocation(
                                       4, seed, minDocSize, maxDocSize));

    if (headerSize > 0) {
        std::string val(headerSize, 'A');
        doc->setValue(doc->getField("hstringval"),
                      document::StringFieldValue(val));
    }

    doPut(doc,
          document::BucketId(16, 4),
          Timestamp(timestamp * 1000000));

    return doc->getId();
}

void
MemFileTest::feedSameDocNTimes(uint32_t n)
{
    for (uint32_t i = 0; i < n; ++i) {
        feedDocument(1234, 1000 + i);
    }
}

void
MemFileTest::setMaxDocumentVersionsOption(uint32_t n)
{
    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options)
                .maxDocumentVersions(n)
                .build());
}

void
MemFileTest::testCacheSize()
{
    // Feed some puts
    for (uint32_t i = 0; i < 4; i++) {
        feedDocument(1234 * (i % 2), 1000 + 200 * i);
    }
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));

    CPPUNIT_ASSERT(file->getCacheSize().sum() > 0);
}

void
MemFileTest::testClearCache()
{
    // Feed some puts
    for (uint32_t i = 0; i < 4; i++) {
        feedDocument(1234 * (i % 2), 1000 + 200 * i);
    }
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    file->flushToDisk();

    CPPUNIT_ASSERT(file->getCacheSize().bodySize > 0);
    CPPUNIT_ASSERT(file->getCacheSize().headerSize > 0);

    file->clearCache(HEADER);

    CPPUNIT_ASSERT(file->getCacheSize().bodySize > 0);
    CPPUNIT_ASSERT(file->getMemFileIO().getCachedSize(BODY) > 0);
    CPPUNIT_ASSERT_EQUAL(0, (int)file->getCacheSize().headerSize);
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), file->getMemFileIO().getCachedSize(HEADER));

    file->clearCache(BODY);

    CPPUNIT_ASSERT_EQUAL(0, (int)file->getCacheSize().bodySize);
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), file->getMemFileIO().getCachedSize(BODY));
}


void
MemFileTest::testCompactGidCollision()
{
    // Feed two puts
    for (uint32_t i = 0; i < 2; i++) {
        feedDocument(1234 * i, 1000 + 200 * i);
    }
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    AutoFlush af(file);
    const_cast<MemSlot&>((*file)[1]).setGlobalId((*file)[0].getGlobalId());

    CPPUNIT_ASSERT_EQUAL(2, (int)file->getSlotCount());

    {
        SlotList toRemove(getSlotsToRemove(*file, 1600, 300, 86400));
        CPPUNIT_ASSERT_EQUAL(0, (int)toRemove.size());
        file->removeSlots(toRemove);
    }
}

void
MemFileTest::testCompactGidCollisionAndNot()
{
    // Feed some puts
    for (uint32_t i = 0; i < 4; i++) {
        feedDocument(1234 * (i % 2), 1000 + 200 * i);
    }
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    AutoFlush af(file);
    const_cast<MemSlot&>((*file)[2]).setGlobalId((*file)[0].getGlobalId());
    const_cast<MemSlot&>((*file)[3]).setGlobalId((*file)[1].getGlobalId());

    CPPUNIT_ASSERT_EQUAL(4, (int)file->getSlotCount());

    {
        SlotList toRemove(getSlotsToRemove(*file, 2000, 300, 86400));

        CPPUNIT_ASSERT_EQUAL(2, (int)toRemove.size());
        ASSERT_SLOT_EQUAL(&(*file)[0], toRemove[0]);
        ASSERT_SLOT_EQUAL(&(*file)[1], toRemove[1]);
        file->removeSlots(toRemove);
    }
}


void
MemFileTest::testCompactRemoveDoublePut()
{
    // Feed two puts at time 1000 and 1200
    for (uint32_t i = 0; i < 2; i++) {
        feedDocument(1234, 1000 + 200 * i);
    }
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    AutoFlush af(file);
    CPPUNIT_ASSERT_EQUAL(2, (int)file->getSlotCount());

    {
        // Not time to collect yet, newest is still revertable
        SlotList toRemove(getSlotsToRemove(*file, 1300, 300, 86400));
        CPPUNIT_ASSERT_EQUAL(0, (int)toRemove.size());
    }

    {
        SlotList toRemove(getSlotsToRemove(*file, 1600, 300, 86400));

        CPPUNIT_ASSERT_EQUAL(1, (int)toRemove.size());
        ASSERT_SLOT_EQUAL(&(*file)[0], toRemove[0]);
        file->removeSlots(toRemove);
    }
}

void
MemFileTest::testCompactPutRemove()
{
    document::DocumentId docId = feedDocument(1234, 1000);

    doRemove(docId, Timestamp(1200*1000000), 0);
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    AutoFlush af(file);

    {
        // Since remove can still be reverted, we can't revert anything.
        SlotList toRemove(getSlotsToRemove(*file, 1300, 300, 600));

        CPPUNIT_ASSERT_EQUAL(0, (int)toRemove.size());
    }

    {
        SlotList toRemove(getSlotsToRemove(*file, 1600, 300, 600));

        CPPUNIT_ASSERT_EQUAL(1, (int)toRemove.size());
        ASSERT_SLOT_EQUAL(&(*file)[0], toRemove[0]);
        file->removeSlots(toRemove);
    }

    {
        SlotList toRemove(getSlotsToRemove(*file, 1900, 300, 600));

        CPPUNIT_ASSERT_EQUAL(1, (int)toRemove.size());
        ASSERT_SLOT_EQUAL(&(*file)[0], toRemove[0]);
        file->removeSlots(toRemove);
    }
}

void
MemFileTest::testCompactCombined()
{
    document::DocumentId docId;

    // Feed some puts at time 1000, 1200, 1400, 1600 and 1800 for same doc.
    for (uint32_t i = 0; i < 5; i++) {
        docId = feedDocument(1234, 1000 + i * 200);
    }
    flush(document::BucketId(16, 4));

    // Now add remove at time 2000.
    doRemove(docId, Timestamp(2000 * 1000000), 0);
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    AutoFlush af(file);
    CPPUNIT_ASSERT_EQUAL(6, (int)file->getSlotCount());

    {
        // Compact all redundant slots that are older than revert period of 300.
        // This includes 1000, 1200, 1400 and 1600.
        SlotList toRemove(getSlotsToRemove(*file, 2001, 300, 86400));
        CPPUNIT_ASSERT_EQUAL(4, (int)toRemove.size());
        for (int i = 0; i < 4; ++i) {
            ASSERT_SLOT_EQUAL(&(*file)[i], toRemove[i]);
        }
        file->removeSlots(toRemove);
    }
}

void
MemFileTest::testCompactDifferentPuts()
{
    document::DocumentId docId;

    // Feed some puts
    for (uint32_t i = 0; i < 2; i++) {
        for (uint32_t j = 0; j < 3; j++) {
            feedDocument(1234 * j, 1000 + (i * 3 + j) * 200);
        }
    }
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    AutoFlush af(file);
    CPPUNIT_ASSERT_EQUAL(6, (int)file->getSlotCount());

    {
        SlotList toRemove(getSlotsToRemove(*file, 3000, 300, 86400));
        CPPUNIT_ASSERT_EQUAL(3, (int)toRemove.size());

        for (uint32_t i = 0; i < 3; i++) {
            bool found = false;
            for (uint32_t j = 0; j < 3; j++) {
                if ((*file)[j] == *toRemove[i]) {
                    found = true;
                }
            }

            CPPUNIT_ASSERT(found);
        }
        file->removeSlots(toRemove);
    }
}

void
MemFileTest::testCompactWithMemFile()
{
    // Feed two puts
    for (uint32_t i = 0; i < 2; i++) {
        document::Document::SP doc(createRandomDocumentAtLocation(
                                           4, 1234, 10, 100));

        doPut(doc, document::BucketId(16, 4), Timestamp((1000 + i * 200)*1000000), 0);
    }
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    AutoFlush af(file);
    CPPUNIT_ASSERT_EQUAL(2, (int)file->getSlotCount());
    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options)
                .revertTimePeriod(framework::MicroSecTime(1000))
                .build());

    getFakeClock()._absoluteTime = framework::MicroSecTime(2000ULL * 1000000);

    CPPUNIT_ASSERT(file->compact());
    CPPUNIT_ASSERT(!file->compact());

    CPPUNIT_ASSERT_EQUAL(1, (int)file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1200 * 1000000), (*file)[0].getTimestamp());
}

/**
 * Feed 5 versions of a single document at absolute times 0 through 4 seconds
 * and run compaction using the provided max document version option.
 * Revert time/keep remove time options are effectively disabled for this test.
 * Returns timestamps of all slots that are marked as compactable.
 */
std::vector<Types::Timestamp>
MemFileTest::compactWithVersionLimit(uint32_t maxVersions)
{
    document::BucketId bucket(16, 4);
    std::shared_ptr<Document> doc(
            createRandomDocumentAtLocation(4, 1234, 10, 100));
    uint32_t versionLimit = 5;
    for (uint32_t i = 0; i < versionLimit; ++i) {
        Timestamp ts(sec(i).getTime());
        doPut(doc, bucket, ts, 0);
    }
    flush(bucket);

    MemFilePtr file(getMemFile(bucket));
    CPPUNIT_ASSERT_EQUAL(versionLimit, file->getSlotCount());

    framework::MicroSecTime currentTime(sec(versionLimit));
    MemFileCompactor compactor(
            currentTime,
            CompactionOptions()
                .revertTimePeriod(sec(versionLimit))
                .keepRemoveTimePeriod(sec(versionLimit))
                .maxDocumentVersions(maxVersions));
    auto slots = compactor.getSlotsToRemove(*file);
    // Convert to timestamps since caller won't have access to actual MemFile.
    std::vector<Timestamp> timestamps;
    for (const MemSlot* slot : slots) {
        timestamps.push_back(slot->getTimestamp());
    }
    return timestamps;
}

void
MemFileTest::testNoCompactionWhenDocumentVersionsWithinLimit()
{
    auto timestamps = compactWithVersionLimit(5);
    CPPUNIT_ASSERT(timestamps.empty());
}

void
MemFileTest::testCompactWhenDocumentVersionsExceedLimit()
{
    auto timestamps = compactWithVersionLimit(2);
    CPPUNIT_ASSERT_EQUAL(size_t(3), timestamps.size());
    std::vector<Timestamp> expected = {
        sec(0), sec(1), sec(2)
    };
    CPPUNIT_ASSERT_EQUAL(expected, timestamps);
}

void
MemFileTest::testCompactLimit1KeepsNewestVersionOnly()
{
    auto timestamps = compactWithVersionLimit(1);
    CPPUNIT_ASSERT_EQUAL(size_t(4), timestamps.size());
    std::vector<Timestamp> expected = {
        sec(0), sec(1), sec(2), sec(3)
    };
    CPPUNIT_ASSERT_EQUAL(expected, timestamps);
}

void
MemFileTest::testCompactionOptionsArePropagatedFromConfig()
{
    vespa::config::storage::StorMemfilepersistenceConfigBuilder mfcBuilder;
    vespa::config::content::PersistenceConfigBuilder pcBuilder;

    pcBuilder.maximumVersionsOfSingleDocumentStored = 12345;
    pcBuilder.revertTimePeriod = 555;
    pcBuilder.keepRemoveTimePeriod = 777;

    vespa::config::storage::StorMemfilepersistenceConfig mfc(mfcBuilder);
    vespa::config::content::PersistenceConfig pc(pcBuilder);
    Options opts(mfc, pc);

    CPPUNIT_ASSERT_EQUAL(framework::MicroSecTime(555 * 1000000),
                         opts._revertTimePeriod);
    CPPUNIT_ASSERT_EQUAL(framework::MicroSecTime(777 * 1000000),
                         opts._keepRemoveTimePeriod);
    CPPUNIT_ASSERT_EQUAL(uint32_t(12345), opts._maxDocumentVersions);
}

void
MemFileTest::testZeroDocumentVersionConfigIsCorrected()
{
    vespa::config::storage::StorMemfilepersistenceConfigBuilder mfcBuilder;
    vespa::config::content::PersistenceConfigBuilder pcBuilder;

    pcBuilder.maximumVersionsOfSingleDocumentStored = 0;

    vespa::config::storage::StorMemfilepersistenceConfig mfc(mfcBuilder);
    vespa::config::content::PersistenceConfig pc(pcBuilder);
    Options opts(mfc, pc);

    CPPUNIT_ASSERT_EQUAL(uint32_t(1), opts._maxDocumentVersions);
}

void
MemFileTest::testGetSlotsByTimestamp()
{
    for (uint32_t i = 0; i < 10; i++) {
        feedDocument(i, 1000 + i);
    }
    flush(document::BucketId(16, 4));

    std::vector<Timestamp> timestamps;
    timestamps.push_back(Timestamp(999  * 1000000));
    timestamps.push_back(Timestamp(1001 * 1000000));
    timestamps.push_back(Timestamp(1002 * 1000000));
    timestamps.push_back(Timestamp(1007 * 1000000));
    timestamps.push_back(Timestamp(1100 * 1000000));
    std::vector<const MemSlot*> slots;

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    file->getSlotsByTimestamp(timestamps, slots);
    CPPUNIT_ASSERT_EQUAL(std::size_t(3), slots.size());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1001 * 1000000), slots[0]->getTimestamp());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1002 * 1000000), slots[1]->getTimestamp());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1007 * 1000000), slots[2]->getTimestamp());
}

void
MemFileTest::testEnsureCached()
{
    // Feed some puts
    for (uint32_t i = 0; i < 5; i++) {
        feedDocument(i, 1000 + i * 200, 600, 600, 600);
    }
    flush(document::BucketId(16, 4));

    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options).maximumReadThroughGap(512).build());
    env()._cache.clear();

    {
        MemFilePtr file(getMemFile(document::BucketId(16, 4)));
        CPPUNIT_ASSERT(file.get());
        CPPUNIT_ASSERT_EQUAL(5, (int)file->getSlotCount());

        file->ensureDocumentIdCached((*file)[1]);

        for (std::size_t i = 0; i < file->getSlotCount(); ++i) {
            if (i == 1) {
                CPPUNIT_ASSERT(file->documentIdAvailable((*file)[i]));
            } else {
                CPPUNIT_ASSERT(!file->documentIdAvailable((*file)[i]));
            }
            CPPUNIT_ASSERT(!file->partAvailable((*file)[i], BODY));
        }
    }

    env()._cache.clear();

    {
        MemFilePtr file(getMemFile(document::BucketId(16, 4)));
        file->ensureDocumentCached((*file)[2], true);

        for (std::size_t i = 0; i < file->getSlotCount(); ++i) {
            if (i == 2) {
                CPPUNIT_ASSERT(file->documentIdAvailable((*file)[i]));
                CPPUNIT_ASSERT(file->partAvailable((*file)[i], HEADER));
            } else {
                CPPUNIT_ASSERT(!file->documentIdAvailable((*file)[i]));
                CPPUNIT_ASSERT(!file->partAvailable((*file)[i], HEADER));
            }
            CPPUNIT_ASSERT(!file->partAvailable((*file)[i], BODY));
        }
    }

    env()._cache.clear();

    {
        MemFilePtr file(getMemFile(document::BucketId(16, 4)));

        file->ensureDocumentCached((*file)[3], false);

        for (std::size_t i = 0; i < file->getSlotCount(); ++i) {
            if (i == 3) {
                CPPUNIT_ASSERT(file->documentIdAvailable((*file)[i]));
                CPPUNIT_ASSERT(file->partAvailable((*file)[i], HEADER));
                CPPUNIT_ASSERT(file->partAvailable((*file)[i], BODY));
            } else {
                CPPUNIT_ASSERT(!file->documentIdAvailable((*file)[i]));
                CPPUNIT_ASSERT(!file->partAvailable((*file)[i], HEADER));
                CPPUNIT_ASSERT(!file->partAvailable((*file)[i], BODY));
            }
        }
    }

    env()._cache.clear();

    {
        MemFilePtr file(getMemFile(document::BucketId(16, 4)));

        std::vector<Timestamp> ts;
        for (int i = 2; i < 5; ++i) {
            ts.push_back((*file)[i].getTimestamp());
        }

        file->ensureDocumentCached(ts, false);

        for (std::size_t i = 0; i < file->getSlotCount(); ++i) {
            if (i > 1 && i < 5) {
                CPPUNIT_ASSERT(file->documentIdAvailable((*file)[i]));
                CPPUNIT_ASSERT(file->partAvailable((*file)[i], HEADER));
                CPPUNIT_ASSERT(file->partAvailable((*file)[i], BODY));
            } else {
                CPPUNIT_ASSERT(!file->documentIdAvailable((*file)[i]));
                CPPUNIT_ASSERT(!file->partAvailable((*file)[i], HEADER));
                CPPUNIT_ASSERT(!file->partAvailable((*file)[i], BODY));
            }
        }
    }

    env()._cache.clear();

    {
        MemFilePtr file(getMemFile(document::BucketId(16, 4)));

        file->ensureHeaderBlockCached();

        for (std::size_t i = 0; i < file->getSlotCount(); ++i) {
            CPPUNIT_ASSERT(file->documentIdAvailable((*file)[i]));
            CPPUNIT_ASSERT(file->partAvailable((*file)[i], HEADER));
            CPPUNIT_ASSERT(!file->partAvailable((*file)[i], BODY));
        }
    }

    env()._cache.clear();

    {
        MemFilePtr file(getMemFile(document::BucketId(16, 4)));

        file->ensureBodyBlockCached();

        for (std::size_t i = 0; i < file->getSlotCount(); ++i) {
            CPPUNIT_ASSERT(file->documentIdAvailable((*file)[i]));
            CPPUNIT_ASSERT(file->partAvailable((*file)[i], HEADER));
            CPPUNIT_ASSERT(file->partAvailable((*file)[i], BODY));
        }
    }
}

void
MemFileTest::testResizeToFreeSpace()
{
    /**
     * This test tests that files are resized to a smaller size when they need
     * to be. This should happen during a call to flushToDisk() in MemFile,
     * which is either dirty or if passed flag to check even if clean. (Which
     * the integrity checker cycle uses). A clean file is used for testing to
     * ensure that no part of the code only works for dirty files. This test
     * only test for the case where body block is too large. The real
     * implementation here will be in the flushUpdatesToFile() function for the
     * given file formats. (VersionSerializer's) If more cases wants to be
     * tested add those as unit tests for the versionserializers themselves.
     */

        // Create a test bucket to test with.
    BucketId bucket(16, 0xa);
    createTestBucket(bucket, 0);

    off_t file_size =
        ((SimpleMemFileIOBuffer&)getMemFile(bucket)->getMemFileIO()).
        getFileHandle().getFileSize();

        // Clear cache so we can manually modify backing file to increase the
        // size of it.
    FileSpecification file(getMemFile(bucket)->getFile());
    env()._cache.clear();
    {
            // Extend file to 1 MB, which should create an excessively large
            // body block such that file should be resized to be smaller
        vespalib::LazyFile fileHandle(file.getPath(), 0);
        fileHandle.write("foobar", 6, 2 * 1024 * 1024 - 6);
    }
    MemFilePtr memFile(getMemFile(bucket));
    memFile->flushToDisk(CHECK_NON_DIRTY_FILE_FOR_SPACE);
    CPPUNIT_ASSERT_EQUAL(file_size,
                         ((SimpleMemFileIOBuffer&)memFile->getMemFileIO()).
                         getFileHandle().getFileSize());
}

namespace {

const vespalib::LazyFile&
getFileHandle(const MemFile& mf1)
{
    return dynamic_cast<const SimpleMemFileIOBuffer&>(
            mf1.getMemFileIO()).getFileHandle();
}

const LoggingLazyFile&
getLoggerFile(const MemFile& file)
{
    return dynamic_cast<const LoggingLazyFile&>(getFileHandle(file));
}

}

void
MemFileTest::testNoFileWriteOnNoOpCompaction()
{
    BucketId bucket(16, 4);
    env()._lazyFileFactory = std::unique_ptr<Environment::LazyFileFactory>(
            new LoggingLazyFile::Factory());

    // Feed some unique puts, none of which can be compacted away.
    for (uint32_t i = 0; i < 2; i++) {
        document::Document::SP doc(createRandomDocumentAtLocation(
                                           4, i, 10, 100));

        doPut(doc, bucket, Timestamp((1000 + i * 200)*1000000), 0);
    }
    flush(bucket);

    MemFilePtr file(getMemFile(bucket));

    size_t opsBeforeFlush = getLoggerFile(*file).getOperationCount();
    file->flushToDisk(CHECK_NON_DIRTY_FILE_FOR_SPACE);
    size_t opsAfterFlush = getLoggerFile(*file).getOperationCount();

    // Disk should not have been touched, since no slots have been
    // compacted away.
    if (opsBeforeFlush != opsAfterFlush) {
        std::cerr << "\n" << getLoggerFile(*file).toString() << "\n";
    }
    CPPUNIT_ASSERT_EQUAL(opsBeforeFlush, opsAfterFlush);
}

void
MemFileTest::testAddSlotWhenDiskFull()
{
    {
        MemFilePtr file(getMemFile(document::BucketId(16, 4)));
        AutoFlush af(file);
        {
            // Add a dummy-slot that can later be removed
            Document::SP doc(createRandomDocumentAtLocation(4));
            file->addPutSlot(*doc, Timestamp(1001));
        }
    }

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    AutoFlush af(file);
    PartitionMonitor* mon = env().getDirectory().getPartition().getMonitor();
    // Set disk to 99% full
    mon->setStatOncePolicy();
    mon->setMaxFillness(.98f);
    mon->overrideRealStat(512, 100000, 99000);
    CPPUNIT_ASSERT(mon->isFull());

    // Test that addSlot with a non-persisted Put fails
    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        try {
            file->addPutSlot(*doc, Timestamp(10003));
            CPPUNIT_ASSERT(false);
        } catch (vespalib::IoException& e) {
            CPPUNIT_ASSERT_EQUAL(vespalib::IoException::NO_SPACE, e.getType());
        }
    }

    // Slots with valid header and body locations should also
    // not fail, as these are added when the file is loaded
    {
        // Just steal parts from existing slot to ensure they're persisted
        const MemSlot* existing = file->getSlotAtTime(Timestamp(1001));

        MemSlot slot(existing->getGlobalId(),
                     Timestamp(1005),
                     existing->getLocation(HEADER),
                     existing->getLocation(BODY),
                     IN_USE,
                     0x1234);
        file->addSlot(slot);
    }

    // Removes should not fail when disk is full
    {
        file->addRemoveSlot(*file->getSlotAtTime(Timestamp(1001)), Timestamp(1003));
    }
}

void
MemFileTest::testGetSerializedSize() {
    document::Document::SP doc(createRandomDocumentAtLocation(
                                       4, 1234, 1024, 1024));

    std::string val("Header");
    doc->setValue(doc->getField("hstringval"),
                  document::StringFieldValue(val));

    doPut(doc, document::BucketId(16, 4), framework::MicroSecTime(1000));
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));
    file->ensureBodyBlockCached();
    const MemSlot* slot = file->getSlotAtTime(framework::MicroSecTime(1000));
    CPPUNIT_ASSERT(slot != 0);

    vespalib::nbostream serializedHeader;
    doc->serializeHeader(serializedHeader);

    vespalib::nbostream serializedBody;
    doc->serializeBody(serializedBody);

    CPPUNIT_ASSERT_EQUAL(uint32_t(serializedHeader.size()),
                         file->getSerializedSize(*slot, HEADER));
    CPPUNIT_ASSERT_EQUAL(uint32_t(serializedBody.size()),
                         file->getSerializedSize(*slot, BODY));
}

void
MemFileTest::testGetBucketInfo()
{
    document::Document::SP doc(createRandomDocumentAtLocation(
                                       4, 1234, 100, 100));
    doc->setValue(doc->getField("content"),
                  document::StringFieldValue("foo"));
    document::Document::SP doc2(createRandomDocumentAtLocation(
                                       4, 1235, 100, 100));
    doc2->setValue(doc->getField("content"),
                   document::StringFieldValue("bar"));

    doPut(doc, document::BucketId(16, 4), framework::MicroSecTime(1000));
    flush(document::BucketId(16, 4));

    doPut(doc2, document::BucketId(16, 4), framework::MicroSecTime(1001));
    flush(document::BucketId(16, 4));

    // Do remove which should only add a single meta entry
    doRemove(doc->getId(), Timestamp(1002), 0);
    flush(document::BucketId(16, 4));

    MemFilePtr file(getMemFile(document::BucketId(16, 4)));

    CPPUNIT_ASSERT_EQUAL(3u, file->getSlotCount());
    uint32_t maxHeaderExtent = (*file)[1].getLocation(HEADER)._pos
                               + (*file)[1].getLocation(HEADER)._size;
    uint32_t maxBodyExtent = (*file)[1].getLocation(BODY)._pos
                             + (*file)[1].getLocation(BODY)._size;

    uint32_t wantedUsedSize = 64 + 40*3 + maxHeaderExtent + maxBodyExtent;
    BucketInfo info = file->getBucketInfo();
    CPPUNIT_ASSERT_EQUAL(1u, info.getDocumentCount());
    CPPUNIT_ASSERT_EQUAL(3u, info.getEntryCount());
    CPPUNIT_ASSERT_EQUAL(wantedUsedSize, info.getUsedSize());
    uint32_t wantedUniqueSize = (*file)[1].getLocation(HEADER)._size
                                + (*file)[1].getLocation(BODY)._size;
    CPPUNIT_ASSERT_EQUAL(wantedUniqueSize, info.getDocumentSize());
}

void
MemFileTest::testCopySlotsPreservesLocationSharing()
{
    document::BucketId bucket(16, 4);
    // Feed two puts to same document (identical seed). These should not
    // share any blocks. Note: implicit sec -> microsec conversion.
    feedDocument(1234, 1000); // slot 0
    auto docId = feedDocument(1234, 1001); // slot 1
    // Update only header of last version of document. This should share
    // slot body block 2 with that slot 1.
    auto update = createHeaderUpdate(docId, document::IntFieldValue(5678));
    doUpdate(bucket, update, Timestamp(1002 * 1000000), 0);
    // Feed a remove for doc in slot 2. This should share the header block of
    // slot 3 with the newest document in slot 2.
    doRemove(docId, Timestamp(1003 * 1000000), 0);
    flush(bucket);

    {
        MemFilePtr src(getMemFile(document::BucketId(16, 4)));
        MemFilePtr dest(getMemFile(document::BucketId(17, 4)));
        std::vector<Timestamp> timestamps {
            Timestamp(1000 * 1000000),
            Timestamp(1001 * 1000000),
            Timestamp(1002 * 1000000),
            Timestamp(1003 * 1000000)
        };
        std::vector<const MemSlot*> slots {
            src->getSlotAtTime(Timestamp(1000 * 1000000)),
            src->getSlotAtTime(Timestamp(1001 * 1000000)),
            src->getSlotAtTime(Timestamp(1002 * 1000000)),
            src->getSlotAtTime(Timestamp(1003 * 1000000))
        };
        dest->copySlotsFrom(*src, slots);
        dest->flushToDisk();
        CPPUNIT_ASSERT_EQUAL(uint32_t(4), dest->getSlotCount());

        DataLocation header[4];
        DataLocation body[4];
        for (int i = 0; i < 4; ++i) {
            const MemSlot* slot = dest->getSlotAtTime(timestamps[i]);
            header[i] = slot->getLocation(HEADER);
            body[i] = slot->getLocation(BODY);
        }
        CPPUNIT_ASSERT(!(header[0] == header[1]));

        CPPUNIT_ASSERT_EQUAL(body[2], body[1]);
        CPPUNIT_ASSERT_EQUAL(header[3], header[2]);
    }
}

void
MemFileTest::testFlushingToNonExistingFileAlwaysRunsCompaction()
{
    document::BucketId bucket(16, 4);

    setMaxDocumentVersionsOption(1);
    feedSameDocNTimes(10);
    flush(bucket);

    // Max version limit is 1, flushing should have compacted it down.
    MemFilePtr file(getMemFile(bucket));
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), file->getSlotCount());
}

void
MemFileTest::testOrderDocSchemeDocumentsCanBeAddedToFile()
{
    // Quick explanation of the esoteric and particular values chosen below:
    // orderdoc mangles the MSB of the bucket ID based on the document ID's
    // ordering parameters and thus its bucket cannot be directly deduced from
    // the generated GID. The values given here specify a document whose GID
    // bits differ from those generated by the document and where a GID-only
    // bucket ownership check would fail (nuking the node with an assertion).
    // We have to make sure cases do not trigger false positives.
    document::BucketId bucket(0x84000000ee723751);
    auto doc = createDocument("the quick red fox trips over a hedge",
                              "orderdoc(3,1):storage_test:group1:9:9");
    doPut(std::shared_ptr<Document>(std::move(doc)),
          bucket,
          Timestamp(1000000 * 1234));
    flush(bucket);

    MemFilePtr file(getMemFile(bucket));
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), file->getSlotCount());
    // Ideally we'd test the failure case as well, but that'd require framework
    // support for death tests.
}

} // memfile
} // storage
