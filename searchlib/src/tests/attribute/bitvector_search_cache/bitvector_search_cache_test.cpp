// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/bitvector_search_cache.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search;
using namespace search::attribute;

using BitVectorSP = BitVectorSearchCache::BitVectorSP;
using Entry = BitVectorSearchCache::Entry;
using EntrySP = std::shared_ptr<Entry>;

EntrySP
makeEntry()
{
    return std::make_shared<Entry>(IDocumentMetaStoreContext::IReadGuard::SP(), BitVector::create(5), 10);
}

class BitVectorSearchCacheTest : public ::testing::Test {
protected:
    BitVectorSearchCache cache;
    EntrySP entry1;
    EntrySP entry2;
    BitVectorSearchCacheTest();
    ~BitVectorSearchCacheTest() override;
};

BitVectorSearchCacheTest::BitVectorSearchCacheTest()
    : ::testing::Test(),
      cache(),
      entry1(makeEntry()),
      entry2(makeEntry()) {
}

BitVectorSearchCacheTest::~BitVectorSearchCacheTest() = default;

TEST_F(BitVectorSearchCacheTest, require_that_bit_vectors_can_be_inserted_and_retrieved)
{
    EXPECT_EQ(0u, cache.size());
    auto old_mem_usage = cache.get_memory_usage();
    cache.insert("foo", entry1);
    cache.insert("bar", entry2);
    EXPECT_EQ(2u, cache.size());
    auto new_mem_usage = cache.get_memory_usage();
    EXPECT_LT(old_mem_usage.usedBytes(), new_mem_usage.usedBytes());
    EXPECT_LT(old_mem_usage.allocatedBytes(), new_mem_usage.allocatedBytes());

    EXPECT_EQ(entry1, cache.find("foo"));
    EXPECT_EQ(entry2, cache.find("bar"));
    EXPECT_TRUE(cache.find("baz").get() == nullptr);
}

TEST_F(BitVectorSearchCacheTest, require_that_insert_doesnt_replace_existing_bit_vector)
{
    cache.insert("foo", entry1);
    auto old_mem_usage = cache.get_memory_usage();
    cache.insert("foo", entry2);
    auto new_mem_usage = cache.get_memory_usage();
    EXPECT_EQ(1u, cache.size());
    EXPECT_EQ(entry1, cache.find("foo"));
    EXPECT_EQ(old_mem_usage.usedBytes(), new_mem_usage.usedBytes());
    EXPECT_EQ(old_mem_usage.allocatedBytes(), new_mem_usage.allocatedBytes());
}

TEST_F(BitVectorSearchCacheTest, require_that_cache_can_be_cleared)
{
    cache.insert("foo", entry1);
    cache.insert("bar", entry2);
    EXPECT_EQ(2u, cache.size());
    auto old_mem_usage = cache.get_memory_usage();
    cache.clear();
    auto new_mem_usage = cache.get_memory_usage();

    EXPECT_EQ(0u, cache.size());
    EXPECT_TRUE(cache.find("foo").get() == nullptr);
    EXPECT_TRUE(cache.find("bar").get() == nullptr);
    EXPECT_GT(old_mem_usage.usedBytes(), new_mem_usage.usedBytes());
    EXPECT_GT(old_mem_usage.allocatedBytes(), new_mem_usage.allocatedBytes());
}

GTEST_MAIN_RUN_ALL_TESTS()
