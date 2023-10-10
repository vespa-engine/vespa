// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <vespa/document/test/make_document_bucket.h>

LOG_SETUP(".singlebucketjointest");

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

struct SingleBucketJoinTest : FileStorTestFixture {
};

TEST_F(SingleBucketJoinTest, persistence_can_handle_single_bucket_join) {
    TestFileStorComponents c(*this);
    document::BucketId targetBucket(16, 1);
    document::BucketId sourceBucket(17, 1);

    createBucket(sourceBucket);
    // Make sure it's not empty
    c.sendPut(sourceBucket, DocumentIndex(0), PutTimestamp(1000));
    expectOkReply<api::PutReply>(c.top);
    c.top.getRepliesOnce();

    auto cmd = std::make_shared<api::JoinBucketsCommand>(makeDocumentBucket(targetBucket));
    cmd->getSourceBuckets().push_back(sourceBucket);
    cmd->getSourceBuckets().push_back(sourceBucket);

    c.top.sendDown(cmd);
    // If single bucket join locking is not working properly, this
    // will hang forever.
    ASSERT_NO_FATAL_FAILURE(expectOkReply<api::JoinBucketsReply>(c.top));
}

} // namespace storage
