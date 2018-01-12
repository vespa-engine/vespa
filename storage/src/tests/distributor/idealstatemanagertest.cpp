// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdstestlib/cppunit/macros.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/operations/idealstate/mergeoperation.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>

using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;

namespace storage {
namespace distributor {

class IdealStateManagerTest : public CppUnit::TestFixture,
                              public DistributorTestUtil
{
public:
    IdealStateManagerTest() {}
    void setUp() override {
        createLinks();
    };

    void tearDown() override {
        close();
    }

    void testSibling();
    void testClearActiveOnNodeDown();
    void testRecheckWhenActive();
    void testRecheckWhenPending();
    void testOpsGenerationBusy();
    void testStatusPage();
    void testDisabledStateChecker();
    void testBlockIdealStateOpsOnFullRequestBucketInfo();
    void testBlockCheckForAllOperationsToSpecificBucket();

    void setSystemState(const lib::ClusterState& systemState) {
        _distributor->enableClusterState(systemState);
    }

    CPPUNIT_TEST_SUITE(IdealStateManagerTest);
    CPPUNIT_TEST(testSibling);
    CPPUNIT_TEST(testClearActiveOnNodeDown);
    CPPUNIT_TEST(testRecheckWhenActive);
    CPPUNIT_TEST(testStatusPage);
    CPPUNIT_TEST(testDisabledStateChecker);
    CPPUNIT_TEST(testBlockIdealStateOpsOnFullRequestBucketInfo);
    CPPUNIT_TEST(testBlockCheckForAllOperationsToSpecificBucket);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(IdealStateManagerTest);

void
IdealStateManagerTest::testSibling()
{
    CPPUNIT_ASSERT_EQUAL(document::BucketId(1,1),
                         getIdealStateManager().getDistributorComponent()
                         .getSibling(document::BucketId(1, 0)));
    CPPUNIT_ASSERT_EQUAL(document::BucketId(1,0),
                         getIdealStateManager().getDistributorComponent()
                         .getSibling(document::BucketId(1, 1)));
    CPPUNIT_ASSERT_EQUAL(document::BucketId(2,3),
                         getIdealStateManager().getDistributorComponent()
                         .getSibling(document::BucketId(2, 1)));
    CPPUNIT_ASSERT_EQUAL(document::BucketId(2,1),
                         getIdealStateManager().getDistributorComponent()
                         .getSibling(document::BucketId(2, 3)));
}

void
IdealStateManagerTest::testStatusPage() {
    close();
    getDirConfig().getConfig("stor-distributormanager").set("splitsize", "100");
    getDirConfig().getConfig("stor-distributormanager").set("splitcount", "1000000");
    getDirConfig().getConfig("stor-distributormanager").set("joinsize", "0");
    getDirConfig().getConfig("stor-distributormanager").set("joincount", "0");
    createLinks();
    setupDistributor(1, 1, "distributor:1 storage:1");

    insertBucketInfo(document::BucketId(16, 5), 0, 0xff, 100, 200, true, true);
    insertBucketInfo(document::BucketId(16, 2), 0, 0xff, 10, 10, true, true);

    std::ostringstream ost;
    getIdealStateManager().getBucketStatus(ost);

    CPPUNIT_ASSERT_EQUAL(std::string("<h2>default - BucketSpace(0x0000000000000001)</h2>\n"
                                     "BucketId(0x4000000000000002) : [node(idx=0,crc=0xff,docs=10/10,bytes=10/10,trusted=true,active=true,ready=false)]<br>\n"
                                     "<b>BucketId(0x4000000000000005):</b> <i> : split: [Splitting bucket because its maximum size (200 b, 100 docs, 100 meta, 200 b total) is "
                                     "higher than the configured limit of (100, 1000000)]</i> [node(idx=0,crc=0xff,docs=100/100,bytes=200/200,trusted=true,"
                                     "active=true,ready=false)]<br>\n"),
                         ost.str());
}

void
IdealStateManagerTest::testDisabledStateChecker() {
    setupDistributor(1, 1, "distributor:1 storage:1");

    getConfig().setSplitSize(100);
    getConfig().setSplitCount(1000000);
    getConfig().disableStateChecker("SplitBucket");

    insertBucketInfo(document::BucketId(16, 5), 0, 0xff, 100, 200, true, true);
    insertBucketInfo(document::BucketId(16, 2), 0, 0xff, 10, 10, true, true);

    std::ostringstream ost;
    getIdealStateManager().getBucketStatus(ost);

    CPPUNIT_ASSERT_EQUAL(std::string(
        "<h2>default - BucketSpace(0x0000000000000001)</h2>\n"
        "BucketId(0x4000000000000002) : [node(idx=0,crc=0xff,docs=10/10,bytes=10/10,trusted=true,active=true,ready=false)]<br>\n"
         "<b>BucketId(0x4000000000000005):</b> <i> : split: [Splitting bucket because its maximum size (200 b, 100 docs, 100 meta, 200 b total) is "
         "higher than the configured limit of (100, 1000000)]</i> [node(idx=0,crc=0xff,docs=100/100,bytes=200/200,trusted=true,"
         "active=true,ready=false)]<br>\n"),
         ost.str());

    tick();
    CPPUNIT_ASSERT_EQUAL(std::string(""),
                         _distributor->getActiveIdealStateOperations());

}

void
IdealStateManagerTest::testClearActiveOnNodeDown()
{
    setSystemState(lib::ClusterState("distributor:1 storage:3"));
    for (int i = 1; i < 4; i++) {
        insertBucketInfo(document::BucketId(16, i), 0, 0xff, 100, 200);
        insertBucketInfo(document::BucketId(16, i), 1, 0xffe, 1020, 2300);
        insertBucketInfo(document::BucketId(16, i), 2, 0xfff, 1030, 2400);
    }

    tick();

    // Start all three operations.
    for (uint32_t i = 0; i < 3; ++i) {
        tick();
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n"
                        "setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000002)) (pri 100)\n"
                        "setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000003)) (pri 100)\n"),
                         _distributor->getActiveIdealStateOperations());

    setSystemState(lib::ClusterState("distributor:1 storage:3 .0.s:d"));

    CPPUNIT_ASSERT_EQUAL(std::string(""),
                         _distributor->getActiveIdealStateOperations());
    CPPUNIT_ASSERT_EQUAL(uint32_t(0),
                         _distributor->getPendingMessageTracker()
                         .getNodeInfo().getPendingCount(0));
}

void
IdealStateManagerTest::testRecheckWhenActive()
{
    for (uint32_t j = 0; j < 3; j++) {
        insertBucketInfo(document::BucketId(16, 1), j, 0xff - j, 100, 200);
    }

    setSystemState(lib::ClusterState("distributor:1 storage:3"));

    tick();

    CPPUNIT_ASSERT_EQUAL(
            std::string("setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n"),
            _distributor->getActiveIdealStateOperations());

    tick();

    CPPUNIT_ASSERT_EQUAL(
            std::string("setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n"),
            _distributor->getActiveIdealStateOperations());

    tick();

    CPPUNIT_ASSERT_EQUAL(
            std::string("setbucketstate to [0] Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)) (pri 100)\n"),
            _distributor->getActiveIdealStateOperations());
}

void
IdealStateManagerTest::testBlockIdealStateOpsOnFullRequestBucketInfo()
{
    setupDistributor(2, 10, "distributor:1 storage:2");

    framework::defaultimplementation::FakeClock clock;
    PendingMessageTracker tracker(_node->getComponentRegister());

    document::BucketId bid(16, 1234);
    std::vector<document::BucketId> buckets;

    // RequestBucketInfoCommand does not have a specific bucketid since it's
    // sent to the entire node. It will then use a null bucketid.
    {
        std::shared_ptr<api::RequestBucketInfoCommand> msg(
                new api::RequestBucketInfoCommand(makeBucketSpace(), buckets));
        msg->setAddress(
                api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 4));
        tracker.insert(msg);
    }

