// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/diskmoveoperationhandler.h>
#include <vespa/storage/persistence/messages.h>
#include <tests/persistence/persistencetestutils.h>
#include <vespa/document/test/make_document_bucket.h>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

struct DiskMoveOperationHandlerTest : PersistenceTestUtils {};

TEST_F(DiskMoveOperationHandlerTest, simple) {
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

    DiskMoveOperationHandler diskMoveHandler(getEnv(3),getPersistenceProvider());
    document::Bucket bucket = makeDocumentBucket(document::BucketId(16, 4));
    auto move = std::make_shared<BucketDiskMoveCommand>(bucket, 3, 4);
    spi::Context context(documentapi::LoadType::DEFAULT, 0, 0);
    diskMoveHandler.handleBucketDiskMove(*move, createTracker(move, bucket));

    EXPECT_EQ("BucketId(0x4000000000000004): 10,4",
              getBucketStatus(document::BucketId(16,4)));
}

}
