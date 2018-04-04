// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/helper/configgetter.h>
#include <cppunit/extensions/HelperMacros.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <iomanip>
#include <tests/common/dummystoragelink.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/operations/external/twophaseupdateoperation.h>
#include <vespa/storageapi/message/batch.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/distributor.h>

using document::test::makeDocumentBucket;

namespace storage {
namespace distributor {

using std::shared_ptr;
using config::ConfigGetter;
using document::DocumenttypesConfig;
using namespace document;
using namespace storage;
using namespace storage::distributor;
using namespace storage::api;
using namespace storage::lib;

using namespace std::literals::string_literals;

class TwoPhaseUpdateOperationTest : public CppUnit::TestFixture,
                                    public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(TwoPhaseUpdateOperationTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testNonExisting);
    CPPUNIT_TEST(testUpdateFailed);
    CPPUNIT_TEST(testFastPathInconsistentTimestamps);
    CPPUNIT_TEST(testFastPathInconsistentTimestampsNotFound);
    CPPUNIT_TEST(testFastPathInconsistentTimestampsUpdateError);
    CPPUNIT_TEST(testFastPathInconsistentTimestampsGetError);
    CPPUNIT_TEST(testFastPathInconsistentTimestampsPutError);
    CPPUNIT_TEST(testFastPathInconsistentTimestampsPutNotStarted);
    CPPUNIT_TEST(testFastPathInconsistentTimestampsInconsistentSplit);
    CPPUNIT_TEST(testFastPathPropagatesMessageSettingsToUpdate);
    CPPUNIT_TEST(testNofM);
    CPPUNIT_TEST(testSafePathUpdatesNewestReceivedDocument);
    CPPUNIT_TEST(testCreateIfNonExistentCreatesDocumentIfAllEmptyGets);
    CPPUNIT_TEST(testUpdateFailsIfSafePathHasFailedPut);
    CPPUNIT_TEST(testUpdateFailsIfSafePathGetsFail);
    CPPUNIT_TEST(testUpdateFailsIfApplyThrowsException);
    CPPUNIT_TEST(testNonExistingWithAutoCreate);
    CPPUNIT_TEST(testSafePathFailsUpdateWhenMismatchingTimestampConstraint);
    CPPUNIT_TEST(testSafePathUpdatePropagatesMessageSettingsToGetsAndPuts);
    CPPUNIT_TEST(testSafePathPropagatesMbusTracesFromReplies);
    CPPUNIT_TEST(testUpdateFailsIfOwnershipChangesBetweenGetAndPut);
    CPPUNIT_TEST(testSafePathConditionMismatchFailsWithTasError);
    CPPUNIT_TEST(testSafePathConditionMatchSendsPutsWithUpdatedDoc);
    CPPUNIT_TEST(testSafePathConditionParseFailureFailsWithIllegalParamsError);
    CPPUNIT_TEST(testSafePathConditonUnknownDocTypeFailsWithIllegalParamsError);
    CPPUNIT_TEST(testSafePathConditionWithMissingDocFailsWithTasError);
    CPPUNIT_TEST(testFastPathCloseEdgeSendsCorrectReply);
    CPPUNIT_TEST(testSafePathCloseEdgeSendsCorrectReply);
    CPPUNIT_TEST_SUITE_END();

    document::TestDocRepo _testRepo;
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType* _doc_type;

protected:
    void testSimple();
    void testNonExisting();
    void testUpdateFailed();
    void testFastPathInconsistentTimestamps();
    void testFastPathInconsistentTimestampsNotFound();
    void testFastPathInconsistentTimestampsUpdateError();
    void testFastPathInconsistentTimestampsGetError();
    void testFastPathInconsistentTimestampsPutError();
    void testFastPathInconsistentTimestampsPutNotStarted();
    void testFastPathInconsistentTimestampsInconsistentSplit();
    void testFastPathPropagatesMessageSettingsToUpdate();
    void testNofM();
    void testSafePathUpdatesNewestReceivedDocument();
    void testCreateIfNonExistentCreatesDocumentIfAllEmptyGets();
    void testUpdateFailsIfSafePathHasFailedPut();
    void testUpdateFailsIfSafePathGetsFail();
    void testUpdateFailsIfApplyThrowsException();
    void testNonExistingWithAutoCreate();
    void testSafePathFailsUpdateWhenMismatchingTimestampConstraint();
    void testSafePathUpdatePropagatesMessageSettingsToGetsAndPuts();
    void testSafePathPropagatesMbusTracesFromReplies();
    void testUpdateFailsIfOwnershipChangesBetweenGetAndPut();
    void testSafePathConditionMismatchFailsWithTasError();
    void testSafePathConditionMatchSendsPutsWithUpdatedDoc();
    void testSafePathConditionParseFailureFailsWithIllegalParamsError();
    void testSafePathConditonUnknownDocTypeFailsWithIllegalParamsError();
    void testSafePathConditionWithMissingDocFailsWithTasError();
    void testFastPathCloseEdgeSendsCorrectReply();
    void testSafePathCloseEdgeSendsCorrectReply();

