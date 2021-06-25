// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/bucket_spaces_stats_provider.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/text/stringtokenizer.h>

using document::Bucket;
using document::BucketId;
using document::BucketSpace;
using document::FixedBucketSpaces;
using document::test::makeBucketSpace;
using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

/**
 * This was copied from LegacyDistributorTest and adjusted to work with one distributor stripe.
 */
struct DistributorStripeTest : Test, DistributorStripeTestUtil {
    DistributorStripeTest();
    ~DistributorStripeTest() override;

    std::vector<document::BucketSpace> _bucketSpaces;

    void SetUp() override {
        createLinks();
        _bucketSpaces = getBucketSpaces();
    };

    void TearDown() override {
        close();
    }

    // Simple type aliases to make interfacing with certain utility functions
    // easier. Note that this is only for readability and does not provide any
    // added type safety.
    using NodeCount = int;
    using Redundancy = int;

    std::string testOp(std::shared_ptr<api::StorageMessage> msg) {
        _stripe->handleMessage(msg);

        std::string tmp = _sender.getCommands();
        _sender.clear();
        return tmp;
    }

};

DistributorStripeTest::DistributorStripeTest()
    : Test(),
      DistributorStripeTestUtil(),
      _bucketSpaces()
{
}

DistributorStripeTest::~DistributorStripeTest() = default;

TEST_F(DistributorStripeTest, operation_generation) {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    document::BucketId bid;
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t");

    EXPECT_EQ("Remove", testOp(std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(bid),
            document::DocumentId("id:m:test:n=1:foo"),
            api::Timestamp(1234))));

    auto cmd = std::make_shared<api::CreateVisitorCommand>(makeBucketSpace(), "foo", "bar", "");
    cmd->addBucketToBeVisited(document::BucketId(16, 1));
    cmd->addBucketToBeVisited(document::BucketId());

    EXPECT_EQ("Visitor Create", testOp(cmd));
}

}
