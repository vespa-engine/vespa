// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memfiletestutils.h"
#include <vespa/document/datatype/documenttype.h>

using document::DocumentType;

namespace storage {
namespace memfile {
namespace {
    spi::LoadType defaultLoadType(0, "default");
}

class SplitOperationHandlerTest : public SingleDiskMemFileTestUtils
{

    void doTestMultiDisk(uint16_t sourceDisk,
                         uint16_t targetDisk0,
                         uint16_t targetDisk1);


    CPPUNIT_TEST_SUITE(SplitOperationHandlerTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testMultiDisk);
    CPPUNIT_TEST(testMultiDiskNonZeroSourceIndex);
    CPPUNIT_TEST(testExceptionDuringSplittingEvictsAllBuckets);
    CPPUNIT_TEST_SUITE_END();

public:
    void testSimple();
    void testMultiDisk();
    void testMultiDiskNonZeroSourceIndex();
    void testExceptionDuringSplittingEvictsAllBuckets();
};

CPPUNIT_TEST_SUITE_REGISTRATION(SplitOperationHandlerTest);

void
SplitOperationHandlerTest::testSimple()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    setupDisks(1);

    for (uint32_t i = 0; i < 100; i++) {
        uint32_t location = 4;
        if (i % 2 == 0) {
            location |= (1 << 16);
        }

        doPut(location, Timestamp(1000 + i));
    }
    flush(document::BucketId(16, 4));

    env()._cache.clear();

    document::BucketId sourceBucket = document::BucketId(16, 4);
    document::BucketId target1 = document::BucketId(17, 4);
    document::BucketId target2 = document::BucketId(17, 4 | (1 << 16));

    SplitOperationHandler handler(env());
    spi::Result result = getPersistenceProvider().split(
            spi::Bucket(sourceBucket, spi::PartitionId(0)),
            spi::Bucket(target1, spi::PartitionId(0)),
            spi::Bucket(target2, spi::PartitionId(0)),
            context);

    env()._cache.clear();

    {
        MemFilePtr file(handler.getMemFile(sourceBucket, 0));
        CPPUNIT_ASSERT_EQUAL(0, (int)file->getSlotCount());
    }

    {
        MemFilePtr file(handler.getMemFile(target1, 0));
        CPPUNIT_ASSERT_EQUAL(50, (int)file->getSlotCount());
        for (uint32_t i = 0; i < file->getSlotCount(); ++i) {
            file->getDocument((*file)[i], ALL);
        }
    }

    {
        MemFilePtr file(handler.getMemFile(target2, 0));
        CPPUNIT_ASSERT_EQUAL(50, (int)file->getSlotCount());
        for (uint32_t i = 0; i < file->getSlotCount(); ++i) {
            file->getDocument((*file)[i], ALL);
        }
    }
}

void
SplitOperationHandlerTest::doTestMultiDisk(uint16_t sourceDisk,
                                           uint16_t targetDisk0,
                                           uint16_t targetDisk1)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    setupDisks(3);

    for (uint32_t i = 0; i < 100; i++) {
        uint32_t location = 4;
        if (i % 2 == 0) {
            location |= (1 << 16);
        }

        doPutOnDisk(sourceDisk, location, Timestamp(1000 + i));
    }
    flush(document::BucketId(16, 4));

    env()._cache.clear();

    document::BucketId sourceBucket = document::BucketId(16, 4);
    document::BucketId target1 = document::BucketId(17, 4);
    document::BucketId target2 = document::BucketId(17, 4 | (1 << 16));

    SplitOperationHandler handler(env());
    spi::Result result = getPersistenceProvider().split(
            spi::Bucket(sourceBucket, spi::PartitionId(sourceDisk)),
            spi::Bucket(target1, spi::PartitionId(targetDisk0)),
            spi::Bucket(target2, spi::PartitionId(targetDisk1)),
            context);

    env()._cache.clear();

    {
        MemFilePtr file(handler.getMemFile(sourceBucket, sourceDisk));
        CPPUNIT_ASSERT_EQUAL(0, (int)file->getSlotCount());
    }

    {
        MemFilePtr file(handler.getMemFile(target1, targetDisk0));
        CPPUNIT_ASSERT_EQUAL(50, (int)file->getSlotCount());
        for (uint32_t i = 0; i < file->getSlotCount(); ++i) {
            file->getDocument((*file)[i], ALL);
        }
    }

    {
        MemFilePtr file(handler.getMemFile(target2, targetDisk1));
        CPPUNIT_ASSERT_EQUAL(50, (int)file->getSlotCount());
        for (uint32_t i = 0; i < file->getSlotCount(); ++i) {
            file->getDocument((*file)[i], ALL);
        }
    }
}

void
SplitOperationHandlerTest::testMultiDisk()
{
    doTestMultiDisk(0, 1, 2);
}

void
SplitOperationHandlerTest::testMultiDiskNonZeroSourceIndex()
{
    doTestMultiDisk(1, 2, 0);
}

void
SplitOperationHandlerTest::testExceptionDuringSplittingEvictsAllBuckets()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    setupDisks(1);

    for (uint32_t i = 0; i < 100; i++) {
        uint32_t location = 4;
        if (i % 2 == 0) {
            location |= (1 << 16);
        }

        doPut(location, Timestamp(1000 + i));
    }
    flush(document::BucketId(16, 4));

    simulateIoErrorsForSubsequentlyOpenedFiles();

    document::BucketId sourceBucket(16, 4);
    document::BucketId target1(17, 4);
    document::BucketId target2(17, 4 | (1 << 16));

    try {
        SplitOperationHandler handler(env());
        spi::Result result = getPersistenceProvider().split(
                spi::Bucket(sourceBucket, spi::PartitionId(0)),
                spi::Bucket(target1, spi::PartitionId(0)),
                spi::Bucket(target2, spi::PartitionId(0)),
                context);
        CPPUNIT_FAIL("Exception not thrown on flush failure");
    } catch (std::exception&) {
    }

    CPPUNIT_ASSERT(!env()._cache.contains(sourceBucket));
    CPPUNIT_ASSERT(!env()._cache.contains(target1));
    CPPUNIT_ASSERT(!env()._cache.contains(target2));

    unSimulateIoErrorsForSubsequentlyOpenedFiles();

    // Source must not have been deleted
    {
        SplitOperationHandler handler(env());
        MemFilePtr file(handler.getMemFile(sourceBucket, 0));
        CPPUNIT_ASSERT_EQUAL(100, (int)file->getSlotCount());
    }
}

}

}
