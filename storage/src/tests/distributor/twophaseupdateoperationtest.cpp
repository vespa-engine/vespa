// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/config/helper/configgetter.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storage/distributor/operations/external/twophaseupdateoperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

namespace storage::distributor {

using document::test::makeDocumentBucket;
using config::ConfigGetter;
using namespace document;
using namespace storage;
using namespace storage::distributor;
using namespace storage::api;
using namespace storage::lib;
using namespace ::testing;

struct TwoPhaseUpdateOperationTest : Test, DistributorStripeTestUtil {
    document::TestDocRepo _testRepo;
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType* _doc_type;
    DistributorMessageSenderStub _sender;

    TwoPhaseUpdateOperationTest();
    ~TwoPhaseUpdateOperationTest() override;

    void checkMessageSettingsPropagatedTo(
        const api::StorageCommand::SP& msg) const;

    std::string getUpdatedValueFromLastPut(DistributorMessageSenderStub&);

    void SetUp() override {
        _repo = _testRepo.getTypeRepoSp();
        _doc_type = _repo->getDocumentType("testdoctype1");
        createLinks();
        setTypeRepo(_repo);
        getClock().setAbsoluteTimeInSeconds(200);
        // TODO, rewrite test to handle enable_metadata_only_fetch_phase_for_inconsistent_updates=true as default
        auto cfg = make_config();
        cfg->set_enable_metadata_only_fetch_phase_for_inconsistent_updates(false);
        configure_stripe(cfg);
    }

    void TearDown() override {
        close();
    }

    void replyToMessage(Operation& callback,
                        DistributorMessageSenderStub& sender,
                        uint32_t index,
                        uint64_t oldTimestamp,
                        api::ReturnCode::Result result = api::ReturnCode::OK);

    void replyToPut(
            Operation& callback,
            DistributorMessageSenderStub& sender,
            uint32_t index,
            api::ReturnCode::Result result = api::ReturnCode::OK,
            const std::string& traceMsg = "");

    void replyToCreateBucket(
            Operation& callback,
            DistributorMessageSenderStub& sender,
            uint32_t index,
            api::ReturnCode::Result result = api::ReturnCode::OK);

    void replyToGet(
            Operation& callback,
            DistributorMessageSenderStub& sender,
            uint32_t index,
            uint64_t oldTimestamp,
            bool haveDocument = true,
            api::ReturnCode::Result result = api::ReturnCode::OK,
            const std::string& traceMsg = "");

    void reply_to_metadata_get(
            Operation& callback,
            DistributorMessageSenderStub& sender,
            uint32_t index,
            uint64_t old_timestamp,
            api::ReturnCode::Result result = api::ReturnCode::OK,
            const std::string& trace_msg = "");

    void reply_to_get_with_tombstone(
            Operation& callback,
            DistributorMessageSenderStub& sender,
            uint32_t index,
            uint64_t old_timestamp);

    struct UpdateOptions {
        bool _makeInconsistentSplit;
        bool _createIfNonExistent;
        bool _withError;
        api::Timestamp _timestampToUpdate;
        documentapi::TestAndSetCondition _condition;

        UpdateOptions()
            : _makeInconsistentSplit(false),
              _createIfNonExistent(false),
              _withError(false),
              _timestampToUpdate(0),
              _condition()
        {
        }

        UpdateOptions& makeInconsistentSplit(bool mis) {
            _makeInconsistentSplit = mis;
            return *this;
        }
        UpdateOptions& createIfNonExistent(bool cine) {
            _createIfNonExistent = cine;
            return *this;
        }
        UpdateOptions& withError(bool error = true) {
            _withError = error;
            return *this;
        }
        UpdateOptions& timestampToUpdate(api::Timestamp ts) {
            _timestampToUpdate = ts;
            return *this;
        }
        UpdateOptions& condition(vespalib::stringref cond) {
            _condition = documentapi::TestAndSetCondition(cond);
            return *this;
        }
    };

    std::shared_ptr<TwoPhaseUpdateOperation>
    sendUpdate(const std::string& bucketState,
               const UpdateOptions& options = UpdateOptions());

    void assertAbortedUpdateReplyWithContextPresent(
            const DistributorMessageSenderStub& closeSender) const;

    void do_test_ownership_changed_between_gets_and_second_phase(Timestamp lowest_get_timestamp,
                                                                 Timestamp highest_get_timestamp,
                                                                 Timestamp expected_response_timestamp);

    std::shared_ptr<TwoPhaseUpdateOperation> set_up_2_inconsistent_replicas_and_start_update(bool enable_3phase = true) {
        setup_stripe(2, 2, "storage:2 distributor:1");
        auto cfg = make_config();
        cfg->set_enable_metadata_only_fetch_phase_for_inconsistent_updates(enable_3phase);
        configure_stripe(cfg);
        auto cb = sendUpdate("0=1/2/3,1=2/3/4"); // Inconsistent replicas.
        cb->start(_sender);
        return cb;
    }

