// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cppunit/extensions/HelperMacros.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storage/distributor/operations/idealstate/removebucketoperation.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/distributor.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>

using document::test::makeDocumentBucket;

namespace storage {
namespace distributor {

class RemoveBucketOperationTest : public CppUnit::TestFixture,
                                  public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(RemoveBucketOperationTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testBucketInfoMismatchFailure);
    CPPUNIT_TEST(testFailWithInvalidBucketInfo);
    CPPUNIT_TEST_SUITE_END();

protected:
    void testSimple();
    void testBucketInfoMismatchFailure();
    void testFailWithInvalidBucketInfo();

public:
    void setUp() override {
        createLinks();
    };

    void tearDown() override {
        close();
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(RemoveBucketOperationTest);

void
RemoveBucketOperationTest::testSimple()
{
    addNodesToBucketDB(document::BucketId(16, 1),
                       "0=10/100/1/t,"
                       "1=10/100/1/t,"
                       "2=10/100/1/t");
    setRedundancy(1);
    enableDistributorClusterState("distributor:1 storage:3");

    RemoveBucketOperation op("storage",
                             BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(1,2)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));


    CPPUNIT_ASSERT_EQUAL(std::string("Delete bucket => 1,"
                                     "Delete bucket => 2"),
                         _sender.getCommands(true));

    sendReply(op, 0);
    sendReply(op, 1);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x4000000000000001) : "
                    "node(idx=0,crc=0xa,docs=100/100,bytes=1/1,trusted=true,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 1)));
}

/**
 * Test that receiving a DeleteBucket failure from a storage node that sends
 * back actual bucket info reinserts that bucket info into the distributor
 * bucket database.
 */
void
RemoveBucketOperationTest::testBucketInfoMismatchFailure()
{
    addNodesToBucketDB(document::BucketId(16, 1), "1=0/0/0/t");

    getComponentRegisterImpl().setDistribution(std::shared_ptr<lib::Distribution>(
            new lib::Distribution(
                lib::Distribution::getDefaultDistributionConfig(1, 10))));

    enableDistributorClusterState("distributor:1 storage:2");

    RemoveBucketOperation op("storage",
                             BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(1)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Delete bucket => 1"),
                         _sender.getCommands(true));

    CPPUNIT_ASSERT_EQUAL((size_t) 1, _sender.commands.size());
    std::shared_ptr<api::StorageCommand> msg2  = _sender.commands[0];
    std::shared_ptr<api::StorageReply> reply(msg2->makeReply().release());
    dynamic_cast<api::DeleteBucketReply&>(*reply).setBucketInfo(
            api::BucketInfo(10, 100, 1));
    reply->setResult(api::ReturnCode::REJECTED);
    op.receive(_sender, reply);

    // RemoveBucketOperation should reinsert bucketinfo into database
    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x4000000000000001) : "
                    "node(idx=1,crc=0xa,docs=100/100,bytes=1/1,trusted=true,active=false,ready=false)"),
            dumpBucket(document::BucketId(16, 1)));
}

/**
 * Test that receiving a DeleteBucket failure from a storage node that does
 * not include valid BucketInfo in its reply does not reinsert the bucket
 * into the distributor.
 */
void
RemoveBucketOperationTest::testFailWithInvalidBucketInfo()
{
    addNodesToBucketDB(document::BucketId(16, 1), "1=0/0/0/t");

    getComponentRegisterImpl().setDistribution(std::shared_ptr<lib::Distribution>(
            new lib::Distribution(
                lib::Distribution::getDefaultDistributionConfig(1, 10))));

    enableDistributorClusterState("distributor:1 storage:2");

    RemoveBucketOperation op("storage",
                             BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                     toVector<uint16_t>(1)));
    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Delete bucket => 1"),
                         _sender.getCommands(true));

    CPPUNIT_ASSERT_EQUAL((size_t) 1, _sender.commands.size());
    std::shared_ptr<api::StorageCommand> msg2  = _sender.commands[0];
    std::shared_ptr<api::StorageReply> reply(msg2->makeReply().release());
    reply->setResult(api::ReturnCode::ABORTED);
    op.receive(_sender, reply);

    CPPUNIT_ASSERT_EQUAL(std::string("NONEXISTING"),
                         dumpBucket(document::BucketId(16, 1)));
}

} // distributor
} // storage
