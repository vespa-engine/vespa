// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/helper/configgetter.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <iomanip>
#include <tests/common/dummystoragelink.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/operations/external/twophaseupdateoperation.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

namespace storage::distributor {

using document::test::makeDocumentBucket;
using config::ConfigGetter;
using document::DocumenttypesConfig;
using namespace document;
using namespace storage;
using namespace storage::distributor;
using namespace storage::api;
using namespace storage::lib;
using namespace ::testing;

struct TwoPhaseUpdateOperationTest : Test, DistributorTestUtil {
    document::TestDocRepo _testRepo;
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType* _doc_type;

    TwoPhaseUpdateOperationTest();
    ~TwoPhaseUpdateOperationTest();

    void checkMessageSettingsPropagatedTo(
        const api::StorageCommand::SP& msg) const;

    std::string getUpdatedValueFromLastPut(DistributorMessageSenderStub&);

    void SetUp() override {
        _repo = _testRepo.getTypeRepoSp();
        _doc_type = _repo->getDocumentType("testdoctype1");
        createLinks();
        setTypeRepo(_repo);
        getClock().setAbsoluteTimeInSeconds(200);
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
        document::FieldUpdate fup(_doc_type->getField("headerval"));
        fup.addUpdate(ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 10));
        update->addUpdate(fup);
    } else {
        // Create an update to a different doctype than the one returned as
        // part of the Get. Just a sneaky way to force an eval error.
        auto* badDocType = _repo->getDocumentType("testdoctype2");
        update = std::make_shared<document::DocumentUpdate>(
                *_repo, *badDocType,
                document::DocumentId("id:ns:" + _doc_type->getName() + "::1"));
        document::FieldUpdate fup(badDocType->getField("onlyinchild"));
        fup.addUpdate(ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 10));
        update->addUpdate(fup);
    }
    update->setCreateIfNonExistent(options._createIfNonExistent);

    document::BucketId id = getExternalOperationHandler().getBucketId(update->getId());
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
    msg->setTimeout(6789);
    msg->setPriority(99);
    if (options._timestampToUpdate) {
        msg->setOldTimestamp(options._timestampToUpdate);
    }
    msg->setCondition(options._condition);
    msg->setTransportContext(std::make_unique<DummyTransportContext>());

    ExternalOperationHandler& handler = getExternalOperationHandler();
    return std::make_shared<TwoPhaseUpdateOperation>(
            handler, getDistributorBucketSpace(), msg, getDistributor().getMetrics());
}

