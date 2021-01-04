// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/storage/distributor/operations/external/putoperation.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <tests/distributor/distributortestutil.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/gtest/gtest.h>

using std::shared_ptr;
using config::ConfigGetter;
using document::DocumenttypesConfig;
using config::FileSpec;
using vespalib::string;
using namespace document;
using namespace storage;
using namespace storage::api;
using namespace storage::lib;
using namespace std::literals::string_literals;
using document::test::makeDocumentBucket;

using namespace ::testing;

namespace storage::distributor {

class PutOperationTest : public Test,
                         public DistributorTestUtil
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
    std::string getNodes(const std::string& infoString);

    void sendReply(int idx = -1,
                      api::ReturnCode::Result result
                      = api::ReturnCode::OK,
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
        api::StorageReply::SP reply(msg->makeReply().release());
        dynamic_cast<api::BucketInfoReply&>(*reply).setBucketInfo(info);
        reply->setResult(result);

        op->receive(_sender, reply);
    }

    void sendPut(std::shared_ptr<api::PutCommand> msg) {
        op = std::make_unique<PutOperation>(distributor_component(),
                                            distributor_component(),
                                            getDistributorBucketSpace(),
                                            msg,
                                            getDistributor().getMetrics().
                                            puts);
        op->start(_sender, framework::MilliSecTime(0));
    }

    const document::DocumentType& doc_type() const {
        return *_testDocMan.getTypeRepo().getDocumentType("testdoctype1");
    }

    Document::SP createDummyDocument(const char* ns, const char* id) const {
        return std::make_shared<Document>(doc_type(), DocumentId(vespalib::make_string("id:%s:testdoctype1::%s", ns, id)));
    }

    static std::shared_ptr<api::PutCommand> createPut(Document::SP doc) {
        return std::make_shared<api::PutCommand>(makeDocumentBucket(document::BucketId(0)), std::move(doc), 100);
    }

    void set_up_3_nodes_and_send_put_with_create_bucket_acks();
};

PutOperationTest::~PutOperationTest() = default;

document::BucketId
PutOperationTest::createAndSendSampleDocument(vespalib::duration timeout) {
    auto doc = std::make_shared<Document>(doc_type(), DocumentId("id:test:testdoctype1::"));

    document::BucketId id = distributor_component().getBucketId(doc->getId());
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
    setupDistributor(1, 1, "storage:1 distributor:1");
    createAndSendSampleDocument(TIMEOUT);

    ASSERT_EQ("Put(BucketId(0x4000000000001dd4), "
              "id:test:testdoctype1::, timestamp 100, size 45) => 0",
              _sender.getCommands(true, true));

    sendReply();

    ASSERT_EQ("PutReply(id:test:testdoctype1::, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NONE)",
              _sender.getLastReply());
}

TEST_F(PutOperationTest, bucket_database_gets_special_entry_when_CreateBucket_sent) {
    setupDistributor(2, 1, "storage:1 distributor:1");

    Document::SP doc(createDummyDocument("test", "test"));
    sendPut(createPut(doc));

    // Database updated before CreateBucket is sent
    ASSERT_EQ("BucketId(0x4000000000008f09) : "
              "node(idx=0,crc=0x1,docs=0/0,bytes=0/0,trusted=true,active=true,ready=false)",
              dumpBucket(distributor_component().getBucketId(doc->getId())));

    ASSERT_EQ("Create bucket => 0,Put => 0", _sender.getCommands(true));
}

TEST_F(PutOperationTest, send_inline_split_before_put_if_bucket_too_large) {
    setupDistributor(1, 1, "storage:1 distributor:1");
    getConfig().setSplitCount(1024);
    getConfig().setSplitSize(1000000);

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
    setupDistributor(1, 1, "storage:1 distributor:1");
    getConfig().setSplitCount(1024);
    getConfig().setDoInlineSplit(false);

    addNodesToBucketDB(document::BucketId(0x4000000000000593), "0=10000/10000/10000/t");

    sendPut(createPut(createDummyDocument("test", "uri")));

    ASSERT_EQ("Put(BucketId(0x4000000000000593), id:test:testdoctype1::uri, timestamp 100, "
              "size 48) => 0",
              _sender.getCommands(true, true));
}

TEST_F(PutOperationTest, return_success_if_op_acked_on_all_replicas_even_if_bucket_concurrently_removed_from_db) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    createAndSendSampleDocument(TIMEOUT);

    ASSERT_EQ("Put(BucketId(0x4000000000001dd4), "
              "id:test:testdoctype1::, timestamp 100, size 45) => 0,"
              "Put(BucketId(0x4000000000001dd4), "
              "id:test:testdoctype1::, timestamp 100, size 45) => 1",
              _sender.getCommands(true, true));

    distributor_component().removeNodeFromDB(makeDocumentBucket(document::BucketId(16, 0x1dd4)), 0);

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
    setupDistributor(2, 1, "storage:1 distributor:1");

    createAndSendSampleDocument(TIMEOUT);

    sendReply(-1, api::ReturnCode::INTERNAL_FAILURE);

    ASSERT_EQ("PutReply(id:test:testdoctype1::, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(INTERNAL_FAILURE)",
              _sender.getLastReply(true));
}

