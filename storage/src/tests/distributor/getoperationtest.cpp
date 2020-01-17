// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/helper/configgetter.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/operations/external/getoperation.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <iomanip>

using std::shared_ptr;
using config::ConfigGetter;
using document::DocumenttypesConfig;
using config::FileSpec;
using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct GetOperationTest : Test, DistributorTestUtil {

    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    document::DocumentId docId;
    document::BucketId bucketId;
    std::unique_ptr<Operation> op;

    GetOperationTest();
    ~GetOperationTest() override;

    void SetUp() override {
        _repo.reset(
                new document::DocumentTypeRepo(*ConfigGetter<DocumenttypesConfig>::
                        getConfig("config-doctypes",
                                  FileSpec("../config-doctypes.cfg"))));
        createLinks();

        docId = document::DocumentId("id:ns:text/html::uri");
        bucketId = getExternalOperationHandler().getBucketId(docId);
    };

    void TearDown() override {
        close();
        op.reset();
    }

    void sendGet(api::InternalReadConsistency consistency = api::InternalReadConsistency::Strong) {
        auto msg = std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId(0)), docId, "[all]");
        op = std::make_unique<GetOperation>(
                getExternalOperationHandler(), getDistributorBucketSpace(),
                getDistributorBucketSpace().getBucketDatabase().acquire_read_guard(),
                msg, getDistributor().getMetrics(). gets[msg->getLoadType()],
                consistency);
        op->start(_sender, framework::MilliSecTime(0));
    }

    static constexpr uint32_t LastCommand = UINT32_MAX;

    void sendReply(uint32_t idx,
               api::ReturnCode::Result result,
               std::string authorVal, uint32_t timestamp)
    {
        if (idx == LastCommand) {
            idx = _sender.commands().size() - 1;
        }

        std::shared_ptr<api::StorageCommand> msg2 = _sender.command(idx);
        ASSERT_EQ(api::MessageType::GET, msg2->getType());

        auto* tmp = static_cast<api::GetCommand*>(msg2.get());
        document::Document::SP doc;

        if (!authorVal.empty()) {
            const document::DocumentType* type(_repo->getDocumentType("text/html"));
            doc = std::make_unique<document::Document>(*type, docId);

            doc->setValue(doc->getField("author"),
                          document::StringFieldValue(authorVal));
        }

        auto reply = std::make_shared<api::GetReply>(*tmp, doc, timestamp);
        reply->setResult(result);

        op->receive(_sender, reply);
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

    void setClusterState(const std::string& clusterState) {
        enableDistributorClusterState(clusterState);
    }

    void do_test_read_consistency_is_propagated(api::InternalReadConsistency consistency);
};

GetOperationTest::GetOperationTest() = default;
GetOperationTest::~GetOperationTest() = default;

TEST_F(GetOperationTest, simple) {
    setClusterState("distributor:1 storage:2");

    addNodesToBucketDB(bucketId, "0=4,1=4");

    sendGet();

    ASSERT_EQ("Get => 0", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(replyWithDocument());

    EXPECT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());
    EXPECT_TRUE(last_reply_had_consistent_replicas());
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
    EXPECT_FALSE(last_reply_had_consistent_replicas());
}

TEST_F(GetOperationTest, ask_all_nodes_if_bucket_is_inconsistent) {
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100/3/10,1=200/4/12");

    sendGet();

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 2));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "oldauthor", 1));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 2) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_EQ("newauthor", getLastReplyAuthor());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
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

    addNodesToBucketDB(document::BucketId(16, 0x0593), "0=100");
    addNodesToBucketDB(document::BucketId(17, 0x10593), "1=200");

    sendGet();

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    ASSERT_NO_FATAL_FAILURE(sendReply(0, api::ReturnCode::OK, "newauthor", 2));
    ASSERT_NO_FATAL_FAILURE(sendReply(1, api::ReturnCode::OK, "oldauthor", 1));

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 2) ReturnCode(NONE)",
              _sender.getLastReply());

    EXPECT_EQ("newauthor", getLastReplyAuthor());
    EXPECT_FALSE(last_reply_had_consistent_replicas());
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
    EXPECT_FALSE(last_reply_had_consistent_replicas());
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
    EXPECT_FALSE(last_reply_had_consistent_replicas());
}

TEST_F(GetOperationTest, return_not_found_when_bucket_not_in_db) {
    setClusterState("distributor:1 storage:1");

    sendGet();

    ASSERT_EQ("GetReply(BucketId(0x0000000000000000), id:ns:text/html::uri, "
              "timestamp 0) ReturnCode(NONE)",
              _sender.getLastReply());
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

    EXPECT_EQ(1, getDistributor().getMetrics().gets[documentapi::LoadType::DEFAULT].
                                 failures.notfound.getValue());
    EXPECT_TRUE(last_reply_had_consistent_replicas());
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
    // Replica had read failure, but they're still in sync. An immutable Get won't change that fact.
    EXPECT_TRUE(last_reply_had_consistent_replicas());
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
    EXPECT_FALSE(last_reply_had_consistent_replicas());
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
    EXPECT_TRUE(last_reply_had_consistent_replicas()); // Doesn't really matter since operation itself failed
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
}

TEST_F(GetOperationTest, multiple_copies_with_failure_on_local_node) {
    setClusterState("distributor:1 storage:4");

    // Node 0 is local copy to distributor 0 and will be preferred when
    // sending initially.
    addNodesToBucketDB(document::BucketId(16, 0x0593), "2=100,0=100");

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
    EXPECT_TRUE(last_reply_had_consistent_replicas());
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

}
