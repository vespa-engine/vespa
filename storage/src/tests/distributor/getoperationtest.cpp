// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/config/helper/configgetter.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storage/distributor/operations/external/getoperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <iomanip>
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using config::ConfigGetter;
using config::FileSpec;
using document::test::makeDocumentBucket;
using document::BucketId;
using documentapi::TestAndSetCondition;
using namespace ::testing;

namespace storage::distributor {

struct GetOperationTest : Test, DistributorStripeTestUtil {

    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    document::DocumentId docId;
    BucketId bucketId;
    std::unique_ptr<GetOperation> op;

    GetOperationTest();
    ~GetOperationTest() override;

    void SetUp() override {
        _repo.reset(
                new document::DocumentTypeRepo(*ConfigGetter<DocumenttypesConfig>::
                        getConfig("config-doctypes",
                                  FileSpec("../config-doctypes.cfg"))));
        createLinks();

        docId = document::DocumentId("id:ns:text/html::uri");
        bucketId = operation_context().make_split_bit_constrained_bucket_id(docId);
    };

    void TearDown() override {
        close();
        op.reset();
    }

    void start_operation(std::shared_ptr<api::GetCommand> cmd, api::InternalReadConsistency consistency) {
        op = std::make_unique<GetOperation>(
                node_context(), getDistributorBucketSpace(),
                getDistributorBucketSpace().getBucketDatabase().acquire_read_guard(),
                std::move(cmd), metrics().gets,
                consistency);
        op->start(_sender);
    }

    void sendGet(api::InternalReadConsistency consistency = api::InternalReadConsistency::Strong) {
        auto msg = std::make_shared<api::GetCommand>(makeDocumentBucket(BucketId(0)), docId, document::AllFields::NAME);
        start_operation(std::move(msg), consistency);
    }

    static constexpr uint32_t LastCommand = UINT32_MAX;

    void sendReply(uint32_t idx,
               api::ReturnCode::Result result,
               std::string authorVal,
               uint32_t timestamp,
               bool is_tombstone = false,
               bool condition_matched = false,
               std::string trace_msg = "")
    {
        if (idx == LastCommand) {
            idx = _sender.commands().size() - 1;
        }

        std::shared_ptr<api::StorageCommand> msg2 = _sender.command(idx);
        ASSERT_EQ(api::MessageType::GET, msg2->getType());

        auto* tmp = dynamic_cast<api::GetCommand*>(msg2.get());
        assert(tmp != nullptr);
        document::Document::SP doc;

        if (!authorVal.empty()) {
            const document::DocumentType* type(_repo->getDocumentType("text/html"));
            doc = std::make_unique<document::Document>(*_repo, *type, docId);

            doc->setValue(doc->getField("author"),
                          document::StringFieldValue(authorVal));
        }

        auto reply = std::make_shared<api::GetReply>(*tmp, doc, timestamp, false, is_tombstone, condition_matched);
        reply->setResult(result);
        if (!trace_msg.empty()) {
            MBUS_TRACE(reply->getTrace(), 1, trace_msg);
        }

        op->receive(_sender, reply);
    }

    void reply_with_tombstone(uint32_t idx, uint32_t tombstone_ts) {
        sendReply(idx, api::ReturnCode::OK, "", tombstone_ts, true);
    }

    void reply_with_condition_match(uint32_t idx, uint32_t timestamp, bool condition_match) {
        sendReply(idx, api::ReturnCode::OK, "", timestamp, false, condition_match);
    }

    void reply_with_trace(uint32_t idx, uint32_t timestamp, std::string trace_message) {
        sendReply(idx, api::ReturnCode::OK, "", timestamp, false, true, std::move(trace_message));
    }

    void replyWithFailure() {
        sendReply(LastCommand, api::ReturnCode::IO_FAILURE, "", 0);
    }

    void replyWithNotFound() {
        sendReply(LastCommand, api::ReturnCode::OK, "", 0);
    }

