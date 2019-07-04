// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storage/distributor/operations/idealstate/garbagecollectionoperation.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct GarbageCollectionOperationTest : Test, DistributorTestUtil {
    void SetUp() override {
        createLinks();
    };

    void TearDown() override {
        close();
    }
};

TEST_F(GarbageCollectionOperationTest, simple) {
    enableDistributorClusterState("distributor:1 storage:2");
    addNodesToBucketDB(document::BucketId(16, 1), "0=250/50/300,1=250/50/300");
    getConfig().setGarbageCollection("music.date < 34", 3600);

    GarbageCollectionOperation op("storage",
                                  BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                          toVector<uint16_t>(0, 1)));

    op.setIdealStateManager(&getIdealStateManager());
    op.start(_sender, framework::MilliSecTime(0));

    ASSERT_EQ(2, _sender.commands().size());

    getClock().setAbsoluteTimeInSeconds(34);

    for (uint32_t i = 0; i < 2; ++i) {
        std::shared_ptr<api::StorageCommand> msg = _sender.command(i);
        ASSERT_EQ(msg->getType(), api::MessageType::REMOVELOCATION);

        auto& tmp = dynamic_cast<api::RemoveLocationCommand&>(*msg);
        EXPECT_EQ("music.date < 34", tmp.getDocumentSelection());

        std::shared_ptr<api::StorageReply> reply(tmp.makeReply());
        auto& sreply = dynamic_cast<api::RemoveLocationReply&>(*reply);
        sreply.setBucketInfo(api::BucketInfo(666, 90, 500));

        op.receive(_sender, reply);
    }

    BucketDatabase::Entry entry = getBucket(document::BucketId(16, 1));
    ASSERT_TRUE(entry.valid());
    ASSERT_EQ(2, entry->getNodeCount());
    EXPECT_EQ(34, entry->getLastGarbageCollectionTime());
    EXPECT_EQ(api::BucketInfo(666, 90, 500), entry->getNodeRef(0).getBucketInfo());
    EXPECT_EQ(api::BucketInfo(666, 90, 500), entry->getNodeRef(1).getBucketInfo());
}

} // storage::distributor
