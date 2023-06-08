// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/operations/external/removeoperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using documentapi::TestAndSetCondition;
using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct RemoveOperationTest : Test, DistributorStripeTestUtil {
    document::DocumentId docId;
    document::BucketId bucketId;
    std::unique_ptr<RemoveOperation> op;

    void minimal_setup() {
        createLinks();
        docId = document::DocumentId("id:test:test::uri");
        bucketId = operation_context().make_split_bit_constrained_bucket_id(docId);
    }

    void SetUp() override {
        minimal_setup();
        enable_cluster_state("distributor:1 storage:4");
    };

    void TearDown() override {
        close();
    }

    void sendRemove(std::shared_ptr<api::RemoveCommand> msg) {
        op = std::make_unique<RemoveOperation>(
                node_context(),
                operation_context(),
                getDistributorBucketSpace(),
                msg,
                metrics().removes,
                metrics().remove_condition_probes);

        op->start(_sender);
    }

    std::shared_ptr<api::RemoveCommand> createRemove(document::DocumentId dId) {
        return std::make_shared<api::RemoveCommand>(makeDocumentBucket(document::BucketId(0)), dId, 100);
    }

    void sendRemove(document::DocumentId dId) {
        sendRemove(createRemove(dId));
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

    void reply_with(auto msg) { op->receive(_sender, std::move(msg)); }

    auto sent_get_command(size_t idx) { return sent_command<api::GetCommand>(idx); }
    auto sent_remove_command(size_t idx) { return sent_command<api::RemoveCommand>(idx); }

    auto make_remove_reply(size_t idx, api::Timestamp old_ts) {
        return std::make_shared<api::RemoveReply>(*sent_remove_command(idx), old_ts);
    }

    auto make_get_reply(size_t idx, api::Timestamp ts, bool is_tombstone, bool condition_matched) {
        return std::make_shared<api::GetReply>(*sent_get_command(idx), std::shared_ptr<document::Document>(), ts,
                                               false, is_tombstone, condition_matched);
    }

    auto make_failure_reply(size_t idx) {
        auto reply = sent_command<api::StorageCommand>(idx)->makeReply();
        reply->setResult(api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE, "did a bork"));
        return reply;
    }
};

struct ExtRemoveOperationTest : public RemoveOperationTest {
    void SetUp() override { minimal_setup(); }
    enum class ReplicaState { NONE, CONSISTENT, INCONSISTENT };
    void set_up_tas_remove_with_2_nodes(ReplicaState state);
};

void ExtRemoveOperationTest::set_up_tas_remove_with_2_nodes(ReplicaState replica_state) {
    setup_stripe(Redundancy(2), NodeCount(2), "version:1 storage:2 distributor:1");
    config_enable_condition_probing(true);
    tag_content_node_supports_condition_probing(0, true);
    tag_content_node_supports_condition_probing(1, true);

    switch (replica_state) {
        case ReplicaState::CONSISTENT:
            addNodesToBucketDB(bucketId, "1=10/20/30,0=10/20/30");
            break;
        case ReplicaState::INCONSISTENT:
            addNodesToBucketDB(bucketId, "1=10/20/30,0=20/30/40");
            break;
        case ReplicaState::NONE:
            break;
    }

    auto remove = createRemove(docId);
    remove->setCondition(TestAndSetCondition("test.foo"));
    remove->getTrace().setLevel(9);
    sendRemove(std::move(remove));
    if (replica_state == ReplicaState::INCONSISTENT) {
        ASSERT_EQ("Get => 1,Get => 0", _sender.getCommands(true));
    }
}

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

TEST_F(ExtRemoveOperationTest, conditional_removes_are_forwarded_with_condition_when_replicas_are_in_sync) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_remove_with_2_nodes(ReplicaState::CONSISTENT));
    ASSERT_EQ("Remove => 1,Remove => 0", _sender.getCommands(true));
    EXPECT_EQ(_sender.replies().size(), 0);
    auto remove_n1 = sent_remove_command(0);
    EXPECT_TRUE(remove_n1->hasTestAndSetCondition());
    auto remove_n0 = sent_remove_command(1);
    EXPECT_TRUE(remove_n0->hasTestAndSetCondition());
}