TEST_F(TwoPhaseUpdateOperationTest, simple) {
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 90) ReturnCode(NONE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, non_existing) {
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate(""));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) ReturnCode(NONE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, update_failed) {
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90, api::ReturnCode::INTERNAL_FAILURE);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(INTERNAL_FAILURE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps) {
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    replyToMessage(*cb, sender, 1, 110);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 1", sender.getLastCommand(true));

    replyToGet(*cb, sender, 2, 110);

    ASSERT_EQ("Update => 0,Update => 1,Get => 1,Put => 1,Put => 0", sender.getCommands(true));

    ASSERT_TRUE(sender.replies().empty());

    replyToPut(*cb, sender, 3);
    replyToPut(*cb, sender, 4);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
              "(best node 1)) ReturnCode(NONE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_not_found) {
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    replyToMessage(*cb, sender, 1, 110);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 1", sender.getLastCommand(true));
    ASSERT_TRUE(sender.replies().empty());

    replyToGet(*cb, sender, 2, 110, false);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
              "(best node 1)) ReturnCode(INTERNAL_FAILURE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_update_error) {
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    ASSERT_TRUE(sender.replies().empty());
    replyToMessage(*cb, sender, 1, 110, api::ReturnCode::IO_FAILURE);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 90) "
              "ReturnCode(IO_FAILURE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_get_error) {
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    replyToMessage(*cb, sender, 1, 110);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 1",
              sender.getLastCommand(true));

    ASSERT_TRUE(sender.replies().empty());
    replyToGet(*cb, sender, 2, 110, false, api::ReturnCode::IO_FAILURE);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
              "(best node 1)) ReturnCode(IO_FAILURE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_put_error) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    replyToMessage(*cb, sender, 1, 110);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 1",
              sender.getLastCommand(true));

    replyToGet(*cb, sender, 2, 110);

    ASSERT_EQ("Update => 0,Update => 1,Get => 1,Put => 1,Put => 0",
              sender.getCommands(true));

    replyToPut(*cb, sender, 3, api::ReturnCode::IO_FAILURE);
    ASSERT_TRUE(sender.replies().empty());
    replyToPut(*cb, sender, 4);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
              "(best node 1)) ReturnCode(IO_FAILURE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_put_not_started) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    replyToMessage(*cb, sender, 1, 110);

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 1",
              sender.getLastCommand(true));
    checkMessageSettingsPropagatedTo(sender.commands().back());

    enableDistributorClusterState("storage:0 distributor:1");
    ASSERT_TRUE(sender.replies().empty());
    replyToGet(*cb, sender, 2, 110);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
              "(best node 1)) ReturnCode(NOT_CONNECTED, "
              "Can't store document: No storage nodes available)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_inconsistent_timestamps_inconsistent_split) {
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=1/2/3",
                       UpdateOptions().makeInconsistentSplit(true)));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    std::string wanted("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 0,"
                       "Get(BucketId(0x440000000000cac4), id:ns:testdoctype1::1) => 0");

    std::string text = sender.getCommands(true, true);
    ASSERT_EQ(wanted, text);

    replyToGet(*cb, sender, 0, 90);
    replyToGet(*cb, sender, 1, 120);

    ASSERT_EQ("Put(BucketId(0x440000000000cac4), id:ns:testdoctype1::1, "
              "timestamp 200000000, size 60) => 1,"
              "Put(BucketId(0x440000000000cac4), id:ns:testdoctype1::1, "
              "timestamp 200000000, size 60) => 0",
              sender.getCommands(true, true, 2));

    replyToPut(*cb, sender, 2);
    ASSERT_TRUE(sender.replies().empty());
    replyToPut(*cb, sender, 3);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 120) "
              "ReturnCode(NONE)",
              sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::checkMessageSettingsPropagatedTo(
        const api::StorageCommand::SP& msg) const
{
    // Settings set in sendUpdate().
    EXPECT_EQ(6, msg->getTrace().getLevel());
    EXPECT_EQ(6789, msg->getTimeout());
    EXPECT_EQ(99, msg->getPriority());
}

TEST_F(TwoPhaseUpdateOperationTest, fast_path_propagates_message_settings_to_update) {
    setupDistributor(1, 1, "storage:1 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0", sender.getCommands(true));

    StorageCommand::SP msg(sender.commands().back());
    checkMessageSettingsPropagatedTo(msg);
}

