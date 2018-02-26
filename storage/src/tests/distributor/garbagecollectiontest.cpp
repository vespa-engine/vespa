// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storage/distributor/operations/idealstate/garbagecollectionoperation.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/document/test/make_document_bucket.h>

using document::test::makeDocumentBucket;

namespace storage {
namespace distributor {

class GarbageCollectionOperationTest : public CppUnit::TestFixture, public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(GarbageCollectionOperationTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST_SUITE_END();

protected:
    void testSimple();

public:
    void setUp() override {
        createLinks();
    };

    void tearDown() override {
        close();
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(GarbageCollectionOperationTest);

void
GarbageCollectionOperationTest::testSimple()
{
    enableDistributorClusterState("distributor:1 storage:2");
    addNodesToBucketDB(document::BucketId(16, 1), "0=250/50/300,1=250/50/300");
    getConfig().setGarbageCollection("music.date < 34", 3600);

    GarbageCollectionOperation op("storage",
                                  BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                          toVector<uint16_t>(0, 1)));

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    CPPUNIT_ASSERT_EQUAL((size_t)2, _sender.commands.size());

    getClock().setAbsoluteTimeInSeconds(34);

    for (uint32_t i = 0; i < 2; ++i) {
        std::shared_ptr<api::StorageCommand> msg  = _sender.commands[i];
        CPPUNIT_ASSERT(msg->getType() == api::MessageType::REMOVELOCATION);

        api::RemoveLocationCommand* tmp = (api::RemoveLocationCommand*)msg.get();
        CPPUNIT_ASSERT_EQUAL(vespalib::string("music.date < 34"),
                                         tmp->getDocumentSelection());

        std::shared_ptr<api::StorageReply> reply(tmp->makeReply().release());
        api::RemoveLocationReply* sreply = (api::RemoveLocationReply*)reply.get();
        sreply->setBucketInfo(api::BucketInfo(666, 90, 500));

        op.receive(_sender, reply);
    }

    BucketDatabase::Entry entry = getBucket(document::BucketId(16, 1));
    CPPUNIT_ASSERT(entry.valid());
    CPPUNIT_ASSERT_EQUAL(2, (int)entry->getNodeCount());
    CPPUNIT_ASSERT_EQUAL(34, (int)entry->getLastGarbageCollectionTime());
    CPPUNIT_ASSERT_EQUAL(api::BucketInfo(666, 90, 500),
                         entry->getNodeRef(0).getBucketInfo());
    CPPUNIT_ASSERT_EQUAL(api::BucketInfo(666, 90, 500),
                         entry->getNodeRef(1).getBucketInfo());
}

} // distributor
} // storage
