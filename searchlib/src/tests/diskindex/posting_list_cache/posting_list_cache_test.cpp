// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/diskindex/posting_list_cache.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::diskindex::PostingListCache;
using search::index::PostingListHandle;

namespace {

class MockFile : public PostingListCache::IPostingListFileBacking {
public:
    MockFile();
    ~MockFile() override;
    PostingListHandle read(const PostingListCache::Key& key) const override;
};

MockFile::MockFile()
    : PostingListCache::IPostingListFileBacking()
{
}

MockFile::~MockFile() = default;

PostingListHandle
MockFile::read(const PostingListCache::Key& key) const
{
    EXPECT_NE(0, key.bit_length);
    PostingListHandle handle;
    handle._allocSize = key.bit_length / 8;
    return handle;
}

}

class PostingListCacheTest : public ::testing::Test
{
protected:
    using Key = PostingListCache::Key;
    MockFile _mock_file;
    PostingListCache _cache;
    Key _key;
    PostingListCacheTest();
    ~PostingListCacheTest() override;
    PostingListHandle read() { return _cache.read(_key); }
};

PostingListCacheTest::PostingListCacheTest()
    : ::testing::Test(),
      _mock_file(),
      _cache(256_Ki),
      _key()
{
    _key.backing_store_file = &_mock_file;
}

PostingListCacheTest::~PostingListCacheTest() = default;

TEST_F(PostingListCacheTest, repeated_lookups_gives_hit)
{
    _key.bit_length = 24 * 8;
    auto handle = read();
    auto handle2 = read();
    auto handle3 = read();
    EXPECT_EQ(24, handle._allocSize);
    auto stats = _cache.get_stats();
    EXPECT_EQ(1, stats.misses);
    EXPECT_EQ(2, stats.hits);
    EXPECT_EQ(1, stats.elements);
    EXPECT_EQ(PostingListCache::element_size() + 24, stats.memory_used);
}

TEST_F(PostingListCacheTest, large_elements_kills_cache_on_next_read)
{
    _key.bit_length = 24 * 8;
    (void) read();
    _key.bit_offset = 1000;
    (void) read();
    auto stats = _cache.get_stats();
    EXPECT_EQ(2, stats.elements);
    _key.bit_length = 512_Ki * 8;
    _key.bit_offset = 16_Ki;
    auto handle = read(); // stats for memory usage updated after eviction check => no eviction
    EXPECT_EQ(512_Ki, handle._allocSize);
    stats = _cache.get_stats();
    EXPECT_EQ(3, stats.elements);
    EXPECT_LT(512_Ki, stats.memory_used);
    _key.bit_length = 25 * 8;
    _key.bit_offset = 2000;
    (void) read(); // Evicts all old entries after adding new one
    stats = _cache.get_stats();
    EXPECT_EQ(1, stats.elements);
    EXPECT_EQ(PostingListCache::element_size() + 25, stats.memory_used);
}

TEST_F(PostingListCacheTest, file_id_is_part_of_key)
{
    _key.bit_length = 24 * 8;
    (void) read();
    _key.file_id = 1;
    (void) read();
    auto stats = _cache.get_stats();
    EXPECT_EQ(2, stats.elements);
}

GTEST_MAIN_RUN_ALL_TESTS()
