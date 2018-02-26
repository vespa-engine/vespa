// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <iomanip>
#include <tests/common/dummystoragelink.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storage/distributor/operations/external/removelocationoperation.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/distributor.h>

using document::test::makeDocumentBucket;

namespace storage {
namespace distributor {

class RemoveLocationOperationTest : public CppUnit::TestFixture,
                                    public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(RemoveLocationOperationTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST_SUITE_END();

protected:
    void testSimple();

public:
    std::unique_ptr<RemoveLocationOperation> op;

    void setUp() override {
        createLinks();
    };

    void tearDown() override {
        close();
    }

    void sendRemoveLocation(const std::string& selection) {
        std::shared_ptr<api::RemoveLocationCommand> msg(
                new api::RemoveLocationCommand(selection, makeDocumentBucket(document::BucketId(0))));

        op.reset(new RemoveLocationOperation(getExternalOperationHandler(),
                                             getDistributorBucketSpace(),
                                             msg,
                                             getDistributor().getMetrics().
                                             removelocations[msg->getLoadType()]));

        op->start(_sender, framework::MilliSecTime(0));
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(RemoveLocationOperationTest);

void
RemoveLocationOperationTest::testSimple()
{
    enableDistributorClusterState("distributor:1 storage:3");

    addNodesToBucketDB(document::BucketId(34, 0x000001234), "0=1,1=1");
    addNodesToBucketDB(document::BucketId(34, 0x100001234), "0=1,2=1");
    addNodesToBucketDB(document::BucketId(34, 0x200001234), "0=1,2=1");
    addNodesToBucketDB(document::BucketId(34, 0x300001234), "1=1,2=1");

    sendRemoveLocation("id.user=4660");

    CPPUNIT_ASSERT_EQUAL(
            std::string("Remove selection(id.user=4660): BucketInfoCommand() => 0,"
                        "Remove selection(id.user=4660): BucketInfoCommand() => 1,"
                        "Remove selection(id.user=4660): BucketInfoCommand() => 0,"
                        "Remove selection(id.user=4660): BucketInfoCommand() => 2,"
                        "Remove selection(id.user=4660): BucketInfoCommand() => 0,"
                        "Remove selection(id.user=4660): BucketInfoCommand() => 2,"
                        "Remove selection(id.user=4660): BucketInfoCommand() => 1,"
                        "Remove selection(id.user=4660): BucketInfoCommand() => 2"),
            _sender.getCommands(true, true));

    for (uint32_t i = 0; i < 8; ++i) {
        sendReply(*op, i);
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketInfoReply(BucketInfo(invalid)) ReturnCode(NONE)"),
            _sender.getLastReply());
}

} // distributor
} // storage
