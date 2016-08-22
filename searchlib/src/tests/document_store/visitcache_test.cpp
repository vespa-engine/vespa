// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/docstore/visitcache.h>

using namespace search;
using namespace search::docstore;

TEST("require that KeySet compares well")
{
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

    EXPECT_FALSE(KeySet({1,3,5}) < KeySet({1,3,5}));
    EXPECT_TRUE(KeySet({1,3}) < KeySet({1,3,5}));
    EXPECT_FALSE(KeySet({1,3,5}) < KeySet({1,3}));
    EXPECT_TRUE(KeySet({1,3,5}) < KeySet({1,4}));
    EXPECT_FALSE(KeySet({1,3,5}) < KeySet({1,2}));
    EXPECT_TRUE(KeySet({1,2}) < KeySet({1,3,5}));
    EXPECT_FALSE(KeySet({1,4}) < KeySet({1,3,5}));
    EXPECT_EQUAL(1, a.getKeys()[0]);
    EXPECT_EQUAL(2, a.getKeys()[1]);
    EXPECT_EQUAL(3, a.getKeys()[2]);
    EXPECT_EQUAL(4, a.getKeys()[3]);
    EXPECT_EQUAL(6, a.getKeys()[4]);
    EXPECT_EQUAL(9, a.getKeys()[5]);
}

TEST_MAIN() { TEST_RUN_ALL(); }
