// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memfiletestutils.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/persistence/spi/test.h>

using document::DocumentType;
using storage::spi::test::makeSpiBucket;

namespace storage {
namespace memfile {
namespace {
    spi::LoadType defaultLoadType(0, "default");
}

class JoinOperationHandlerTest : public MemFileTestUtils
{
    CPPUNIT_TEST_SUITE(JoinOperationHandlerTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testTargetExists);
    CPPUNIT_TEST(testTargetWithOverlap);
    CPPUNIT_TEST(testMultiDisk);
    CPPUNIT_TEST(testMultiDiskFlushed);
    CPPUNIT_TEST(testInternalJoin);
    CPPUNIT_TEST(testInternalJoinDiskFull);
    CPPUNIT_TEST(testTargetIoWriteExceptionEvictsTargetFromCache);
    CPPUNIT_TEST(test1stSourceIoReadExceptionEvictsSourceFromCache);
    CPPUNIT_TEST(test2ndSourceExceptionEvictsExistingTargetFromCache);
    CPPUNIT_TEST_SUITE_END();

public:
    void testSimple();
    void testTargetExists();
    void testTargetWithOverlap();
    void testMultiDisk();
    void testMultiDiskFlushed();
    void testInternalJoin();
    void testInternalJoinDiskFull();
    void testTargetIoWriteExceptionEvictsTargetFromCache();
    void test1stSourceIoReadExceptionEvictsSourceFromCache();
    void test2ndSourceExceptionEvictsExistingTargetFromCache();

    void insertDocumentInBucket(uint64_t location,
                                Timestamp timestamp,
                                document::BucketId bucket);

private:
    void feedSingleDisk();
    void feedMultiDisk();
    std::string getStandardMemFileStatus(uint32_t disk = 0);

    spi::Result doJoin(const document::BucketId to,
                       const document::BucketId from1,
                       const document::BucketId from2);
};

namespace {

document::BucketId TARGET = document::BucketId(15, 4);
document::BucketId SOURCE1 = document::BucketId(16, 4);
document::BucketId SOURCE2 = document::BucketId(16, (uint64_t)4 | ((uint64_t)1 << 15));
}

CPPUNIT_TEST_SUITE_REGISTRATION(JoinOperationHandlerTest);

void
JoinOperationHandlerTest::feedSingleDisk()
{
    for (uint32_t i = 0; i < 100; i++) {
        std::ostringstream ost;
        ost << "userdoc:storage_test:1234:" << i;
        const DocumentType& type(
                *getTypeRepo()->getDocumentType("testdoctype1"));
        document::Document::SP doc(
                new document::Document(type, document::DocumentId(ost.str())));

        document::BucketId bucket(
                getBucketIdFactory().getBucketId(doc->getId()));
        bucket.setUsedBits(33);
        doPut(doc, Timestamp(1000 + i), 0, 33);
        flush(bucket);
    }
}

void
JoinOperationHandlerTest::feedMultiDisk()
{
    for (uint32_t i = 0; i < 100; i += 2) {
        doPutOnDisk(7, 4 | (1 << 15), Timestamp(1000 + i));
    }
    flush(SOURCE2);

    for (uint32_t i = 1; i < 100; i += 2) {
        doPutOnDisk(4, 4, Timestamp(1000 + i));
    }
    flush(SOURCE1);

    {
        MemFilePtr file(getMemFile(SOURCE1, 4));
        CPPUNIT_ASSERT_EQUAL(50, (int)file->getSlotCount());
        CPPUNIT_ASSERT_EQUAL(4, (int)file->getDisk());
    }

    {
        MemFilePtr file(getMemFile(SOURCE2, 7));
        CPPUNIT_ASSERT_EQUAL(50, (int)file->getSlotCount());
        CPPUNIT_ASSERT_EQUAL(7, (int)file->getDisk());
    }
}

std::string
JoinOperationHandlerTest::getStandardMemFileStatus(uint32_t disk)
{
    std::ostringstream ost;

    ost << getMemFileStatus(TARGET, disk) << "\n"
        << getMemFileStatus(SOURCE1, disk ) << "\n"
        << getMemFileStatus(SOURCE2, disk) << "\n";

    return ost.str();
}

void
JoinOperationHandlerTest::insertDocumentInBucket(
        uint64_t location,
        Timestamp timestamp,
        document::BucketId bucket)
{
    Document::SP doc(
            createRandomDocumentAtLocation(
                    location, timestamp.getTime(), 100, 100));
    doPut(doc, bucket, timestamp);
}

spi::Result
JoinOperationHandlerTest::doJoin(const document::BucketId to,
                                 const document::BucketId from1,
                                 const document::BucketId from2)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    return getPersistenceProvider().join(
            makeSpiBucket(from1),
            makeSpiBucket(from2),
            makeSpiBucket(to),
            context);
}

void
JoinOperationHandlerTest::testSimple()
{
    setupDisks(1);
    feedSingleDisk();

    {
        MemFilePtr file(getMemFile(document::BucketId(33, 1234)));
        CPPUNIT_ASSERT_EQUAL(50, (int)file->getSlotCount());
    }

    {
        MemFilePtr file(getMemFile(document::BucketId(33, (uint64_t)1234 | ((uint64_t)1 << 32))));
        CPPUNIT_ASSERT_EQUAL(50, (int)file->getSlotCount());
    }

    spi::Result result =
        doJoin(document::BucketId(32, 1234),
               document::BucketId(33, 1234),
               document::BucketId(33, (uint64_t)1234 | ((uint64_t)1 << 32)));

    {
        MemFilePtr file(getMemFile(document::BucketId(32, (uint64_t)1234)));
        CPPUNIT_ASSERT_EQUAL(100, (int)file->getSlotCount());
        CPPUNIT_ASSERT(!file->slotsAltered());
    }
}

void
JoinOperationHandlerTest::testTargetExists()
{
    setupDisks(1);

    for (uint32_t i = 0; i < 100; i += 2) {
        doPut(4 | (1 << 15), Timestamp(1000 + i));
    }
    flush(SOURCE2);

    for (uint32_t i = 1; i < 100; i += 2) {
        doPut(4, Timestamp(1000 + i));
    }
    flush(SOURCE1);

    for (uint32_t i = 0; i < 100; i++) {
        uint32_t location = 4;
        if (i % 2 == 0) {
            location |= (1 << 15);
        }

        insertDocumentInBucket(location, Timestamp(500 + i), TARGET);
    }
    flush(TARGET);

    doJoin(TARGET, SOURCE1, SOURCE2);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x3c00000000000004): 200,0\n"
                    "BucketId(0x4000000000000004): 0,0\n"
                    "BucketId(0x4000000000008004): 0,0\n"),
            getStandardMemFileStatus());
}

