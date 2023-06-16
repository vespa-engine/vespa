// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/docstore/visitcache.h>

using namespace search;
using namespace search::docstore;

TEST("require that KeySet compares well") {
    KeySet a({2,1,4,3,9,6});
    EXPECT_TRUE(a.contains(1));
    EXPECT_TRUE(a.contains(2));
    EXPECT_TRUE(a.contains(3));
    EXPECT_TRUE(a.contains(4));
    EXPECT_TRUE(a.contains(6));
    EXPECT_TRUE(a.contains(9));
    EXPECT_EQUAL(1u, a.hash());
    EXPECT_TRUE(a.contains(KeySet({4,1,9})));
    EXPECT_FALSE(a.contains(KeySet({4,1,9,5})));
    EXPECT_TRUE(a.contains(KeySet({4,1,9,2,3,6})));
    EXPECT_FALSE(a.contains(KeySet({11,4,1,9,2,3,6})));

    EXPECT_TRUE(KeySet({1,5,7}) == KeySet({7,1,5}));
    EXPECT_FALSE(KeySet({1,5,7}) == KeySet({7,1,5,4}));
    EXPECT_FALSE(KeySet({1,5,7}) == KeySet({7,1,5,9}));
    EXPECT_FALSE(KeySet({1,5,7,9}) == KeySet({7,1,5}));
    EXPECT_FALSE(KeySet({1,5,7,9}) == KeySet({7,1,5,8}));

    EXPECT_FALSE(KeySet({1,3,5}) < KeySet({1,3,5}));
    EXPECT_TRUE(KeySet({1,3}) < KeySet({1,3,5}));
    EXPECT_FALSE(KeySet({1,3,5}) < KeySet({1,3}));
    EXPECT_TRUE(KeySet({1,3,5}) < KeySet({1,4}));
    EXPECT_FALSE(KeySet({1,3,5}) < KeySet({1,2}));
    EXPECT_TRUE(KeySet({1,2}) < KeySet({1,3,5}));
    EXPECT_FALSE(KeySet({1,4}) < KeySet({1,3,5}));
    EXPECT_EQUAL(1u, a.getKeys()[0]);
    EXPECT_EQUAL(2u, a.getKeys()[1]);
    EXPECT_EQUAL(3u, a.getKeys()[2]);
    EXPECT_EQUAL(4u, a.getKeys()[3]);
    EXPECT_EQUAL(6u, a.getKeys()[4]);
    EXPECT_EQUAL(9u, a.getKeys()[5]);
}

namespace {

void verifyAB(const BlobSet & a) {
    EXPECT_EQUAL(0u, a.get(8).size());
    EXPECT_EQUAL(6u, a.get(7).size());
    EXPECT_EQUAL(5u, a.get(9).size());
    EXPECT_EQUAL(0, strncmp(a.get(7).c_str(), "aaaaaa", 6));
    EXPECT_EQUAL(0, strncmp(a.get(9).c_str(), "bbbbb", 5));
    EXPECT_EQUAL(11u, a.getBuffer().size());
    EXPECT_EQUAL(0, strncmp(a.getBuffer().c_str(), "aaaaaabbbbb", 11));
}

}

using B=vespalib::ConstBufferRef;
TEST("require that BlobSet can be built") {
    using CompressionConfig = vespalib::compression::CompressionConfig;
    BlobSet a;
    a.append(7, B("aaaaaa",6));
    a.append(9, B("bbbbb",5));
    verifyAB(a);
    CompressionConfig cfg(CompressionConfig::LZ4);
    CompressedBlobSet ca(cfg, std::move(a));
    BlobSet b = ca.getBlobSet();
    verifyAB(b);
}

TEST_MAIN() { TEST_RUN_ALL(); }