    void set_up_distributor_with_feed_blocked_state() {
        setup_stripe(2, 2,
                     lib::ClusterStateBundle(lib::ClusterState("distributor:1 storage:2"),
                                             {}, {true, "full disk"}, false));
    }

};

TwoPhaseUpdateOperationTest::TwoPhaseUpdateOperationTest() = default;
TwoPhaseUpdateOperationTest::~TwoPhaseUpdateOperationTest() = default;

void
TwoPhaseUpdateOperationTest::replyToMessage(
        Operation& callback,
        DistributorMessageSenderStub& sender,
        uint32_t index,
        uint64_t oldTimestamp,
        api::ReturnCode::Result result)
{
    std::shared_ptr<api::StorageMessage> msg2 = sender.command(index);
    auto& updatec = dynamic_cast<UpdateCommand&>(*msg2);
    std::unique_ptr<api::StorageReply> reply(updatec.makeReply());
    auto& update_reply = dynamic_cast<api::UpdateReply&>(*reply);
    update_reply.setOldTimestamp(oldTimestamp);
    update_reply.setBucketInfo(api::BucketInfo(0x123, 1, 100)); // Dummy info to avoid invalid info being returned
    reply->setResult(api::ReturnCode(result, ""));

    callback.receive(sender,
                     std::shared_ptr<StorageReply>(reply.release()));
}

void
TwoPhaseUpdateOperationTest::replyToPut(
        Operation& callback,
        DistributorMessageSenderStub& sender,
        uint32_t index,
        api::ReturnCode::Result result,
        const std::string& traceMsg)
{
    std::shared_ptr<api::StorageMessage> msg2 = sender.command(index);
    auto& putc = dynamic_cast<PutCommand&>(*msg2);
    std::unique_ptr<api::StorageReply> reply(putc.makeReply());
    reply->setResult(api::ReturnCode(result, ""));
    if (!traceMsg.empty()) {
        MBUS_TRACE(reply->getTrace(), 1, traceMsg);
    }
    callback.receive(sender,
                     std::shared_ptr<StorageReply>(reply.release()));
}

void
TwoPhaseUpdateOperationTest::replyToCreateBucket(
        Operation& callback,
        DistributorMessageSenderStub& sender,
        uint32_t index,
        api::ReturnCode::Result result)
{
    std::shared_ptr<api::StorageMessage> msg2 = sender.command(index);
    auto& putc = dynamic_cast<CreateBucketCommand&>(*msg2);
    std::unique_ptr<api::StorageReply> reply(putc.makeReply());
    reply->setResult(api::ReturnCode(result, ""));
    callback.receive(sender,
                     std::shared_ptr<StorageReply>(reply.release()));
}

void
TwoPhaseUpdateOperationTest::replyToGet(
        Operation& callback,
        DistributorMessageSenderStub& sender,
        uint32_t index,
        uint64_t oldTimestamp,
        bool haveDocument,
        api::ReturnCode::Result result,
        const std::string& traceMsg)
{
    auto& get = dynamic_cast<const api::GetCommand&>(*sender.command(index));
    std::shared_ptr<api::StorageReply> reply;

    if (haveDocument) {
        auto doc(std::make_shared<Document>(*_doc_type, DocumentId("id:ns:" + _doc_type->getName() + "::1")));
        doc->setValue("headerval", IntFieldValue(oldTimestamp));

        reply = std::make_shared<api::GetReply>(get, doc, oldTimestamp);
    } else {
        reply = std::make_shared<api::GetReply>(get, Document::SP(), 0);
    }
    reply->setResult(api::ReturnCode(result, ""));
    if (!traceMsg.empty()) {
        MBUS_TRACE(reply->getTrace(), 1, traceMsg);
    }

    callback.receive(sender, reply);
}

void
TwoPhaseUpdateOperationTest::reply_to_metadata_get(
        Operation& callback,
        DistributorMessageSenderStub& sender,
        uint32_t index,
        uint64_t old_timestamp,
        api::ReturnCode::Result result,
        const std::string& trace_msg)
{
    auto& get = dynamic_cast<const api::GetCommand&>(*sender.command(index));
    auto reply = std::make_shared<api::GetReply>(get, std::shared_ptr<Document>(), old_timestamp);
    reply->setResult(api::ReturnCode(result, ""));
    if (!trace_msg.empty()) {
        MBUS_TRACE(reply->getTrace(), 1, trace_msg);
    }
    callback.receive(sender, reply);
}

void
TwoPhaseUpdateOperationTest::reply_to_get_with_tombstone(
        Operation& callback,
        DistributorMessageSenderStub& sender,
        uint32_t index,
        uint64_t old_timestamp)
{
    auto& get = dynamic_cast<const api::GetCommand&>(*sender.command(index));
    auto reply = std::make_shared<api::GetReply>(get, std::shared_ptr<Document>(), old_timestamp, false, true);
    callback.receive(sender, reply);
}

namespace {

struct DummyTransportContext : api::TransportContext {
    // No methods to implement.
};

}

std::shared_ptr<TwoPhaseUpdateOperation>
TwoPhaseUpdateOperationTest::sendUpdate(const std::string& bucketState,
                                        const UpdateOptions& options) 
{
    document::DocumentUpdate::SP update;
    if (!options._withError) {
        update = std::make_shared<document::DocumentUpdate>(
                *_repo, *_doc_type,
                document::DocumentId("id:ns:" + _doc_type->getName() + "::1"));
        update->addUpdate(FieldUpdate(_doc_type->getField("headerval")).addUpdate(std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 10)));
    } else {
        // Create an update to a different doctype than the one returned as
        // part of the Get. Just a sneaky way to force an eval error.
        auto* badDocType = _repo->getDocumentType("testdoctype2");
        update = std::make_shared<document::DocumentUpdate>(
                *_repo, *badDocType,
                document::DocumentId("id:ns:" + _doc_type->getName() + "::1"));
        update->addUpdate(FieldUpdate(badDocType->getField("onlyinchild")).addUpdate(std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 10)));
    }
    update->setCreateIfNonExistent(options._createIfNonExistent);

    document::BucketId id = operation_context().make_split_bit_constrained_bucket_id(update->getId());
    document::BucketId id2 = document::BucketId(id.getUsedBits() + 1, id.getRawId());

    if (bucketState.length()) {
        addNodesToBucketDB(id, bucketState);
    }

    if (options._makeInconsistentSplit) {
        addNodesToBucketDB(id2, bucketState);
    }

    auto msg(std::make_shared<api::UpdateCommand>(
            makeDocumentBucket(document::BucketId(0)), update, api::Timestamp(0)));
    // Misc settings for checking that propagation works.
    msg->getTrace().setLevel(6);
    msg->setTimeout(6789ms);
    msg->setPriority(99);
    if (options._timestampToUpdate) {
        msg->setOldTimestamp(options._timestampToUpdate);
    }
    msg->setCondition(options._condition);
    msg->setTransportContext(std::make_unique<DummyTransportContext>());

    return std::make_shared<TwoPhaseUpdateOperation>(
            node_context(), operation_context(), doc_selection_parser(),
            getDistributorBucketSpace(), msg, metrics());
}

TEST_F(TwoPhaseUpdateOperationTest, simple) {
    setup_stripe(1, 1, "storage:1 distributor:1");
    auto cb = sendUpdate("0=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0", _sender.getCommands(true));

    replyToMessage(*cb, _sender, 0, 90);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 90) ReturnCode(NONE)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.failures.notfound.getValue(), 0);
    EXPECT_EQ(metrics().updates.failures.test_and_set_failed.getValue(), 0);
}

TEST_F(TwoPhaseUpdateOperationTest, non_existing) {
    setup_stripe(1, 1, "storage:1 distributor:1");
    auto cb = sendUpdate("");
    cb->start(_sender);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) ReturnCode(NONE)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.failures.notfound.getValue(), 1);
}