void
JoinOperationHandlerTest::testTargetWithOverlap()
{
    setupDisks(1);

    for (uint32_t i = 0; i < 100; i += 2) {
        doPut(4 | (1 << 15), Timestamp(1000 + i));
    }
    flush(SOURCE2);

    for (uint32_t i = 1; i < 100; i += 2) {
        doPut(4, Timestamp(1000 + i));
    }
    flush(SOURCE1);

    for (uint32_t i = 0; i < 100; i++) {
        uint32_t location = 4;
        if (i % 2 == 0) {
            location |= (1 << 15);
        }

        insertDocumentInBucket(location, Timestamp(950 + i), TARGET);
    }
    flush(TARGET);

    doJoin(TARGET, SOURCE1, SOURCE2);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x3c00000000000004): 150,0\n"
                    "BucketId(0x4000000000000004): 0,0\n"
                    "BucketId(0x4000000000008004): 0,0\n"),
            getStandardMemFileStatus());
}

void
JoinOperationHandlerTest::testMultiDisk()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    setupDisks(10);
    feedMultiDisk();

    getPersistenceProvider().join(makeSpiBucket(SOURCE2, spi::PartitionId(7)),
                                  makeSpiBucket(SOURCE1, spi::PartitionId(4)),
                                  makeSpiBucket(TARGET, spi::PartitionId(3)),
                                  context);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x3c00000000000004): 100,3\n"
                    "BucketId(0x4000000000000004): 0,0\n"
                    "BucketId(0x4000000000008004): 0,0\n"),
            getStandardMemFileStatus());
}