TEST_F(TwoPhaseUpdateOperationTest, n_of_m) {
    setupDistributor(2, 2, "storage:2 distributor:1", 1);

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true));

    ASSERT_TRUE(sender.replies().empty());
    replyToMessage(*cb, sender, 0, 90);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 90) ReturnCode(NONE)",
              sender.getLastReply(true));

    replyToMessage(*cb, sender, 1, 123);
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
    setupDistributor(3, 3, "storage:3 distributor:1");
    // 0,1 in sync. 2 out of sync.
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4"));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 0,"
              "Get(BucketId(0x400000000000cac4), id:ns:testdoctype1::1) => 2",
              sender.getCommands(true, true));
    replyToGet(*cb, sender, 0, 50);
    replyToGet(*cb, sender, 1, 70);

    ASSERT_EQ("Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 1,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 2,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 0",
              sender.getCommands(true, true, 2));
    // Make sure Put contains an updated document (+10 arith. update on field
    // whose value equals gotten timestamp). In this case we want 70 -> 80.
    ASSERT_EQ("80", getUpdatedValueFromLastPut(sender));

    replyToPut(*cb, sender, 2);
    replyToPut(*cb, sender, 3);
    ASSERT_TRUE(sender.replies().empty());
    replyToPut(*cb, sender, 4);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 70) "
              "ReturnCode(NONE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, create_if_non_existent_creates_document_if_all_empty_gets) {
    setupDistributor(3, 3, "storage:3 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4",
                       UpdateOptions().createIfNonExistent(true)));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Get => 0,Get => 2", sender.getCommands(true));
    replyToGet(*cb, sender, 0, 0, false);
    replyToGet(*cb, sender, 1, 0, false);
    // Since create-if-non-existent is set, distributor should create doc from
    // scratch.
    ASSERT_EQ("Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 1,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 2,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, timestamp 200000000, size 60) => 0",
              sender.getCommands(true, true, 2));

    ASSERT_EQ("10", getUpdatedValueFromLastPut(sender));

    replyToPut(*cb, sender, 2);
    replyToPut(*cb, sender, 3);
    ASSERT_TRUE(sender.replies().empty());
    replyToPut(*cb, sender, 4);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 200000000) "
              "ReturnCode(NONE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, update_fails_if_safe_path_has_failed_put) {
    setupDistributor(3, 3, "storage:3 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4",
                       UpdateOptions().createIfNonExistent(true)));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Get => 0,Get => 2", sender.getCommands(true));
    replyToGet(*cb, sender, 0, 0, false);
    replyToGet(*cb, sender, 1, 0, false);
    // Since create-if-non-existent is set, distributor should create doc from
    // scratch.
    ASSERT_EQ("Put => 1,Put => 2,Put => 0", sender.getCommands(true, false, 2));

    replyToPut(*cb, sender, 2);
    replyToPut(*cb, sender, 3);
    ASSERT_TRUE(sender.replies().empty());
    replyToPut(*cb, sender, 4, api::ReturnCode::IO_FAILURE);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 200000000) "
              "ReturnCode(IO_FAILURE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, update_fails_if_safe_path_gets_fail) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4",
                       UpdateOptions().createIfNonExistent(true)));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Get => 0,Get => 1", sender.getCommands(true));
    replyToGet(*cb, sender, 0, 0, false, api::ReturnCode::IO_FAILURE);
    ASSERT_TRUE(sender.replies().empty());
    replyToGet(*cb, sender, 1, 0, false, api::ReturnCode::IO_FAILURE);
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(IO_FAILURE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, update_fails_if_apply_throws_exception) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    // Create update for wrong doctype which will fail the update.
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().withError()));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Get => 0,Get => 1", sender.getCommands(true));
    replyToGet(*cb, sender, 0, 50);
    ASSERT_TRUE(sender.replies().empty());
    replyToGet(*cb, sender, 1, 70);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 70) "
              "ReturnCode(INTERNAL_FAILURE, Can not apply a "
              "\"testdoctype2\" document update to a "
              "\"testdoctype1\" document.)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, non_existing_with_auto_create) {
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("", UpdateOptions().createIfNonExistent(true)));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("CreateBucketCommand(BucketId(0x400000000000cac4), active) "
              "Reasons to start:  => 0,"
              "Put(BucketId(0x400000000000cac4), id:ns:testdoctype1::1, "
              "timestamp 200000000, size 60) => 0",
              sender.getCommands(true, true));

    ASSERT_EQ("10", getUpdatedValueFromLastPut(sender));

    replyToCreateBucket(*cb, sender, 0);
    ASSERT_TRUE(sender.replies().empty());
    replyToPut(*cb, sender, 1);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 200000000) "
              "ReturnCode(NONE)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_fails_update_when_mismatching_timestamp_constraint) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4",
                       UpdateOptions().timestampToUpdate(1234)));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Get => 0,Get => 1", sender.getCommands(true));
    replyToGet(*cb, sender, 0, 100);
    ASSERT_TRUE(sender.replies().empty());
    replyToGet(*cb, sender, 1, 110);
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(NONE, No document with requested "
                         "timestamp found)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_update_propagates_message_settings_to_gets_and_puts) {
    setupDistributor(3, 3, "storage:3 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Get => 0,Get => 2", sender.getCommands(true));
    checkMessageSettingsPropagatedTo(sender.command(0));
    checkMessageSettingsPropagatedTo(sender.command(1));
    replyToGet(*cb, sender, 0, 50);
    replyToGet(*cb, sender, 1, 70);
    ASSERT_EQ("Put => 1,Put => 2,Put => 0", sender.getCommands(true, false, 2));
    checkMessageSettingsPropagatedTo(sender.command(2));
    checkMessageSettingsPropagatedTo(sender.command(3));
    checkMessageSettingsPropagatedTo(sender.command(4));
    replyToPut(*cb, sender, 2);
    replyToPut(*cb, sender, 3);
    replyToPut(*cb, sender, 4);
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_propagates_mbus_traces_from_replies) {
    setupDistributor(3, 3, "storage:3 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Get => 0,Get => 2", sender.getCommands(true));
    replyToGet(*cb, sender, 0, 50, true, api::ReturnCode::OK, "hello earthlings");
    replyToGet(*cb, sender, 1, 70);
    ASSERT_EQ("Put => 1,Put => 2,Put => 0", sender.getCommands(true, false, 2));
    replyToPut(*cb, sender, 2, api::ReturnCode::OK, "fooo");
    replyToPut(*cb, sender, 3, api::ReturnCode::OK, "baaa");
    ASSERT_TRUE(sender.replies().empty());
    replyToPut(*cb, sender, 4);
    
    ASSERT_EQ("Update Reply", sender.getLastReply(false));

    std::string trace(sender.replies().back()->getTrace().toString());
    ASSERT_THAT(trace, HasSubstr("hello earthlings"));
    ASSERT_THAT(trace, HasSubstr("fooo"));
    ASSERT_THAT(trace, HasSubstr("baaa"));
}

