// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributortestutil.h>
#include <vespa/storage/distributor/operations/idealstate/setbucketstateoperation.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/gtest/gtest.h>
#include "dummy_cluster_context.h"

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct BucketStateOperationTest : Test, DistributorTestUtil {
    void SetUp() override {
        createLinks();
    }
    void TearDown() override {
        close();
    }
};

TEST_F(BucketStateOperationTest, active_state_supported_in_bucket_db) {
    document::BucketId bid(16, 1);
    insertBucketInfo(bid, 0, 0xabc, 10, 1100, true, true);

    BucketDatabase::Entry entry = getBucket(bid);
    ASSERT_TRUE(entry.valid());
    EXPECT_TRUE(entry->getNode(0)->active());
    EXPECT_EQ("node(idx=0,crc=0xabc,docs=10/10,bytes=1100/1100,"
              "trusted=true,active=true,ready=false)",
              entry->getNode(0)->toString());
}

TEST_F(BucketStateOperationTest, activate_single_node) {
    document::BucketId bid(16, 1);
    insertBucketInfo(bid, 0, 0xabc, 10, 1100, true, false);

    BucketAndNodes bucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(0));
    std::vector<uint16_t> active;
    active.push_back(0);
    SetBucketStateOperation op(dummy_cluster_context, bucketAndNodes, active);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    ASSERT_EQ(1, _sender.commands().size());

    std::shared_ptr<api::StorageCommand> msg  = _sender.command(0);
    ASSERT_EQ(msg->getType(), api::MessageType::SETBUCKETSTATE);
    EXPECT_EQ(api::StorageMessageAddress(dummy_cluster_context.cluster_name_ptr(), lib::NodeType::STORAGE, 0).toString(),
              msg->getAddress()->toString());

    auto& cmd = dynamic_cast<const api::SetBucketStateCommand&>(*msg);
    EXPECT_EQ(bid, cmd.getBucketId());
    EXPECT_EQ(api::SetBucketStateCommand::ACTIVE, cmd.getState());

    std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
    op.receive(_sender, reply);

    BucketDatabase::Entry entry = getBucket(bid);
    ASSERT_TRUE(entry.valid());
    EXPECT_TRUE(entry->getNodeRef(0).active());

    EXPECT_TRUE(op.ok());

    // TODO: check that it's done
}

TEST_F(BucketStateOperationTest, activate_and_deactivate_nodes) {
    document::BucketId bid(16, 1);
    insertBucketInfo(bid, 0, 0xabc, 10, 1100, false, true);
    insertBucketInfo(bid, 1, 0xdef, 15, 1500, false, false);

    BucketAndNodes bucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(0, 1));
    std::vector<uint16_t> active;
    active.push_back(1);
    SetBucketStateOperation op(dummy_cluster_context, bucketAndNodes, active);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    ASSERT_EQ(1, _sender.commands().size());
    {
        std::shared_ptr<api::StorageCommand> msg = _sender.command(0);
        ASSERT_EQ(msg->getType(), api::MessageType::SETBUCKETSTATE);
        EXPECT_EQ(api::StorageMessageAddress(dummy_cluster_context.cluster_name_ptr(), lib::NodeType::STORAGE, 1).toString(),
                  msg->getAddress()->toString());

        auto& cmd = dynamic_cast<const api::SetBucketStateCommand&>(*msg);
        EXPECT_EQ(bid, cmd.getBucketId());
        EXPECT_EQ(api::SetBucketStateCommand::ACTIVE, cmd.getState());

        std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
        op.receive(_sender, reply);
    }

    ASSERT_EQ(2, _sender.commands().size());
    {
        std::shared_ptr<api::StorageCommand> msg  = _sender.command(1);
        ASSERT_EQ(msg->getType(), api::MessageType::SETBUCKETSTATE);
        EXPECT_EQ(api::StorageMessageAddress(dummy_cluster_context.cluster_name_ptr(),
                                             lib::NodeType::STORAGE, 0).toString(),
                  msg->getAddress()->toString());

        auto& cmd = dynamic_cast<const api::SetBucketStateCommand&>(*msg);
        EXPECT_EQ(bid, cmd.getBucketId());
        EXPECT_EQ(api::SetBucketStateCommand::INACTIVE, cmd.getState());

        std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
        op.receive(_sender, reply);
    }

    BucketDatabase::Entry entry = getBucket(bid);
    ASSERT_TRUE(entry.valid());
    EXPECT_EQ("node(idx=0,crc=0xabc,docs=10/10,bytes=1100/1100,"
              "trusted=true,active=false,ready=false)",
              entry->getNodeRef(0).toString());
    EXPECT_EQ("node(idx=1,crc=0xdef,docs=15/15,bytes=1500/1500,"
              "trusted=false,active=true,ready=false)",
              entry->getNodeRef(1).toString());

    EXPECT_TRUE(op.ok());
}