void
JoinOperationHandlerTest::testMultiDiskFlushed()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    setupDisks(10);
    feedMultiDisk();

    // Flush everything to disk, to check that we can join even
    // if it's not in cache before.
    env()._cache.flushDirtyEntries();
    env()._cache.clear();

    getPersistenceProvider().join(makeSpiBucket(SOURCE2, spi::PartitionId(7)),
                                  makeSpiBucket(SOURCE1, spi::PartitionId(4)),
                                  makeSpiBucket(TARGET, spi::PartitionId(3)),
                                  context);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x3c00000000000004): 100,3\n"
                    "BucketId(0x4000000000000004): 0,3\n"
                    "BucketId(0x4000000000008004): 0,3\n"),
            getStandardMemFileStatus(3));
}

void
JoinOperationHandlerTest::testInternalJoin()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    setupDisks(10);

    for (uint32_t i = 4; i < 6; i++) {
        for (uint32_t j = 0; j < 10; j++) {
            uint32_t location = 4;
            doPutOnDisk(i, location, Timestamp(i * 1000 + j));
        }
        flush(document::BucketId(16, 4), i);
        env()._cache.clear();
    }

    std::string fileName1 =
        env().calculatePathInDir(SOURCE1, (*env()._mountPoints)[4]);
    std::string fileName2 =
        env().calculatePathInDir(SOURCE1, (*env()._mountPoints)[5]);

    CPPUNIT_ASSERT(vespalib::stat(fileName1).get());
    vespalib::FileInfo::UP file2(vespalib::stat(fileName2));

    CPPUNIT_ASSERT(file2.get());
    CPPUNIT_ASSERT(file2->_size > 0);

    PartitionMonitor* mon = env().getDirectory(5).getPartition().getMonitor();
    // Set disk under 80% full. Over 80%, we shouldn't move buckets to the target.
    mon->setStatOncePolicy();
    mon->overrideRealStat(512, 100000, 50000);
    CPPUNIT_ASSERT(!mon->isFull(0, .80f));

    getPersistenceProvider().join(makeSpiBucket(SOURCE1, spi::PartitionId(4)),
                                  makeSpiBucket(SOURCE1, spi::PartitionId(4)),
                                  makeSpiBucket(SOURCE1, spi::PartitionId(5)),
                                  context);

    env()._cache.clear();

    CPPUNIT_ASSERT(!vespalib::stat(fileName1).get());
    CPPUNIT_ASSERT(vespalib::stat(fileName2).get());
}

void
JoinOperationHandlerTest::testInternalJoinDiskFull()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    setupDisks(10);

    for (uint32_t i = 4; i < 6; i++) {
        for (uint32_t j = 0; j < 10; j++) {
            uint32_t location = 4;
            doPutOnDisk(i, location, Timestamp(i * 1000 + j));
        }
        flush(document::BucketId(16, 4), i);
        env()._cache.clear();
    }

    std::string fileName1 =
        env().calculatePathInDir(SOURCE1, (*env()._mountPoints)[4]);
    std::string fileName2 =
        env().calculatePathInDir(SOURCE1, (*env()._mountPoints)[5]);

    CPPUNIT_ASSERT(vespalib::stat(fileName1).get());
    vespalib::FileInfo::UP file2(vespalib::stat(fileName2));

    CPPUNIT_ASSERT(file2.get());
    CPPUNIT_ASSERT(file2->_size > 0);

    PartitionMonitor* mon = env().getDirectory(5).getPartition().getMonitor();
    // Set disk to 81% full. Over 80%, we shouldn't move buckets to the target.
    mon->setStatOncePolicy();
    mon->overrideRealStat(512, 100000, 81000);
    CPPUNIT_ASSERT(!mon->isFull());
    CPPUNIT_ASSERT(mon->isFull(0, .08f));

    spi::Result result =
        getPersistenceProvider().join(makeSpiBucket(SOURCE1, spi::PartitionId(4)),
                                      makeSpiBucket(SOURCE1, spi::PartitionId(4)),
                                      makeSpiBucket(SOURCE1, spi::PartitionId(5)),
                                      context);

    CPPUNIT_ASSERT(result.hasError());
}

