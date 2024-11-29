// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/diskindex/posting_list_cache.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::BitVector;
using search::diskindex::PostingListCache;
using search::index::PostingListHandle;

namespace {

class MockFile : public PostingListCache::IPostingListFileBacking {
public:
    MockFile();
    ~MockFile() override;
    PostingListHandle read(const PostingListCache::Key& key, PostingListCache::Context& ctx) const override;
    std::shared_ptr<BitVector> read(const PostingListCache::BitVectorKey& key, PostingListCache::Context& ctx) const override;
};

MockFile::MockFile()
    : PostingListCache::IPostingListFileBacking()
{
}

MockFile::~MockFile() = default;

PostingListHandle
MockFile::read(const PostingListCache::Key& key, PostingListCache::Context& ctx) const
{
    EXPECT_NE(0, key.bit_length);
    ctx.cache_miss = true;
    PostingListHandle handle;
    handle._allocSize = key.bit_length / 8;
    return handle;
}

std::shared_ptr<BitVector>
MockFile::read(const PostingListCache::BitVectorKey& key, PostingListCache::Context& ctx) const
{
    EXPECT_NE(0, key.lookup_result.idx);
    ctx.cache_miss = true;
    return BitVector::create(100 * key.file_id + key.lookup_result.idx);
}

}

class PostingListCacheTest : public ::testing::Test
{
protected:
    using Key = PostingListCache::Key;
    using BitVectorKey = PostingListCache::BitVectorKey;
    MockFile _mock_file;
    PostingListCache _cache;
    Key _key;
    BitVectorKey _bv_key;
    PostingListCache::Context _ctx;
    PostingListCacheTest();
    ~PostingListCacheTest() override;
    PostingListHandle read() {
        _ctx.cache_miss = false;
        return _cache.read(_key, _ctx);
    }
    std::shared_ptr<BitVector> read_bv() {
        _ctx.cache_miss = false;
        return _cache.read(_bv_key, _ctx);
    }
};

PostingListCacheTest::PostingListCacheTest()
    : ::testing::Test(),
      _mock_file(),
      _cache(256_Ki, 256_Ki),
      _key(),
      _bv_key(),
      _ctx(&_mock_file)
{
}

PostingListCacheTest::~PostingListCacheTest() = default;

TEST_F(PostingListCacheTest, repeated_lookups_gives_hit)
{
    _key.bit_length = 24 * 8;
    auto handle = read();
    EXPECT_TRUE(_ctx.cache_miss);
    auto handle2 = read();
    EXPECT_FALSE(_ctx.cache_miss);
    auto handle3 = read();
    EXPECT_FALSE(_ctx.cache_miss);
    EXPECT_EQ(24, handle._allocSize);
    auto stats = _cache.get_stats();
    EXPECT_EQ(1, stats.misses);
    EXPECT_EQ(2, stats.hits);
    EXPECT_EQ(1, stats.elements);
    EXPECT_EQ(PostingListCache::element_size() + 24, stats.memory_used);
}

TEST_F(PostingListCacheTest, large_elements_immediately_evicts_from_cache)
{
    _key.bit_length = 24 * 8;
    (void) read();
    _key.bit_offset = 1000;
    (void) read();
    auto stats = _cache.get_stats();
    EXPECT_EQ(2, stats.elements);
    _key.bit_length = 512_Ki * 8;
    _key.bit_offset = 16_Ki;
    auto handle = read(); // stats for memory usage updated before eviction check => eviction
    EXPECT_EQ(512_Ki, handle._allocSize);
    stats = _cache.get_stats();
    EXPECT_EQ(1, stats.elements);
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

TEST_F(PostingListCacheTest, repeated_bitvector_lookup_gives_hit)
{
    _bv_key.lookup_result.idx = 1;
    _bv_key.file_id = 2;
    auto bv = read_bv();
    EXPECT_TRUE(_ctx.cache_miss);
    auto bv2 = read_bv();
    EXPECT_FALSE(_ctx.cache_miss);
    EXPECT_EQ(bv, bv2);
    auto stats = _cache.get_bitvector_stats();
    EXPECT_EQ(1, stats.misses);
    EXPECT_EQ(1, stats.hits);
    EXPECT_EQ(1, stats.elements);
    EXPECT_EQ(PostingListCache::bitvector_element_size() + bv->get_allocated_bytes(true), stats.memory_used);
}

GTEST_MAIN_RUN_ALL_TESTS()