TEST_F(BucketStateOperationTest, do_not_deactivate_if_activate_fails) {
    document::BucketId bid(16, 1);
    insertBucketInfo(bid, 0, 0xabc, 10, 1100, false, true);
    insertBucketInfo(bid, 1, 0xdef, 15, 1500, false, false);

    BucketAndNodes bucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(0, 1));
    std::vector<uint16_t> active;
    active.push_back(1);
    SetBucketStateOperation op(dummy_cluster_context, bucketAndNodes, active);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    ASSERT_EQ(1, _sender.commands().size());
    {
        std::shared_ptr<api::StorageCommand> msg  = _sender.command(0);
        ASSERT_EQ(msg->getType(), api::MessageType::SETBUCKETSTATE);
        EXPECT_EQ(api::StorageMessageAddress(dummy_cluster_context.cluster_name_ptr(),
                                             lib::NodeType::STORAGE, 1).toString(),
                  msg->getAddress()->toString());

        auto& cmd = dynamic_cast<const api::SetBucketStateCommand&>(*msg);
        EXPECT_EQ(bid, cmd.getBucketId());
        EXPECT_EQ(api::SetBucketStateCommand::ACTIVE, cmd.getState());

        std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
        reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "aaarg!"));
        op.receive(_sender, reply);
    }

    ASSERT_EQ(1, _sender.commands().size());

    BucketDatabase::Entry entry = getBucket(bid);
    ASSERT_TRUE(entry.valid());
    EXPECT_EQ("node(idx=0,crc=0xabc,docs=10/10,bytes=1100/1100,"
              "trusted=true,active=true,ready=false)",
              entry->getNodeRef(0).toString());
    EXPECT_EQ("node(idx=1,crc=0xdef,docs=15/15,bytes=1500/1500,"
              "trusted=false,active=false,ready=false)",
              entry->getNodeRef(1).toString());

    EXPECT_FALSE(op.ok());
}

TEST_F(BucketStateOperationTest, bucket_db_not_updated_on_failure) {
    document::BucketId bid(16, 1);
    insertBucketInfo(bid, 0, 0xabc, 10, 1100, true, false);

    BucketAndNodes bucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(0));
    std::vector<uint16_t> active;
    active.push_back(0);
    SetBucketStateOperation op(dummy_cluster_context, bucketAndNodes, active);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    ASSERT_EQ(1, _sender.commands().size());

    std::shared_ptr<api::StorageCommand> msg  = _sender.command(0);
    ASSERT_EQ(msg->getType(), api::MessageType::SETBUCKETSTATE);
    EXPECT_EQ(api::StorageMessageAddress(dummy_cluster_context.cluster_name_ptr(),
                                         lib::NodeType::STORAGE, 0).toString(),
              msg->getAddress()->toString());

    std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
    reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "aaarg!"));
    op.receive(_sender, reply);

    BucketDatabase::Entry entry = getBucket(bid);
    ASSERT_TRUE(entry.valid());
    // Should not be updated
    EXPECT_FALSE(entry->getNodeRef(0).active());

    EXPECT_FALSE(op.ok());
}

} // namespace storage::distributor