TEST_F(TwoPhaseUpdateOperationTest, update_failed) {
    setup_stripe(1, 1, "storage:1 distributor:1");
    auto cb = sendUpdate("0=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0", _sender.getCommands(true));

    replyToMessage(*cb, _sender, 0, 90, api::ReturnCode::INTERNAL_FAILURE);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(INTERNAL_FAILURE)",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0,Update => 1", _sender.getCommands(true));

    replyToMessage(*cb, _sender, 0, 90);
    replyToMessage(*cb, _sender, 1, 110);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 1", _sender.getLastCommand(true));

    replyToGet(*cb, _sender, 2, 110);

    ASSERT_EQ("Update => 0,Update => 1,Get => 1,Put => 1,Put => 0", _sender.getCommands(true));

    ASSERT_TRUE(_sender.replies().empty());

    replyToPut(*cb, _sender, 3);
    replyToPut(*cb, _sender, 4);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
              "(best node 1)) ReturnCode(NONE)",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_not_found) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0,Update => 1", _sender.getCommands(true));

    replyToMessage(*cb, _sender, 0, 90);
    replyToMessage(*cb, _sender, 1, 110);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 1", _sender.getLastCommand(true));
    ASSERT_TRUE(_sender.replies().empty());

    replyToGet(*cb, _sender, 2, 110, false);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
              "(best node 1)) ReturnCode(INTERNAL_FAILURE)",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_update_error) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0,Update => 1", _sender.getCommands(true));

    replyToMessage(*cb, _sender, 0, 90);
    ASSERT_TRUE(_sender.replies().empty());
    replyToMessage(*cb, _sender, 1, 110, api::ReturnCode::IO_FAILURE);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 90) "
              "ReturnCode(IO_FAILURE)",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_get_error) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0,Update => 1", _sender.getCommands(true));

    replyToMessage(*cb, _sender, 0, 90);
    replyToMessage(*cb, _sender, 1, 110);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 1",
              _sender.getLastCommand(true));

    ASSERT_TRUE(_sender.replies().empty());
    replyToGet(*cb, _sender, 2, 110, false, api::ReturnCode::IO_FAILURE);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
              "(best node 1)) ReturnCode(IO_FAILURE)",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_put_error) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0,Update => 1", _sender.getCommands(true));

    replyToMessage(*cb, _sender, 0, 90);
    replyToMessage(*cb, _sender, 1, 110);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 1",
              _sender.getLastCommand(true));

    replyToGet(*cb, _sender, 2, 110);

    ASSERT_EQ("Update => 0,Update => 1,Get => 1,Put => 1,Put => 0",
              _sender.getCommands(true));

    replyToPut(*cb, _sender, 3, api::ReturnCode::IO_FAILURE);
    ASSERT_TRUE(_sender.replies().empty());
    replyToPut(*cb, _sender, 4);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
              "(best node 1)) ReturnCode(IO_FAILURE)",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_put_not_started) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0,Update => 1", _sender.getCommands(true));

    replyToMessage(*cb, _sender, 0, 90);
    replyToMessage(*cb, _sender, 1, 110);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 1",
              _sender.getLastCommand(true));
    checkMessageSettingsPropagatedTo(_sender.commands().back());

    enable_cluster_state("storage:0 distributor:1");
    ASSERT_TRUE(_sender.replies().empty());
    replyToGet(*cb, _sender, 2, 110);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
              "(best node 1)) ReturnCode(NOT_CONNECTED, "
              "Can't store document: No storage nodes available)",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_inconsistent_split) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3", UpdateOptions().makeInconsistentSplit(true));
    cb->start(_sender);

    std::string wanted("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 0,"
                       "Get(BucketId(0x440000000000cac4), id:ns:testdoctype1::1) => 0");

    std::string text = _sender.getCommands(true, true);
    ASSERT_EQ(wanted, text);

    replyToGet(*cb, _sender, 0, 90);
    replyToGet(*cb, _sender, 1, 120);

    ASSERT_EQ("Put(BucketId(0x440000000000cac4), id:ns:testdoctype1::1, "
              "timestamp 200000000, size 60) => 1,"
              "Put(BucketId(0x440000000000cac4), id:ns:testdoctype1::1, "
              "timestamp 200000000, size 60) => 0",
              _sender.getCommands(true, true, 2));

    replyToPut(*cb, _sender, 2);
    ASSERT_TRUE(_sender.replies().empty());
    replyToPut(*cb, _sender, 3);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 120) "
              "ReturnCode(NONE)",
              _sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::checkMessageSettingsPropagatedTo(
        const api::StorageCommand::SP& msg) const
{
    // Settings set in sendUpdate().
    EXPECT_EQ(6, msg->getTrace().getLevel());
    EXPECT_EQ(6789ms, msg->getTimeout());
    EXPECT_EQ(99, msg->getPriority());
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_propagates_message_settings_to_update) {
    setup_stripe(1, 1, "storage:1 distributor:1");
    auto cb = sendUpdate("0=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0", _sender.getCommands(true));

    StorageCommand::SP msg(_sender.commands().back());
    checkMessageSettingsPropagatedTo(msg);
}

TEST_F(TwoPhaseUpdateOperationTest, n_of_m) {
    setup_stripe(2, 2, "storage:2 distributor:1", 1);
    auto cb = sendUpdate("0=1/2/3,1=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0,Update => 1", _sender.getCommands(true));

    ASSERT_TRUE(_sender.replies().empty());
    replyToMessage(*cb, _sender, 0, 90);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 90) ReturnCode(NONE)",
              _sender.getLastReply(true));

    replyToMessage(*cb, _sender, 1, 123);
}

std::string
TwoPhaseUpdateOperationTest::getUpdatedValueFromLastPut(
        DistributorMessageSenderStub& sender)
{
    Document::SP doc(dynamic_cast<api::PutCommand&>(*sender.commands().back())
                     .getDocument());
    FieldValue::UP value(doc->getValue("headerval"));
    return value->toString();
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_updates_newest_received_document) {
    setup_stripe(3, 3, "storage:3 distributor:1");
    // 0,1 in sync. 2 out of sync.
    auto cb = sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4");
    cb->start(_sender);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 0,"
              "Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 2",
              _sender.getCommands(true, true));
    replyToGet(*cb, _sender, 0, 50);
    replyToGet(*cb, _sender, 1, 70);

    ASSERT_EQ("Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 1,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 2,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 0",
              _sender.getCommands(true, true, 2));
    // Make sure Put contains an updated document (+10 arith. update on field
    // whose value equals gotten timestamp). In this case we want 70 -> 80.
    ASSERT_EQ("80", getUpdatedValueFromLastPut(_sender));

    replyToPut(*cb, _sender, 2);
    replyToPut(*cb, _sender, 3);
    ASSERT_TRUE(_sender.replies().empty());
    replyToPut(*cb, _sender, 4);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 70) "
              "ReturnCode(NONE)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.ok.getValue(), 1);
}