    void checkMessageSettingsPropagatedTo(
        const api::StorageCommand::SP& msg) const;

    std::string getUpdatedValueFromLastPut(MessageSenderStub&);
public:
    void setUp() override {
        _repo = _testRepo.getTypeRepoSp();
        _doc_type = _repo->getDocumentType("testdoctype1");
        createLinks();
        setTypeRepo(_repo);
        getClock().setAbsoluteTimeInSeconds(200);
    }

    void tearDown() override {
        close();
    }

    void replyToMessage(Operation& callback,
                        MessageSenderStub& sender,
                        uint32_t index,
                        uint64_t oldTimestamp,
                        api::ReturnCode::Result result = api::ReturnCode::OK);

    void replyToPut(
            Operation& callback,
            MessageSenderStub& sender,
            uint32_t index,
            api::ReturnCode::Result result = api::ReturnCode::OK,
            const std::string& traceMsg = "");

    void replyToCreateBucket(
            Operation& callback,
            MessageSenderStub& sender,
            uint32_t index,
            api::ReturnCode::Result result = api::ReturnCode::OK);

    void replyToGet(
            Operation& callback,
            MessageSenderStub& sender,
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
            const MessageSenderStub& closeSender) const;

};

CPPUNIT_TEST_SUITE_REGISTRATION(TwoPhaseUpdateOperationTest);

void
TwoPhaseUpdateOperationTest::replyToMessage(
        Operation& callback,
        MessageSenderStub& sender,
        uint32_t index,
        uint64_t oldTimestamp,
        api::ReturnCode::Result result)
{
    std::shared_ptr<api::StorageMessage> msg2 = sender.commands.at(index);
    UpdateCommand& updatec = dynamic_cast<UpdateCommand&>(*msg2);
    std::unique_ptr<api::StorageReply> reply(updatec.makeReply());
    static_cast<api::UpdateReply*>(reply.get())->setOldTimestamp(oldTimestamp);
    reply->setResult(api::ReturnCode(result, ""));

    callback.receive(sender,
                     std::shared_ptr<StorageReply>(reply.release()));
}

void
TwoPhaseUpdateOperationTest::replyToPut(
        Operation& callback,
        MessageSenderStub& sender,
        uint32_t index,
        api::ReturnCode::Result result,
        const std::string& traceMsg)
{
    std::shared_ptr<api::StorageMessage> msg2 = sender.commands.at(index);
    PutCommand& putc = dynamic_cast<PutCommand&>(*msg2);
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
        MessageSenderStub& sender,
        uint32_t index,
        api::ReturnCode::Result result)
{
    std::shared_ptr<api::StorageMessage> msg2 = sender.commands.at(index);
    CreateBucketCommand& putc = dynamic_cast<CreateBucketCommand&>(*msg2);
    std::unique_ptr<api::StorageReply> reply(putc.makeReply());
    reply->setResult(api::ReturnCode(result, ""));
    callback.receive(sender,
                     std::shared_ptr<StorageReply>(reply.release()));
}

void
TwoPhaseUpdateOperationTest::replyToGet(
        Operation& callback,
        MessageSenderStub& sender,
        uint32_t index,
        uint64_t oldTimestamp,
        bool haveDocument,
        api::ReturnCode::Result result,
        const std::string& traceMsg)
{
    const api::GetCommand& get(
            static_cast<const api::GetCommand&>(*sender.commands.at(index)));
    std::shared_ptr<api::StorageReply> reply;

    if (haveDocument) {
        auto doc(std::make_shared<Document>(
                *_doc_type, DocumentId(DocIdString("test", "test"))));
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
                *_doc_type,
                document::DocumentId(document::DocIdString("test", "test")));
        document::FieldUpdate fup(_doc_type->getField("headerval"));
        fup.addUpdate(ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 10));
        update->addUpdate(fup);
    } else {
        // Create an update to a different doctype than the one returned as
        // part of the Get. Just a sneaky way to force an eval error.
        auto* badDocType = _repo->getDocumentType("testdoctype2");
        update = std::make_shared<document::DocumentUpdate>(
                *badDocType,
                document::DocumentId(document::DocIdString("test", "test")));
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


void
TwoPhaseUpdateOperationTest::testSimple()
{
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 90) ReturnCode(NONE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testNonExisting()
{
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate(""));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 0) ReturnCode(NONE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testUpdateFailed()
{
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90, api::ReturnCode::INTERNAL_FAILURE);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 0) "
                        "ReturnCode(INTERNAL_FAILURE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testFastPathInconsistentTimestamps()
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    replyToMessage(*cb, sender, 1, 110);

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000008b13), doc:test:test) => 1"),
            sender.getLastCommand(true));

    replyToGet(*cb, sender, 2, 110);

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1,Get => 1,Put => 1,Put => 0"),
            sender.getCommands(true));

    CPPUNIT_ASSERT(sender.replies.empty());

    replyToPut(*cb, sender, 3);
    replyToPut(*cb, sender, 4);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
                        "(best node 1)) ReturnCode(NONE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testFastPathInconsistentTimestampsNotFound()
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    replyToMessage(*cb, sender, 1, 110);

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000008b13), doc:test:test) => 1"),
            sender.getLastCommand(true));
    CPPUNIT_ASSERT(sender.replies.empty());

    replyToGet(*cb, sender, 2, 110, false);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
                        "(best node 1)) ReturnCode(INTERNAL_FAILURE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testFastPathInconsistentTimestampsUpdateError()
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToMessage(*cb, sender, 1, 110, api::ReturnCode::IO_FAILURE);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 90) "
                        "ReturnCode(IO_FAILURE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testFastPathInconsistentTimestampsGetError()
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    replyToMessage(*cb, sender, 1, 110);

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000008b13), doc:test:test) => 1"),
            sender.getLastCommand(true));

    CPPUNIT_ASSERT(sender.replies.empty());
    replyToGet(*cb, sender, 2, 110, false, api::ReturnCode::IO_FAILURE);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
                        "(best node 1)) ReturnCode(IO_FAILURE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testFastPathInconsistentTimestampsPutError()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    replyToMessage(*cb, sender, 1, 110);

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000008b13), doc:test:test) => 1"),
            sender.getLastCommand(true));

    replyToGet(*cb, sender, 2, 110);

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1,Get => 1,Put => 1,Put => 0"),
            sender.getCommands(true));

    replyToPut(*cb, sender, 3, api::ReturnCode::IO_FAILURE);
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToPut(*cb, sender, 4);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
                        "(best node 1)) ReturnCode(IO_FAILURE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testFastPathInconsistentTimestampsPutNotStarted()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);
    replyToMessage(*cb, sender, 1, 110);

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000008b13), doc:test:test) => 1"),
            sender.getLastCommand(true));
    checkMessageSettingsPropagatedTo(sender.commands.back());

    enableDistributorClusterState("storage:0 distributor:1");
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToGet(*cb, sender, 2, 110);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 110 Was inconsistent "
                        "(best node 1)) ReturnCode(NOT_CONNECTED, "
                        "Can't store document: No storage nodes available)"),
            sender.getLastReply(true));
}


