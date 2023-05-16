// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/operations/external/putoperation.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/config/helper/configgetter.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using config::ConfigGetter;
using config::FileSpec;
using vespalib::string;
using namespace document;
using namespace std::literals::string_literals;
using document::test::makeDocumentBucket;
using storage::api::TestAndSetCondition;

using namespace ::testing;

namespace storage::distributor {

class PutOperationTest : public Test,
                         public DistributorStripeTestUtil
{
public:
    document::TestDocMan _testDocMan;
    std::unique_ptr<Operation> op;

    ~PutOperationTest() override;

    void do_test_creation_with_bucket_activation_disabled(bool disabled);

    void SetUp() override {
        createLinks();
    };

    void TearDown() override {
        close();
    }

    document::BucketId createAndSendSampleDocument(vespalib::duration timeout);

    void sendReply(int idx = -1,
                   api::ReturnCode::Result result = api::ReturnCode::OK,
                   api::BucketInfo info = api::BucketInfo(1,2,3,4,5))
    {
        ASSERT_FALSE(_sender.commands().empty());
        if (idx == -1) {
            idx = _sender.commands().size() - 1;
        } else if (static_cast<size_t>(idx) >= _sender.commands().size()) {
            throw std::logic_error("Specified message index is greater "
                                   "than number of received messages");
        }

        std::shared_ptr<api::StorageCommand> msg =  _sender.command(idx);
        api::StorageReply::SP reply(msg->makeReply());
        dynamic_cast<api::BucketInfoReply&>(*reply).setBucketInfo(info);
        reply->setResult(result);

        op->receive(_sender, reply);
    }

    void sendPut(std::shared_ptr<api::PutCommand> msg) {
        op = std::make_unique<PutOperation>(node_context(),
                                            operation_context(),
                                            getDistributorBucketSpace(),
                                            msg,
                                            metrics().puts,
                                            metrics().put_condition_probes);
        op->start(_sender);
    }

    const document::DocumentType& doc_type() const {
        return *_testDocMan.getTypeRepo().getDocumentType("testdoctype1");
    }

    const document::DocumentTypeRepo& type_repo() const {
        return _testDocMan.getTypeRepo();
    }

    Document::SP createDummyDocument(const char* ns, const char* id) const {
        return std::make_shared<Document>(type_repo(), doc_type(), DocumentId(vespalib::make_string("id:%s:testdoctype1::%s", ns, id)));
    }

    static std::shared_ptr<api::PutCommand> createPut(Document::SP doc) {
        return std::make_shared<api::PutCommand>(makeDocumentBucket(document::BucketId(0)), std::move(doc), 100);
    }

    void set_up_3_nodes_and_send_put_with_create_bucket_acks();

    std::shared_ptr<api::GetCommand> sent_get_command(size_t idx) {
        return sent_command<api::GetCommand>(idx);
    }

    std::shared_ptr<api::PutCommand> sent_put_command(size_t idx) {
        return sent_command<api::PutCommand>(idx);
    }

    static std::shared_ptr<api::GetReply> make_get_reply(const api::GetCommand& cmd, api::Timestamp ts,
                                                         bool is_tombstone, bool condition_matched)
    {
        return std::make_shared<api::GetReply>(cmd, std::shared_ptr<document::Document>(), ts,
                                               false, is_tombstone, condition_matched);
    }

    std::shared_ptr<api::GetReply> make_failed_get_reply(size_t cmd_idx) {
        auto reply = make_get_reply(*sent_get_command(cmd_idx), 0, false, false);
        reply->setResult(api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE, "did a bork"));
        return reply;
    }

    void set_up_tas_put_with_2_inconsistent_replica_nodes(bool create = false);

};

PutOperationTest::~PutOperationTest() = default;

document::BucketId
PutOperationTest::createAndSendSampleDocument(vespalib::duration timeout) {
    auto doc = std::make_shared<Document>(type_repo(), doc_type(), DocumentId("id:test:testdoctype1::"));

    document::BucketId id = operation_context().make_split_bit_constrained_bucket_id(doc->getId());
    addIdealNodes(id);

    auto msg = std::make_shared<api::PutCommand>(makeDocumentBucket(document::BucketId(0)), doc, 0);
    msg->setTimestamp(100);
    msg->setPriority(128);
    msg->setTimeout(timeout);
    sendPut(msg);
    return id;
}

namespace {

using Redundancy = int;
using NodeCount = int;
using ReturnAfter = uint32_t;
using RequirePrimaryWritten = bool;

}

const vespalib::duration TIMEOUT = 180ms;

