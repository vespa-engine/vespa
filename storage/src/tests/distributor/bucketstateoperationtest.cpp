// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/storage/distributor/operations/idealstate/setbucketstateoperation.h>
#include <vespa/storage/distributor/distributor.h>

namespace storage {

namespace distributor {

class BucketStateOperationTest : public CppUnit::TestFixture,
                                 public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(BucketStateOperationTest);
    CPPUNIT_TEST(testActiveStateSupportedInBucketDb);
    CPPUNIT_TEST(testActivateSingleNode);
    CPPUNIT_TEST(testActivateAndDeactivateNodes);
    CPPUNIT_TEST(testDoNotDeactivateIfActivateFails);
    CPPUNIT_TEST(testBucketDbNotUpdatedOnFailure);
    CPPUNIT_TEST_SUITE_END();

private:
    void testActiveStateSupportedInBucketDb();
    void testActivateSingleNode();
    void testActivateAndDeactivateNodes();
    void testDoNotDeactivateIfActivateFails();
    void testBucketDbNotUpdatedOnFailure();

public:
    void setUp() override {
        createLinks();
    }
    void tearDown() override {
        close();
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(BucketStateOperationTest);

void
BucketStateOperationTest::testActiveStateSupportedInBucketDb()
{
    document::BucketId bid(16, 1);
    insertBucketInfo(bid, 0, 0xabc, 10, 1100, true, true);

    BucketDatabase::Entry entry = getBucket(bid);
    CPPUNIT_ASSERT(entry.valid());
    CPPUNIT_ASSERT(entry->getNode(0)->active());
    CPPUNIT_ASSERT_EQUAL(
            std::string("node(idx=0,crc=0xabc,docs=10/10,bytes=1100/1100,"
                        "trusted=true,active=true,ready=false)"),
            entry->getNode(0)->toString());
}

void
BucketStateOperationTest::testActivateSingleNode()
{
    document::BucketId bid(16, 1);
    insertBucketInfo(bid, 0, 0xabc, 10, 1100, true, false);

    BucketAndNodes bucketAndNodes(bid, toVector<uint16_t>(0));
    std::vector<uint16_t> active;
    active.push_back(0);
    SetBucketStateOperation op("storage", bucketAndNodes, active);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL((size_t)1, _sender.commands.size());

    std::shared_ptr<api::StorageCommand> msg  = _sender.commands[0];
    CPPUNIT_ASSERT(msg->getType() == api::MessageType::SETBUCKETSTATE);
    CPPUNIT_ASSERT_EQUAL(
            api::StorageMessageAddress(
                    "storage", lib::NodeType::STORAGE, 0).toString(),
            msg->getAddress()->toString());

    const api::SetBucketStateCommand& cmd(
            dynamic_cast<const api::SetBucketStateCommand&>(*msg));
    CPPUNIT_ASSERT_EQUAL(bid, cmd.getBucketId());
    CPPUNIT_ASSERT_EQUAL(api::SetBucketStateCommand::ACTIVE, cmd.getState());

    std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
    op.receive(_sender, reply);

    BucketDatabase::Entry entry = getBucket(bid);
    CPPUNIT_ASSERT(entry.valid());
    CPPUNIT_ASSERT(entry->getNodeRef(0).active());

    CPPUNIT_ASSERT(op.ok());

    // TODO: check that it's done
}

void
BucketStateOperationTest::testActivateAndDeactivateNodes()
{
    document::BucketId bid(16, 1);
    insertBucketInfo(bid, 0, 0xabc, 10, 1100, false, true);
    insertBucketInfo(bid, 1, 0xdef, 15, 1500, false, false);

    BucketAndNodes bucketAndNodes(bid, toVector<uint16_t>(0, 1));
    std::vector<uint16_t> active;
    active.push_back(1);
    SetBucketStateOperation op("storage", bucketAndNodes, active);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL((size_t)1, _sender.commands.size());
    {
        std::shared_ptr<api::StorageCommand> msg  = _sender.commands[0];
        CPPUNIT_ASSERT(msg->getType() == api::MessageType::SETBUCKETSTATE);
        CPPUNIT_ASSERT_EQUAL(
                api::StorageMessageAddress(
                        "storage", lib::NodeType::STORAGE, 1).toString(),
                msg->getAddress()->toString());

        const api::SetBucketStateCommand& cmd(
                dynamic_cast<const api::SetBucketStateCommand&>(*msg));
        CPPUNIT_ASSERT_EQUAL(bid, cmd.getBucketId());
        CPPUNIT_ASSERT_EQUAL(api::SetBucketStateCommand::ACTIVE, cmd.getState());

        std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
        op.receive(_sender, reply);
    }

    CPPUNIT_ASSERT_EQUAL((size_t)2, _sender.commands.size());
    {
        std::shared_ptr<api::StorageCommand> msg  = _sender.commands[1];
        CPPUNIT_ASSERT(msg->getType() == api::MessageType::SETBUCKETSTATE);
        CPPUNIT_ASSERT_EQUAL(
                api::StorageMessageAddress(
                        "storage", lib::NodeType::STORAGE, 0).toString(),
                msg->getAddress()->toString());

        const api::SetBucketStateCommand& cmd(
                dynamic_cast<const api::SetBucketStateCommand&>(*msg));
        CPPUNIT_ASSERT_EQUAL(bid, cmd.getBucketId());
        CPPUNIT_ASSERT_EQUAL(api::SetBucketStateCommand::INACTIVE, cmd.getState());

        std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
        op.receive(_sender, reply);
    }

    BucketDatabase::Entry entry = getBucket(bid);
    CPPUNIT_ASSERT(entry.valid());
    CPPUNIT_ASSERT_EQUAL(
            std::string("node(idx=0,crc=0xabc,docs=10/10,bytes=1100/1100,"
                        "trusted=true,active=false,ready=false)"),
                entry->getNodeRef(0).toString());
    CPPUNIT_ASSERT_EQUAL(
            std::string("node(idx=1,crc=0xdef,docs=15/15,bytes=1500/1500,"
                        "trusted=false,active=true,ready=false)"),
            entry->getNodeRef(1).toString());

    CPPUNIT_ASSERT(op.ok());
}

void
BucketStateOperationTest::testDoNotDeactivateIfActivateFails()
{
    document::BucketId bid(16, 1);
    insertBucketInfo(bid, 0, 0xabc, 10, 1100, false, true);
    insertBucketInfo(bid, 1, 0xdef, 15, 1500, false, false);

    BucketAndNodes bucketAndNodes(bid, toVector<uint16_t>(0, 1));
    std::vector<uint16_t> active;
    active.push_back(1);
    SetBucketStateOperation op("storage", bucketAndNodes, active);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL((size_t)1, _sender.commands.size());
    {
        std::shared_ptr<api::StorageCommand> msg  = _sender.commands[0];
        CPPUNIT_ASSERT(msg->getType() == api::MessageType::SETBUCKETSTATE);
        CPPUNIT_ASSERT_EQUAL(
                api::StorageMessageAddress(
                        "storage", lib::NodeType::STORAGE, 1).toString(),
                msg->getAddress()->toString());

        const api::SetBucketStateCommand& cmd(
                dynamic_cast<const api::SetBucketStateCommand&>(*msg));
        CPPUNIT_ASSERT_EQUAL(bid, cmd.getBucketId());
        CPPUNIT_ASSERT_EQUAL(api::SetBucketStateCommand::ACTIVE, cmd.getState());

        std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
        reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "aaarg!"));
        op.receive(_sender, reply);
    }