TEST_F(ExtRemoveOperationTest, conditional_removes_are_instantly_successful_when_there_are_no_replicas) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_remove_with_2_nodes(ReplicaState::NONE));
    ASSERT_EQ("", _sender.getCommands(true));
    ASSERT_EQ(_sender.replies().size(), 1);
    EXPECT_EQ("RemoveReply(BucketId(0x0000000000000000), "
              "id:test:test::uri, "
              "timestamp 100, not found) "
              "ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(ExtRemoveOperationTest, matching_condition_probe_sends_unconditional_removes_to_all_nodes) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_remove_with_2_nodes(ReplicaState::INCONSISTENT));

    reply_with(make_get_reply(0, 50, false, true));
    reply_with(make_get_reply(1, 50, false, true));

    ASSERT_EQ("Get => 1,Get => 0,Remove => 1,Remove => 0", _sender.getCommands(true)); // Note: cumulative message list

    auto remove_n1 = sent_remove_command(2);
    EXPECT_FALSE(remove_n1->hasTestAndSetCondition());
    auto remove_n0 = sent_remove_command(3);
    EXPECT_FALSE(remove_n0->hasTestAndSetCondition());

    // Ensure replies are no longer routed to condition checker
    ASSERT_TRUE(_sender.replies().empty());
    reply_with(make_remove_reply(2, 50)); // remove from node 1
    ASSERT_TRUE(_sender.replies().empty());
    reply_with(make_remove_reply(3, 50)); // remove from node 0
    ASSERT_EQ(_sender.replies().size(), 1);
    EXPECT_EQ("RemoveReply(BucketId(0x0000000000000000), "
              "id:test:test::uri, "
              "timestamp 100, removed doc from 50) "
              "ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(ExtRemoveOperationTest, mismatching_condition_probe_fails_op_with_tas_error) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_remove_with_2_nodes(ReplicaState::INCONSISTENT));

    reply_with(make_get_reply(0, 50, false, false));
    reply_with(make_get_reply(1, 50, false, false));

    ASSERT_EQ("Get => 1,Get => 0", _sender.getCommands(true));
    EXPECT_EQ("RemoveReply(BucketId(0x0000000000000000), "
              "id:test:test::uri, "
              "timestamp 100, not found) "
              "ReturnCode(TEST_AND_SET_CONDITION_FAILED, Condition did not match document)",
              _sender.getLastReply());
}

// TODO change semantics for Not Found...
TEST_F(ExtRemoveOperationTest, not_found_condition_probe_fails_op_with_tas_error) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_remove_with_2_nodes(ReplicaState::INCONSISTENT));

    reply_with(make_get_reply(0, 0, false, false));
    reply_with(make_get_reply(1, 0, false, false));

    ASSERT_EQ("Get => 1,Get => 0", _sender.getCommands(true));
    EXPECT_EQ("RemoveReply(BucketId(0x0000000000000000), "
              "id:test:test::uri, "
              "timestamp 100, not found) "
              "ReturnCode(TEST_AND_SET_CONDITION_FAILED, Document does not exist)",
              _sender.getLastReply());
}

TEST_F(ExtRemoveOperationTest, failed_condition_probe_fails_op_with_returned_error) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_remove_with_2_nodes(ReplicaState::INCONSISTENT));

    reply_with(make_get_reply(0, 0, false, false));
    reply_with(make_failure_reply(1));

    ASSERT_EQ("Get => 1,Get => 0", _sender.getCommands(true));
    EXPECT_EQ("RemoveReply(BucketId(0x0000000000000000), "
              "id:test:test::uri, "
              "timestamp 100, not found) "
              "ReturnCode(ABORTED, Failed during write repair condition probe step. Reason: "
              "One or more replicas failed during test-and-set condition evaluation)",
              _sender.getLastReply());
}

TEST_F(ExtRemoveOperationTest, trace_is_propagated_from_condition_probe_gets_ok_probe_case) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_remove_with_2_nodes(ReplicaState::INCONSISTENT));

    ASSERT_EQ(sent_get_command(0)->getTrace().getLevel(), 9);
    auto get_reply = make_get_reply(0, 50, false, true);
    MBUS_TRACE(get_reply->getTrace(), 1, "a foo walks into a bar");

    op->receive(_sender, get_reply);
    op->receive(_sender, make_get_reply(1, 50, false, true));

    ASSERT_EQ("Get => 1,Get => 0,Remove => 1,Remove => 0", _sender.getCommands(true));
    reply_with(make_remove_reply(2, 50)); // remove from node 1
    reply_with(make_remove_reply(3, 50)); // remove from node 0
    ASSERT_EQ(_sender.replies().size(), 1);
    auto remove_reply = sent_reply<api::RemoveReply>(0);

    auto trace_str = remove_reply->getTrace().toString();
    EXPECT_THAT(trace_str, HasSubstr("a foo walks into a bar"));
}

TEST_F(ExtRemoveOperationTest, trace_is_propagated_from_condition_probe_gets_failed_probe_case) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_remove_with_2_nodes(ReplicaState::INCONSISTENT));

    auto get_reply = make_get_reply(0, 50, false, false);
    MBUS_TRACE(get_reply->getTrace(), 1, "a foo walks into a zoo");

    op->receive(_sender, get_reply);
    op->receive(_sender, make_get_reply(1, 50, false, false));

    ASSERT_EQ("Get => 1,Get => 0", _sender.getCommands(true));
    ASSERT_EQ(_sender.replies().size(), 1);
    auto remove_reply = sent_reply<api::RemoveReply>(0);

    auto trace_str = remove_reply->getTrace().toString();
    EXPECT_THAT(trace_str, HasSubstr("a foo walks into a zoo"));
}

} // storage::distributor
