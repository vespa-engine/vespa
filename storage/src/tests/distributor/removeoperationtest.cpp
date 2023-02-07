// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/operations/external/removeoperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct RemoveOperationTest : Test, DistributorStripeTestUtil {
    document::DocumentId docId;
    document::BucketId bucketId;
    std::unique_ptr<RemoveOperation> op;

    void SetUp() override {
        createLinks();

        docId = document::DocumentId("id:test:test::uri");
        bucketId = operation_context().make_split_bit_constrained_bucket_id(docId);
        enable_cluster_state("distributor:1 storage:4");
    };

    void TearDown() override {
        close();
    }

    void sendRemove(document::DocumentId dId) {
        auto msg = std::make_shared<api::RemoveCommand>(makeDocumentBucket(document::BucketId(0)), dId, 100);

        op = std::make_unique<RemoveOperation>(
                node_context(),
                operation_context(),
                getDistributorBucketSpace(),
                msg,
                metrics().removes);

        op->start(_sender);
    }

    void replyToMessage(RemoveOperation& callback,
                        uint32_t index,
                        uint64_t oldTimestamp)
    {
        if (index == (uint32_t)-1) {
            index = _sender.commands().size() - 1;
        }

        std::shared_ptr<api::StorageMessage> msg2  = _sender.command(index);
        auto* removec = dynamic_cast<api::RemoveCommand*>(msg2.get());
        std::unique_ptr<api::StorageReply> reply(removec->makeReply());
        auto* removeR = dynamic_cast<api::RemoveReply*>(reply.get());
        removeR->setOldTimestamp(oldTimestamp);
        callback.onReceive(_sender, std::shared_ptr<api::StorageReply>(reply.release()));
    }

    void sendRemove() {
        sendRemove(docId);
    }
};

TEST_F(RemoveOperationTest, simple) {
    addNodesToBucketDB(bucketId, "1=0");

    sendRemove();

    ASSERT_EQ("Remove(BucketId(0x4000000000000593), id:test:test::uri, "
              "timestamp 100) => 1",
            _sender.getLastCommand());

    replyToMessage(*op, -1, 34);

    ASSERT_EQ("RemoveReply(BucketId(0x0000000000000000), id:test:test::uri, "
              "timestamp 100, removed doc from 34) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(RemoveOperationTest, not_found) {
    addNodesToBucketDB(bucketId, "1=0");

    sendRemove();

    ASSERT_EQ("Remove(BucketId(0x4000000000000593), id:test:test::uri, "
              "timestamp 100) => 1",
              _sender.getLastCommand());

    replyToMessage(*op, -1, 0);

    ASSERT_EQ("RemoveReply(BucketId(0x0000000000000000), id:test:test::uri, "
              "timestamp 100, not found) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(RemoveOperationTest, storage_failure) {
    addNodesToBucketDB(bucketId, "1=0");

    sendRemove();

    ASSERT_EQ("Remove(BucketId(0x4000000000000593), id:test:test::uri, "
              "timestamp 100) => 1",
              _sender.getLastCommand());

    sendReply(*op, -1, api::ReturnCode::INTERNAL_FAILURE);

    ASSERT_EQ("RemoveReply(BucketId(0x0000000000000000), id:test:test::uri, "
              "timestamp 100, not found) ReturnCode(INTERNAL_FAILURE)",
              _sender.getLastReply());
}

TEST_F(RemoveOperationTest, not_in_db) {
    sendRemove();

    ASSERT_EQ("RemoveReply(BucketId(0x0000000000000000), "
              "id:test:test::uri, timestamp 100, not found) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(RemoveOperationTest, multiple_copies) {
    addNodesToBucketDB(bucketId, "1=0, 2=0, 3=0");

    sendRemove();

    ASSERT_EQ("Remove(BucketId(0x4000000000000593), id:test:test::uri, "
              "timestamp 100) => 1,"
              "Remove(BucketId(0x4000000000000593), id:test:test::uri, "
              "timestamp 100) => 2,"
              "Remove(BucketId(0x4000000000000593), id:test:test::uri, "
              "timestamp 100) => 3",
              _sender.getCommands(true, true));

    replyToMessage(*op, 0, 34);
    replyToMessage(*op, 1, 34);
    replyToMessage(*op, 2, 75);

    ASSERT_EQ("RemoveReply(BucketId(0x0000000000000000), "
              "id:test:test::uri, timestamp 100, removed doc from 75) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(RemoveOperationTest, can_send_remove_when_all_replica_nodes_retired) {
    enable_cluster_state("distributor:1 storage:1 .0.s:r");
    addNodesToBucketDB(bucketId, "0=123");
    sendRemove();

    ASSERT_EQ("Remove(BucketId(0x4000000000000593), id:test:test::uri, "
              "timestamp 100) => 0",
              _sender.getLastCommand());
}

} // storage::distributor