TEST_F(TwoPhaseUpdateOperationTest, create_if_non_existent_creates_document_if_all_empty_gets) {
    setup_stripe(3, 3, "storage:3 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4", UpdateOptions().createIfNonExistent(true));
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 2", _sender.getCommands(true));
    replyToGet(*cb, _sender, 0, 0, false);
    replyToGet(*cb, _sender, 1, 0, false);
    // Since create-if-non-existent is set, distributor should create doc from
    // scratch.
    ASSERT_EQ("Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 1,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 2,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 0",
              _sender.getCommands(true, true, 2));

    ASSERT_EQ("10", getUpdatedValueFromLastPut(_sender));

    replyToPut(*cb, _sender, 2);
    replyToPut(*cb, _sender, 3);
    ASSERT_TRUE(_sender.replies().empty());
    replyToPut(*cb, _sender, 4);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 200000000) "
              "ReturnCode(NONE)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.ok.getValue(), 1);
}

TEST_F(TwoPhaseUpdateOperationTest, update_fails_if_safe_path_has_failed_put) {
    setup_stripe(3, 3, "storage:3 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4", UpdateOptions().createIfNonExistent(true));
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 2", _sender.getCommands(true));
    replyToGet(*cb, _sender, 0, 0, false);
    replyToGet(*cb, _sender, 1, 0, false);
    // Since create-if-non-existent is set, distributor should create doc from
    // scratch.
    ASSERT_EQ("Put => 1,Put => 2,Put => 0", _sender.getCommands(true, false, 2));

    replyToPut(*cb, _sender, 2);
    replyToPut(*cb, _sender, 3);
    ASSERT_TRUE(_sender.replies().empty());
    replyToPut(*cb, _sender, 4, api::ReturnCode::IO_FAILURE);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 200000000) "
              "ReturnCode(IO_FAILURE)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.ok.getValue(), 0);
    EXPECT_EQ(metrics().updates.failures.storagefailure.getValue(), 1);
}

TEST_F(TwoPhaseUpdateOperationTest, update_fails_if_safe_path_gets_fail) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().createIfNonExistent(true));
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    replyToGet(*cb, _sender, 0, 0, false, api::ReturnCode::IO_FAILURE);
    ASSERT_TRUE(_sender.replies().empty());
    replyToGet(*cb, _sender, 1, 0, false, api::ReturnCode::IO_FAILURE);
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(IO_FAILURE)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.ok.getValue(), 0);
    EXPECT_EQ(metrics().updates.failures.storagefailure.getValue(), 1);
}

TEST_F(TwoPhaseUpdateOperationTest, update_fails_if_apply_throws_exception) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    // Create update for wrong doctype which will fail the update.
    auto cb = sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().withError());
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    replyToGet(*cb, _sender, 0, 50);
    ASSERT_TRUE(_sender.replies().empty());
    replyToGet(*cb, _sender, 1, 70);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 70) "
              "ReturnCode(INTERNAL_FAILURE, Can not apply a "
              "\"testdoctype2\" document update to a "
              "\"testdoctype1\" document.)",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, non_existing_with_auto_create) {
    setup_stripe(1, 1, "storage:1 distributor:1");

    auto cb = sendUpdate("", UpdateOptions().createIfNonExistent(true));
    cb->start(_sender);

    ASSERT_EQ("CreateBucketCommand(BucketId(0x400000000000cac4), active) "
              "Reasons to start:  => 0,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, "
              "timestamp 200000000, size 60) => 0",
              _sender.getCommands(true, true));

    ASSERT_EQ("10", getUpdatedValueFromLastPut(_sender));

    replyToCreateBucket(*cb, _sender, 0);
    ASSERT_TRUE(_sender.replies().empty());
    replyToPut(*cb, _sender, 1);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 200000000) "
              "ReturnCode(NONE)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.ok.getValue(), 1);
    // "Not found" failure not counted when create: true is set, since the update itself isn't failed.
    EXPECT_EQ(metrics().updates.failures.notfound.getValue(), 0);
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_fails_update_when_mismatching_timestamp_constraint) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().timestampToUpdate(1234));

    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    replyToGet(*cb, _sender, 0, 100);
    ASSERT_TRUE(_sender.replies().empty());
    replyToGet(*cb, _sender, 1, 110);
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(NONE, No document with requested "
                         "timestamp found)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.ok.getValue(), 0);
    EXPECT_EQ(metrics().updates.failures.notfound.getValue(), 1);
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_update_propagates_message_settings_to_gets_and_puts) {
    setup_stripe(3, 3, "storage:3 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4");
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 2", _sender.getCommands(true));
    checkMessageSettingsPropagatedTo(_sender.command(0));
    checkMessageSettingsPropagatedTo(_sender.command(1));
    replyToGet(*cb, _sender, 0, 50);
    replyToGet(*cb, _sender, 1, 70);
    ASSERT_EQ("Put => 1,Put => 2,Put => 0", _sender.getCommands(true, false, 2));
    checkMessageSettingsPropagatedTo(_sender.command(2));
    checkMessageSettingsPropagatedTo(_sender.command(3));
    checkMessageSettingsPropagatedTo(_sender.command(4));
    replyToPut(*cb, _sender, 2);
    replyToPut(*cb, _sender, 3);
    replyToPut(*cb, _sender, 4);
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_propagates_mbus_traces_from_replies) {
    setup_stripe(3, 3, "storage:3 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4");
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 2", _sender.getCommands(true));
    replyToGet(*cb, _sender, 0, 50, true, api::ReturnCode::OK, "hello earthlings");
    replyToGet(*cb, _sender, 1, 70);
    ASSERT_EQ("Put => 1,Put => 2,Put => 0", _sender.getCommands(true, false, 2));
    replyToPut(*cb, _sender, 2, api::ReturnCode::OK, "fooo");
    replyToPut(*cb, _sender, 3, api::ReturnCode::OK, "baaa");
    ASSERT_TRUE(_sender.replies().empty());
    replyToPut(*cb, _sender, 4);
    
    ASSERT_EQ("Update Reply", _sender.getLastReply(false));

    std::string trace(_sender.replies().back()->getTrace().toString());
    ASSERT_THAT(trace, HasSubstr("hello earthlings"));
    ASSERT_THAT(trace, HasSubstr("fooo"));
    ASSERT_THAT(trace, HasSubstr("baaa"));
}

