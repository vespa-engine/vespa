// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/operations/idealstate/mergelimiter.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage::distributor {

struct MergeLimiterTest : public CppUnit::TestFixture
{
    void testKeepsAllBelowLimit();
    void testLessThanMaxUntrusted();
    void testMoreThanMaxUntrusted();
    void testAllUntrustedLessThanMaxVariants();
    void testAllUntrustedMoreThanMaxVariants();
    void testSourceOnlyLast();
    void limited_set_cannot_be_just_source_only();
    void non_source_only_replica_chosen_from_in_sync_group();
    void non_source_only_replicas_preferred_when_replicas_not_in_sync();
    void at_least_one_non_source_only_replica_chosen_when_all_trusted();
    void missing_replica_distinct_from_empty_replica();

    CPPUNIT_TEST_SUITE(MergeLimiterTest);
    CPPUNIT_TEST(testKeepsAllBelowLimit);
    CPPUNIT_TEST(testLessThanMaxUntrusted);
    CPPUNIT_TEST(testMoreThanMaxUntrusted);
    CPPUNIT_TEST(testAllUntrustedLessThanMaxVariants);
    CPPUNIT_TEST(testAllUntrustedMoreThanMaxVariants);
    CPPUNIT_TEST(testSourceOnlyLast);
    CPPUNIT_TEST(limited_set_cannot_be_just_source_only);
    CPPUNIT_TEST(non_source_only_replica_chosen_from_in_sync_group);
    CPPUNIT_TEST(non_source_only_replicas_preferred_when_replicas_not_in_sync);
    CPPUNIT_TEST(at_least_one_non_source_only_replica_chosen_when_all_trusted);
    CPPUNIT_TEST(missing_replica_distinct_from_empty_replica);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MergeLimiterTest);

namespace {
    using BucketCopyPtr = std::unique_ptr<BucketCopy>;
    std::vector<BucketCopyPtr> _bucketDatabase;

    struct NodeFactory {
        std::vector<MergeMetaData> _nodes;

        NodeFactory& add(int index, int crc) {
            _bucketDatabase.push_back(BucketCopyPtr(
                    new BucketCopy(0, index, api::BucketInfo(crc, 5, 10))));
            _nodes.push_back(MergeMetaData(index, *_bucketDatabase.back()));
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

    #define ASSERT_LIMIT(maxNodes, nodes, result) \
    { \
        MergeLimiter limiter(maxNodes); \
        auto nodesCopy = nodes; \
        limiter.limitMergeToMaxNodes(nodesCopy); \
        std::ostringstream actual; \
        for (uint32_t i = 0; i < nodesCopy.size(); ++i) { \
            if (i != 0) actual << ","; \
            actual << nodesCopy[i]._nodeIndex; \
            if (nodesCopy[i]._sourceOnly) actual << 's'; \
        } \
        CPPUNIT_ASSERT_EQUAL(std::string(result), actual.str()); \
    }
}

// If there is <= max nodes, then none should be removed.
void
MergeLimiterTest::testKeepsAllBelowLimit()
{
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(3, 0x4)
        .addTrusted(5, 0x4)
        .add(9, 0x6)
        .add(2, 0x6)
        .add(4, 0x5));

