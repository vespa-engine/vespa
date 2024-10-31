// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/diskindex/posting_list_cache.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::diskindex::PostingListCache;
using search::index::PostingListHandle;

namespace {

class MockFile : public PostingListCache::IPostingListFileBacking {
    bool _direct_io;
public:
    MockFile();
    ~MockFile() override;
    PostingListHandle read(const PostingListCache::Key& key) override;
    void set_direct_io(bool value) { _direct_io = value; }
};

MockFile::MockFile()
    : PostingListCache::IPostingListFileBacking(),
      _direct_io(false)
{
}

MockFile::~MockFile() = default;

PostingListHandle
MockFile::read(const PostingListCache::Key& key)
{
    EXPECT_NE(0, key.bit_length);
    uint64_t start_offset = key.bit_offset >> 3;
    // Align start at 64-bit boundary
    start_offset -= (start_offset & 7);
    uint64_t end_offset = (key.bit_offset + key.bit_length + 7) >> 3;
    // Align end at 64-bit boundary
    end_offset += (-end_offset & 7);
    uint64_t vector_len = end_offset - start_offset;
    uint64_t pad_before = 0;
    uint64_t pad_after = 0;
    if (_direct_io) {
        pad_before = start_offset & (4_Ki - 1);
        pad_after = -end_offset & (4_Ki - 1);
    }
    uint64_t pad_extra_after = 0;
    constexpr uint64_t min_pad_after = 16; // space for prefetch memory
    if (pad_after < min_pad_after) {
        pad_extra_after = min_pad_after - pad_after;
    }
    PostingListHandle handle;
    handle._read_bytes = pad_before + vector_len + pad_after;
    handle._allocSize = pad_before + vector_len + pad_after + pad_extra_after;
    if (_direct_io) {
        handle._allocSize += (4_Ki - 1); // cf. FastOS_File::AllocateDirectIOBuffer
    }
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
    _key.bit_length = 10;
    _key.bit_offset = 20;
    auto handle = read();
    auto handle2 = read();
    auto handle3 = read();
    EXPECT_EQ(8, handle._read_bytes);
    EXPECT_EQ(24, handle._allocSize);
    EXPECT_EQ(8, handle2._read_bytes);
    EXPECT_EQ(8, handle3._read_bytes);
    auto stats = _cache.get_stats();
    EXPECT_EQ(1, stats.misses);
    EXPECT_EQ(2, stats.hits);
    EXPECT_EQ(1, stats.elements);
    EXPECT_EQ(PostingListCache::element_size() + 24, stats.memory_used);
}

TEST_F(PostingListCacheTest, large_elements_kills_cache_on_next_read)
{
    _key.bit_length = 10;
    _key.bit_offset = 20;
    (void) read();
    _key.bit_offset = 30;
    (void) read();
    auto stats = _cache.get_stats();
    EXPECT_EQ(2, stats.elements);
    _key.bit_length = 512_Ki * 8;
    _key.bit_offset = 64;
    auto handle = read(); // stats for memory usage updated after eviction check => no eviction
    EXPECT_EQ(512_Ki, handle._read_bytes);
    stats = _cache.get_stats();
    EXPECT_EQ(3, stats.elements);
    EXPECT_LT(512_Ki, stats.memory_used);
    _key.bit_length = 10;
    _key.bit_offset = 40;
    (void) read(); // Evicts all old entries after adding new one
    stats = _cache.get_stats();
    EXPECT_EQ(1, stats.elements);
    EXPECT_EQ(PostingListCache::element_size() + 24, stats.memory_used);
}

TEST_F(PostingListCacheTest, file_id_is_part_of_key)
{
    _key.bit_length = 10;
    _key.bit_offset = 20;
    (void) read();
    _key.file_id = 1;
    (void) read();
    auto stats = _cache.get_stats();
    EXPECT_EQ(2, stats.elements);
}

TEST_F(PostingListCacheTest, padded_data_from_direct_io_is_not_stripped)
{
    _mock_file.set_direct_io(true);
    _key.bit_length = 10;
    _key.bit_offset = 2000;
    auto handle = read();
    EXPECT_EQ(4_Ki, handle._read_bytes);
    EXPECT_EQ(4_Ki + (4_Ki - 1), handle._allocSize);
}

GTEST_MAIN_RUN_ALL_TESTS()