    void replyWithDocument() {
        sendReply(LastCommand, api::ReturnCode::OK, "foo", 100);
    }

    std::string getLastReplyAuthor() {
        api::StorageMessage& msg = *_sender.replies().back();

        if (msg.getType() == api::MessageType::GET_REPLY) {
            document::Document::SP doc(
                    dynamic_cast<api::GetReply&>(msg).getDocument());

            return doc->getValue(doc->getField("author"))->toString();
        } else {
            std::ostringstream ost;
            ost << "Last reply was not a GET reply, but " << msg;
            return ost.str();
        }
    }

    bool last_reply_had_consistent_replicas() {
        assert(!_sender.replies().empty());
        auto& msg = *_sender.replies().back();
        assert(msg.getType() == api::MessageType::GET_REPLY);
        return dynamic_cast<api::GetReply&>(msg).had_consistent_replicas();
    }

    bool last_reply_has_document() {
        assert(!_sender.replies().empty());
        auto& msg = *_sender.replies().back();
        assert(msg.getType() == api::MessageType::GET_REPLY);
        return (dynamic_cast<api::GetReply&>(msg).getDocument().get() != nullptr);
    }

    void setClusterState(const std::string& clusterState) {
        enable_cluster_state(clusterState);
    }

    void do_test_read_consistency_is_propagated(api::InternalReadConsistency consistency);
    void set_up_condition_match_get_operation();
};

GetOperationTest::GetOperationTest() = default;
GetOperationTest::~GetOperationTest() = default;

namespace {

NewestReplica replica_of(api::Timestamp ts, const document::BucketId& bucket_id, uint16_t node,
                         bool is_tombstone, bool condition_matched)
{
    return NewestReplica::of(ts, bucket_id, node, is_tombstone, condition_matched);
}

}

TEST_F(GetOperationTest, simple) {
    setClusterState("distributor:1 storage:2");

    addNodesToBucketDB(bucketId, "0=4,1=4");

    sendGet();

    ASSERT_EQ("Get => 0", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(replyWithDocument());

    EXPECT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_TRUE(last_reply_had_consistent_replicas());
    ASSERT_TRUE(op->newest_replica().has_value());
    EXPECT_EQ(replica_of(api::Timestamp(100), bucketId, 0, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, ask_all_checksum_groups_if_inconsistent_even_if_trusted_replica_available) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100/3/10,1=200/4/12/t");

    sendGet();

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 2));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "oldauthor", 1));

    EXPECT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 2) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    ASSERT_TRUE(op->newest_replica().has_value());
    EXPECT_EQ(replica_of(api::Timestamp(2), bucketId, 0, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, ask_all_nodes_if_bucket_is_inconsistent) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100/3/10,1=200/4/12");

    sendGet();

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "newauthor", 2));
    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "oldauthor", 1));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 2) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_EQ("newauthor", getLastReplyAuthor());

    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    ASSERT_TRUE(op->newest_replica().has_value());
    EXPECT_EQ(replica_of(api::Timestamp(2), bucketId, 1, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, send_to_all_invalid_copies) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "2=0/0/1,3=0/0/1");

    sendGet();

    ASSERT_EQ("Get => 2,Get => 3", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 2));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "oldauthor", 1));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 2) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_EQ("newauthor", getLastReplyAuthor());

    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
}

TEST_F(GetOperationTest, send_to_all_invalid_nodes_when_inconsistent) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100,1=200,2=0/0/1,3=0/0/1");

    sendGet();

    ASSERT_EQ("Get => 2,Get => 3,Get => 0,Get => 1",
              _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 2));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "oldauthor", 1));
    ASSERT_NO_FATAL_FAILURE(sendReply(2, api::ReturnCode::OK, "oldauthor", 1));
    ASSERT_NO_FATAL_FAILURE(sendReply(3, api::ReturnCode::OK, "oldauthor", 1));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 2) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_EQ("newauthor", getLastReplyAuthor());
}