void
JoinOperationHandlerTest::testTargetIoWriteExceptionEvictsTargetFromCache()
{
    setupDisks(1);
    feedSingleDisk();

    document::BucketId src1(33, 1234);
    document::BucketId src2(33, 1234ULL | (1ULL << 32));
    document::BucketId target(32, 1234);

    CPPUNIT_ASSERT(env()._cache.contains(src1));
    CPPUNIT_ASSERT(env()._cache.contains(src2));
    CPPUNIT_ASSERT(!env()._cache.contains(target));

    // Reading existing (fully cached) files will go fine, but writing
    // new file will not.
    simulateIoErrorsForSubsequentlyOpenedFiles();

    spi::Result result = doJoin(target, src1, src2);
    CPPUNIT_ASSERT(result.hasError());
    CPPUNIT_ASSERT(result.getErrorMessage().find("A simulated I/O write")
                   != vespalib::string::npos);

    CPPUNIT_ASSERT(!env()._cache.contains(target));
    // NOTE: since we end up renaming src1 -> target during the first
    // iteration of join, src1 will actually be empty. This should not
    // matter since the service layer will query the bucket info for
    // all these afterwards and will thus pick up on this automatically.
    unSimulateIoErrorsForSubsequentlyOpenedFiles();
    {
        MemFilePtr file(getMemFile(src1));
        CPPUNIT_ASSERT_EQUAL(0, (int)file->getSlotCount());
        CPPUNIT_ASSERT(!file->slotsAltered());
    }
    {
        MemFilePtr file(getMemFile(src2));
        CPPUNIT_ASSERT_EQUAL(50, (int)file->getSlotCount());
        CPPUNIT_ASSERT(!file->slotsAltered());
    }
    {
        MemFilePtr file(getMemFile(target));
        // Renamed from src1
        CPPUNIT_ASSERT_EQUAL(50, (int)file->getSlotCount());
        CPPUNIT_ASSERT(!file->slotsAltered());
    }
}

void
JoinOperationHandlerTest::test1stSourceIoReadExceptionEvictsSourceFromCache()
{
    setupDisks(1);
    feedSingleDisk();

    document::BucketId src1(33, 1234);
    document::BucketId src2(33, 1234ULL | (1ULL << 32));
    document::BucketId target(32, 1234);

    env()._cache.clear();
    // Allow for reading in initial metadata so that loadFile itself doesn't
    // fail. This could otherwise cause a false negative since that happens
    // during initial cache lookup on a cache miss, at which point any
    // exception will always stop a file from being added to the cache. Here
    // we want to test the case where a file has been successfully hoisted
    // out of the cache initially.
    simulateIoErrorsForSubsequentlyOpenedFiles(IoErrors().afterReads(1));

    spi::Result result = doJoin(target, src1, src2);
    CPPUNIT_ASSERT(result.hasError());
    CPPUNIT_ASSERT(result.getErrorMessage().find("A simulated I/O read")
                   != vespalib::string::npos);

    CPPUNIT_ASSERT(!env()._cache.contains(src1));
    CPPUNIT_ASSERT(!env()._cache.contains(src2));
    CPPUNIT_ASSERT(!env()._cache.contains(target));
}

/**
 * It must be exception safe for any source bucket to throw an exception during
 * processing. Otherwise the node will core due to cache sanity checks.
 *
 * See VESPA-674 for context. In this scenario, it was not possible to write
 * to the target file when attempting to join in the 2nd source bucket due to
 * the disk fill ratio exceeding configured limits.
 */
void
JoinOperationHandlerTest::test2ndSourceExceptionEvictsExistingTargetFromCache()
{
    setupDisks(1);
    feedSingleDisk();

    constexpr uint64_t location = 1234;

    document::BucketId src1(33, location);
    document::BucketId src2(33, location | (1ULL << 32));
    document::BucketId target(32, location);

    // Ensure target file is _not_ empty so that copySlots is triggered for
    // each source bucket (rather than just renaming the file, which does not
    // invoke the file read/write paths).
    insertDocumentInBucket(location, Timestamp(100000), target);
    flush(target);

    env()._cache.clear();
    // File rewrites are buffered before ever reaching the failure simulation
    // layer, so only 1 actual write is used to flush the target file after
    // the first source file has been processed. Attempting to flush the writes
    // for the second source file should fail with an exception.
    simulateIoErrorsForSubsequentlyOpenedFiles(
            IoErrors().afterReads(INT_MAX).afterWrites(1));

    spi::Result result = doJoin(target, src1, src2);
    CPPUNIT_ASSERT(result.hasError());
    CPPUNIT_ASSERT(result.getErrorMessage().find("A simulated I/O write")
                   != vespalib::string::npos);

    CPPUNIT_ASSERT(!env()._cache.contains(src1));
    CPPUNIT_ASSERT(!env()._cache.contains(src2));
    CPPUNIT_ASSERT(!env()._cache.contains(target));
}

}

}
