// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/attribute/bitvector_search_cache.h>
#include <vespa/searchlib/common/bitvector.h>

using namespace search;
using namespace search::attribute;

using BitVectorSP = BitVectorSearchCache::BitVectorSP;

BitVectorSP
makeBitVector()
{
    return BitVectorSP(BitVector::create(5).release());
}

struct Fixture {
    BitVectorSearchCache cache;
    BitVectorSP vec1;
    BitVectorSP vec2;
    Fixture()
        : cache(),
          vec1(makeBitVector()),
          vec2(makeBitVector())
    {}
};

TEST_F("require that bit vectors can be inserted and retrieved", Fixture)
{
    EXPECT_EQUAL(0u, f.cache.size());
    f.cache.insert("foo", f.vec1);
    f.cache.insert("bar", f.vec2);
    EXPECT_EQUAL(2u, f.cache.size());

    EXPECT_EQUAL(f.vec1, f.cache.find("foo"));
    EXPECT_EQUAL(f.vec2, f.cache.find("bar"));
    EXPECT_TRUE(f.cache.find("baz").get() == nullptr);
}

TEST_F("require that insert() doesn't replace existing bit vector", Fixture)
{
    f.cache.insert("foo", f.vec1);
    f.cache.insert("foo", f.vec2);
    EXPECT_EQUAL(1u, f.cache.size());
    EXPECT_EQUAL(f.vec1, f.cache.find("foo"));
}

TEST_F("require that cache can be cleared", Fixture)
{
    f.cache.insert("foo", f.vec1);
    f.cache.insert("bar", f.vec2);
    EXPECT_EQUAL(2u, f.cache.size());
    f.cache.clear();

    EXPECT_EQUAL(0u, f.cache.size());
    EXPECT_TRUE(f.cache.find("foo").get() == nullptr);
    EXPECT_TRUE(f.cache.find("bar").get() == nullptr);
}

TEST_MAIN() { TEST_RUN_ALL(); }
