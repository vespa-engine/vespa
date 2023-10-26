// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/operations/idealstate/mergelimiter.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace storage::distributor {

namespace {

using BucketCopyPtr = std::unique_ptr<BucketCopy>;
std::vector<BucketCopyPtr> _bucketDatabase;

struct NodeFactory {
    std::vector<MergeMetaData> _nodes;

    NodeFactory& add(int index, int crc) {
        _bucketDatabase.emplace_back(
                std::make_unique<BucketCopy>(0, index, api::BucketInfo(crc, 5, 10)));
        _nodes.emplace_back(MergeMetaData(index, *_bucketDatabase.back()));
        return *this;
    }
    NodeFactory& addTrusted(int index, int crc) {
        add(index, crc);
        _bucketDatabase.back()->setTrusted(true);
        return *this;
    }
    NodeFactory& addMissing(int index) {
        add(index, 0x1); // "Magic" checksum value implying invalid/recently created replica
        return *this;
    }
    NodeFactory& addEmpty(int index) {
        add(index, 0x0);
        return *this;
    }
    NodeFactory& setSourceOnly() {
        _nodes.back()._sourceOnly = true;
        return *this;
    }

    operator const MergeLimiter::NodeArray&() const { return _nodes; }
};

}

struct MergeLimiterTest : Test {

    static std::string limit(uint32_t max_nodes, std::vector<MergeMetaData> nodes) {
        MergeLimiter limiter(max_nodes);
        limiter.limitMergeToMaxNodes(nodes);
        std::ostringstream actual;
        for (uint32_t i = 0; i < nodes.size(); ++i) {
            if (i != 0) {
                actual << ",";
            }
            actual << nodes[i]._nodeIndex;
            if (nodes[i]._sourceOnly) {
                actual << 's';
            }
        }
        return actual.str();
    }

};

// If there is <= max nodes, then none should be removed.
TEST_F(MergeLimiterTest, keeps_all_below_limit) {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(3, 0x4)
        .addTrusted(5, 0x4)
        .add(9, 0x6)
        .add(2, 0x6)
        .add(4, 0x5));

    ASSERT_EQ(limit(8, nodes), "3,5,9,2,4");
}
// If less than max nodes is untrusted, merge all untrusted copies with a
// trusted one. (Optionally with extra trusted copies if there is space)
TEST_F(MergeLimiterTest, less_than_max_untrusted) {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(3, 0x4)
        .addTrusted(5, 0x4)
        .add(9, 0x6)
        .add(2, 0x6)
        .add(4, 0x5));
    ASSERT_EQ(limit(4, nodes), "2,4,9,5");
}
// With more than max untrusted, just merge one trusted with as many untrusted
// that fits.
TEST_F(MergeLimiterTest, more_than_max_untrusted) {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(3, 0x4)
        .addTrusted(5, 0x4)
        .add(9, 0x6)
        .add(2, 0x6)
        .add(13, 0x9)
        .add(1, 0x7)
        .add(4, 0x5));
    ASSERT_EQ(limit(4, nodes), "2,13,1,5");
}
// With nothing trusted. If there is <= max different variants (checksums),
// merge one of each variant. After this merge, all these nodes can be set
// trusted. (Except for any source only ones)
TEST_F(MergeLimiterTest, all_untrusted_less_than_max_variants) {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .add(3, 0x4)
        .add(5, 0x4)
        .add(9, 0x6)
        .add(2, 0x6)
        .add(13, 0x3)
        .add(1, 0x3)
        .add(4, 0x3));
    ASSERT_EQ(limit(4, nodes), "5,2,4,3");
}
// With nothing trusted and more than max variants, we just have to merge one
// of each variant until we end up with less than max variants.
TEST_F(MergeLimiterTest, all_untrusted_more_than_max_variants) {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .add(3, 0x4)
        .add(5, 0x5)
        .add(9, 0x6)
        .add(2, 0x6)
        .add(13, 0x3)
        .add(1, 0x9)
        .add(4, 0x8));
    ASSERT_EQ(limit(4, nodes), "3,5,2,13");
}

// With more than max untrusted, just merge one trusted with as many untrusted
// that fits.
TEST_F(MergeLimiterTest, source_only_last) {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(3, 0x4)
        .addTrusted(5, 0x4).setSourceOnly()
        .add(9, 0x6)
        .add(2, 0x6).setSourceOnly()
        .add(13, 0x9)
        .add(1, 0x7)
        .add(4, 0x5));
    ASSERT_EQ(limit(4, nodes), "9,3,5s,2s");
}

TEST_F(MergeLimiterTest, limited_set_cannot_be_just_source_only) {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(9, 0x6)
        .addTrusted(2, 0x6)
        .addTrusted(13, 0x6).setSourceOnly()
        .add(1, 0x7).setSourceOnly());
    ASSERT_EQ(limit(2, nodes), "2,13s");
    ASSERT_EQ(limit(3, nodes), "2,13s,1s");
}

TEST_F(MergeLimiterTest, non_source_only_replica_chosen_from_in_sync_group) {
    // nodes 9, 2, 13 are all in sync. Merge limiter will currently by default
    // pop the _last_ node of an in-sync replica "group" when outputting a limited
    // set. Unless we special-case source-only replicas here, we'd end up with an
    // output set of "13s,1s", i.e. all source-only.
    MergeLimiter::NodeArray nodes(NodeFactory()
        .add(9, 0x6)
        .add(2, 0x6)
        .add(13, 0x6).setSourceOnly()
        .add(1, 0x7).setSourceOnly());
    ASSERT_EQ(limit(2, nodes), "2,13s");
    ASSERT_EQ(limit(3, nodes), "2,13s,1s");
}

TEST_F(MergeLimiterTest, non_source_only_replicas_preferred_when_replicas_not_in_sync) {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .add(9, 0x4)
        .add(2, 0x5)
        .add(13, 0x6).setSourceOnly()
        .add(1, 0x7).setSourceOnly());
    ASSERT_EQ(limit(2, nodes), "9,2");
    ASSERT_EQ(limit(3, nodes), "9,2,13s");
}

TEST_F(MergeLimiterTest, at_least_one_non_source_only_replica_chosen_when_all_trusted) {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(9, 0x6)
        .addTrusted(2, 0x6)
        .addTrusted(13, 0x6).setSourceOnly()
        .addTrusted(1, 0x6).setSourceOnly());
    ASSERT_EQ(limit(2, nodes), "2,13s");
    ASSERT_EQ(limit(3, nodes), "2,13s,1s");
}

TEST_F(MergeLimiterTest, missing_replica_distinct_from_empty_replica) {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addEmpty(3)
        .addEmpty(5)
        .addMissing(1)
        .addMissing(2));
    ASSERT_EQ(limit(2, nodes), "5,2");
    ASSERT_EQ(limit(3, nodes), "5,2,3");
}

} // storage::distributor