TEST_F(PutOperationTest, simple) {
    setup_stripe(1, 1, "storage:1 distributor:1");
    createAndSendSampleDocument(TIMEOUT);

    ASSERT_EQ("Put(BucketId(0x4000000000001dd4), "
              "id:test:testdoctype1::, timestamp 100, size 45) => 0",
              _sender.getCommands(true, true));

    auto put_n1 = sent_put_command(0);
    EXPECT_FALSE(put_n1->get_create_if_non_existent()); // False by default

    sendReply();

    ASSERT_EQ("PutReply(id:test:testdoctype1::, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(PutOperationTest, bucket_database_gets_special_entry_when_CreateBucket_sent) {
    setup_stripe(2, 1, "storage:1 distributor:1");

    Document::SP doc(createDummyDocument("test", "test"));
    sendPut(createPut(doc));

    // Database updated before CreateBucket is sent
    ASSERT_EQ("BucketId(0x4000000000008f09) : "
              "node(idx=0,crc=0x1,docs=0/0,bytes=0/0,trusted=true,active=true,ready=false)",
              dumpBucket(operation_context().make_split_bit_constrained_bucket_id(doc->getId())));

    ASSERT_EQ("Create bucket => 0,Put => 0", _sender.getCommands(true));
}

TEST_F(PutOperationTest, failed_CreateBucket_removes_replica_from_db_and_sends_RequestBucketInfo) {
    setup_stripe(2, 2, "distributor:1 storage:2");

    auto doc = createDummyDocument("test", "test");
    sendPut(createPut(doc));

    ASSERT_EQ("Create bucket => 1,Create bucket => 0,Put => 1,Put => 0", _sender.getCommands(true));

    // Simulate timeouts on node 1. Replica existence is in a Schrödinger's cat state until we send
    // a RequestBucketInfo to the node and open the box to find out for sure.
    sendReply(0, api::ReturnCode::TIMEOUT, api::BucketInfo()); // CreateBucket
    sendReply(2, api::ReturnCode::TIMEOUT, api::BucketInfo()); // Put
    // Pretend everything went fine on node 0
    sendReply(1); // CreateBucket
    sendReply(3); // Put

    ASSERT_EQ("BucketId(0x4000000000008f09) : "
              "node(idx=0,crc=0x1,docs=2/4,bytes=3/5,trusted=true,active=false,ready=false)",
              dumpBucket(operation_context().make_split_bit_constrained_bucket_id(doc->getId())));

    // TODO remove revert concept; does not make sense with Proton (since it's not a multi-version store and
    //  therefore does not have anything to revert back to) and is config-disabled by default for this provider.
    ASSERT_EQ("RequestBucketInfoCommand(1 buckets, super bucket BucketId(0x4000000000008f09). ) => 1,"
              "Revert(BucketId(0x4000000000008f09)) => 0",
              _sender.getCommands(true, true, 4));
}

TEST_F(PutOperationTest, send_inline_split_before_put_if_bucket_too_large) {
    setup_stripe(1, 1, "storage:1 distributor:1");
    auto cfg = make_config();
    cfg->setSplitCount(1024);
    cfg->setSplitSize(1000000);
    configure_stripe(cfg);

    addNodesToBucketDB(document::BucketId(0x4000000000000593), "0=10000/10000/10000/t");

    sendPut(createPut(createDummyDocument("test", "uri")));

    ASSERT_EQ("SplitBucketCommand(BucketId(0x4000000000000593)Max doc count: "
              "1024, Max total doc size: 1000000) Reasons to start: "
              "[Splitting bucket because its maximum size (10000 b, 10000 docs, 10000 meta, 10000 b total) is "
              "higher than the configured limit of (1000000, 1024)] => 0,"
              "Put(BucketId(0x4000000000000593), id:test:testdoctype1::uri, timestamp 100, "
              "size 48) => 0",
              _sender.getCommands(true, true));
}

TEST_F(PutOperationTest, do_not_send_inline_split_if_not_configured) {
    setup_stripe(1, 1, "storage:1 distributor:1");
    auto cfg = make_config();
    cfg->setSplitCount(1024);
    cfg->setDoInlineSplit(false);
    configure_stripe(cfg);

    addNodesToBucketDB(document::BucketId(0x4000000000000593), "0=10000/10000/10000/t");

    sendPut(createPut(createDummyDocument("test", "uri")));

    ASSERT_EQ("Put(BucketId(0x4000000000000593), id:test:testdoctype1::uri, timestamp 100, "
              "size 48) => 0",
              _sender.getCommands(true, true));
}

TEST_F(PutOperationTest, return_success_if_op_acked_on_all_replicas_even_if_bucket_concurrently_removed_from_db) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    createAndSendSampleDocument(TIMEOUT);

    ASSERT_EQ("Put(BucketId(0x4000000000001dd4), "
              "id:test:testdoctype1::, timestamp 100, size 45) => 0,"
              "Put(BucketId(0x4000000000001dd4), "
              "id:test:testdoctype1::, timestamp 100, size 45) => 1",
              _sender.getCommands(true, true));

    operation_context().remove_node_from_bucket_database(makeDocumentBucket(document::BucketId(16, 0x1dd4)), 0);

    // If we get an ACK from the backend nodes, the operation has been persisted OK.
    // Even if the bucket has been removed from the DB in the meantime (usually would
    // happen due to ownership changes) there is no reason for us to trigger a client
    // resend in this scenario.
    // If a node goes down (as opposed to distributor ownership transfer) and therefore
    // has its replicas removed from the DB, this by definition has happened-after
    // the ACK was sent from the node, so returning OK here still maintains the
    // backend persistence property.
    sendReply(0);
    sendReply(1);

    ASSERT_EQ("PutReply(id:test:testdoctype1::, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(PutOperationTest, storage_failed) {
    setup_stripe(2, 1, "storage:1 distributor:1");

    createAndSendSampleDocument(TIMEOUT);

    sendReply(-1, api::ReturnCode::INTERNAL_FAILURE);

    ASSERT_EQ("PutReply(id:test:testdoctype1::, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(INTERNAL_FAILURE)",
              _sender.getLastReply(true));
}

TEST_F(PutOperationTest, multiple_copies) {
    setup_stripe(3, 4, "storage:4 distributor:1");

    Document::SP doc(createDummyDocument("test", "test"));
    sendPut(createPut(doc));

    ASSERT_EQ("Create bucket => 3,Create bucket => 2,"
              "Create bucket => 1,Put => 3,Put => 2,Put => 1",
              _sender.getCommands(true));

    for (uint32_t i = 0;  i < 6; i++) {
        sendReply(i);
    }

    ASSERT_EQ("PutReply(id:test:testdoctype1::test, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply(true));

    ASSERT_EQ("BucketId(0x4000000000008f09) : "
              "node(idx=3,crc=0x1,docs=2/4,bytes=3/5,trusted=true,active=false,ready=false), "
              "node(idx=2,crc=0x1,docs=2/4,bytes=3/5,trusted=true,active=false,ready=false), "
              "node(idx=1,crc=0x1,docs=2/4,bytes=3/5,trusted=true,active=false,ready=false)",
              dumpBucket(operation_context().make_split_bit_constrained_bucket_id(doc->getId())));
}

TEST_F(PutOperationTest, multiple_copies_early_return_primary_required) {
    setup_stripe(3, 4, "storage:4 distributor:1", 2, true);

    sendPut(createPut(createDummyDocument("test", "test")));

    ASSERT_EQ("Create bucket => 3,Create bucket => 2,"
              "Create bucket => 1,Put => 3,Put => 2,Put => 1",
              _sender.getCommands(true));

    // Reply to 2 CreateBucket, including primary
    for (uint32_t i = 0;  i < 2; i++) {
        sendReply(i);
    }
    // Reply to 2 puts, including primary
    for (uint32_t i = 0;  i < 2; i++) {
        sendReply(3 + i);
    }

    ASSERT_EQ("PutReply(id:test:testdoctype1::test, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(PutOperationTest, multiple_copies_early_return_primary_not_required) {
    setup_stripe(3, 4, "storage:4 distributor:1", 2, false);

    sendPut(createPut(createDummyDocument("test", "test")));

    ASSERT_EQ("Create bucket => 3,Create bucket => 2,"
              "Create bucket => 1,Put => 3,Put => 2,Put => 1",
              _sender.getCommands(true));

    // Reply only to 2 nodes (but not the primary)
    for (uint32_t i = 1;  i < 3; i++) {
        sendReply(i); // CreateBucket
    }
    for (uint32_t i = 1;  i < 3; i++) {
        sendReply(3 + i); // Put
    }

    ASSERT_EQ("PutReply(id:test:testdoctype1::test, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(PutOperationTest, multiple_copies_early_return_primary_required_not_done) {
    setup_stripe(3, 4, "storage:4 distributor:1", 2, true);

    sendPut(createPut(createDummyDocument("test", "test")));

    ASSERT_EQ("Create bucket => 3,Create bucket => 2,"
              "Create bucket => 1,Put => 3,Put => 2,Put => 1",
              _sender.getCommands(true));

    // Reply only to 2 nodes (but not the primary)
    sendReply(1);
    sendReply(2);
    sendReply(4);
    sendReply(5);

    ASSERT_EQ(0, _sender.replies().size());
}

TEST_F(PutOperationTest, do_not_revert_on_failure_after_early_return) {
    setup_stripe(Redundancy(3),NodeCount(4), "storage:4 distributor:1",
                     ReturnAfter(2), RequirePrimaryWritten(false));

    sendPut(createPut(createDummyDocument("test", "test")));

    ASSERT_EQ("Create bucket => 3,Create bucket => 2,"
              "Create bucket => 1,Put => 3,Put => 2,Put => 1",
              _sender.getCommands(true));

    for (uint32_t i = 0;  i < 3; i++) {
        sendReply(i); // CreateBucket
    }
    for (uint32_t i = 0;  i < 2; i++) {
        sendReply(3 + i); // Put
    }

    ASSERT_EQ("PutReply(id:test:testdoctype1::test, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());

    sendReply(5, api::ReturnCode::INTERNAL_FAILURE);
    // Should not be any revert commands sent
    ASSERT_EQ("Create bucket => 3,Create bucket => 2,"
              "Create bucket => 1,Put => 3,Put => 2,Put => 1",
              _sender.getCommands(true));
}

TEST_F(PutOperationTest, revert_successful_copies_when_one_fails) {
    setup_stripe(3, 4, "storage:4 distributor:1");

    createAndSendSampleDocument(TIMEOUT);

    ASSERT_EQ("Put => 0,Put => 2,Put => 1", _sender.getCommands(true));

    for (uint32_t i = 0;  i < 2; i++) {
        sendReply(i);
    }

    sendReply(2, api::ReturnCode::INTERNAL_FAILURE);

    ASSERT_EQ("PutReply(id:test:testdoctype1::, "
              "BucketId(0x0000000000000000), timestamp 100) "
              "ReturnCode(INTERNAL_FAILURE)",
              _sender.getLastReply(true));

    ASSERT_EQ("Revert => 0,Revert => 2", _sender.getCommands(true, false, 3));
}

TEST_F(PutOperationTest, no_revert_if_revert_disabled) {
    close();
    getDirConfig().getConfig("stor-distributormanager")
                  .set("enable_revert", "false");
    SetUp();
    setup_stripe(3, 4, "storage:4 distributor:1");

    createAndSendSampleDocument(TIMEOUT);

    ASSERT_EQ("Put => 0,Put => 2,Put => 1", _sender.getCommands(true));

    for (uint32_t i = 0;  i < 2; i++) {
        sendReply(i);
    }

    sendReply(2, api::ReturnCode::INTERNAL_FAILURE);

    ASSERT_EQ("PutReply(id:test:testdoctype1::, "
              "BucketId(0x0000000000000000), timestamp 100) "
              "ReturnCode(INTERNAL_FAILURE)",
              _sender.getLastReply(true));

    ASSERT_EQ("", _sender.getCommands(true, false, 3));
}

TEST_F(PutOperationTest, do_not_send_CreateBucket_if_already_pending) {
    setup_stripe(2, 2, "storage:2 distributor:1");

    Document::SP doc(createDummyDocument("test", "uri"));
    sendPut(createPut(doc));

    ASSERT_EQ("Create bucket => 1,Create bucket => 0,"
              "Put => 1,Put => 0",
              _sender.getCommands(true));

    // Manually shove sent messages into pending message tracker, since
    // this isn't done automatically.
    for (size_t i = 0; i < _sender.commands().size(); ++i) {
        operation_context().pending_message_tracker()
            .insert(_sender.command(i));
    }

    sendPut(createPut(doc));

    ASSERT_EQ("Create bucket => 1,Create bucket => 0,"
              "Put => 1,Put => 0,"
              "Put => 1,Put => 0",
              _sender.getCommands(true));
}

TEST_F(PutOperationTest, no_storage_nodes) {
    setup_stripe(2, 1, "storage:0 distributor:1");
    createAndSendSampleDocument(TIMEOUT);
    ASSERT_EQ("PutReply(id:test:testdoctype1::, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NOT_CONNECTED, "
              "Can't store document: No storage nodes available)",
              _sender.getLastReply(true));
}

TEST_F(PutOperationTest, update_correct_bucket_on_remapped_put) {
    setup_stripe(2, 2, "storage:2 distributor:1");

    auto doc = std::make_shared<Document>(type_repo(), doc_type(), DocumentId("id:test:testdoctype1:n=13:uri"));
    addNodesToBucketDB(document::BucketId(16,13), "0=0,1=0");
    sendPut(createPut(doc));

    ASSERT_EQ("Put => 0,Put => 1", _sender.getCommands(true));

    {
        std::shared_ptr<api::StorageCommand> msg2  = _sender.command(0);
        std::shared_ptr<api::StorageReply> reply(msg2->makeReply().release());
        auto* sreply = dynamic_cast<api::PutReply*>(reply.get());
        ASSERT_TRUE(sreply);
        sreply->remapBucketId(document::BucketId(17, 13));
        sreply->setBucketInfo(api::BucketInfo(1,2,3,4,5));
        op->receive(_sender, reply);
    }

    sendReply(1);

    ASSERT_EQ("PutReply(id:test:testdoctype1:n=13:uri, "
              "BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());

    ASSERT_EQ("BucketId(0x440000000000000d) : "
              "node(idx=0,crc=0x1,docs=2/4,bytes=3/5,trusted=true,active=false,ready=false)",
              dumpBucket(document::BucketId(17, 13)));
}

TEST_F(PutOperationTest, replica_not_resurrected_in_db_when_node_down_in_active_state) {
    setup_stripe(Redundancy(3), NodeCount(3), "distributor:1 storage:3");

    Document::SP doc(createDummyDocument("test", "uri"));
    document::BucketId bId = operation_context().make_split_bit_constrained_bucket_id(doc->getId());

    addNodesToBucketDB(bId, "0=1/2/3/t,1=1/2/3/t,2=1/2/3/t");

    sendPut(createPut(doc));

    ASSERT_EQ("Put => 1,Put => 2,Put => 0", _sender.getCommands(true));

    enable_cluster_state("distributor:1 storage:3 .1.s:d .2.s:m");
    addNodesToBucketDB(bId, "0=1/2/3/t"); // This will actually remove node #1.

    sendReply(0, api::ReturnCode::OK, api::BucketInfo(9, 9, 9));
    sendReply(1, api::ReturnCode::OK, api::BucketInfo(5, 6, 7));
    sendReply(2, api::ReturnCode::OK, api::BucketInfo(7, 8, 9));

    ASSERT_EQ("BucketId(0x4000000000000593) : "
              "node(idx=0,crc=0x7,docs=8/8,bytes=9/9,trusted=true,active=false,ready=false)",
              dumpBucket(operation_context().make_split_bit_constrained_bucket_id(doc->getId())));
}

TEST_F(PutOperationTest, replica_not_resurrected_in_db_when_node_down_in_pending_state) {
    setup_stripe(Redundancy(3), NodeCount(4), "version:1 distributor:1 storage:3");

    auto doc = createDummyDocument("test", "uri");
    auto bucket = operation_context().make_split_bit_constrained_bucket_id(doc->getId());
    addNodesToBucketDB(bucket, "0=1/2/3/t,1=1/2/3/t,2=1/2/3/t");
    sendPut(createPut(doc));

    ASSERT_EQ("Put => 1,Put => 2,Put => 0", _sender.getCommands(true));
    // Trigger a pending (but not completed) cluster state transition where content
    // node 0 is down. This will prune its replica from the DB. We assume that the
    // downed node managed to send off a reply to the Put before it went down, and
    // this reply must not recreate the replica in the bucket database. This is because
    // we have an invariant that the DB shall not contain replicas on nodes that are
    // not available.
    // We also let node 2 be in maintenance to ensure we also consider this an unavailable state.
    // Note that we have to explicitly trigger a transition that requires an async bucket
    // fetch here; if we just set a node down the cluster state would be immediately applied
    // and the distributor's "clear pending messages for downed nodes" logic would kick in
    // and hide the problem.
    simulate_set_pending_cluster_state("version:2 distributor:1 storage:4 .0.s:d .2.s:m");

    sendReply(0, api::ReturnCode::OK, api::BucketInfo(5, 6, 7));
    sendReply(1, api::ReturnCode::OK, api::BucketInfo(6, 7, 8));
    sendReply(2, api::ReturnCode::OK, api::BucketInfo(9, 8, 7));

    ASSERT_EQ("BucketId(0x4000000000000593) : "
              "node(idx=1,crc=0x5,docs=6/6,bytes=7/7,trusted=true,active=false,ready=false)",
              dumpBucket(bucket));
}

// TODO probably also do this for updates and removes
// TODO consider if we should use the pending state verbatim for computing targets if it exists
TEST_F(PutOperationTest, put_is_failed_with_busy_if_target_down_in_pending_state) {
    setup_stripe(Redundancy(3), NodeCount(4), "version:1 distributor:1 storage:3");
    auto doc = createDummyDocument("test", "test");
    auto bucket = operation_context().make_split_bit_constrained_bucket_id(doc->getId());
    addNodesToBucketDB(bucket, "0=1/2/3/t,1=1/2/3/t,2=1/2/3/t");
    simulate_set_pending_cluster_state("version:2 distributor:1 storage:4 .0.s:d .2.s:m");
    _sender.clear();

    sendPut(createPut(doc));
    EXPECT_EQ("", _sender.getCommands(true));
    EXPECT_EQ("PutReply(id:test:testdoctype1::test, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(BUSY, "
              "One or more target content nodes are unavailable in the pending cluster state)",
              _sender.getLastReply(true));
}

TEST_F(PutOperationTest, send_to_retired_nodes_if_no_up_nodes_available) {
    setup_stripe(Redundancy(2), NodeCount(2),
                     "distributor:1 storage:2 .0.s:r .1.s:r");
    Document::SP doc(createDummyDocument("test", "uri"));
    document::BucketId bucket(
            operation_context().make_split_bit_constrained_bucket_id(doc->getId()));
    addNodesToBucketDB(bucket, "0=1/2/3/t,1=1/2/3/t");

    sendPut(createPut(doc));

    ASSERT_EQ("Put => 0,Put => 1", _sender.getCommands(true));
}

void PutOperationTest::do_test_creation_with_bucket_activation_disabled(bool disabled) {
    setup_stripe(Redundancy(2), NodeCount(2), "distributor:1 storage:1");
    disableBucketActivationInConfig(disabled);

    Document::SP doc(createDummyDocument("test", "uri"));
    sendPut(createPut(doc));

    ASSERT_EQ("Create bucket => 0,Put => 0", _sender.getCommands(true));
    auto cmd = _sender.command(0);
    auto createCmd = std::dynamic_pointer_cast<api::CreateBucketCommand>(cmd);
    ASSERT_TRUE(createCmd.get() != nullptr);
    // There's only 1 content node, so if activation were not disabled, it
    // should always be activated.
    ASSERT_EQ(!disabled, createCmd->getActive());
}

TEST_F(PutOperationTest, replica_implicitly_activated_when_activation_is_not_disabled) {
    do_test_creation_with_bucket_activation_disabled(false);
}

TEST_F(PutOperationTest, replica_not_implicitly_activated_when_activation_is_disabled) {
    do_test_creation_with_bucket_activation_disabled(true);
}

void PutOperationTest::set_up_3_nodes_and_send_put_with_create_bucket_acks() {
    setup_stripe(3, 3, "storage:3 distributor:1");

    Document::SP doc(createDummyDocument("test", "test"));
    sendPut(createPut(doc));

    // Include CreateBucket to ensure we don't count it towards Put ACK majorities
    ASSERT_EQ("Create bucket => 2,Create bucket => 1,Create bucket => 0,"
              "Put => 2,Put => 1,Put => 0",
              _sender.getCommands(true));

    // ACK all CreateBuckets
    for (uint32_t i = 0;  i < 3; ++i) {
        sendReply(i);
    }
}

TEST_F(PutOperationTest, majority_ack_with_minority_tas_failure_returns_success) {
    ASSERT_NO_FATAL_FAILURE(set_up_3_nodes_and_send_put_with_create_bucket_acks());
    // Majority ACK, minority NACK
    sendReply(3);
    sendReply(4, api::ReturnCode::TEST_AND_SET_CONDITION_FAILED);
    sendReply(5);

    ASSERT_EQ("PutReply(id:test:testdoctype1::test, "
              "BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(PutOperationTest, minority_ack_with_majority_tas_failure_returns_tas_failure) {
    ASSERT_NO_FATAL_FAILURE(set_up_3_nodes_and_send_put_with_create_bucket_acks());
    // Minority ACK, majority NACK
    sendReply(3);
    sendReply(4, api::ReturnCode::TEST_AND_SET_CONDITION_FAILED);
    sendReply(5, api::ReturnCode::TEST_AND_SET_CONDITION_FAILED);

    ASSERT_EQ("PutReply(id:test:testdoctype1::test, "
              "BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(TEST_AND_SET_CONDITION_FAILED)",
              _sender.getLastReply());
}

TEST_F(PutOperationTest, minority_failure_override_not_in_effect_for_non_tas_errors) {
    ASSERT_NO_FATAL_FAILURE(set_up_3_nodes_and_send_put_with_create_bucket_acks());
    // Minority ACK, majority NACK. But non-TaS failure.
    sendReply(3);
    sendReply(4, api::ReturnCode::ABORTED);
    sendReply(5);

    ASSERT_EQ("PutReply(id:test:testdoctype1::test, "
              "BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(ABORTED)",
              _sender.getLastReply());
}

void PutOperationTest::set_up_tas_put_with_2_inconsistent_replica_nodes(bool create) {
    setup_stripe(Redundancy(2), NodeCount(2), "version:1 storage:2 distributor:1");
    config_enable_condition_probing(true);
    tag_content_node_supports_condition_probing(0, true);
    tag_content_node_supports_condition_probing(1, true);

    auto doc = createDummyDocument("test", "test");
    auto bucket = operation_context().make_split_bit_constrained_bucket_id(doc->getId());
    addNodesToBucketDB(bucket, "1=10/20/30,0=20/30/40");

    auto put = createPut(doc);
    put->setCondition(TestAndSetCondition("test.foo"));
    put->set_create_if_non_existent(create);
    put->getTrace().setLevel(9);
    sendPut(std::move(put));
    ASSERT_EQ("Get => 1,Get => 0", _sender.getCommands(true));
}

TEST_F(PutOperationTest, matching_condition_probe_sends_unconditional_puts_to_all_nodes) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_put_with_2_inconsistent_replica_nodes());

    op->receive(_sender, make_get_reply(*sent_get_command(0), 50, false, true));
    op->receive(_sender, make_get_reply(*sent_get_command(1), 50, false, true));

    ASSERT_EQ("Get => 1,Get => 0,Put => 1,Put => 0", _sender.getCommands(true)); // Note: cumulative message list

    auto put_n1 = sent_put_command(2);
    EXPECT_FALSE(put_n1->hasTestAndSetCondition());
    auto put_n0 = sent_put_command(3);
    EXPECT_FALSE(put_n0->hasTestAndSetCondition());

    // Ensure replies are no longer routed to condition checker
    ASSERT_TRUE(_sender.replies().empty());
    sendReply(2); // put to node 1
    ASSERT_TRUE(_sender.replies().empty());
    sendReply(3); // put to node 0
    ASSERT_EQ(_sender.replies().size(), 1);
    EXPECT_EQ("PutReply(id:test:testdoctype1::test, "
              "BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(PutOperationTest, mismatching_condition_probe_fails_op_with_tas_error) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_put_with_2_inconsistent_replica_nodes());

    op->receive(_sender, make_get_reply(*sent_get_command(0), 50, false, false));
    op->receive(_sender, make_get_reply(*sent_get_command(1), 50, false, false));

    ASSERT_EQ("Get => 1,Get => 0", _sender.getCommands(true));
    ASSERT_EQ("PutReply(id:test:testdoctype1::test, "
              "BucketId(0x0000000000000000), timestamp 100) "
              "ReturnCode(TEST_AND_SET_CONDITION_FAILED, Condition did not match document)",
              _sender.getLastReply());
}

// TODO change semantics for Not Found...
TEST_F(PutOperationTest, not_found_condition_probe_fails_op_with_tas_error) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_put_with_2_inconsistent_replica_nodes());

    op->receive(_sender, make_get_reply(*sent_get_command(0), 0, false, false));
    op->receive(_sender, make_get_reply(*sent_get_command(1), 0, false, false));

    ASSERT_EQ("Get => 1,Get => 0", _sender.getCommands(true));
    ASSERT_EQ("PutReply(id:test:testdoctype1::test, "
              "BucketId(0x0000000000000000), timestamp 100) "
              "ReturnCode(TEST_AND_SET_CONDITION_FAILED, Document does not exist)",
              _sender.getLastReply());
}

TEST_F(PutOperationTest, failed_condition_probe_fails_op_with_returned_error) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_put_with_2_inconsistent_replica_nodes());

    op->receive(_sender, make_get_reply(*sent_get_command(0), 0, false, false));
    op->receive(_sender, make_failed_get_reply(1));

    ASSERT_EQ("Get => 1,Get => 0", _sender.getCommands(true));
    ASSERT_EQ("PutReply(id:test:testdoctype1::test, "
              "BucketId(0x0000000000000000), timestamp 100) "
              "ReturnCode(ABORTED, Failed during write repair condition probe step. Reason: "
              "One or more replicas failed during test-and-set condition evaluation)",
              _sender.getLastReply());
}

TEST_F(PutOperationTest, create_flag_in_parent_put_is_propagated_to_sent_puts) {
    setup_stripe(Redundancy(2), NodeCount(2), "version:1 storage:2 distributor:1");
    auto doc = createDummyDocument("test", "test");
    auto bucket = operation_context().make_split_bit_constrained_bucket_id(doc->getId());
    addNodesToBucketDB(bucket, "1=10/20/30,0=20/30/40");

    auto put = createPut(doc);
    // Toggling the create-flag only makes sense in the presence of a TaS-condition
    put->setCondition(TestAndSetCondition("test.foo"));
    put->set_create_if_non_existent(true);

    sendPut(std::move(put));
    ASSERT_EQ("Put => 1,Put => 0", _sender.getCommands(true));

    auto put_n1 = sent_put_command(0);
    EXPECT_TRUE(put_n1->get_create_if_non_existent());
    auto put_n0 = sent_put_command(1);
    EXPECT_TRUE(put_n0->get_create_if_non_existent());
}

TEST_F(PutOperationTest, not_found_condition_probe_with_create_set_acts_as_if_matched) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_put_with_2_inconsistent_replica_nodes(true));

    op->receive(_sender, make_get_reply(*sent_get_command(0), 0, false, false));
    op->receive(_sender, make_get_reply(*sent_get_command(1), 0, false, false));

    EXPECT_EQ(_sender.replies().size(), 0);
    ASSERT_EQ("Get => 1,Get => 0,Put => 1,Put => 0", _sender.getCommands(true));

    auto put_n1 = sent_put_command(2);
    EXPECT_FALSE(put_n1->hasTestAndSetCondition());
    auto put_n0 = sent_put_command(3);
    EXPECT_FALSE(put_n0->hasTestAndSetCondition());
}

