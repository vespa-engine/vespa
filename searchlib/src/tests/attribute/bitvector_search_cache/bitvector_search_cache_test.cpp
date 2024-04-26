// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/attribute/bitvector_search_cache.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/util/memoryusage.h>

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

struct Fixture {
    BitVectorSearchCache cache;
    EntrySP entry1;
    EntrySP entry2;
    Fixture()
        : cache(),
          entry1(makeEntry()),
          entry2(makeEntry())
    {}
};

TEST_F("require that bit vectors can be inserted and retrieved", Fixture)
{
    EXPECT_EQUAL(0u, f.cache.size());
    auto old_mem_usage = f.cache.get_memory_usage();
    f.cache.insert("foo", f.entry1);
    f.cache.insert("bar", f.entry2);
    EXPECT_EQUAL(2u, f.cache.size());
    auto new_mem_usage = f.cache.get_memory_usage();
    EXPECT_LESS(old_mem_usage.usedBytes(), new_mem_usage.usedBytes());
    EXPECT_LESS(old_mem_usage.allocatedBytes(), new_mem_usage.allocatedBytes());

    EXPECT_EQUAL(f.entry1, f.cache.find("foo"));
    EXPECT_EQUAL(f.entry2, f.cache.find("bar"));
    EXPECT_TRUE(f.cache.find("baz").get() == nullptr);
}

TEST_F("require that insert() doesn't replace existing bit vector", Fixture)
{
    f.cache.insert("foo", f.entry1);
    auto old_mem_usage = f.cache.get_memory_usage();
    f.cache.insert("foo", f.entry2);
    auto new_mem_usage = f.cache.get_memory_usage();
    EXPECT_EQUAL(1u, f.cache.size());
    EXPECT_EQUAL(f.entry1, f.cache.find("foo"));
    EXPECT_EQUAL(old_mem_usage.usedBytes(), new_mem_usage.usedBytes());
    EXPECT_EQUAL(old_mem_usage.allocatedBytes(), new_mem_usage.allocatedBytes());
}

TEST_F("require that cache can be cleared", Fixture)
{
    f.cache.insert("foo", f.entry1);
    f.cache.insert("bar", f.entry2);
    EXPECT_EQUAL(2u, f.cache.size());
    auto old_mem_usage = f.cache.get_memory_usage();
    f.cache.clear();
    auto new_mem_usage = f.cache.get_memory_usage();

    EXPECT_EQUAL(0u, f.cache.size());
    EXPECT_TRUE(f.cache.find("foo").get() == nullptr);
    EXPECT_TRUE(f.cache.find("bar").get() == nullptr);
    EXPECT_GREATER(old_mem_usage.usedBytes(), new_mem_usage.usedBytes());
    EXPECT_GREATER(old_mem_usage.allocatedBytes(), new_mem_usage.allocatedBytes());
}

TEST_MAIN() { TEST_RUN_ALL(); }
