// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/operations/external/removelocationoperation.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

struct RemoveLocationOperationTest : Test, DistributorStripeTestUtil {
    std::unique_ptr<RemoveLocationOperation> op;

    void SetUp() override {
        createLinks();
    };

    void TearDown() override {
        close();
    }

    void sendRemoveLocation(const std::string& selection) {
        auto msg = std::make_shared<api::RemoveLocationCommand>(selection, makeDocumentBucket(document::BucketId(0)));

        op = std::make_unique<RemoveLocationOperation>(
                node_context(),
                operation_context(),
                doc_selection_parser(),
                getDistributorBucketSpace(),
                msg,
                metrics().removelocations);

        op->start(_sender);
    }
};

TEST_F(RemoveLocationOperationTest, simple) {
    enable_cluster_state("distributor:1 storage:3");

    addNodesToBucketDB(document::BucketId(34, 0x000001234), "0=1,1=1");
    addNodesToBucketDB(document::BucketId(34, 0x100001234), "0=1,2=1");
    addNodesToBucketDB(document::BucketId(34, 0x200001234), "0=1,2=1");
    addNodesToBucketDB(document::BucketId(34, 0x300001234), "1=1,2=1");

    sendRemoveLocation("id.user=4660");

    ASSERT_EQ("Remove selection(id.user=4660): BucketInfoCommand() => 0,"
              "Remove selection(id.user=4660): BucketInfoCommand() => 1,"
              "Remove selection(id.user=4660): BucketInfoCommand() => 0,"
              "Remove selection(id.user=4660): BucketInfoCommand() => 2,"
              "Remove selection(id.user=4660): BucketInfoCommand() => 0,"
              "Remove selection(id.user=4660): BucketInfoCommand() => 2,"
              "Remove selection(id.user=4660): BucketInfoCommand() => 1,"
              "Remove selection(id.user=4660): BucketInfoCommand() => 2",
              _sender.getCommands(true, true));

    for (uint32_t i = 0; i < 8; ++i) {
        sendReply(*op, i);
    }

    ASSERT_EQ("BucketInfoReply(BucketInfo(invalid)) ReturnCode(NONE)",
              _sender.getLastReply());
}

} // storage::distributor
