// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cppunit/extensions/HelperMacros.h>
#include <iomanip>
#include <tests/common/dummystoragelink.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storage/distributor/operations/idealstate/splitoperation.h>
#include <vespa/document/base/documentid.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storageapi/message/multioperation.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/distributor.h>

using std::shared_ptr;
using namespace document;
using document::test::makeDocumentBucket;

namespace storage {

namespace distributor {

class SplitOperationTest : public CppUnit::TestFixture,
                           public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(SplitOperationTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testMultiNodeFailure);
    CPPUNIT_TEST(testCopyTrustedStatusNotCarriedOverAfterSplit);
    CPPUNIT_TEST(testOperationBlockedByPendingJoin);
    CPPUNIT_TEST_SUITE_END();

    uint32_t splitByteSize;
    uint32_t tooLargeBucketSize;
    uint32_t splitCount;
    uint32_t maxSplitBits;

protected:
    void testSimple();
    void testMultiNodeFailure();
    void testCopyTrustedStatusNotCarriedOverAfterSplit();
    void testOperationBlockedByPendingJoin();

public:
    SplitOperationTest();

    void setUp() override {
        createLinks();
        getConfig().setSplitCount(splitCount);
        getConfig().setSplitSize(splitByteSize);

    }

    void tearDown() override {
        close();
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(SplitOperationTest);

SplitOperationTest::SplitOperationTest()
    : splitByteSize(10*1024*1024),
      tooLargeBucketSize(splitByteSize * 1.1),
      splitCount(UINT32_MAX),
      maxSplitBits(58)
{
}

void
SplitOperationTest::testSimple()
{
    enableDistributorClusterState("distributor:1 storage:1");

    insertBucketInfo(document::BucketId(16, 1), 0, 0xabc, 1000,
                     tooLargeBucketSize, 250);

    SplitOperation op("storage",
                      BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(0)),
                      maxSplitBits,
                      splitCount,
                      splitByteSize);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    {
        CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.commands.size());

        std::shared_ptr<api::StorageCommand> msg  = _sender.commands[0];
        CPPUNIT_ASSERT(msg->getType() == api::MessageType::SPLITBUCKET);
        CPPUNIT_ASSERT_EQUAL(
                api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 0)
                        .toString(),
                msg->getAddress()->toString());

        std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
        api::SplitBucketReply* sreply(
                static_cast<api::SplitBucketReply*>(reply.get()));

        sreply->getSplitInfo().push_back(api::SplitBucketReply::Entry(
                        document::BucketId(17, 1),
                        api::BucketInfo(100, 600, 5000000)));

        sreply->getSplitInfo().push_back(api::SplitBucketReply::Entry(
                        document::BucketId(17, 0x10001),
                        api::BucketInfo(110, 400, 6000000)));

        op.receive(_sender, reply);
    }

    CPPUNIT_ASSERT(!getBucket(document::BucketId(16, 1)).valid());

    {
        BucketDatabase::Entry entry = getBucket(document::BucketId(17, 1));

        CPPUNIT_ASSERT(entry.valid());
        CPPUNIT_ASSERT_EQUAL((uint16_t)0, entry->getNodeRef(0).getNode());
        CPPUNIT_ASSERT_EQUAL((uint32_t)100, entry->getNodeRef(0).getChecksum());
        CPPUNIT_ASSERT_EQUAL((uint32_t)5000000,
                             entry->getNodeRef(0).getTotalDocumentSize());
        CPPUNIT_ASSERT_EQUAL((uint32_t)600,
                             entry->getNodeRef(0).getDocumentCount());
    }

    {
        BucketDatabase::Entry entry(getBucket(document::BucketId(17, 0x10001)));

        CPPUNIT_ASSERT(entry.valid());
        CPPUNIT_ASSERT_EQUAL((uint16_t)0, entry->getNodeRef(0).getNode());
        CPPUNIT_ASSERT_EQUAL((uint32_t)110, entry->getNodeRef(0).getChecksum());
        CPPUNIT_ASSERT_EQUAL((uint32_t)6000000,
                             entry->getNodeRef(0).getTotalDocumentSize());
        CPPUNIT_ASSERT_EQUAL((uint32_t)400,
                             entry->getNodeRef(0).getDocumentCount());
    }
}