TEST_F(GetOperationTest, inconsistent_split) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(BucketId(16, 0x0593), "0=100");
    addNodesToBucketDB(BucketId(17, 0x10593), "1=200");

    sendGet();

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 2));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "oldauthor", 1));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 2) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_EQ("newauthor", getLastReplyAuthor());

    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    // Bucket with highest timestamp should be returned. In this case it's the one on node 0.
    ASSERT_TRUE(op->newest_replica().has_value());
    EXPECT_EQ(replica_of(api::Timestamp(2), BucketId(16, 0x0593), 0, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, multi_inconsistent_bucket_not_found) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");

    sendGet();

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 2));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "", 0));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 2) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
}

TEST_F(GetOperationTest, multi_inconsistent_bucket_not_found_deleted) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");

    sendGet();

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 2));
    // This signifies that the latest change was that the document was deleted
    // at timestamp 3.
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "", 3));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 3) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    ASSERT_TRUE(op->newest_replica().has_value());
    EXPECT_EQ(replica_of(api::Timestamp(3), bucketId, 1, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, multi_inconsistent_bucket) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");

    sendGet();

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 2));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "oldauthor", 1));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 2) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_EQ("newauthor", getLastReplyAuthor());

    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
}

TEST_F(GetOperationTest, multi_inconsistent_bucket_fail) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");

    sendGet();

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 1));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::DISK_FAILURE, "", 0));

    ASSERT_EQ("Get(BucketId(0x4000000000000593), id:ns:text/html::uri) => 3",
              _sender.getLastCommand());

    ASSERT_NO_FATAL_FAILURE(replyWithDocument());

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_TRUE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    ASSERT_TRUE(op->newest_replica().has_value());
    // First send to node 2 fails, second is to node 3 which returned the highest timestamp
    EXPECT_EQ(replica_of(api::Timestamp(100), bucketId, 3, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, return_not_found_when_bucket_not_in_db) {
    setClusterState("distributor:1 storage:1");

    sendGet();

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 0) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_TRUE(last_reply_had_consistent_replicas()); // Nothing in the bucket, so nothing to be inconsistent with.
}

TEST_F(GetOperationTest, not_found) {
    setClusterState("distributor:1 storage:1");

    addNodesToBucketDB(bucketId, "0=100");

    sendGet();

    ASSERT_EQ("Get(BucketId(0x4000000000000593), id:ns:text/html::uri) => 0",
              _sender.getLastCommand());

    ASSERT_NO_FATAL_FAILURE(replyWithNotFound());

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 0) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_EQ(1, metrics().gets.failures.notfound.getValue());
    EXPECT_FALSE(op->any_replicas_failed()); // "Not found" is not a failure.
    EXPECT_TRUE(last_reply_had_consistent_replicas());
    EXPECT_TRUE(op->newest_replica().has_value());
    // "Not found" is still a success with a timestamp of 0. This is because
    // the caller may want to perform special logic if all replicas are in sync
    // but are missing the document.
    // FIXME make sure all callers are aware of this!
    EXPECT_EQ(replica_of(api::Timestamp(0), bucketId, 0, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, not_found_on_subset_of_replicas_marks_get_as_inconsistent) {
    setClusterState("distributor:1 storage:2");
    addNodesToBucketDB(bucketId, "0=100,1=200");
    sendGet();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 101));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "", 0)); // Not found.

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 101) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
}

