// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/dummystoragelink.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/operations/external/statbucketoperation.h>
#include <vespa/storage/distributor/operations/external/statbucketlistoperation.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>

using document::test::makeDocumentBucket;

namespace storage {
namespace distributor {

struct StatOperationTest : public CppUnit::TestFixture,
                           public DistributorTestUtil
{
    void setUp() override {
        createLinks();
    };

    void tearDown() override {
        close();
    }

    void testBucketInfo();
    void testBucketList();

    CPPUNIT_TEST_SUITE(StatOperationTest);
    CPPUNIT_TEST(testBucketInfo);
    CPPUNIT_TEST(testBucketList);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(StatOperationTest);

void
StatOperationTest::testBucketInfo()
{
    enableDistributorClusterState("distributor:1 storage:2");

    addNodesToBucketDB(document::BucketId(16, 5),
                       "0=4/2/100,1=4/2/100");

    StatBucketOperation op(
            getExternalOperationHandler(),
            getDistributorBucketSpace(),
            std::shared_ptr<api::StatBucketCommand>(
                    new api::StatBucketCommand(makeDocumentBucket(document::BucketId(16, 5)), "")));

    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(std::string("Statbucket => 0,Statbucket => 1"),
                         _sender.getCommands(true));

    {
        api::StatBucketCommand* tmp(
                static_cast<api::StatBucketCommand*>(_sender.commands[0].get()));
        api::StatBucketReply* reply = new api::StatBucketReply(*tmp, "foo");
        op.receive(_sender, std::shared_ptr<api::StorageReply>(reply));
    }

    {
        api::StatBucketCommand* tmp(
                static_cast<api::StatBucketCommand*>(_sender.commands[1].get()));
        api::StatBucketReply* reply = new api::StatBucketReply(*tmp, "bar");
        op.receive(_sender, std::shared_ptr<api::StorageReply>(reply));
    }

    api::StatBucketReply* replyback(
            static_cast<api::StatBucketReply*>(_sender.replies.back().get()));
    CPPUNIT_ASSERT_CONTAIN("foo", replyback->getResults());
    CPPUNIT_ASSERT_CONTAIN("bar", replyback->getResults());
}

void
StatOperationTest::testBucketList() {
    setupDistributor(2, 2, "distributor:1 storage:2");

    getConfig().setSplitCount(10);
    getConfig().setSplitSize(100);

    for (uint32_t i = 0; i < 2; ++i) {
        insertBucketInfo(document::BucketId(16, 5), i,
                         0xff, 100, 200, true, (i == 1));
    }

    std::shared_ptr<api::GetBucketListCommand> msg(
            new api::GetBucketListCommand(makeDocumentBucket(document::BucketId(16, 5))));

    StatBucketListOperation op(
            getDistributorBucketSpace().getBucketDatabase(),
            getIdealStateManager(),
            getExternalOperationHandler().getIndex(),
            msg);
    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL(1, (int)_sender.replies.size());

    api::GetBucketListReply* repl(
            dynamic_cast<api::GetBucketListReply*>(_sender.replies[0].get()));

    CPPUNIT_ASSERT_EQUAL(1, (int)repl->getBuckets().size());
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 5),
                         repl->getBuckets()[0]._bucket);
    CPPUNIT_ASSERT_EQUAL(
            vespalib::string(
                    "[distributor:0] split: "
                    "[Splitting bucket because its maximum size (200 b, 100 docs, 100 meta, 200 b total) "
                    "is higher than the configured limit of (100, 10)] "
                    "[node(idx=0,crc=0xff,docs=100/100,bytes=200/200,trusted=true,active=false,ready=false), "
                    "node(idx=1,crc=0xff,docs=100/100,bytes=200/200,trusted=true,active=true,ready=false)]"),
            repl->getBuckets()[0]._bucketInformation);
}

} // distributor
} // storage