void
SplitOperationTest::testMultiNodeFailure()
{
    {
        BucketDatabase::Entry entry(document::BucketId(16, 1));

        BucketCopy copy(0, 0, api::BucketInfo(250, 1000, tooLargeBucketSize));
        entry->addNode(copy, toVector<uint16_t>(0));

        entry->addNode(BucketCopy(0, 1, copy.getBucketInfo()),
                       toVector<uint16_t>(0));
        getBucketDatabase().update(entry);
    }

    enableDistributorClusterState("distributor:1 storage:2");


    SplitOperation op("storage",
                      BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(0,1)),
                      maxSplitBits,
                      splitCount,
                      splitByteSize);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    {
        CPPUNIT_ASSERT_EQUAL((size_t)2, _sender.commands.size());

        {
            std::shared_ptr<api::StorageCommand> msg  = _sender.commands[0];
            CPPUNIT_ASSERT(msg->getType() == api::MessageType::SPLITBUCKET);
            CPPUNIT_ASSERT_EQUAL(
                    api::StorageMessageAddress("storage",
                            lib::NodeType::STORAGE, 0).toString(),
                    msg->getAddress()->toString());

            api::SplitBucketReply* sreply(
                    static_cast<api::SplitBucketReply*>(
                            msg->makeReply().release()));
            sreply->setResult(api::ReturnCode::OK);

            sreply->getSplitInfo().push_back(api::SplitBucketReply::Entry(
                            document::BucketId(17, 1),
                            api::BucketInfo(100, 600, 5000000)));

            sreply->getSplitInfo().push_back(api::SplitBucketReply::Entry(
                            document::BucketId(17, 0x10001),
                            api::BucketInfo(110, 400, 6000000)));

            op.receive(_sender, std::shared_ptr<api::StorageReply>(sreply));
        }

        sendReply(op, 1, api::ReturnCode::NOT_CONNECTED);
    }

    {
        BucketDatabase::Entry entry = getBucket(document::BucketId(16, 1));

        CPPUNIT_ASSERT(entry.valid());
        CPPUNIT_ASSERT_EQUAL((uint32_t)1, entry->getNodeCount());

        CPPUNIT_ASSERT_EQUAL((uint16_t)1, entry->getNodeRef(0).getNode());
        CPPUNIT_ASSERT_EQUAL((uint32_t)250, entry->getNodeRef(0).getChecksum());
        CPPUNIT_ASSERT_EQUAL(tooLargeBucketSize,
                             entry->getNodeRef(0).getTotalDocumentSize());
        CPPUNIT_ASSERT_EQUAL((uint32_t)1000,
                             entry->getNodeRef(0).getDocumentCount());
    }

    {
        BucketDatabase::Entry entry = getBucket(document::BucketId(17, 1));

        CPPUNIT_ASSERT(entry.valid());
        CPPUNIT_ASSERT_EQUAL((uint32_t)1, entry->getNodeCount());

        CPPUNIT_ASSERT_EQUAL((uint16_t)0, entry->getNodeRef(0).getNode());
        CPPUNIT_ASSERT_EQUAL((uint32_t)100, entry->getNodeRef(0).getChecksum());
        CPPUNIT_ASSERT_EQUAL((uint32_t)5000000,
                             entry->getNodeRef(0).getTotalDocumentSize());
        CPPUNIT_ASSERT_EQUAL((uint32_t)600,
                             entry->getNodeRef(0).getDocumentCount());
    }

    {
        BucketDatabase::Entry entry(getBucket(document::BucketId(17, 0x10001)));

        CPPUNIT_ASSERT(entry.valid());
        CPPUNIT_ASSERT_EQUAL((uint32_t)1, entry->getNodeCount());

        CPPUNIT_ASSERT_EQUAL((uint16_t)0, entry->getNodeRef(0).getNode());
        CPPUNIT_ASSERT_EQUAL((uint32_t)110, entry->getNodeRef(0).getChecksum());
        CPPUNIT_ASSERT_EQUAL((uint32_t)6000000,
                             entry->getNodeRef(0).getTotalDocumentSize());
        CPPUNIT_ASSERT_EQUAL((uint32_t)400,
                             entry->getNodeRef(0).getDocumentCount());
    }
}