void
TwoPhaseUpdateOperationTest::testFastPathInconsistentTimestampsInconsistentSplit()
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=1/2/3",
                       UpdateOptions().makeInconsistentSplit(true)));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    std::string wanted("Get(BucketId(0x4000000000008b13), doc:test:test) => 0,"
                       "Get(BucketId(0x4400000000008b13), doc:test:test) => 0");

    std::string text = sender.getCommands(true, true);
    CPPUNIT_ASSERT_EQUAL(wanted, text);

    replyToGet(*cb, sender, 0, 90);
    replyToGet(*cb, sender, 1, 120);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "Put(BucketId(0x4400000000008b13), doc:test:test, "
                    "timestamp 200000000, size 52) => 1,"
                    "Put(BucketId(0x4400000000008b13), doc:test:test, "
                    "timestamp 200000000, size 52) => 0"),
            sender.getCommands(true, true, 2));

    replyToPut(*cb, sender, 2);
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToPut(*cb, sender, 3);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, "
                        "BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 120) "
                        "ReturnCode(NONE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::checkMessageSettingsPropagatedTo(
        const api::StorageCommand::SP& msg) const
{
    // Settings set in sendUpdate().
    CPPUNIT_ASSERT_EQUAL(uint32_t(6), msg->getTrace().getLevel());
    CPPUNIT_ASSERT_EQUAL(uint32_t(6789), msg->getTimeout());
    CPPUNIT_ASSERT_EQUAL(uint8_t(99), msg->getPriority());
}