TEST_F(GetOperationTest, resend_on_storage_failure) {
    setClusterState("distributor:1 storage:3");

    // Add two nodes that are not trusted. GET should retry each one of them
    // if one fails.
    addNodesToBucketDB(bucketId, "1=100,2=100");

    sendGet();

    ASSERT_EQ("Get(BucketId(0x4000000000000593), id:ns:text/html::uri) => 1",
              _sender.getLastCommand());

    ASSERT_NO_FATAL_FAILURE(replyWithFailure());

    ASSERT_EQ("Get(BucketId(0x4000000000000593), id:ns:text/html::uri) => 2",
              _sender.getLastCommand());

    ASSERT_NO_FATAL_FAILURE(replyWithDocument());

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_TRUE(op->any_replicas_failed());
    // Replica had read failure, but they're still in sync. An immutable Get won't change that fact.
    EXPECT_TRUE(last_reply_had_consistent_replicas());
    ASSERT_TRUE(op->newest_replica().has_value());
    EXPECT_EQ(replica_of(api::Timestamp(100), bucketId, 2, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, storage_failure_of_out_of_sync_replica_is_tracked_as_inconsistent) {
    setClusterState("distributor:1 storage:3");
    addNodesToBucketDB(bucketId, "1=100,2=200");
    sendGet();
    ASSERT_EQ("Get => 1,Get => 2", _sender.getCommands(true));
    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::TIMEOUT, "", 0));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "newestauthor", 3));
    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 3) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_TRUE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    ASSERT_TRUE(op->newest_replica().has_value());
    EXPECT_EQ(replica_of(api::Timestamp(3), bucketId, 2, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, resend_on_storage_failure_all_fail) {
    setClusterState("distributor:1 storage:3");

    // Add two nodes that are not trusted. GET should retry each one of them
    // if one fails.
    addNodesToBucketDB(bucketId, "1=100,2=100");

    sendGet();

    ASSERT_EQ("Get(BucketId(0x4000000000000593), id:ns:text/html::uri) => 1",
              _sender.getLastCommand());

    ASSERT_NO_FATAL_FAILURE(replyWithFailure());

    ASSERT_EQ("Get(BucketId(0x4000000000000593), id:ns:text/html::uri) => 2",
              _sender.getLastCommand());

    ASSERT_NO_FATAL_FAILURE(replyWithFailure());

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 0) ReturnCode(IO_FAILURE)",
              _sender.getLastReply());

    EXPECT_TRUE(op->any_replicas_failed());
    EXPECT_TRUE(last_reply_had_consistent_replicas()); // Doesn't really matter since operation itself failed
    EXPECT_FALSE(op->newest_replica().has_value());
}