void
SplitOperationTest::testCopyTrustedStatusNotCarriedOverAfterSplit()
{
    enableDistributorClusterState("distributor:1 storage:2");

    document::BucketId sourceBucket(16, 1);
    /*
     * Need 3 nodes to reproduce bug 6418516. Otherwise, the source bucket is
     * left with only 1 copy which implicitly becomes trusted. When this copy
     * is then split, the distributor db will automatically un-trust all buckets
     * since it sees that multiple copies are trusted that are not consistent
     * with each other. This prevents the bug from being visible.
     */
    addNodesToBucketDB(sourceBucket, "0=150/20/30000000/t,1=450/50/60000/u,"
                                     "2=550/60/70000");

    SplitOperation op("storage",
                      BucketAndNodes(makeDocumentBucket(sourceBucket), toVector<uint16_t>(0, 1)),
                      maxSplitBits,
                      splitCount,
                      splitByteSize);

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(size_t(3), _sender.commands.size());

    std::vector<document::BucketId> childBuckets;
    childBuckets.push_back(document::BucketId(17, 1));
    childBuckets.push_back(document::BucketId(17, 0x10001));

    // Note: only 2 out of 3 requests replied to!
    for (int i = 0; i < 2; ++i) {
        std::shared_ptr<api::StorageCommand> msg  = _sender.commands[i];
        CPPUNIT_ASSERT(msg->getType() == api::MessageType::SPLITBUCKET);
        std::shared_ptr<api::StorageReply> reply(msg->makeReply().release());
        api::SplitBucketReply* sreply(
                static_cast<api::SplitBucketReply*>(reply.get()));

        // Make sure copies differ so they cannot become implicitly trusted.
        sreply->getSplitInfo().push_back(api::SplitBucketReply::Entry(
                        childBuckets[0],
                        api::BucketInfo(100 + i, 600, 5000000)));
        sreply->getSplitInfo().push_back(api::SplitBucketReply::Entry(
                        childBuckets[1],
                        api::BucketInfo(110 + i, 400, 6000000)));

        op.receive(_sender, reply);
    }

    CPPUNIT_ASSERT(getBucket(sourceBucket).valid()); // Still alive

    for (uint32_t i = 0; i < 2; ++i) {
        BucketDatabase::Entry entry(getBucket(childBuckets[i]));

        CPPUNIT_ASSERT(entry.valid());
        CPPUNIT_ASSERT_EQUAL(size_t(2), entry->getNodes().size());

        for (uint16_t j = 0; j < 2; ++j) {
            CPPUNIT_ASSERT(!entry->getNodeRef(i).trusted());
        }
    }
}

void
SplitOperationTest::testOperationBlockedByPendingJoin()
{
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg);

    enableDistributorClusterState("distributor:1 storage:2");

    document::BucketId joinTarget(2, 1);
    std::vector<document::BucketId> joinSources = {
        document::BucketId(3, 1), document::BucketId(3, 5)
    };
    auto joinCmd = std::make_shared<api::JoinBucketsCommand>(makeDocumentBucket(joinTarget));
    joinCmd->getSourceBuckets() = joinSources;
    joinCmd->setAddress(
            api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 0));

    tracker.insert(joinCmd);

    insertBucketInfo(joinTarget, 0, 0xabc, 1000, 1234, 250);

    SplitOperation op("storage",
                      BucketAndNodes(makeDocumentBucket(joinTarget), toVector<uint16_t>(0)),
                      maxSplitBits,
                      splitCount,
                      splitByteSize);

    CPPUNIT_ASSERT(op.isBlocked(tracker));

    // Now, pretend there's a join for another node in the same bucket. This
    // will happen when a join is partially completed.
    tracker.clearMessagesForNode(0);
    CPPUNIT_ASSERT(!op.isBlocked(tracker));

    joinCmd->setAddress(
            api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 1));
    tracker.insert(joinCmd);

    CPPUNIT_ASSERT(op.isBlocked(tracker));
}

} // distributor
} // storage