    CPPUNIT_ASSERT_EQUAL((size_t)1, _sender.commands.size());

    BucketDatabase::Entry entry = getBucket(bid);
    CPPUNIT_ASSERT(entry.valid());
    CPPUNIT_ASSERT_EQUAL(
            std::string("node(idx=0,crc=0xabc,docs=10/10,bytes=1100/1100,"
                        "trusted=true,active=true,ready=false)"),
                entry->getNodeRef(0).toString());
    CPPUNIT_ASSERT_EQUAL(
            std::string("node(idx=1,crc=0xdef,docs=15/15,bytes=1500/1500,"
                        "trusted=false,active=false,ready=false)"),
            entry->getNodeRef(1).toString());

    CPPUNIT_ASSERT(!op.ok());
}

void
BucketStateOperationTest::testBucketDbNotUpdatedOnFailure()
{
    document::BucketId bid(16, 1);
    insertBucketInfo(bid, 0, 0xabc, 10, 1100, true, false);

    BucketAndNodes bucketAndNodes(bid, toVector<uint16_t>(0));
    std::vector<uint16_t> active;
    active.push_back(0);
    SetBucketStateOperation op("storage", bucketAndNodes, active);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL((size_t)1, _sender.commands.size());

    std::shared_ptr<api::StorageCommand> msg  = _sender.commands[0];
    CPPUNIT_ASSERT(msg->getType() == api::MessageType::SETBUCKETSTATE);
    CPPUNIT_ASSERT_EQUAL(
            api::StorageMessageAddress(
                    "storage", lib::NodeType::STORAGE, 0).toString(),
            msg->getAddress()->toString());

    std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
    reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "aaarg!"));
    op.receive(_sender, reply);

    BucketDatabase::Entry entry = getBucket(bid);
    CPPUNIT_ASSERT(entry.valid());
    // Should not be updated
    CPPUNIT_ASSERT(!entry->getNodeRef(0).active());

    CPPUNIT_ASSERT(!op.ok());
}

} // namespace distributor

} // namespace storage
