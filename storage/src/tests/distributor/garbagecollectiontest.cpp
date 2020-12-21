// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storage/distributor/operations/idealstate/garbagecollectionoperation.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/gtest/gtest.h>
#include "dummy_cluster_context.h"

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct GarbageCollectionOperationTest : Test, DistributorTestUtil {
    void SetUp() override {
        createLinks();
        enableDistributorClusterState("distributor:1 storage:2");
        addNodesToBucketDB(document::BucketId(16, 1), "0=250/50/300,1=250/50/300");
        getConfig().setGarbageCollection("music.date < 34", 3600s);
        getClock().setAbsoluteTimeInSeconds(34);
    };

    void TearDown() override {
        close();
    }

    std::shared_ptr<GarbageCollectionOperation> create_op() {
        auto op = std::make_shared<GarbageCollectionOperation>(
                dummy_cluster_context, BucketAndNodes(makeDocumentBucket(document::BucketId(16, 1)),
                                                            toVector<uint16_t>(0, 1)));
        op->setIdealStateManager(&getIdealStateManager());
        return op;
    }

    // FIXME fragile to assume that send order == node index, but that's the way it currently works
    void reply_to_nth_request(GarbageCollectionOperation& op, size_t n,
                              uint32_t bucket_info_checksum, uint32_t n_docs_removed) {
        auto msg = _sender.command(n);
        assert(msg->getType() == api::MessageType::REMOVELOCATION);
        std::shared_ptr<api::StorageReply> reply(msg->makeReply());
        auto& gc_reply = dynamic_cast<api::RemoveLocationReply&>(*reply);
        gc_reply.set_documents_removed(n_docs_removed);
        gc_reply.setBucketInfo(api::BucketInfo(bucket_info_checksum, 90, 500));

        op.receive(_sender, reply);
    }

    void assert_bucket_db_contains(std::vector<api::BucketInfo> info, uint32_t last_gc_time) {
        BucketDatabase::Entry entry = getBucket(document::BucketId(16, 1));
        ASSERT_TRUE(entry.valid());
        ASSERT_EQ(entry->getNodeCount(), info.size());
        EXPECT_EQ(entry->getLastGarbageCollectionTime(), last_gc_time);
        for (size_t i = 0; i < info.size(); ++i) {
            EXPECT_EQ(info[i], entry->getNode(i)->getBucketInfo())
                    << "Mismatching info for node " << i << ": " << info[i] << " vs "
                    << entry->getNode(i)->getBucketInfo();
        }
    }

    uint32_t gc_removed_documents_metric() {
        auto metric_base = getIdealStateManager().getMetrics().operations[IdealStateOperation::GARBAGE_COLLECTION];
        auto gc_metrics = std::dynamic_pointer_cast<GcMetricSet>(metric_base);
        assert(gc_metrics);
        return gc_metrics->documents_removed.getValue();
    }
};

TEST_F(GarbageCollectionOperationTest, simple) {
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));

    ASSERT_EQ(2, _sender.commands().size());
    EXPECT_EQ(0u, gc_removed_documents_metric());

    for (uint32_t i = 0; i < 2; ++i) {
        std::shared_ptr<api::StorageCommand> msg = _sender.command(i);
        ASSERT_EQ(msg->getType(), api::MessageType::REMOVELOCATION);
        auto& tmp = dynamic_cast<api::RemoveLocationCommand&>(*msg);
        EXPECT_EQ("music.date < 34", tmp.getDocumentSelection());
        reply_to_nth_request(*op, i, 777 + i, 50);
    }
    ASSERT_NO_FATAL_FAILURE(assert_bucket_db_contains({api::BucketInfo(777, 90, 500), api::BucketInfo(778, 90, 500)}, 34));
    EXPECT_EQ(50u, gc_removed_documents_metric());
}

TEST_F(GarbageCollectionOperationTest, replica_bucket_info_not_added_to_db_until_all_replies_received) {
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    ASSERT_EQ(2, _sender.commands().size());
    EXPECT_EQ(0u, gc_removed_documents_metric());

    // Respond to 1st request. Should _not_ cause bucket info to be merged into the database yet
    reply_to_nth_request(*op, 0, 1234, 70);
    ASSERT_NO_FATAL_FAILURE(assert_bucket_db_contains({api::BucketInfo(250, 50, 300), api::BucketInfo(250, 50, 300)}, 0));

    // Respond to 2nd request. This _should_ cause bucket info to be merged into the database.
    reply_to_nth_request(*op, 1, 4567, 60);
    ASSERT_NO_FATAL_FAILURE(assert_bucket_db_contains({api::BucketInfo(1234, 90, 500), api::BucketInfo(4567, 90, 500)}, 34));

    EXPECT_EQ(70u, gc_removed_documents_metric()); // Use max of received metrics
}

TEST_F(GarbageCollectionOperationTest, gc_bucket_info_does_not_overwrite_later_sequenced_bucket_info_writes) {
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    ASSERT_EQ(2, _sender.commands().size());

    reply_to_nth_request(*op, 0, 1234, 0);
    // Change to replica on node 0 happens after GC op, but before GC info is merged into the DB. Must not be lost.
    insertBucketInfo(op->getBucketId(), 0, 7777, 100, 2000);
    reply_to_nth_request(*op, 1, 4567, 0);
    // Bucket info for node 0 is that of the later sequenced operation, _not_ from the earlier GC op.
    ASSERT_NO_FATAL_FAILURE(assert_bucket_db_contains({api::BucketInfo(7777, 100, 2000), api::BucketInfo(4567, 90, 500)}, 34));
}

} // storage::distributor