void TwoPhaseUpdateOperationTest::do_test_ownership_changed_between_gets_and_second_phase(
        Timestamp lowest_get_timestamp,
        Timestamp highest_get_timestamp,
        Timestamp expected_response_timestamp)
{
    setup_stripe(2, 2, "storage:2 distributor:1");
    // Update towards inconsistent bucket invokes safe path.
    auto cb = sendUpdate("0=1/2/3,1=2/3/4");
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));

    // Alter cluster state so that distributor is now down (technically the
    // entire cluster is down in this state, but this should not matter). In
    // this new state, the distributor no longer owns the bucket in question
    // and the operation should thus be failed. We must not try to send Puts
    // to a bucket we no longer own.
    enable_cluster_state("storage:2 distributor:1 .0.s:d");
    getBucketDatabase().clear();
    replyToGet(*cb, _sender, 0, lowest_get_timestamp);
    replyToGet(*cb, _sender, 1, highest_get_timestamp);

    // BUCKET_NOT_FOUND is a transient error code which should cause the client
    // to re-send the operation, presumably to the correct distributor the next
    // time.
    // Timestamp of updated doc varies depending on whether fast or safe path
    // was triggered, as the reply is created via different paths.
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: " + std::to_string(expected_response_timestamp) + ") "
              "ReturnCode(BUCKET_NOT_FOUND, Distributor lost "
              "ownership of bucket between executing the read "
              "and write phases of a two-phase update operation)",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, update_fails_if_ownership_changes_between_get_and_put) {
    do_test_ownership_changed_between_gets_and_second_phase(70, 71, 71);
}

TEST_F(TwoPhaseUpdateOperationTest, update_fails_if_ownership_changes_between_get_and_restarted_fast_path_updates) {
    // TODO find a way to test this case properly again since this test now triggers
    // the "replica set has changed" check and does not actually restart with a fast
    // update path.
    do_test_ownership_changed_between_gets_and_second_phase(70, 70, 70); // Timestamps in sync -> Update restart
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_mismatch_fails_with_tas_error) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition("testdoctype1.headerval==120"));

    cb->start(_sender);
    // Newest doc has headerval==110, not 120.
    replyToGet(*cb, _sender, 0, 100);
    replyToGet(*cb, _sender, 1, 110);
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(TEST_AND_SET_CONDITION_FAILED, "
                         "Condition did not match document)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.failures.notfound.getValue(), 0);
    EXPECT_EQ(metrics().updates.failures.test_and_set_failed.getValue(), 1);
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_match_sends_puts_with_updated_doc) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition("testdoctype1.headerval==110"));

    cb->start(_sender);
    replyToGet(*cb, _sender, 0, 100);
    replyToGet(*cb, _sender, 1, 110);
    ASSERT_EQ("Put => 1,Put => 0", _sender.getCommands(true, false, 2));
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_parse_failure_fails_with_illegal_params_error) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition("testdoctype1.san==fran...cisco"));

    cb->start(_sender);
    replyToGet(*cb, _sender, 0, 100);
    replyToGet(*cb, _sender, 1, 110);
    // NOTE: condition is currently not attempted parsed until Gets have been
    // replied to. This may change in the future.
    // XXX reliance on parser/exception error message is very fragile.
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
                          "BucketId(0x0000000000000000), "
                          "timestamp 0, timestamp of updated doc: 0) "
                          "ReturnCode(ILLEGAL_PARAMETERS, "
                                      "Failed to parse test and set condition: "
                                      "syntax error, unexpected . at column 24 when "
                                      "parsing selection 'testdoctype1.san==fran...cisco')",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_unknown_doc_type_fails_with_illegal_params_error) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition("langbein.headerval=1234"));

    cb->start(_sender);
    replyToGet(*cb, _sender, 0, 100);
    replyToGet(*cb, _sender, 1, 110);
    // NOTE: condition is currently not attempted parsed until Gets have been
    // replied to. This may change in the future.
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
                          "BucketId(0x0000000000000000), "
                          "timestamp 0, timestamp of updated doc: 0) "
                          "ReturnCode(ILLEGAL_PARAMETERS, "
                                     "Failed to parse test and set condition: "
                                     "Document type 'langbein' not found at column 1 "
                                     "when parsing selection 'langbein.headerval=1234')",
              _sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_with_missing_doc_and_no_auto_create_fails_with_tas_error) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition("testdoctype1.headerval==120"));

    cb->start(_sender);
    // Both Gets return nothing at all, nothing at all.
    replyToGet(*cb, _sender, 0, 100, false);
    replyToGet(*cb, _sender, 1, 110, false);
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
                          "BucketId(0x0000000000000000), "
                          "timestamp 0, timestamp of updated doc: 0) "
                          "ReturnCode(TEST_AND_SET_CONDITION_FAILED, "
                                     "Document did not exist)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.failures.notfound.getValue(), 0); // Not counted as "not found" failure when TaS is present
    EXPECT_EQ(metrics().updates.failures.test_and_set_failed.getValue(), 1);
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_with_missing_doc_and_auto_create_sends_puts) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cb = sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions()
                    .condition("testdoctype1.headerval==120")
                    .createIfNonExistent(true));

    cb->start(_sender);
    replyToGet(*cb, _sender, 0, 0, false);
    replyToGet(*cb, _sender, 1, 0, false);
    ASSERT_EQ("Put => 1,Put => 0", _sender.getCommands(true, false, 2));

    replyToPut(*cb, _sender, 2);
    replyToPut(*cb, _sender, 3);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
                          "BucketId(0x0000000000000000), "
                          "timestamp 0, timestamp of updated doc: 200000000) "
                          "ReturnCode(NONE)",
              _sender.getLastReply(true));

    EXPECT_EQ(metrics().updates.failures.notfound.getValue(), 0); // Not counted as "not found" failure when we auto create
    EXPECT_EQ(metrics().updates.failures.test_and_set_failed.getValue(), 0);
    EXPECT_EQ(metrics().updates.ok.getValue(), 1);
}