void
TwoPhaseUpdateOperationTest::testFastPathPropagatesMessageSettingsToUpdate()
{
    setupDistributor(1, 1, "storage:1 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Update => 0"), sender.getCommands(true));

    StorageCommand::SP msg(sender.commands.back());
    checkMessageSettingsPropagatedTo(msg);
}

void
TwoPhaseUpdateOperationTest::testNofM()
{
    setupDistributor(2, 2, "storage:2 distributor:1", 1);

    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1"),
            sender.getCommands(true));

    CPPUNIT_ASSERT(sender.replies.empty());
    replyToMessage(*cb, sender, 0, 90);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 90) ReturnCode(NONE)"),
            sender.getLastReply(true));

    replyToMessage(*cb, sender, 1, 123);
}

std::string
TwoPhaseUpdateOperationTest::getUpdatedValueFromLastPut(
        MessageSenderStub& sender)
{
    Document::SP doc(dynamic_cast<api::PutCommand&>(*sender.commands.back())
                     .getDocument());
    FieldValue::UP value(doc->getValue("headerval"));
    return value->toString();
}

void
TwoPhaseUpdateOperationTest::testSafePathUpdatesNewestReceivedDocument()
{
    setupDistributor(3, 3, "storage:3 distributor:1");
    // 0,1 in sync. 2 out of sync.
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4"));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
        std::string("Get(BucketId(0x4000000000008b13), doc:test:test) => 0,"
                    "Get(BucketId(0x4000000000008b13), doc:test:test) => 2"),
        sender.getCommands(true, true));
    replyToGet(*cb, sender, 0, 50);
    replyToGet(*cb, sender, 1, 70);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "Put(BucketId(0x4000000000008b13), doc:test:test, "
                    "timestamp 200000000, size 52) => 1,"
                    "Put(BucketId(0x4000000000008b13), doc:test:test, "
                    "timestamp 200000000, size 52) => 0,"
                    "Put(BucketId(0x4000000000008b13), doc:test:test, "
                    "timestamp 200000000, size 52) => 2"),
            sender.getCommands(true, true, 2));
    // Make sure Put contains an updated document (+10 arith. update on field
    // whose value equals gotten timestamp). In this case we want 70 -> 80.
    CPPUNIT_ASSERT_EQUAL(std::string("80"), getUpdatedValueFromLastPut(sender));

    replyToPut(*cb, sender, 2);
    replyToPut(*cb, sender, 3);
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToPut(*cb, sender, 4);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, "
                                     "BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 70) "
                        "ReturnCode(NONE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testCreateIfNonExistentCreatesDocumentIfAllEmptyGets()
{
    setupDistributor(3, 3, "storage:3 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4",
                       UpdateOptions().createIfNonExistent(true)));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Get => 0,Get => 2"),
                         sender.getCommands(true));
    replyToGet(*cb, sender, 0, 0, false);
    replyToGet(*cb, sender, 1, 0, false);
    // Since create-if-non-existent is set, distributor should create doc from
    // scratch.
    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "Put(BucketId(0x4000000000008b13), doc:test:test, "
                    "timestamp 200000000, size 52) => 1,"
                    "Put(BucketId(0x4000000000008b13), doc:test:test, "
                    "timestamp 200000000, size 52) => 0,"
                    "Put(BucketId(0x4000000000008b13), doc:test:test, "
                    "timestamp 200000000, size 52) => 2"),
            sender.getCommands(true, true, 2));

    CPPUNIT_ASSERT_EQUAL(std::string("10"), getUpdatedValueFromLastPut(sender));

    replyToPut(*cb, sender, 2);
    replyToPut(*cb, sender, 3);
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToPut(*cb, sender, 4);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, "
                                     "BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 200000000) "
                        "ReturnCode(NONE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testUpdateFailsIfSafePathHasFailedPut()
{
    setupDistributor(3, 3, "storage:3 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4",
                       UpdateOptions().createIfNonExistent(true)));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Get => 0,Get => 2"),
                         sender.getCommands(true));
    replyToGet(*cb, sender, 0, 0, false);
    replyToGet(*cb, sender, 1, 0, false);
    // Since create-if-non-existent is set, distributor should create doc from
    // scratch.
    CPPUNIT_ASSERT_EQUAL(std::string("Put => 1,Put => 0,Put => 2"),
                         sender.getCommands(true, false, 2));

    replyToPut(*cb, sender, 2);
    replyToPut(*cb, sender, 3);
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToPut(*cb, sender, 4, api::ReturnCode::IO_FAILURE);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, "
                                     "BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 200000000) "
                        "ReturnCode(IO_FAILURE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testUpdateFailsIfSafePathGetsFail()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4",
                       UpdateOptions().createIfNonExistent(true)));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Get => 0,Get => 1"),
                         sender.getCommands(true));
    replyToGet(*cb, sender, 0, 0, false, api::ReturnCode::IO_FAILURE);
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToGet(*cb, sender, 1, 0, false, api::ReturnCode::IO_FAILURE);
    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, "
                                     "BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 0) "
                        "ReturnCode(IO_FAILURE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testUpdateFailsIfApplyThrowsException()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    // Create update for wrong doctype which will fail the update.
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().withError()));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Get => 0,Get => 1"),
                         sender.getCommands(true));
    replyToGet(*cb, sender, 0, 50);
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToGet(*cb, sender, 1, 70);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, "
                                     "BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 70) "
                        "ReturnCode(INTERNAL_FAILURE, Can not apply a "
                        "\"testdoctype2\" document update to a "
                        "\"testdoctype1\" document.)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testNonExistingWithAutoCreate()
{
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("", UpdateOptions().createIfNonExistent(true)));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "CreateBucketCommand(BucketId(0x4000000000008b13), active) "
                    "Reasons to start:  => 0,"
                    "Put(BucketId(0x4000000000008b13), doc:test:test, "
                    "timestamp 200000000, size 52) => 0"),
            sender.getCommands(true, true));

    CPPUNIT_ASSERT_EQUAL(std::string("10"), getUpdatedValueFromLastPut(sender));

    replyToCreateBucket(*cb, sender, 0);
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToPut(*cb, sender, 1);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, "
                                     "BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 200000000) "
                        "ReturnCode(NONE)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testSafePathFailsUpdateWhenMismatchingTimestampConstraint()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4",
                       UpdateOptions().timestampToUpdate(1234)));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Get => 0,Get => 1"),
                         sender.getCommands(true));
    replyToGet(*cb, sender, 0, 100);
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToGet(*cb, sender, 1, 110);
    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, "
                                     "BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 0) "
                        "ReturnCode(NONE, No document with requested "
                        "timestamp found)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testSafePathUpdatePropagatesMessageSettingsToGetsAndPuts()
{
    setupDistributor(3, 3, "storage:3 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Get => 0,Get => 2"),
                         sender.getCommands(true));
    checkMessageSettingsPropagatedTo(sender.commands.at(0));
    checkMessageSettingsPropagatedTo(sender.commands.at(1));
    replyToGet(*cb, sender, 0, 50);
    replyToGet(*cb, sender, 1, 70);
    CPPUNIT_ASSERT_EQUAL(std::string("Put => 1,Put => 0,Put => 2"),
                         sender.getCommands(true, false, 2));
    checkMessageSettingsPropagatedTo(sender.commands.at(2));
    checkMessageSettingsPropagatedTo(sender.commands.at(3));
    checkMessageSettingsPropagatedTo(sender.commands.at(4));
    replyToPut(*cb, sender, 2);
    replyToPut(*cb, sender, 3);
    replyToPut(*cb, sender, 4);
}