TEST_F(GetOperationTest, send_to_ideal_copy_if_bucket_in_sync) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "1=100,2=100,3=100");

    sendGet();

    // Should always send to node 1 (follow bucket db order)
    ASSERT_EQ("Get(BucketId(0x4000000000000593), id:ns:text/html::uri) => 1",
              _sender.getLastCommand());

    ASSERT_NO_FATAL_FAILURE(replyWithDocument());

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_TRUE(last_reply_had_consistent_replicas());
    ASSERT_TRUE(op->newest_replica().has_value());
    EXPECT_EQ(replica_of(api::Timestamp(100), bucketId, 1, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, multiple_copies_with_failure_on_local_node) {
    setClusterState("distributor:1 storage:4");

    // Node 0 is local copy to distributor 0 and will be preferred when
    // sending initially.
    addNodesToBucketDB(BucketId(16, 0x0593), "2=100,0=100");

    sendGet();

    ASSERT_EQ("Get => 0", _sender.getCommands(true));

    // Fail local node; no reply must be sent yet since we've got more nodes
    // to try.
    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::TIMEOUT, "", 0));

    // Retry with remaining copy on node 2.
    ASSERT_EQ("Get => 0,Get => 2", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "newestauthor", 3));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 3) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_EQ("newestauthor", getLastReplyAuthor());

    EXPECT_TRUE(op->any_replicas_failed());
    EXPECT_TRUE(last_reply_had_consistent_replicas());
    ASSERT_TRUE(op->newest_replica().has_value());
    EXPECT_EQ(replica_of(api::Timestamp(3), BucketId(16, 0x0593), 2, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, can_get_documents_when_all_replica_nodes_retired) {
    setClusterState("distributor:1 storage:2 .0.s:r .1.s:r");
    addNodesToBucketDB(bucketId, "0=4,1=4");
    sendGet();

    EXPECT_EQ("Get => 0", _sender.getCommands(true));
}

void GetOperationTest::do_test_read_consistency_is_propagated(api::InternalReadConsistency consistency) {
    setClusterState("distributor:1 storage:1");
    addNodesToBucketDB(bucketId, "0=4");
    sendGet(consistency);
    ASSERT_TRUE(op);
    EXPECT_EQ(dynamic_cast<GetOperation&>(*op).desired_read_consistency(), consistency);
    ASSERT_EQ("Get => 0", _sender.getCommands(true));
    auto& cmd = dynamic_cast<const api::GetCommand&>(*_sender.command(0));
    EXPECT_EQ(cmd.internal_read_consistency(), consistency);
}

TEST_F(GetOperationTest, can_send_gets_with_strong_internal_read_consistency) {
    do_test_read_consistency_is_propagated(api::InternalReadConsistency::Strong);
}

TEST_F(GetOperationTest, can_send_gets_with_weak_internal_read_consistency) {
    do_test_read_consistency_is_propagated(api::InternalReadConsistency::Weak);
}

TEST_F(GetOperationTest, replicas_considered_consistent_if_all_equal_tombstone_timestamps) {
    setClusterState("distributor:1 storage:4");
    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");
    sendGet();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(reply_with_tombstone(0, 100));
    ASSERT_NO_FATAL_FAILURE(reply_with_tombstone(1, 100));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, timestamp 0) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_TRUE(last_reply_had_consistent_replicas());
    EXPECT_FALSE(last_reply_has_document());
    EXPECT_EQ(replica_of(api::Timestamp(100), bucketId, 0, true, false), *op->newest_replica());
}

TEST_F(GetOperationTest, newer_tombstone_hides_older_document) {
    setClusterState("distributor:1 storage:4");
    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");
    sendGet();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(reply_with_tombstone(1, 200));
    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 100));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, timestamp 0) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    EXPECT_FALSE(last_reply_has_document());
    EXPECT_EQ(replica_of(api::Timestamp(200), bucketId, 1, true, false), *op->newest_replica());
}

TEST_F(GetOperationTest, older_tombstone_does_not_hide_newer_document) {
    setClusterState("distributor:1 storage:4");
    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");
    sendGet();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(reply_with_tombstone(1, 100));
    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 200));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, timestamp 200) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    EXPECT_TRUE(last_reply_has_document());
    EXPECT_EQ(replica_of(api::Timestamp(200), bucketId, 0, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, provided_condition_is_propagated_to_sent_gets) {
    setClusterState("distributor:1 storage:1");
    addNodesToBucketDB(bucketId, "0=123");

    TestAndSetCondition my_cond("my_cool_condition");
    auto msg = std::make_shared<api::GetCommand>(makeDocumentBucket(BucketId(0)), docId, document::NoFields::NAME);
    msg->set_condition(my_cond);

    start_operation(std::move(msg), api::InternalReadConsistency::Strong);
    ASSERT_EQ("Get => 0", _sender.getCommands(true));
    auto& cmd = dynamic_cast<const api::GetCommand&>(*_sender.command(0));
    EXPECT_EQ(cmd.condition().getSelection(), my_cond.getSelection());
}

void GetOperationTest::set_up_condition_match_get_operation() {
    setClusterState("distributor:1 storage:3");
    addNodesToBucketDB(bucketId, "0=100,2=200,1=300");

    TestAndSetCondition my_cond("my_cool_condition");
    auto msg = std::make_shared<api::GetCommand>(makeDocumentBucket(BucketId(0)), docId, document::NoFields::NAME);
    msg->set_condition(my_cond);
    msg->getTrace().setLevel(9); // FIXME a very tiny bit dirty to set this here >_>
    start_operation(std::move(msg), api::InternalReadConsistency::Strong);

    ASSERT_EQ("Get => 0,Get => 2,Get => 1", _sender.getCommands(true));
}

TEST_F(GetOperationTest, condition_match_result_is_aggregated_for_newest_replica_mismatch_case) {
    ASSERT_NO_FATAL_FAILURE(set_up_condition_match_get_operation());
    // node 0 (send index 0) has an old doc without a match
    // node 2 (send index 1) has an old tombstone without match
    // node 1 (send index 2) has a new doc without a match
    // Newest replica should reflect node 1's results
    ASSERT_NO_FATAL_FAILURE(reply_with_condition_match(0, 200, false));
    ASSERT_NO_FATAL_FAILURE(reply_with_tombstone(1, 100));
    ASSERT_NO_FATAL_FAILURE(reply_with_condition_match(2, 300, false));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, timestamp 300) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    EXPECT_FALSE(last_reply_has_document());
    EXPECT_EQ(replica_of(api::Timestamp(300), bucketId, 1, false, false), *op->newest_replica());
}