void
TwoPhaseUpdateOperationTest::assertAbortedUpdateReplyWithContextPresent(
        const DistributorMessageSenderStub& closeSender) const
{
    ASSERT_EQ(1, closeSender.replies().size());
    StorageReply::SP reply(closeSender.replies().back());
    ASSERT_EQ(api::MessageType::UPDATE_REPLY, reply->getType());
    ASSERT_EQ(api::ReturnCode::ABORTED, reply->getResult().getResult());
    auto context = reply->getTransportContext(); // Transfers ownership
    ASSERT_TRUE(context.get());
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_close_edge_sends_correct_reply) {
    setup_stripe(1, 1, "storage:1 distributor:1");
    // Only 1 replica; consistent with itself by definition.
    auto cb = sendUpdate("0=1/2/3");
    cb->start(_sender);

    ASSERT_EQ("Update => 0", _sender.getCommands(true));
    // Close the operation. This should generate a single reply that is
    // bound to the original command. We can identify rogue replies by these
    // not having a transport context, as these are unique_ptrs that are
    // moved to the reply upon the first reply construction. Any subsequent or
    // erroneous replies will not have this context attached to themselves.
    DistributorMessageSenderStub closeSender;
    cb->onClose(closeSender);

    assertAbortedUpdateReplyWithContextPresent(closeSender);
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_close_edge_sends_correct_reply) {
    setup_stripe(2, 2, "storage:2 distributor:1");

    auto cb = sendUpdate("0=1/2/3,1=2/3/4"); // Inconsistent replicas.
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    // Closing the operation should now only return an ABORTED reply for
    // the UpdateCommand, _not_ from the nested, pending Get operation (which
    // will implicitly generate an ABORTED reply for the synthesized Get
    // command passed to it).
    DistributorMessageSenderStub closeSender;
    cb->onClose(closeSender);

    assertAbortedUpdateReplyWithContextPresent(closeSender);
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_consistent_get_reply_timestamps_restarts_with_fast_path_if_enabled) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cfg = make_config();
    cfg->set_update_fast_path_restart_enabled(true);
    configure_stripe(cfg);

    auto cb = sendUpdate("0=1/2/3,1=2/3/4"); // Inconsistent replicas.
    cb->start(_sender);

    Timestamp old_timestamp = 500;
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    replyToGet(*cb, _sender, 0, old_timestamp);
    replyToGet(*cb, _sender, 1, old_timestamp);

    ASSERT_EQ("Update => 0,Update => 1", _sender.getCommands(true, false, 2));
    replyToMessage(*cb, _sender, 2, old_timestamp);
    replyToMessage(*cb, _sender, 3, old_timestamp);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 500) "
              "ReturnCode(NONE)",
              _sender.getLastReply(true));

    auto& m = metrics().updates;
    EXPECT_EQ(1, m.fast_path_restarts.getValue());
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_consistent_get_reply_timestamps_does_not_restart_with_fast_path_if_disabled) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cfg = make_config();
    cfg->set_update_fast_path_restart_enabled(false);
    configure_stripe(cfg);

    auto cb = sendUpdate("0=1/2/3,1=2/3/4"); // Inconsistent replicas.
    cb->start(_sender);

    Timestamp old_timestamp = 500;
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    replyToGet(*cb, _sender, 0, old_timestamp);
    replyToGet(*cb, _sender, 1, old_timestamp);

    // Should _not_ be restarted with fast path, as it has been config disabled
    ASSERT_EQ("Put => 1,Put => 0", _sender.getCommands(true, false, 2));

    auto& m = metrics().updates;
    EXPECT_EQ(0, m.fast_path_restarts.getValue());
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_not_restarted_if_replica_set_altered_between_get_send_and_receive) {
    setup_stripe(3, 3, "storage:3 distributor:1");
    auto cfg = make_config();
    cfg->set_update_fast_path_restart_enabled(true);
    configure_stripe(cfg);

    auto cb = sendUpdate("0=1/2/3,1=2/3/4"); // Inconsistent replicas.
    cb->start(_sender);

    // Replica set changes between time of Get requests sent and
    // responses received. This may happen e.g. if concurrent mutations
    // to the same bucket create a new replica. If this happens, we
    // must not send the Update operations verbatim, as they will
    // be started with the _current_ replica set, not the one that
    // was present during the Get request.
    BucketId bucket(0x400000000000cac4); // Always the same in the test.
    addNodesToBucketDB(bucket, "0=1/2/3,1=2/3/4,2=3/3/3");

    Timestamp old_timestamp = 500;
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    replyToGet(*cb, _sender, 0, old_timestamp);
    replyToGet(*cb, _sender, 1, old_timestamp);

    ASSERT_EQ("Put => 1,Put => 2,Put => 0", _sender.getCommands(true, false, 2));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_not_restarted_if_document_not_found_on_a_replica_node) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cfg = make_config();
    cfg->set_update_fast_path_restart_enabled(true);
    configure_stripe(cfg);

    auto cb = sendUpdate("0=1/2/3,1=2/3/4"); // Inconsistent replicas.
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    replyToGet(*cb, _sender, 0, Timestamp(0), false);
    replyToGet(*cb, _sender, 1, Timestamp(500));

    // Should _not_ send Update operations!
    ASSERT_EQ("Put => 1,Put => 0", _sender.getCommands(true, false, 2));
}

// Buckets must be created from scratch by Put operations, updates alone cannot do this.
TEST_F(TwoPhaseUpdateOperationTest, fast_path_not_restarted_if_no_initial_replicas_exist) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cfg = make_config();
    cfg->set_update_fast_path_restart_enabled(true);
    configure_stripe(cfg);

    // No replicas, technically consistent but cannot use fast path.
    auto cb = sendUpdate("", UpdateOptions().createIfNonExistent(true));
    cb->start(_sender);
    ASSERT_EQ("Create bucket => 1,Create bucket => 0,Put => 1,Put => 0",
              _sender.getCommands(true));
}

// The weak consistency config _only_ applies to Get operations initiated directly
// by the client, not those indirectly initiated by the distributor in order to
// fulfill update write-repairs.
TEST_F(TwoPhaseUpdateOperationTest, update_gets_are_sent_with_strong_consistency_even_if_weak_consistency_configured) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cfg = make_config();
    cfg->set_use_weak_internal_read_consistency_for_client_gets(true);
    configure_stripe(cfg);

    auto cb = sendUpdate("0=1/2/3,1=2/3/4"); // Inconsistent replicas.
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    auto& get_cmd = dynamic_cast<const api::GetCommand&>(*_sender.command(0));
    EXPECT_EQ(get_cmd.internal_read_consistency(), api::InternalReadConsistency::Strong);
}

TEST_F(TwoPhaseUpdateOperationTest, operation_is_rejected_in_safe_path_if_feed_is_blocked) {
    set_up_distributor_with_feed_blocked_state();
    auto cb = sendUpdate("0=1/2/3,1=2/3/4"); // Inconsistent replicas to trigger safe path
    cb->start(_sender);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(NO_SPACE, External feed is blocked due to resource exhaustion: full disk)",
              _sender.getLastReply(true));
}

struct ThreePhaseUpdateTest : TwoPhaseUpdateOperationTest {};