void
TwoPhaseUpdateOperationTest::testSafePathPropagatesMbusTracesFromReplies()
{
    setupDistributor(3, 3, "storage:3 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=1/2/3,2=2/3/4"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Get => 0,Get => 2"),
                         sender.getCommands(true));
    replyToGet(*cb, sender, 0, 50, true,
               api::ReturnCode::OK, "hello earthlings");
    replyToGet(*cb, sender, 1, 70);
    CPPUNIT_ASSERT_EQUAL(std::string("Put => 1,Put => 0,Put => 2"),
                         sender.getCommands(true, false, 2));
    replyToPut(*cb, sender, 2, api::ReturnCode::OK, "fooo");
    replyToPut(*cb, sender, 3, api::ReturnCode::OK, "baaa");
    CPPUNIT_ASSERT(sender.replies.empty());
    replyToPut(*cb, sender, 4);
    
    CPPUNIT_ASSERT_EQUAL(std::string("Update Reply"),
                         sender.getLastReply(false));

    std::string trace(sender.replies.back()->getTrace().toString());
    //std::cout << "\n\n" << trace << "\n\n";
    CPPUNIT_ASSERT(trace.find("hello earthlings") != std::string::npos);
    CPPUNIT_ASSERT(trace.find("fooo") != std::string::npos);
    CPPUNIT_ASSERT(trace.find("baaa") != std::string::npos);
}

