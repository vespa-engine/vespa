// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <iomanip>
#include <tests/common/dummystoragelink.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/bucket.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/operations/external/updateoperation.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/config/helper/configgetter.hpp>

using std::shared_ptr;
using namespace document;
using namespace storage;
using namespace storage::distributor;
using namespace storage::api;
using namespace std;
using namespace storage::lib;
using config::ConfigGetter;
using config::FileSpec;
using vespalib::string;
using document::test::makeDocumentBucket;

class UpdateOperation_Test : public CppUnit::TestFixture,
                             public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(UpdateOperation_Test);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testNotFound);
    CPPUNIT_TEST(testMultiNode);
    CPPUNIT_TEST(testMultiNodeInconsistentTimestamp);
    CPPUNIT_TEST_SUITE_END();

    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType *_html_type;

protected:
    void testSimple();
    void testNotFound();
    void testMultiNode();
    void testMultiNodeInconsistentTimestamp();

public:
    void setUp() override {
        _repo.reset(
                new DocumentTypeRepo(*ConfigGetter<DocumenttypesConfig>::
                                     getConfig("config-doctypes",
                                               FileSpec(TEST_PATH("config-doctypes.cfg")))));
        _html_type = _repo->getDocumentType("text/html");
        createLinks();
    }

    void tearDown() override {
        close();
    }

    void replyToMessage(
            UpdateOperation& callback,
            MessageSenderStub& sender,
            uint32_t index,
            uint64_t oldTimestamp,
            api::BucketInfo info = api::BucketInfo(2,4,6));

    std::shared_ptr<UpdateOperation>
    sendUpdate(const std::string& bucketState);

    document::BucketId _bId;
};

CPPUNIT_TEST_SUITE_REGISTRATION(UpdateOperation_Test);

std::shared_ptr<UpdateOperation>
UpdateOperation_Test::sendUpdate(const std::string& bucketState)
{
    document::DocumentUpdate::SP update(
            new document::DocumentUpdate(
                    *_html_type,
                    document::DocumentId(document::DocIdString("test", "test"))));

    _bId = getExternalOperationHandler().getBucketId(update->getId());

    addNodesToBucketDB(_bId, bucketState);

    std::shared_ptr<api::UpdateCommand> msg(
            new api::UpdateCommand(makeDocumentBucket(document::BucketId(0)),
                                   update,
                                   100));

    ExternalOperationHandler& handler = getExternalOperationHandler();
    return std::shared_ptr<UpdateOperation>(
            new UpdateOperation(handler,
                                getDistributorBucketSpace(),
                                msg,
                                getDistributor().getMetrics().updates[msg->getLoadType()]));
}


void
UpdateOperation_Test::replyToMessage(
        UpdateOperation& callback,
        MessageSenderStub& sender,
        uint32_t index,
        uint64_t oldTimestamp,
        api::BucketInfo info)
{
    std::shared_ptr<api::StorageMessage> msg2  = sender.commands[index];
    UpdateCommand* updatec = dynamic_cast<UpdateCommand*>(msg2.get());
    std::unique_ptr<api::StorageReply> reply(updatec->makeReply());
    UpdateReply* updateR = static_cast<api::UpdateReply*>(reply.get());
    updateR->setOldTimestamp(oldTimestamp);
    updateR->setBucketInfo(info);

    callback.onReceive(sender,
                       std::shared_ptr<StorageReply>(reply.release()));
}

void
UpdateOperation_Test::testSimple()
{
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 100, timestamp of updated doc: 90) ReturnCode(NONE)"),
            sender.getLastReply(true));
}

void
UpdateOperation_Test::testNotFound()
{
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 0);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 100, timestamp of updated doc: 0) ReturnCode(NONE)"),
            sender.getLastReply(true));
}

void
UpdateOperation_Test::testMultiNode()
{
    setupDistributor(2, 2, "distributor:1 storage:2");
    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 120);
    replyToMessage(*cb, sender, 1, 120);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 100, timestamp of updated doc: 120) ReturnCode(NONE)"),
            sender.getLastReply(true));

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    _bId.toString() + " : "
                    "node(idx=1,crc=0x2,docs=4/4,bytes=6/6,trusted=true,active=false,ready=false), "
                    "node(idx=0,crc=0x2,docs=4/4,bytes=6/6,trusted=true,active=false,ready=false)"),
            dumpBucket(_bId));
}

void
UpdateOperation_Test::testMultiNodeInconsistentTimestamp()
{
    setupDistributor(2, 2, "distributor:1 storage:2");
    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    MessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(
            std::string("Update => 0,Update => 1"),
            sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 119);
    replyToMessage(*cb, sender, 1, 120);

    CPPUNIT_ASSERT_EQUAL(
            std::string("UpdateReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 100, timestamp of updated doc: 120 Was inconsistent "
                        "(best node 1)) ReturnCode(NONE)"),
            sender.getLastReply(true));
}