TEST_F(PutOperationTest, conditional_put_no_replicas_case_with_create_set_acts_as_if_matched) {
    setup_stripe(Redundancy(2), NodeCount(2), "version:1 storage:2 distributor:1");
    config_enable_condition_probing(true);
    tag_content_node_supports_condition_probing(0, true);
    tag_content_node_supports_condition_probing(1, true);

    // Don't init the DB with any replicas for the put's target bucket
    auto put = createPut(createDummyDocument("test", "test"));
    put->setCondition(TestAndSetCondition("test.foo"));
    put->set_create_if_non_existent(true);
    sendPut(std::move(put));

    EXPECT_EQ(_sender.replies().size(), 0);
    // Pipelined create + put
    ASSERT_EQ("Create bucket => 1,Create bucket => 0,Put => 1,Put => 0", _sender.getCommands(true));

    // In this case we can preserve both the condition + create-flag.
    // The content node will deal with them appropriately.
    auto put_n1 = sent_put_command(2);
    EXPECT_TRUE(put_n1->hasTestAndSetCondition());
    EXPECT_TRUE(put_n1->get_create_if_non_existent());
    auto put_n0 = sent_put_command(3);
    EXPECT_TRUE(put_n0->hasTestAndSetCondition());
    EXPECT_TRUE(put_n1->get_create_if_non_existent());
}