    ASSERT_LIMIT(8, nodes, "3,5,9,2,4");
}
// If less than max nodes is untrusted, merge all untrusted copies with a
// trusted one. (Optionally with extra trusted copies if there is space)
void
MergeLimiterTest::testLessThanMaxUntrusted()
{
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(3, 0x4)
        .addTrusted(5, 0x4)
        .add(9, 0x6)
        .add(2, 0x6)
        .add(4, 0x5));
    ASSERT_LIMIT(4, nodes, "2,4,9,5");
}
// With more than max untrusted, just merge one trusted with as many untrusted
// that fits.
void
MergeLimiterTest::testMoreThanMaxUntrusted()
{
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(3, 0x4)
        .addTrusted(5, 0x4)
        .add(9, 0x6)
        .add(2, 0x6)
        .add(13, 0x9)
        .add(1, 0x7)
        .add(4, 0x5));
    ASSERT_LIMIT(4, nodes, "2,13,1,5");
}
// With nothing trusted. If there is <= max different variants (checksums),
// merge one of each variant. After this merge, all these nodes can be set
// trusted. (Except for any source only ones)
void
MergeLimiterTest::testAllUntrustedLessThanMaxVariants()
{
    MergeLimiter::NodeArray nodes(NodeFactory()
        .add(3, 0x4)
        .add(5, 0x4)
        .add(9, 0x6)
        .add(2, 0x6)
        .add(13, 0x3)
        .add(1, 0x3)
        .add(4, 0x3));
    ASSERT_LIMIT(4, nodes, "5,2,4,3");
}
// With nothing trusted and more than max variants, we just have to merge one
// of each variant until we end up with less than max variants.
void
MergeLimiterTest::testAllUntrustedMoreThanMaxVariants()
{
    MergeLimiter::NodeArray nodes(NodeFactory()
        .add(3, 0x4)
        .add(5, 0x5)
        .add(9, 0x6)
        .add(2, 0x6)
        .add(13, 0x3)
        .add(1, 0x9)
        .add(4, 0x8));
    ASSERT_LIMIT(4, nodes, "3,5,2,13");
}

// With more than max untrusted, just merge one trusted with as many untrusted
// that fits.
void
MergeLimiterTest::testSourceOnlyLast()
{
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(3, 0x4)
        .addTrusted(5, 0x4).setSourceOnly()
        .add(9, 0x6)
        .add(2, 0x6).setSourceOnly()
        .add(13, 0x9)
        .add(1, 0x7)
        .add(4, 0x5));
    ASSERT_LIMIT(4, nodes, "9,3,5s,2s");
}

void MergeLimiterTest::limited_set_cannot_be_just_source_only() {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(9, 0x6)
        .addTrusted(2, 0x6)
        .addTrusted(13, 0x6).setSourceOnly()
        .add(1, 0x7).setSourceOnly());
    ASSERT_LIMIT(2, nodes, "2,13s");
    ASSERT_LIMIT(3, nodes, "2,13s,1s");
}

void MergeLimiterTest::non_source_only_replica_chosen_from_in_sync_group() {
    // nodes 9, 2, 13 are all in sync. Merge limiter will currently by default
    // pop the _last_ node of an in-sync replica "group" when outputting a limited
    // set. Unless we special-case source-only replicas here, we'd end up with an
    // output set of "13s,1s", i.e. all source-only.
    MergeLimiter::NodeArray nodes(NodeFactory()
        .add(9, 0x6)
        .add(2, 0x6)
        .add(13, 0x6).setSourceOnly()
        .add(1, 0x7).setSourceOnly());
    ASSERT_LIMIT(2, nodes, "2,13s");
    ASSERT_LIMIT(3, nodes, "2,13s,1s");
}

void MergeLimiterTest::non_source_only_replicas_preferred_when_replicas_not_in_sync() {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .add(9, 0x4)
        .add(2, 0x5)
        .add(13, 0x6).setSourceOnly()
        .add(1, 0x7).setSourceOnly());
    ASSERT_LIMIT(2, nodes, "9,2");
    ASSERT_LIMIT(3, nodes, "9,2,13s");
}

void MergeLimiterTest::at_least_one_non_source_only_replica_chosen_when_all_trusted() {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addTrusted(9, 0x6)
        .addTrusted(2, 0x6)
        .addTrusted(13, 0x6).setSourceOnly()
        .addTrusted(1, 0x6).setSourceOnly());
    ASSERT_LIMIT(2, nodes, "2,13s");
    ASSERT_LIMIT(3, nodes, "2,13s,1s");
}

void MergeLimiterTest::missing_replica_distinct_from_empty_replica() {
    MergeLimiter::NodeArray nodes(NodeFactory()
        .addEmpty(3)
        .addEmpty(5)
        .addMissing(1)
        .addMissing(2));
    ASSERT_LIMIT(2, nodes, "5,2");
    ASSERT_LIMIT(3, nodes, "5,2,3");
}

} // storage::distributor