TEST_F(ThreePhaseUpdateTest, metadata_only_gets_are_sent_if_3phase_update_enabled) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    {
        auto& get_cmd = dynamic_cast<const api::GetCommand&>(*_sender.command(0));
        EXPECT_EQ(document::NoFields::NAME, get_cmd.getFieldSet());
        EXPECT_EQ(get_cmd.internal_read_consistency(), api::InternalReadConsistency::Weak);
        checkMessageSettingsPropagatedTo(_sender.command(0));
    }
    {
        auto& get_cmd = dynamic_cast<const api::GetCommand&>(*_sender.command(1));
        EXPECT_EQ(document::NoFields::NAME, get_cmd.getFieldSet());
        EXPECT_EQ(get_cmd.internal_read_consistency(), api::InternalReadConsistency::Weak);
        checkMessageSettingsPropagatedTo(_sender.command(1));
    }
}

TEST_F(ThreePhaseUpdateTest, full_document_get_sent_to_replica_with_highest_timestamp) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 0, 1000U);
    reply_to_metadata_get(*cb, _sender, 1, 2000U);

    auto& m = metrics().update_metadata_gets;
    EXPECT_EQ(1, m.ok.getValue()); // Technically tracks an entire operation covering multiple Gets.

    // Node 1 has newest document version at ts=2000
    ASSERT_EQ("Get => 1", _sender.getCommands(true, false, 2));
    {
        auto& get_cmd = dynamic_cast<const api::GetCommand&>(*_sender.command(2));
        EXPECT_EQ(document::AllFields::NAME, get_cmd.getFieldSet());
        EXPECT_EQ(get_cmd.internal_read_consistency(), api::InternalReadConsistency::Strong);
    }
}

TEST_F(ThreePhaseUpdateTest, puts_are_sent_after_receiving_full_document_get) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 0, 2000U);
    reply_to_metadata_get(*cb, _sender, 1, 1000U);
    ASSERT_EQ("Get => 0", _sender.getCommands(true, false, 2));
    replyToGet(*cb, _sender, 2, 2000U);
    ASSERT_EQ("Put => 1,Put => 0", _sender.getCommands(true, false, 3));

    auto& m = metrics().update_gets;
    EXPECT_EQ(1, m.ok.getValue());
}

TEST_F(ThreePhaseUpdateTest, consistent_meta_get_timestamps_can_restart_in_fast_path) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    api::Timestamp old_timestamp(1500);
    reply_to_metadata_get(*cb, _sender, 0, old_timestamp);
    reply_to_metadata_get(*cb, _sender, 1, old_timestamp);

    ASSERT_EQ("Update => 0,Update => 1", _sender.getCommands(true, false, 2));
    replyToMessage(*cb, _sender, 2, old_timestamp);
    replyToMessage(*cb, _sender, 3, old_timestamp);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 1500) "
              "ReturnCode(NONE)",
              _sender.getLastReply(true));

    auto& m = metrics().updates;
    EXPECT_EQ(1, m.fast_path_restarts.getValue());
}

TEST_F(ThreePhaseUpdateTest, fast_path_not_restarted_if_document_not_found_subset_of_replicas) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 0, 0U);
    reply_to_metadata_get(*cb, _sender, 1, 1000U);
    ASSERT_EQ("Get => 1", _sender.getCommands(true, false, 2)); // Not sending updates.
}

TEST_F(ThreePhaseUpdateTest, no_document_found_on_any_replicas_is_considered_consistent) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    api::Timestamp no_document_timestamp(0);
    reply_to_metadata_get(*cb, _sender, 0, no_document_timestamp);
    reply_to_metadata_get(*cb, _sender, 1, no_document_timestamp);

    ASSERT_EQ("Update => 0,Update => 1", _sender.getCommands(true, false, 2));
    auto& m = metrics().updates;
    EXPECT_EQ(1, m.fast_path_restarts.getValue());
}

TEST_F(ThreePhaseUpdateTest, metadata_get_phase_fails_if_any_replicas_return_failure) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 1, 1000U);
    reply_to_metadata_get(*cb, _sender, 0, 0U, api::ReturnCode::INTERNAL_FAILURE);
    ASSERT_EQ("", _sender.getCommands(true, false, 2)); // No further requests sent.

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(ABORTED, One or more metadata Get operations failed; aborting Update)",
              _sender.getLastReply(true));
}

TEST_F(ThreePhaseUpdateTest, update_failed_with_transient_error_code_if_replica_set_changed_after_metadata_gets) {
    setup_stripe(3, 3, "storage:3 distributor:1");
    auto cfg = make_config();
    cfg->set_enable_metadata_only_fetch_phase_for_inconsistent_updates(true);
    configure_stripe(cfg);
    auto cb = sendUpdate("0=1/2/3,1=2/3/4"); // 2 replicas, room for 1 more.
    cb->start(_sender);
    // Add new replica to deterministic test bucket after gets have been sent
    BucketId bucket(0x400000000000cac4); // Always the same in the test.
    addNodesToBucketDB(bucket, "0=1/2/3,1=2/3/4,2=3/3/3");

    Timestamp old_timestamp = 500;
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 0, old_timestamp);
    reply_to_metadata_get(*cb, _sender, 1, old_timestamp);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(BUCKET_NOT_FOUND, Replica sets changed between update phases, client must retry)",
              _sender.getLastReply(true));
}

TEST_F(ThreePhaseUpdateTest, single_full_get_cannot_restart_in_fast_path) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cfg = make_config();
    cfg->set_enable_metadata_only_fetch_phase_for_inconsistent_updates(true);
    cfg->set_update_fast_path_restart_enabled(true);
    configure_stripe(cfg);
    auto cb = sendUpdate("0=1/2/3,1=2/3/4"); // Inconsistent replicas.
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 0, 1000U);
    reply_to_metadata_get(*cb, _sender, 1, 2000U);
    ASSERT_EQ("Get => 1", _sender.getCommands(true, false, 2));
    replyToGet(*cb, _sender, 2, 2000U);
    ASSERT_EQ("Put => 1,Put => 0", _sender.getCommands(true, false, 3));
}

