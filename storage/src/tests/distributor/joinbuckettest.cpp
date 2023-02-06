// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "dummy_cluster_context.h"
#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/operations/idealstate/joinoperation.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct JoinOperationTest : Test, DistributorStripeTestUtil {
    void checkSourceBucketsAndSendReply(
            JoinOperation& op,
            size_t msgIndex,
            const std::vector<document::BucketId>& wantedIds);

    void SetUp() override {
        createLinks();
    };

    void TearDown() override {
        close();
    }
};

TEST_F(JoinOperationTest, simple) {
    auto cfg = make_config();
    cfg->setJoinCount(100);
    cfg->setJoinSize(1000);
    configure_stripe(cfg);

    addNodesToBucketDB(document::BucketId(33, 1), "0=250/50/300");
    addNodesToBucketDB(document::BucketId(33, 0x100000001), "0=300/40/200");

    enable_cluster_state("distributor:1 storage:1");

    JoinOperation op(dummy_cluster_context,
                     BucketAndNodes(makeDocumentBucket(document::BucketId(32, 0)),
                                    toVector<uint16_t>(0)),
                     toVector(document::BucketId(33, 1),
                              document::BucketId(33, 0x100000001)));

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender);

    checkSourceBucketsAndSendReply(op, 0, {{33, 1}, {33, 0x100000001}});

    EXPECT_FALSE(getBucket(document::BucketId(33, 0x100000001)).valid());
    EXPECT_FALSE(getBucket(document::BucketId(33, 1)).valid());

    BucketDatabase::Entry entry = getBucket(document::BucketId(32, 0));
    ASSERT_TRUE(entry.valid());
    EXPECT_EQ(0, entry->getNodeRef(0).getNode());
    EXPECT_EQ(api::BucketInfo(666, 90, 500), entry->getNodeRef(0).getBucketInfo());
}

void
JoinOperationTest::checkSourceBucketsAndSendReply(
        JoinOperation& op,
        size_t msgIndex,
        const std::vector<document::BucketId>& wantedIds)
{
    ASSERT_GT(_sender.commands().size(), msgIndex);

    std::shared_ptr<api::StorageCommand> msg(_sender.command(msgIndex));
    ASSERT_EQ(api::MessageType::JOINBUCKETS, msg->getType());

    auto& joinCmd = dynamic_cast<api::JoinBucketsCommand&>(*msg);
    EXPECT_THAT(joinCmd.getSourceBuckets(), ContainerEq(wantedIds));

    std::shared_ptr<api::StorageReply> reply(joinCmd.makeReply());
    auto& sreply = dynamic_cast<api::JoinBucketsReply&>(*reply);
    sreply.setBucketInfo(api::BucketInfo(666, 90, 500));

    op.receive(_sender, reply);
}

/**
 * If the set of buckets kept on nodes is disjoint, send sparse joins (same
 * bucket id used as both source buckets) for those nodes having only one of
 * the buckets.
 */
TEST_F(JoinOperationTest, send_sparse_joins_to_nodes_without_both_source_buckets) {
    auto cfg = make_config();
    cfg->setJoinCount(100);
    cfg->setJoinSize(1000);
    configure_stripe(cfg);

    addNodesToBucketDB(document::BucketId(33, 1), "0=250/50/300,1=250/50/300");
    addNodesToBucketDB(document::BucketId(33, 0x100000001), "0=300/40/200");

    enable_cluster_state("distributor:1 storage:2");

    JoinOperation op(dummy_cluster_context,
                     BucketAndNodes(makeDocumentBucket(document::BucketId(32, 0)),
                                    toVector<uint16_t>(0, 1)),
                     toVector(document::BucketId(33, 1),
                              document::BucketId(33, 0x100000001)));

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender);

    ASSERT_NO_FATAL_FAILURE(checkSourceBucketsAndSendReply(op, 0, {{33, 1}, {33, 0x100000001}}));
    ASSERT_NO_FATAL_FAILURE(checkSourceBucketsAndSendReply(op, 1, {{33, 1}, {33, 1}}));
}

}
