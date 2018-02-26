// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cppunit/extensions/HelperMacros.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storage/distributor/operations/idealstate/joinoperation.h>
#include <vespa/storage/distributor/distributor.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>

using document::test::makeDocumentBucket;

namespace storage {
namespace distributor {

class JoinOperationTest : public CppUnit::TestFixture, public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(JoinOperationTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(sendSparseJoinsToNodesWithoutBothSourceBuckets);
    CPPUNIT_TEST_SUITE_END();

    void checkSourceBucketsAndSendReply(
            JoinOperation& op,
            size_t msgIndex,
            const std::vector<document::BucketId>& wantedIds);

protected:
    void testSimple();
    void sendSparseJoinsToNodesWithoutBothSourceBuckets();

public:
    void setUp() override {
        createLinks();
    };

    void tearDown() override {
        close();
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(JoinOperationTest);

void
JoinOperationTest::testSimple()
{
    getConfig().setJoinCount(100);
    getConfig().setJoinSize(1000);

    addNodesToBucketDB(document::BucketId(33, 1), "0=250/50/300");
    addNodesToBucketDB(document::BucketId(33, 0x100000001), "0=300/40/200");

    enableDistributorClusterState("distributor:1 storage:1");

    JoinOperation op("storage",
                     BucketAndNodes(makeDocumentBucket(document::BucketId(32, 0)),
                                    toVector<uint16_t>(0)),
                     toVector(document::BucketId(33, 1),
                              document::BucketId(33, 0x100000001)));

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    checkSourceBucketsAndSendReply(op, 0, {{33, 1}, {33, 0x100000001}});

    CPPUNIT_ASSERT(!getBucket(document::BucketId(33, 0x100000001)).valid());
    CPPUNIT_ASSERT(!getBucket(document::BucketId(33, 1)).valid());

    BucketDatabase::Entry entry = getBucket(document::BucketId(32, 0));
    CPPUNIT_ASSERT(entry.valid());
    CPPUNIT_ASSERT_EQUAL((uint16_t)0, entry->getNodeRef(0).getNode());
    CPPUNIT_ASSERT_EQUAL(api::BucketInfo(666, 90, 500),
                         entry->getNodeRef(0).getBucketInfo());
}

void
JoinOperationTest::checkSourceBucketsAndSendReply(
        JoinOperation& op,
        size_t msgIndex,
        const std::vector<document::BucketId>& wantedIds)
{
    CPPUNIT_ASSERT(_sender.commands.size() > msgIndex);

    std::shared_ptr<api::StorageCommand> msg(_sender.commands[msgIndex]);
    CPPUNIT_ASSERT_EQUAL(api::MessageType::JOINBUCKETS, msg->getType());

    api::JoinBucketsCommand& joinCmd(
            dynamic_cast<api::JoinBucketsCommand&>(*msg));
    CPPUNIT_ASSERT_EQUAL(wantedIds, joinCmd.getSourceBuckets());

    std::shared_ptr<api::StorageReply> reply(joinCmd.makeReply());
    api::JoinBucketsReply& sreply(
            dynamic_cast<api::JoinBucketsReply&>(*reply));
    sreply.setBucketInfo(api::BucketInfo(666, 90, 500));

    op.receive(_sender, reply);
}

/**
 * If the set of buckets kept on nodes is disjoint, send sparse joins (same
 * bucket id used as both source buckets) for those nodes having only one of
 * the buckets.
 */
void
JoinOperationTest::sendSparseJoinsToNodesWithoutBothSourceBuckets()
{
    getConfig().setJoinCount(100);
    getConfig().setJoinSize(1000);

    addNodesToBucketDB(document::BucketId(33, 1), "0=250/50/300,1=250/50/300");
    addNodesToBucketDB(document::BucketId(33, 0x100000001), "0=300/40/200");

    enableDistributorClusterState("distributor:1 storage:2");

    JoinOperation op("storage",
                     BucketAndNodes(makeDocumentBucket(document::BucketId(32, 0)),
                                    toVector<uint16_t>(0, 1)),
                     toVector(document::BucketId(33, 1),
                              document::BucketId(33, 0x100000001)));

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    checkSourceBucketsAndSendReply(op, 0, {{33, 1}, {33, 0x100000001}});
    checkSourceBucketsAndSendReply(op, 1, {{33, 1}, {33, 1}});
}

}

}