TEST_F(GetOperationTest, condition_match_result_is_aggregated_for_newest_replica_match_case) {
    ASSERT_NO_FATAL_FAILURE(set_up_condition_match_get_operation());
    // node 0 (send index 0) has a new doc with a match
    // node 2 (send index 1) has an old tombstone without match
    // node 1 (send index 2) has an old doc without a match
    // Newest replica should reflect node 0's results
    ASSERT_NO_FATAL_FAILURE(reply_with_condition_match(0, 400, true));
    ASSERT_NO_FATAL_FAILURE(reply_with_tombstone(1, 300));
    ASSERT_NO_FATAL_FAILURE(reply_with_condition_match(2, 200, false));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, timestamp 400) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    EXPECT_FALSE(last_reply_has_document());
    EXPECT_EQ(replica_of(api::Timestamp(400), bucketId, 0, false, true), *op->newest_replica());
}

TEST_F(GetOperationTest, condition_match_result_is_aggregated_for_newest_replica_tombstone_case) {
    ASSERT_NO_FATAL_FAILURE(set_up_condition_match_get_operation());
    // node 0 (send index 0) has an old doc with a match
    // node 2 (send index 1) has a new tombstone without match
    // node 1 (send index 2) has an old doc without a match
    // Newest replica should reflect node 2's results
    ASSERT_NO_FATAL_FAILURE(reply_with_condition_match(0, 400, true));
    ASSERT_NO_FATAL_FAILURE(reply_with_tombstone(1, 500));
    ASSERT_NO_FATAL_FAILURE(reply_with_condition_match(2, 300, false));

    // Timestamp 0 in reply signals "not found" to clients
    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, timestamp 0) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_FALSE(op->any_replicas_failed());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
    EXPECT_FALSE(last_reply_has_document());
    EXPECT_EQ(replica_of(api::Timestamp(500), bucketId, 2, true, false), *op->newest_replica());
}

TEST_F(GetOperationTest, trace_is_aggregated_from_all_sub_replies_and_propagated_to_operation_reply) {
    ASSERT_NO_FATAL_FAILURE(set_up_condition_match_get_operation());

    ASSERT_NO_FATAL_FAILURE(reply_with_trace(0, 400, "foo"));
    ASSERT_NO_FATAL_FAILURE(reply_with_trace(1, 500, "bar"));
    ASSERT_NO_FATAL_FAILURE(reply_with_trace(2, 300, "baz"));

    ASSERT_EQ(_sender.replies().size(), 1);
    auto get_reply = sent_reply<api::GetReply>(0);

    auto trace_str = get_reply->getTrace().toString();
    EXPECT_THAT(trace_str, HasSubstr("foo"));
    EXPECT_THAT(trace_str, HasSubstr("bar"));
    EXPECT_THAT(trace_str, HasSubstr("baz"));
}


}