void
TwoPhaseUpdateOperationTest::testUpdateFailsIfOwnershipChangesBetweenGetAndPut()
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    // Update towards inconsistent bucket invokes safe path.
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Get => 0,Get => 1"),
                         sender.getCommands(true));

    // Alter cluster state so that distributor is now down (technically the
    // entire cluster is down in this state, but this should not matter). In
    // this new state, the distributor no longer owns the bucket in question
    // and the operation should thus be failed. We must not try to send Puts
    // to a bucket we no longer own.
    enableDistributorClusterState("storage:2 distributor:1 .0.s:d");
    getBucketDatabase().clear();
    replyToGet(*cb, sender, 0, 70);
    replyToGet(*cb, sender, 1, 70);
    
    // BUCKET_NOT_FOUND is a transient error code which should cause the client
    // to re-send the operation, presumably to the correct distributor the next
    // time.
    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, "
                                     "BucketId(0x0000000000000000), "
                        "timestamp 0, timestamp of updated doc: 70) "
                        "ReturnCode(BUCKET_NOT_FOUND, Distributor lost "
                        "ownership of bucket between executing the read "
                        "and write phases of a two-phase update operation)"),
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testSafePathConditionMismatchFailsWithTasError()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition(
                            "testdoctype1.headerval==120")));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    // Newest doc has headerval==110, not 120.
    replyToGet(*cb, sender, 0, 100);
    replyToGet(*cb, sender, 1, 110);
    CPPUNIT_ASSERT_EQUAL(
            "UpdateReply(doc:test:test, "
                         "BucketId(0x0000000000000000), "
                         "timestamp 0, timestamp of updated doc: 0) "
                         "ReturnCode(TEST_AND_SET_CONDITION_FAILED, "
                                    "Condition did not match document)"s,
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testSafePathConditionMatchSendsPutsWithUpdatedDoc()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition(
                            "testdoctype1.headerval==110")));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    replyToGet(*cb, sender, 0, 100);
    replyToGet(*cb, sender, 1, 110);
    CPPUNIT_ASSERT_EQUAL("Put => 1,Put => 0"s,
                         sender.getCommands(true, false, 2));
}