void TwoPhaseUpdateOperationTest::do_test_ownership_changed_between_gets_and_second_phase(
        Timestamp lowest_get_timestamp,
        Timestamp highest_get_timestamp,
        Timestamp expected_response_timestamp)
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    // Update towards inconsistent bucket invokes safe path.
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=2/3/4"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Get => 0,Get => 1", sender.getCommands(true));

    // Alter cluster state so that distributor is now down (technically the
    // entire cluster is down in this state, but this should not matter). In
    // this new state, the distributor no longer owns the bucket in question
    // and the operation should thus be failed. We must not try to send Puts
    // to a bucket we no longer own.
    enableDistributorClusterState("storage:2 distributor:1 .0.s:d");
    getBucketDatabase().clear();
    replyToGet(*cb, sender, 0, lowest_get_timestamp);
    replyToGet(*cb, sender, 1, highest_get_timestamp);

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
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, update_fails_if_ownership_changes_between_get_and_put) {
    do_test_ownership_changed_between_gets_and_second_phase(70, 71, 71);
}

TEST_F(TwoPhaseUpdateOperationTest, update_fails_if_ownership_changes_between_get_and_restarted_fast_path_updates) {
    do_test_ownership_changed_between_gets_and_second_phase(70, 70, 0); // Timestamps in sync -> Update restart
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_mismatch_fails_with_tas_error) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition(
                            "testdoctype1.headerval==120")));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    // Newest doc has headerval==110, not 120.
    replyToGet(*cb, sender, 0, 100);
    replyToGet(*cb, sender, 1, 110);
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 0) "
              "ReturnCode(TEST_AND_SET_CONDITION_FAILED, "
                         "Condition did not match document)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_match_sends_puts_with_updated_doc) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition(
                            "testdoctype1.headerval==110")));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    replyToGet(*cb, sender, 0, 100);
    replyToGet(*cb, sender, 1, 110);
    ASSERT_EQ("Put => 1,Put => 0", sender.getCommands(true, false, 2));
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_parse_failure_fails_with_illegal_params_error) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition(
                            "testdoctype1.san==fran...cisco")));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    replyToGet(*cb, sender, 0, 100);
    replyToGet(*cb, sender, 1, 110);
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
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_unknown_doc_type_fails_with_illegal_params_error) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition(
                            "langbein.headerval=1234")));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    replyToGet(*cb, sender, 0, 100);
    replyToGet(*cb, sender, 1, 110);
    // NOTE: condition is currently not attempted parsed until Gets have been
    // replied to. This may change in the future.
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
                          "BucketId(0x0000000000000000), "
                          "timestamp 0, timestamp of updated doc: 0) "
                          "ReturnCode(ILLEGAL_PARAMETERS, "
                                     "Failed to parse test and set condition: "
                                     "Document type 'langbein' not found at column 1 "
                                     "when parsing selection 'langbein.headerval=1234')",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_with_missing_doc_and_no_auto_create_fails_with_tas_error) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition(
                            "testdoctype1.headerval==120")));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    // Both Gets return nothing at all, nothing at all.
    replyToGet(*cb, sender, 0, 100, false);
    replyToGet(*cb, sender, 1, 110, false);
    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
                          "BucketId(0x0000000000000000), "
                          "timestamp 0, timestamp of updated doc: 0) "
                          "ReturnCode(TEST_AND_SET_CONDITION_FAILED, "
                                     "Document did not exist)",
              sender.getLastReply(true));
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_condition_with_missing_doc_and_auto_create_sends_puts) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions()
                    .condition("testdoctype1.headerval==120")
                    .createIfNonExistent(true)));

    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    replyToGet(*cb, sender, 0, 100, false);
    replyToGet(*cb, sender, 1, 110, false);
    ASSERT_EQ("Put => 1,Put => 0", sender.getCommands(true, false, 2));
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
    setupDistributor(1, 1, "storage:1 distributor:1");
    // Only 1 replica; consistent with itself by definition.
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0", sender.getCommands(true));
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
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=2/3/4")); // Inconsistent replicas.
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Get => 0,Get => 1", sender.getCommands(true));
    // Closing the operation should now only return an ABORTED reply for
    // the UpdateCommand, _not_ from the nested, pending Get operation (which
    // will implicitly generate an ABORTED reply for the synthesized Get
    // command passed to it).
    DistributorMessageSenderStub closeSender;
    cb->onClose(closeSender);

    assertAbortedUpdateReplyWithContextPresent(closeSender);
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_consistent_get_reply_timestamps_restarts_with_fast_path_if_enabled) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    getConfig().set_update_fast_path_restart_enabled(true);

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=2/3/4")); // Inconsistent replicas.
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    Timestamp old_timestamp = 500;
    ASSERT_EQ("Get => 0,Get => 1", sender.getCommands(true));
    replyToGet(*cb, sender, 0, old_timestamp);
    replyToGet(*cb, sender, 1, old_timestamp);

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true, false, 2));
    replyToMessage(*cb, sender, 2, old_timestamp);
    replyToMessage(*cb, sender, 3, old_timestamp);

    EXPECT_EQ("UpdateReply(id:ns:testdoctype1::1, "
              "BucketId(0x0000000000000000), "
              "timestamp 0, timestamp of updated doc: 500) "
              "ReturnCode(NONE)",
              sender.getLastReply(true));

    auto& metrics = getDistributor().getMetrics().updates[documentapi::LoadType::DEFAULT];
    EXPECT_EQ(1, metrics.fast_path_restarts.getValue());
}

TEST_F(TwoPhaseUpdateOperationTest, safe_path_consistent_get_reply_timestamps_does_not_restart_with_fast_path_if_disabled) {
    setupDistributor(2, 2, "storage:2 distributor:1");
    getConfig().set_update_fast_path_restart_enabled(false);

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=2/3/4")); // Inconsistent replicas.
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    Timestamp old_timestamp = 500;
    ASSERT_EQ("Get => 0,Get => 1", sender.getCommands(true));
    replyToGet(*cb, sender, 0, old_timestamp);
    replyToGet(*cb, sender, 1, old_timestamp);

    // Should _not_ be restarted with fast path, as it has been config disabled
    ASSERT_EQ("Put => 1,Put => 0", sender.getCommands(true, false, 2));

    auto& metrics = getDistributor().getMetrics().updates[documentapi::LoadType::DEFAULT];
    EXPECT_EQ(0, metrics.fast_path_restarts.getValue());
}

// XXX currently differs in behavior from content nodes in that updates for
// document IDs without explicit doctypes will _not_ be auto-failed on the
// distributor.

// XXX: test case where update reply has been sent but callback still
// has pending messages (e.g. n-of-m case).

} // storage::distributor