    {
        RemoveBucketOperation op("storage",
                                 BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(3, 4)));
        CPPUNIT_ASSERT(op.isBlocked(tracker));
    }

    {
        // Don't trigger on requests to other nodes.
        RemoveBucketOperation op("storage",
                                 BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(3, 5)));
        CPPUNIT_ASSERT(!op.isBlocked(tracker));
    }

    // Don't block on null-bucket messages that aren't RequestBucketInfo.
    {
        std::shared_ptr<api::CreateVisitorCommand> msg(
                new api::CreateVisitorCommand(makeBucketSpace(), "foo", "bar", "baz"));
        msg->setAddress(
                api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 7));
        tracker.insert(msg);
    }

    {
        RemoveBucketOperation op("storage",
                                 BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(7)));
        CPPUNIT_ASSERT(!op.isBlocked(tracker));
    }
}

void
IdealStateManagerTest::testBlockCheckForAllOperationsToSpecificBucket()
{
    setupDistributor(2, 10, "distributor:1 storage:2");
    framework::defaultimplementation::FakeClock clock;
    PendingMessageTracker tracker(_node->getComponentRegister());
    document::BucketId bid(16, 1234);

    {
        auto msg = std::make_shared<api::JoinBucketsCommand>(makeDocumentBucket(bid));
        msg->setAddress(
                api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 4));
        tracker.insert(msg);
    }
    {
        RemoveBucketOperation op("storage",
                                 BucketAndNodes(makeDocumentBucket(bid), toVector<uint16_t>(7)));
        // Not blocked for exact node match.
        CPPUNIT_ASSERT(!op.checkBlock(makeDocumentBucket(bid), tracker));
        // But blocked for bucket match!
        CPPUNIT_ASSERT(op.checkBlockForAllNodes(makeDocumentBucket(bid), tracker));
    }
}

} // distributor
} // storage

