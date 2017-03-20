// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/operations/idealstate/mergelimiter.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace distributor {

struct MergeLimiterTest : public CppUnit::TestFixture
{
    void testKeepsAllBelowLimit();
    void testLessThanMaxUntrusted();
    void testMoreThanMaxUntrusted();
    void testAllUntrustedLessThanMaxVariants();
    void testAllUntrustedMoreThanMaxVariants();
    void testSourceOnlyLast();

    CPPUNIT_TEST_SUITE(MergeLimiterTest);
    CPPUNIT_TEST(testKeepsAllBelowLimit);
    CPPUNIT_TEST(testLessThanMaxUntrusted);
    CPPUNIT_TEST(testMoreThanMaxUntrusted);
    CPPUNIT_TEST(testAllUntrustedLessThanMaxVariants);
    CPPUNIT_TEST(testAllUntrustedMoreThanMaxVariants);
    CPPUNIT_TEST(testSourceOnlyLast);
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
        NodeFactory& setSourceOnly() {
            _nodes.back()._sourceOnly = true;
            return *this;
        }

        operator const MergeLimiter::NodeArray&() const { return _nodes; }
    };

    #define ASSERT_LIMIT(maxNodes, nodes, result) \
    { \
        MergeLimiter limiter(maxNodes); \
        limiter.limitMergeToMaxNodes(nodes); \
        std::ostringstream actual; \
        for (uint32_t i=0; i<nodes.size(); ++i) { \
            if (i != 0) actual << ","; \
            actual << nodes[i]._nodeIndex; \
            if (nodes[i]._sourceOnly) actual << 's'; \
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
    ASSERT_LIMIT(4, nodes, "13,1,2s,5s");
}

} // distributor
} // storage