void
TwoPhaseUpdateOperationTest::testSafePathConditionParseFailureFailsWithIllegalParamsError()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition(
                            "testdoctype1.san==fran...cisco")));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    replyToGet(*cb, sender, 0, 100);
    replyToGet(*cb, sender, 1, 110);
    // NOTE: condition is currently not attempted parsed until Gets have been
    // replied to. This may change in the future.
    // XXX reliance on parser/exception error message is very fragile.
    CPPUNIT_ASSERT_EQUAL(
            "UpdateReply(doc:test:test, "
                         "BucketId(0x0000000000000000), "
                         "timestamp 0, timestamp of updated doc: 0) "
                         "ReturnCode(ILLEGAL_PARAMETERS, "
                                     "Failed to parse test and set condition: "
                                     "syntax error, unexpected . at column 24 when "
                                     "parsing selection 'testdoctype1.san==fran...cisco')"s,
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testSafePathConditonUnknownDocTypeFailsWithIllegalParamsError()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition(
                            "langbein.headerval=1234")));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    replyToGet(*cb, sender, 0, 100);
    replyToGet(*cb, sender, 1, 110);
    // NOTE: condition is currently not attempted parsed until Gets have been
    // replied to. This may change in the future.
    CPPUNIT_ASSERT_EQUAL(
            "UpdateReply(doc:test:test, "
                         "BucketId(0x0000000000000000), "
                         "timestamp 0, timestamp of updated doc: 0) "
                         "ReturnCode(ILLEGAL_PARAMETERS, "
                                    "Failed to parse test and set condition: "
                                    "Document type 'langbein' not found at column 1 "
                                    "when parsing selection 'langbein.headerval=1234')"s,
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::testSafePathConditionWithMissingDocFailsWithTasError()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4", UpdateOptions().condition(
                            "testdoctype1.headerval==120")));

    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    // Both Gets return nothing at all, nothing at all.
    replyToGet(*cb, sender, 0, 100, false);
    replyToGet(*cb, sender, 1, 110, false);
    CPPUNIT_ASSERT_EQUAL(
            "UpdateReply(doc:test:test, "
                         "BucketId(0x0000000000000000), "
                         "timestamp 0, timestamp of updated doc: 0) "
                         "ReturnCode(TEST_AND_SET_CONDITION_FAILED, "
                                    "Document did not exist)"s,
            sender.getLastReply(true));
}

void
TwoPhaseUpdateOperationTest::assertAbortedUpdateReplyWithContextPresent(
        const MessageSenderStub& closeSender) const
{
    CPPUNIT_ASSERT_EQUAL(size_t(1), closeSender.replies.size());
    StorageReply::SP reply(closeSender.replies.back());
    CPPUNIT_ASSERT_EQUAL(api::MessageType::UPDATE_REPLY, reply->getType());
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::ABORTED,
                         reply->getResult().getResult());
    auto context = reply->getTransportContext(); // Transfers ownership
    CPPUNIT_ASSERT(context.get());
}

void
TwoPhaseUpdateOperationTest::testFastPathCloseEdgeSendsCorrectReply()
{
    setupDistributor(1, 1, "storage:1 distributor:1");
    // Only 1 replica; consistent with itself by definition.
    std::shared_ptr<TwoPhaseUpdateOperation> cb(sendUpdate("0=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL("Update => 0"s, sender.getCommands(true));
    // Close the operation. This should generate a single reply that is
    // bound to the original command. We can identify rogue replies by these
    // not having a transport context, as these are unique_ptrs that are
    // moved to the reply upon the first reply construction. Any subsequent or
    // erroneous replies will not have this context attached to themselves.
    MessageSenderStub closeSender;
    cb->onClose(closeSender);

    assertAbortedUpdateReplyWithContextPresent(closeSender);
}

void
TwoPhaseUpdateOperationTest::testSafePathCloseEdgeSendsCorrectReply()
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    std::shared_ptr<TwoPhaseUpdateOperation> cb(
            sendUpdate("0=1/2/3,1=2/3/4")); // Inconsistent replicas.
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Get => 0,Get => 1"),
                         sender.getCommands(true));
    // Closing the operation should now only return an ABORTED reply for
    // the UpdateCommand, _not_ from the nested, pending Get operation (which
    // will implicitly generate an ABORTED reply for the synthesized Get
    // command passed to it).
    MessageSenderStub closeSender;
    cb->onClose(closeSender);

    assertAbortedUpdateReplyWithContextPresent(closeSender);
}

// XXX currently differs in behavior from content nodes in that updates for
// document IDs without explicit doctypes will _not_ be auto-failed on the
// distributor.

// XXX shouldn't be necessary to have any special handling of create-if... and
// test-and-set right? They appear fully mutually exclusive.

// XXX: test case where update reply has been sent but callback still
// has pending messages (e.g. n-of-m case).

} // distributor
} // storage
