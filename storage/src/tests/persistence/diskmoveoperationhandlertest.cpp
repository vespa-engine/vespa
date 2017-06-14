// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/diskmoveoperationhandler.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/persistence/messages.h>
#include <tests/persistence/persistencetestutils.h>

namespace storage {

class DiskMoveOperationHandlerTest : public PersistenceTestUtils
{
    CPPUNIT_TEST_SUITE(DiskMoveOperationHandlerTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST_SUITE_END();

public:
    void testSimple();
    void testTargetExists();
    void testTargetWithOverlap();

    void insertDocumentInBucket(uint64_t location, uint64_t timestamp, document::BucketId bucket);
};

CPPUNIT_TEST_SUITE_REGISTRATION(DiskMoveOperationHandlerTest);

void
DiskMoveOperationHandlerTest::testSimple()
{
    setupDisks(10);

    // Create bucket 16, 4 on disk 3.
    {
        StorBucketDatabase::WrappedEntry entry(
                createBucket(document::BucketId(16, 4)));
        entry->disk = 3;
        entry.write();
    }

    for (uint32_t i = 0; i < 10; i++) {
        doPutOnDisk(3, 4, spi::Timestamp(1000 + i));
    }

    DiskMoveOperationHandler diskMoveHandler(
            getEnv(3),
            getPersistenceProvider());
    BucketDiskMoveCommand move(document::BucketId(16, 4), 3, 4);

    spi::Context context(documentapi::LoadType::DEFAULT, 0, 0);
    diskMoveHandler.handleBucketDiskMove(move, context);

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000004): 10,4"),
            getBucketStatus(document::BucketId(16,4)));
}

}