TEST_F(PutOperationTest, multiple_copies) {
    setupDistributor(3, 4, "storage:4 distributor:1");

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
              dumpBucket(distributor_component().getBucketId(doc->getId())));
}

TEST_F(PutOperationTest, multiple_copies_early_return_primary_required) {
    setupDistributor(3, 4, "storage:4 distributor:1", 2, true);

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
    setupDistributor(3, 4, "storage:4 distributor:1", 2, false);

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
    setupDistributor(3, 4, "storage:4 distributor:1", 2, true);

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
    setupDistributor(Redundancy(3),NodeCount(4), "storage:4 distributor:1",
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
    setupDistributor(3, 4, "storage:4 distributor:1");

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
    setupDistributor(3, 4, "storage:4 distributor:1");

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
    setupDistributor(2, 2, "storage:2 distributor:1");

    Document::SP doc(createDummyDocument("test", "uri"));
    sendPut(createPut(doc));

    ASSERT_EQ("Create bucket => 1,Create bucket => 0,"
              "Put => 1,Put => 0",
              _sender.getCommands(true));

    // Manually shove sent messages into pending message tracker, since
    // this isn't done automatically.
    for (size_t i = 0; i < _sender.commands().size(); ++i) {
        distributor_component().getDistributor().getPendingMessageTracker()
            .insert(_sender.command(i));
    }

    sendPut(createPut(doc));

    ASSERT_EQ("Create bucket => 1,Create bucket => 0,"
              "Put => 1,Put => 0,"
              "Put => 1,Put => 0",
              _sender.getCommands(true));
}

TEST_F(PutOperationTest, no_storage_nodes) {
    setupDistributor(2, 1, "storage:0 distributor:1");
    createAndSendSampleDocument(TIMEOUT);
    ASSERT_EQ("PutReply(id:test:testdoctype1::, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(NOT_CONNECTED, "
              "Can't store document: No storage nodes available)",
              _sender.getLastReply(true));
}

TEST_F(PutOperationTest, update_correct_bucket_on_remapped_put) {
    setupDistributor(2, 2, "storage:2 distributor:1");

    auto doc = std::make_shared<Document>(doc_type(), DocumentId("id:test:testdoctype1:n=13:uri"));
    addNodesToBucketDB(document::BucketId(16,13), "0=0,1=0");
    sendPut(createPut(doc));

    ASSERT_EQ("Put => 0,Put => 1", _sender.getCommands(true));

    {
        std::shared_ptr<api::StorageCommand> msg2  = _sender.command(0);
        std::shared_ptr<api::StorageReply> reply(msg2->makeReply().release());
        PutReply* sreply = (PutReply*)reply.get();
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

BucketInfo
parseBucketInfoString(const std::string& nodeList) {
    vespalib::StringTokenizer tokenizer(nodeList, ",");

    BucketInfo entry;
    for (uint32_t i = 0; i < tokenizer.size(); i++) {
        vespalib::StringTokenizer tokenizer2(tokenizer[i], "-");
        int node = atoi(tokenizer2[0].data());
        int size = atoi(tokenizer2[1].data());
        bool trusted = (tokenizer2[2] == "true");

        entry.addNode(BucketCopy(0,
                                 node,
                                 api::BucketInfo(size, size * 1000, size * 2000))
                      .setTrusted(trusted),
                      toVector<uint16_t>(0));
    }

    return entry;
}

std::string
PutOperationTest::getNodes(const std::string& infoString) {
    Document::SP doc(createDummyDocument("test", "uri"));
    document::BucketId bid(distributor_component().getBucketId(doc->getId()));

    BucketInfo entry = parseBucketInfoString(infoString);

    std::ostringstream ost;

    std::vector<uint16_t> targetNodes;
    std::vector<uint16_t> createNodes;
    PutOperation::getTargetNodes(getDistributorBucketSpace().get_ideal_service_layer_nodes_bundle(bid).get_available_nodes(),
                                 targetNodes, createNodes, entry, 2);

    ost << "target( ";
    for (uint32_t i = 0; i < targetNodes.size(); i++) {
        ost << targetNodes[i] << " ";
    }
    ost << ") create( ";
    for (uint32_t i = 0; i < createNodes.size(); i++) {
        ost << createNodes[i] << " ";
    }
    ost << ")";

    return ost.str();
}

TEST_F(PutOperationTest, target_nodes) {
    setupDistributor(2, 6, "storage:6 distributor:1");

    // Ideal state of bucket is 1,2.
    ASSERT_EQ("target( 1 2 ) create( 1 2 )", getNodes(""));
    ASSERT_EQ("target( 1 2 ) create( 2 )",   getNodes("1-1-true"));
    ASSERT_EQ("target( 1 2 ) create( 2 )",   getNodes("1-1-false"));
    ASSERT_EQ("target( 3 4 5 ) create( )",   getNodes("3-1-true,4-1-true,5-1-true"));
    ASSERT_EQ("target( 3 4 ) create( )",     getNodes("3-2-true,4-2-true,5-1-false"));
    ASSERT_EQ("target( 1 3 4 ) create( )",   getNodes("3-2-true,4-2-true,1-1-false"));
    ASSERT_EQ("target( 4 5 ) create( )",     getNodes("4-2-false,5-1-false"));
    ASSERT_EQ("target( 1 4 ) create( 1 )",   getNodes("4-1-true"));
}

TEST_F(PutOperationTest, replica_not_resurrected_in_db_when_node_down_in_active_state) {
    setupDistributor(Redundancy(3), NodeCount(3), "distributor:1 storage:3");

    Document::SP doc(createDummyDocument("test", "uri"));
    document::BucketId bId = distributor_component().getBucketId(doc->getId());

    addNodesToBucketDB(bId, "0=1/2/3/t,1=1/2/3/t,2=1/2/3/t");

    sendPut(createPut(doc));

    ASSERT_EQ("Put => 1,Put => 2,Put => 0", _sender.getCommands(true));

    enableDistributorClusterState("distributor:1 storage:3 .1.s:d .2.s:m");
    addNodesToBucketDB(bId, "0=1/2/3/t"); // This will actually remove node #1.

    sendReply(0, api::ReturnCode::OK, api::BucketInfo(9, 9, 9));
    sendReply(1, api::ReturnCode::OK, api::BucketInfo(5, 6, 7));
    sendReply(2, api::ReturnCode::OK, api::BucketInfo(7, 8, 9));

    ASSERT_EQ("BucketId(0x4000000000000593) : "
              "node(idx=0,crc=0x7,docs=8/8,bytes=9/9,trusted=true,active=false,ready=false)",
              dumpBucket(distributor_component().getBucketId(doc->getId())));
}

TEST_F(PutOperationTest, replica_not_resurrected_in_db_when_node_down_in_pending_state) {
    setupDistributor(Redundancy(3), NodeCount(4), "version:1 distributor:1 storage:3");

    auto doc = createDummyDocument("test", "uri");
    auto bucket = distributor_component().getBucketId(doc->getId());
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
    getBucketDBUpdater().onSetSystemState(
            std::make_shared<api::SetSystemStateCommand>(
                    lib::ClusterState("version:2 distributor:1 storage:4 .0.s:d .2.s:m")));

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
    setupDistributor(Redundancy(3), NodeCount(4), "version:1 distributor:1 storage:3");
    auto doc = createDummyDocument("test", "test");
    auto bucket = distributor_component().getBucketId(doc->getId());
    addNodesToBucketDB(bucket, "0=1/2/3/t,1=1/2/3/t,2=1/2/3/t");
    getBucketDBUpdater().onSetSystemState(
            std::make_shared<api::SetSystemStateCommand>(
                    lib::ClusterState("version:2 distributor:1 storage:4 .0.s:d .2.s:m")));
    _sender.clear();

    sendPut(createPut(doc));
    EXPECT_EQ("", _sender.getCommands(true));
    EXPECT_EQ("PutReply(id:test:testdoctype1::test, BucketId(0x0000000000000000), "
              "timestamp 100) ReturnCode(BUSY, "
              "One or more target content nodes are unavailable in the pending cluster state)",
              _sender.getLastReply(true));
}

TEST_F(PutOperationTest, send_to_retired_nodes_if_no_up_nodes_available) {
    setupDistributor(Redundancy(2), NodeCount(2),
                     "distributor:1 storage:2 .0.s:r .1.s:r");
    Document::SP doc(createDummyDocument("test", "uri"));
    document::BucketId bucket(
            distributor_component().getBucketId(doc->getId()));
    addNodesToBucketDB(bucket, "0=1/2/3/t,1=1/2/3/t");

    sendPut(createPut(doc));

    ASSERT_EQ("Put => 0,Put => 1", _sender.getCommands(true));
}

void PutOperationTest::do_test_creation_with_bucket_activation_disabled(bool disabled) {
    setupDistributor(Redundancy(2), NodeCount(2), "distributor:1 storage:1");
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
    setupDistributor(3, 3, "storage:3 distributor:1");

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

}