/*
 * We unify checking for changed replica sets and changed bucket ownership by only
 * checking for changed replica sets, thereby avoiding a relatively costly ideal
 * state recomputation that is otherwise redundant. Rationale for why this shall
 * always be safe:
 * - for metadata gets to be sent at all, there must be at least one replica
 *   under the target bucket subtree
 *   - if there are no replicas, the bucket is implicitly considered inconsistent,
 *     triggering safe path
 *   - since there were no replicas initially, the safe path will _not_ restart in
 *     fast path
 *   - the safe path will perform the update locally and start a PutOperation,
 *     implicitly creating new replicas
 *     - this happens in the same execution context as starting the update operation itself,
 *       consequently ownership in DB cannot have changed concurrently
 * - when the a state transition happens where a distributor loses ownership of
 *   a bucket, it will always immediately purge it from its DB
 *   - this means that the replica set will inherently change
 *
 * It is technically possible to have an ABA situation where, in the course of
 * an operation's lifetime, a distributor goes from owning a bucket to not
 * owning it, back to owning it again. Although extremely unlikely to happen,
 * it doesn't matter since the bucket info from the resulting mutations will
 * be applied to the current state of the database anyway.
 */
TEST_F(ThreePhaseUpdateTest, update_aborted_if_ownership_changed_between_gets_and_fast_restart_update) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    // See do_test_ownership_changed_between_gets_and_second_phase() for more in-depth
    // comments on why this particular cluster state is used.
    enable_cluster_state("storage:2 distributor:1 .0.s:d");
    getBucketDatabase().clear();
    reply_to_metadata_get(*cb, _sender, 0, api::Timestamp(70));
    reply_to_metadata_get(*cb, _sender, 1, api::Timestamp(71));

    // As mentioned in the above comments, ownership changes trigger
    // on the replicas changed test instead of an explicit ownership
    // change test.
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(BUCKET_NOT_FOUND, Replica sets changed between update phases, client must retry)",
              _sender.getLastReply(true));
}

TEST_F(ThreePhaseUpdateTest, safe_mode_is_implicitly_triggered_if_no_replicas_exist) {
    setup_stripe(1, 1, "storage:1 distributor:1");
    auto cfg = make_config();
    cfg->set_enable_metadata_only_fetch_phase_for_inconsistent_updates(true);
    configure_stripe(cfg);
    auto cb = sendUpdate("", UpdateOptions().createIfNonExistent(true));
    cb->start(_sender);

    ASSERT_EQ("CreateBucketCommand(BucketId(0x400000000000cac4), active) "
              "Reasons to start:  => 0,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, "
              "timestamp 200000000, size 60) => 0",
              _sender.getCommands(true, true));
}

TEST_F(ThreePhaseUpdateTest, metadata_gets_propagate_mbus_trace_to_reply) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 1, 1000U);
    reply_to_metadata_get(*cb, _sender, 0, 0U, api::ReturnCode::INTERNAL_FAILURE,
                          "'ello 'ello what's all this then?");
    ASSERT_EQ("", _sender.getCommands(true, false, 2));
    ASSERT_EQ("Update Reply", _sender.getLastReply(false));

    std::string trace(_sender.replies().back()->getTrace().toString());
    ASSERT_THAT(trace, HasSubstr("'ello 'ello what's all this then?"));
}

TEST_F(ThreePhaseUpdateTest, single_get_mbus_trace_is_propagated_to_reply) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 0, 0U);
    reply_to_metadata_get(*cb, _sender, 1, 1000U);
    ASSERT_EQ("Get => 1", _sender.getCommands(true, false, 2));
    replyToGet(*cb, _sender, 2, 2000U, false, api::ReturnCode::INTERNAL_FAILURE,
               "it is me, Leclerc! *lifts glasses*");
    ASSERT_EQ("Update Reply", _sender.getLastReply(false));

    std::string trace(_sender.replies().back()->getTrace().toString());
    ASSERT_THAT(trace, HasSubstr("it is me, Leclerc! *lifts glasses*"));
}

TEST_F(ThreePhaseUpdateTest, single_full_get_reply_received_after_close_is_no_op) {
    auto cb = set_up_2_inconsistent_replicas_and_start_update();
    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 0, 0U);
    reply_to_metadata_get(*cb, _sender, 1, 1000U);
    ASSERT_EQ("Get => 1", _sender.getCommands(true, false, 2));
    cb->onClose(_sender);
    ASSERT_EQ("Update Reply", _sender.getLastReply(false));
    // Operation closed prior to receiving Get. Note that we should not really get
    // into this situation since the owner of the operation itself should clear
    // any mappings associating the reply with the operation, but ensure we handle
    // it gracefully anyway.
    replyToGet(*cb, _sender, 2, 2000U);
    ASSERT_EQ("", _sender.getCommands(true, false, 3)); // Nothing new sent.
}

TEST_F(ThreePhaseUpdateTest, single_full_get_tombstone_is_no_op_without_auto_create) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cfg = make_config();
    cfg->set_enable_metadata_only_fetch_phase_for_inconsistent_updates(true);
    cfg->set_update_fast_path_restart_enabled(true);
    configure_stripe(cfg);
    auto cb = sendUpdate("0=1/2/3,1=2/3/4");
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 0, 1000U);
    reply_to_metadata_get(*cb, _sender, 1, 2000U);
    ASSERT_EQ("Get => 1", _sender.getCommands(true, false, 2));
    reply_to_get_with_tombstone(*cb, _sender, 2, 2000U);
    // No puts should be sent, as Get returned a tombstone and no auto-create is set.
    ASSERT_EQ("", _sender.getCommands(true, false, 3));
    // Nothing was updated.
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(NONE)",
              _sender.getLastReply(true));
}

TEST_F(ThreePhaseUpdateTest, single_full_get_tombstone_sends_puts_with_auto_create) {
    setup_stripe(2, 2, "storage:2 distributor:1");
    auto cfg = make_config();
    cfg->set_enable_metadata_only_fetch_phase_for_inconsistent_updates(true);
    cfg->set_update_fast_path_restart_enabled(true);
    configure_stripe(cfg);
    auto cb = sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().createIfNonExistent(true));
    cb->start(_sender);

    ASSERT_EQ("Get => 0,Get => 1", _sender.getCommands(true));
    reply_to_metadata_get(*cb, _sender, 0, 1000U);
    reply_to_metadata_get(*cb, _sender, 1, 2000U);
    ASSERT_EQ("Get => 1", _sender.getCommands(true, false, 2));
    reply_to_get_with_tombstone(*cb, _sender, 2, 2000U);
    // Tombstone is treated as Not Found in this case, which auto-creates a new
    // document version locally and pushes it out with Puts as expected.
    ASSERT_EQ("Put => 1,Put => 0", _sender.getCommands(true, false, 3));
}

// XXX currently differs in behavior from content nodes in that updates for
// document IDs without explicit doctypes will _not_ be auto-failed on the
// distributor.

// XXX: test case where update reply has been sent but callback still
// has pending messages (e.g. n-of-m case).

} // storage::distributor