TEST_F(PutOperationTest, trace_is_propagated_from_condition_probe_gets_ok_probe_case) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_put_with_2_inconsistent_replica_nodes());

    ASSERT_EQ(sent_get_command(0)->getTrace().getLevel(), 9);
    auto get_reply = make_get_reply(*sent_get_command(0), 50, false, true);
    MBUS_TRACE(get_reply->getTrace(), 1, "a foo walks into a bar");

    op->receive(_sender, get_reply);
    op->receive(_sender, make_get_reply(*sent_get_command(1), 50, false, true));

    ASSERT_EQ("Get => 1,Get => 0,Put => 1,Put => 0", _sender.getCommands(true));
    sendReply(2);
    sendReply(3);
    ASSERT_EQ(_sender.replies().size(), 1);
    auto put_reply = sent_reply<api::PutReply>(0);

    auto trace_str = put_reply->getTrace().toString();
    EXPECT_THAT(trace_str, HasSubstr("a foo walks into a bar"));
}

TEST_F(PutOperationTest, trace_is_propagated_from_condition_probe_gets_failed_probe_case) {
    ASSERT_NO_FATAL_FAILURE(set_up_tas_put_with_2_inconsistent_replica_nodes());

    auto get_reply = make_get_reply(*sent_get_command(0), 50, false, false);
    MBUS_TRACE(get_reply->getTrace(), 1, "a foo walks into a zoo");

    op->receive(_sender, get_reply);
    op->receive(_sender, make_get_reply(*sent_get_command(1), 50, false, false));

    ASSERT_EQ("Get => 1,Get => 0", _sender.getCommands(true));
    ASSERT_EQ(_sender.replies().size(), 1);
    auto put_reply = sent_reply<api::PutReply>(0);

    auto trace_str = put_reply->getTrace().toString();
    EXPECT_THAT(trace_str, HasSubstr("a foo walks into a zoo"));
}

}
