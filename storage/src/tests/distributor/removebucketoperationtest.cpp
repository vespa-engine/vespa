// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/dummystoragelink.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storage/distributor/operations/idealstate/removebucketoperation.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/distributor.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/gtest/gtest.h>
#include "dummy_cluster_context.h"

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct RemoveBucketOperationTest : Test, DistributorTestUtil {
    void SetUp() override {
        createLinks();
    };

    void TearDown() override {
        close();
    }
};

TEST_F(RemoveBucketOperationTest, simple) {
    addNodesToBucketDB(document::BucketId(16, 1),
                       "0=10/100/1/t,"
                       "1=10/100/1/t,"
                       "2=10/100/1/t");
    setRedundancy(1);
    enableDistributorClusterState("distributor:1 storage:3");

    RemoveBucketOperation op(dummy_cluster_context,
                             BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(1,2)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));


    ASSERT_EQ("Delete bucket => 1,"
              "Delete bucket => 2",
              _sender.getCommands(true));

    sendReply(op, 0);
    sendReply(op, 1);

    ASSERT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=0,crc=0xa,docs=100/100,bytes=1/1,trusted=true,active=false,ready=false)",
              dumpBucket(document::BucketId(16, 1)));
}

/**
 * Test that receiving a DeleteBucket failure from a storage node that sends
 * back actual bucket info reinserts that bucket info into the distributor
 * bucket database.
 */
TEST_F(RemoveBucketOperationTest, bucket_info_mismatch_failure) {
    addNodesToBucketDB(document::BucketId(16, 1), "1=0/0/0/t");

    getComponentRegisterImpl().setDistribution(
            std::make_shared<lib::Distribution>(lib::Distribution::getDefaultDistributionConfig(1, 10)));

    enableDistributorClusterState("distributor:1 storage:2");

    RemoveBucketOperation op(dummy_cluster_context,
                             BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(1)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    ASSERT_EQ("Delete bucket => 1", _sender.getCommands(true));
    ASSERT_EQ(1, _sender.commands().size());

    std::shared_ptr<api::StorageCommand> msg2  = _sender.command(0);
    std::shared_ptr<api::StorageReply> reply(msg2->makeReply().release());
    dynamic_cast<api::DeleteBucketReply&>(*reply).setBucketInfo(
            api::BucketInfo(10, 100, 1));
    reply->setResult(api::ReturnCode::REJECTED);
    op.receive(_sender, reply);

    // RemoveBucketOperation should reinsert bucketinfo into database
    ASSERT_EQ("BucketId(0x4000000000000001) : "
              "node(idx=1,crc=0xa,docs=100/100,bytes=1/1,trusted=true,active=false,ready=false)",
              dumpBucket(document::BucketId(16, 1)));
}

/**
 * Test that receiving a DeleteBucket failure from a storage node that does
 * not include valid BucketInfo in its reply does not reinsert the bucket
 * into the distributor.
 */
TEST_F(RemoveBucketOperationTest, fail_with_invalid_bucket_info) {
    addNodesToBucketDB(document::BucketId(16, 1), "1=0/0/0/t");

    getComponentRegisterImpl().setDistribution(
            std::make_shared<lib::Distribution>(lib::Distribution::getDefaultDistributionConfig(1, 10)));

    enableDistributorClusterState("distributor:1 storage:2");

    RemoveBucketOperation op(dummy_cluster_context,
                             BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(1)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    ASSERT_EQ("Delete bucket => 1", _sender.getCommands(true));
    ASSERT_EQ(1, _sender.commands().size());

    std::shared_ptr<api::StorageCommand> msg2  = _sender.command(0);
    std::shared_ptr<api::StorageReply> reply(msg2->makeReply().release());
    reply->setResult(api::ReturnCode::ABORTED);
    op.receive(_sender, reply);

    EXPECT_EQ("NONEXISTING", dumpBucket(document::BucketId(16, 1)));
}

} // storage::distributor
