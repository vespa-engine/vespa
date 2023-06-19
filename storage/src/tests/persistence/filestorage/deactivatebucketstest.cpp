// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/persistence/spi/test.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <vespa/vdslib/state/clusterstate.h>

using storage::spi::test::makeSpiBucket;
using namespace ::testing;

namespace storage {

struct DeactivateBucketsTest : FileStorTestFixture {
    std::unique_ptr<TestFileStorComponents> _c;

    [[nodiscard]] bool is_active(const document::BucketId&) const;

    void SetUp() override {
        FileStorTestFixture::SetUp();
        _c = std::make_unique<TestFileStorComponents>(*this);

        std::string up_state("storage:2 distributor:2");
        _node->getStateUpdater().setClusterState(
                std::make_shared<const lib::ClusterState>(up_state));

        createBucket(test_bucket());

        api::BucketInfo serviceLayerInfo(1, 2, 3, 4, 5, true, true);
        {
            StorBucketDatabase::WrappedEntry entry(
                    _node->getStorageBucketDatabase().get(test_bucket(), "foo",
                                                          StorBucketDatabase::CREATE_IF_NONEXISTING));
            entry->info = serviceLayerInfo;
            entry.write();
        }
    }

    void TearDown() override {
        _c.reset();
        FileStorTestFixture::TearDown();
    }

    static document::BucketId test_bucket() noexcept {
        return {8, 123};
    }

    static std::shared_ptr<const lib::ClusterState> state_of(const char* str) {
        return std::make_shared<const lib::ClusterState>(str);
    }
};

bool
DeactivateBucketsTest::is_active(const document::BucketId& bucket) const
{
    StorBucketDatabase::WrappedEntry entry(
            _node->getStorageBucketDatabase().get(bucket, "foo"));
    assert(entry.exists());
    return entry->info.isActive();
}

TEST_F(DeactivateBucketsTest, buckets_deactivated_when_node_marked_down)
{
    EXPECT_TRUE(is_active(test_bucket()));
    _node->getStateUpdater().setClusterState(state_of("storage:2 .1.s:d distributor:2"));
    // Buckets should have been deactivated in content layer
    EXPECT_FALSE(is_active(test_bucket()));
}

TEST_F(DeactivateBucketsTest, buckets_not_deactivated_when_node_marked_maintenance)
{
    EXPECT_TRUE(is_active(test_bucket()));
    _node->getStateUpdater().setClusterState(state_of("storage:2 .1.s:m distributor:2"));
    EXPECT_TRUE(is_active(test_bucket()));
}

TEST_F(DeactivateBucketsTest, buckets_deactivated_when_node_goes_from_maintenance_to_up)
{
    EXPECT_TRUE(is_active(test_bucket()));
    _node->getStateUpdater().setClusterState(state_of("storage:2 .1.s:m distributor:2"));
    _node->getStateUpdater().setClusterState(state_of("storage:2 distributor:2"));
    EXPECT_FALSE(is_active(test_bucket()));
}

TEST_F(DeactivateBucketsTest, buckets_deactivated_when_node_goes_from_maintenance_to_down)
{
    EXPECT_TRUE(is_active(test_bucket()));
    _node->getStateUpdater().setClusterState(state_of("storage:2 .1.s:m distributor:2"));
    _node->getStateUpdater().setClusterState(state_of("storage:2 .1.s:d distributor:2"));
    EXPECT_FALSE(is_active(test_bucket()));
}

// If we only have a subset of the bucket spaces in maintenance mode (i.e. global
// bucket merge enforcement), we treat this as the node being down from the perspective
// of default space bucket deactivation.
TEST_F(DeactivateBucketsTest, bucket_space_subset_in_maintenance_deactivates_buckets)
{
    EXPECT_TRUE(is_active(test_bucket()));
    auto derived = lib::ClusterStateBundle::BucketSpaceStateMapping({
        {document::FixedBucketSpaces::default_space(), state_of("storage:2 .1.s:m distributor:2")},
        {document::FixedBucketSpaces::global_space(),  state_of("storage:2 distributor:2")}
    });
    _node->getStateUpdater().setClusterStateBundle(
            std::make_shared<const lib::ClusterStateBundle>(*state_of("storage:2 .1.s:m distributor:2"),
                                                            std::move(derived)));
    EXPECT_FALSE(is_active(test_bucket()));
}

// TODO should also test SPI interaction

} // namespace storage
