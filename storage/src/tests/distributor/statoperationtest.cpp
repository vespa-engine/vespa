// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/operations/external/statbucketlistoperation.h>
#include <vespa/storage/distributor/operations/external/statbucketoperation.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct StatOperationTest : Test, DistributorStripeTestUtil {
    void SetUp() override {
        createLinks();
    };

    void TearDown() override {
        close();
    }
};

TEST_F(StatOperationTest, bucket_info) {
    enable_cluster_state("distributor:1 storage:2");

    addNodesToBucketDB(document::BucketId(16, 5), "0=4/2/100,1=4/2/100");

    StatBucketOperation op(
            getDistributorBucketSpace(),
            std::make_shared<api::StatBucketCommand>(
                    makeDocumentBucket(document::BucketId(16, 5)), ""));

    op.start(_sender);

    ASSERT_EQ("Statbucket => 0,Statbucket => 1", _sender.getCommands(true));

    {
        auto* tmp = static_cast<api::StatBucketCommand*>(_sender.command(0).get());
        auto reply = std::make_shared<api::StatBucketReply>(*tmp, "foo");
        op.receive(_sender, reply);
    }

    {
        auto* tmp = static_cast<api::StatBucketCommand*>(_sender.command(1).get());
        auto reply = std::make_shared<api::StatBucketReply>(*tmp, "bar");
        op.receive(_sender, reply);
    }

    auto* replyback = static_cast<api::StatBucketReply*>(_sender.replies().back().get());
    EXPECT_THAT(replyback->getResults(), HasSubstr("foo"));
    EXPECT_THAT(replyback->getResults(), HasSubstr("bar"));
}

TEST_F(StatOperationTest, bucket_list) {
    setup_stripe(2, 2, "distributor:1 storage:2");

    auto cfg = make_config();
    cfg->setSplitCount(10);
    cfg->setSplitSize(100);
    configure_stripe(cfg);

    for (uint32_t i = 0; i < 2; ++i) {
        insertBucketInfo(document::BucketId(16, 5), i,
                         0xff, 100, 200, true, (i == 1));
    }

    auto msg = std::make_shared<api::GetBucketListCommand>(makeDocumentBucket(document::BucketId(16, 5)));

    StatBucketListOperation op(
            getDistributorBucketSpace().getBucketDatabase(),
            getIdealStateManager(),
            node_context().node_index(),
            msg);
    op.start(_sender);

    ASSERT_EQ(1, _sender.replies().size());

    auto& repl = dynamic_cast<api::GetBucketListReply&>(*_sender.reply(0));

    ASSERT_EQ(1, repl.getBuckets().size());
    EXPECT_EQ(repl.getBuckets()[0]._bucket, document::BucketId(16, 5));
    EXPECT_EQ("[distributor:0] split: "
              "[Splitting bucket because its maximum size (200 b, 100 docs, 100 meta, 200 b total) "
              "is higher than the configured limit of (100, 10)] "
              "[node(idx=0,crc=0xff,docs=100/100,bytes=200/200,trusted=true,active=false,ready=false), "
              "node(idx=1,crc=0xff,docs=100/100,bytes=200/200,trusted=true,active=true,ready=false)]",
              repl.getBuckets()[0]._bucketInformation);
}

} // storage::distributor
