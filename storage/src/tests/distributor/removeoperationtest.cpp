// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <iomanip>
#include <tests/common/dummystoragelink.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storageapi/message/persistence.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/operations/external/removeoperation.h>

using document::test::makeDocumentBucket;

namespace storage {
namespace distributor {

class RemoveOperationTest : public CppUnit::TestFixture,
                            public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(RemoveOperationTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testNotFound);
    CPPUNIT_TEST(testStorageFailure);
    CPPUNIT_TEST(testNotInDB);
    CPPUNIT_TEST(testMultipleCopies);
    CPPUNIT_TEST(canSendRemoveWhenAllReplicaNodesRetired);
    CPPUNIT_TEST_SUITE_END();

protected:
    void testSimple();
    void testNotFound();
    void testStorageFailure();
    void testNoReply();
    void testNotInDB();
    void testMultipleCopies();
    void testRevert();
    void canSendRemoveWhenAllReplicaNodesRetired();

public:
    document::DocumentId docId;
    document::BucketId bucketId;
    std::unique_ptr<RemoveOperation> op;

    void setUp() override {
        createLinks();

        docId = document::DocumentId(document::DocIdString("test", "uri"));
        bucketId = getExternalOperationHandler().getBucketId(docId);
        enableDistributorClusterState("distributor:1 storage:4");
    };

    void tearDown() override {
        close();
    }

    void sendRemove(document::DocumentId dId) {
        std::shared_ptr<api::RemoveCommand> msg(
                new api::RemoveCommand(makeDocumentBucket(document::BucketId(0)), dId, 100));

        op.reset(new RemoveOperation(getExternalOperationHandler(),
                                     getDistributorBucketSpace(),
                                     msg,
                                     getDistributor().getMetrics().
                                     removes[msg->getLoadType()]));

        op->start(_sender, framework::MilliSecTime(0));
    }

    void replyToMessage(RemoveOperation& callback,
                        uint32_t index,
                        uint64_t oldTimestamp)
    {
        if (index == (uint32_t)-1) {
            index = _sender.commands.size() - 1;
        }

        std::shared_ptr<api::StorageMessage> msg2  = _sender.commands[index];
        api::RemoveCommand* removec = dynamic_cast<api::RemoveCommand*>(msg2.get());
        std::unique_ptr<api::StorageReply> reply(removec->makeReply());
        api::RemoveReply* removeR = static_cast<api::RemoveReply*>(reply.get());
        removeR->setOldTimestamp(oldTimestamp);
        callback.onReceive(_sender,
                           std::shared_ptr<api::StorageReply>(reply.release()));
    }

    void sendRemove() {
        sendRemove(docId);
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(RemoveOperationTest);

void
RemoveOperationTest::testSimple()
{
    addNodesToBucketDB(bucketId, "1=0");

    sendRemove();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Remove(BucketId(0x4000000000002a52), doc:test:uri, "
                        "timestamp 100) => 1"),
            _sender.getLastCommand());

    replyToMessage(*op, -1, 34);

    CPPUNIT_ASSERT_EQUAL(
            std::string("RemoveReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 100, removed doc from 34) ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
RemoveOperationTest::testNotFound()
{
    addNodesToBucketDB(bucketId, "1=0");

    sendRemove();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Remove(BucketId(0x4000000000002a52), doc:test:uri, "
                        "timestamp 100) => 1"),
            _sender.getLastCommand());

    replyToMessage(*op, -1, 0);

    CPPUNIT_ASSERT_EQUAL(
            std::string("RemoveReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 100, not found) ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
RemoveOperationTest::testStorageFailure()
{
    addNodesToBucketDB(bucketId, "1=0");

    sendRemove();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Remove(BucketId(0x4000000000002a52), doc:test:uri, "
                        "timestamp 100) => 1"),
            _sender.getLastCommand());

    sendReply(*op, -1, api::ReturnCode::INTERNAL_FAILURE);

    CPPUNIT_ASSERT_EQUAL(
            std::string("RemoveReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 100, not found) ReturnCode(INTERNAL_FAILURE)"),
            _sender.getLastReply());
}

void
RemoveOperationTest::testNotInDB()
{
    sendRemove();

    CPPUNIT_ASSERT_EQUAL(std::string("RemoveReply(BucketId(0x0000000000000000), "
                                     "doc:test:uri, timestamp 100, not found) ReturnCode(NONE)"),
                         _sender.getLastReply());
}

void
RemoveOperationTest::testMultipleCopies()
{
    addNodesToBucketDB(bucketId, "1=0, 2=0, 3=0");

    sendRemove();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Remove(BucketId(0x4000000000002a52), doc:test:uri, "
                        "timestamp 100) => 1,"
                        "Remove(BucketId(0x4000000000002a52), doc:test:uri, "
                        "timestamp 100) => 2,"
                        "Remove(BucketId(0x4000000000002a52), doc:test:uri, "
                        "timestamp 100) => 3"),
            _sender.getCommands(true, true));

    replyToMessage(*op, 0, 34);
    replyToMessage(*op, 1, 34);
    replyToMessage(*op, 2, 75);

    CPPUNIT_ASSERT_EQUAL(
            std::string("RemoveReply(BucketId(0x0000000000000000), "
                        "doc:test:uri, timestamp 100, removed doc from 75) ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
RemoveOperationTest::canSendRemoveWhenAllReplicaNodesRetired()
{
    enableDistributorClusterState("distributor:1 storage:1 .0.s:r");
    addNodesToBucketDB(bucketId, "0=123");
    sendRemove();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Remove(BucketId(0x4000000000002a52), doc:test:uri, "
                        "timestamp 100) => 0"),
            _sender.getLastCommand());
}

} // distributor
} // storage
