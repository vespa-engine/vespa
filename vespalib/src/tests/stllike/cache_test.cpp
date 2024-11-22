// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/small_string.h>
#include <vespa/vespalib/stllike/cache.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <map>

using namespace vespalib;
using namespace ::testing;

template <typename K, typename V>
class Map : public std::map<K, V> {
    using M = std::map<K, V>;
public:
    mutable std::string _forwarded_arg;

    bool read(const K& k, V& v) const {
        auto found = M::find(k);
        bool ok = (found != this->end());
        if (ok) {
            v = found->second;
        }
        return ok;
    }
    bool read(const K& k, V& v, std::string_view arg) const {
        _forwarded_arg = arg;
        return read(k, v);
    }
    void write(const K& k, const V& v) {
        (*this)[k] = v;
    }
    void erase(const K& k) {
        M::erase(k);
    }
};

using P = LruParam<uint32_t, vespa_string>;
using B = Map<uint32_t, vespa_string>;

struct CacheTest : Test {
    B m;
};

TEST_F(CacheTest, basic) {
    cache<CacheParam<P, B>> cache(m, -1);
    // Verify start conditions.
    EXPECT_TRUE(cache.size() == 0);
    EXPECT_TRUE(cache.empty());
    EXPECT_FALSE(cache.hasKey(1));
    cache.write(1, "First inserted string");
    EXPECT_TRUE(cache.hasKey(1) );
    m[2] = "String inserted beneath";
    EXPECT_FALSE(cache.hasKey(2));
    EXPECT_EQ(cache.read(2), "String inserted beneath");
    EXPECT_TRUE(cache.hasKey(2));
    cache.erase(1);
    EXPECT_FALSE(cache.hasKey(1));
    EXPECT_TRUE(cache.size() == 1);
}

TEST_F(CacheTest, cache_size) {
    cache<CacheParam<P, B>> cache(m, -1);
    cache.write(1, "10 bytes string");
    EXPECT_EQ(80u, cache.sizeBytes());
    cache.write(1, "10 bytes string"); // Still the same size
    EXPECT_EQ(80u, cache.sizeBytes());
}

TEST_F(CacheTest, cache_size_deep) {
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, -1);
    cache.write(1, "15 bytes string");
    EXPECT_EQ(95u, cache.sizeBytes());
    cache.write(1, "10 bytes s");
    EXPECT_EQ(90u, cache.sizeBytes());
    cache.write(1, "20 bytes string ssss");
    EXPECT_EQ(100u, cache.sizeBytes());
}

TEST_F(CacheTest, max_elements_is_honored) {
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, -1);
    cache.maxElements(1);
    cache.write(1, "15 bytes string");
    EXPECT_EQ(1u, cache.size());
    EXPECT_EQ(95u, cache.sizeBytes());
    cache.write(2, "16 bytes stringg");
    EXPECT_EQ(1u, cache.size());
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_FALSE(cache.hasKey(1));
    EXPECT_EQ(96u, cache.sizeBytes());
}

TEST_F(CacheTest, max_cache_size_is_honored) {
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, 200);
    cache.write(1, "15 bytes string");
    EXPECT_EQ(1u, cache.size());
    EXPECT_EQ(95u, cache.sizeBytes());
    cache.write(2, "16 bytes stringg");
    EXPECT_EQ(2u, cache.size());
    EXPECT_EQ(191u, cache.sizeBytes());
    cache.write(3, "17 bytes stringgg");
    EXPECT_EQ(2, cache.size());
    EXPECT_EQ(193, cache.sizeBytes());
    cache.write(4, "18 bytes stringggg");
    EXPECT_EQ(2, cache.size());
    EXPECT_EQ(195, cache.sizeBytes());
}

TEST_F(CacheTest, overflow_can_remove_multiple_elements) {
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, 2000);
    
    for (size_t j(0); j < 5; j++) {
        for (size_t i(0); cache.size() == i; i++) {
            cache.write(j*53+i, "a");
        }
    }
    EXPECT_EQ(24, cache.size());
    EXPECT_EQ(1944, cache.sizeBytes());
    EXPECT_FALSE(cache.hasKey(0));
    std::string ls("long string aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    std::string vls=ls+ls+ls+ls+ls+ls; // 2844 bytes
    cache.write(53+5, ls);
    EXPECT_EQ(18, cache.size());
    EXPECT_EQ(1931, cache.sizeBytes());
    EXPECT_FALSE(cache.hasKey(1));
    cache.write(53*7+5, ls);
    EXPECT_EQ(13, cache.size());
    EXPECT_EQ(1999, cache.sizeBytes());
    EXPECT_FALSE(cache.hasKey(2));
    cache.write(53*8+5, vls);
    EXPECT_EQ(1, cache.size());
    EXPECT_EQ(2924, cache.sizeBytes());
    cache.write(53*9+6, vls);
    EXPECT_EQ(1u, cache.size());
    EXPECT_EQ(2924u, cache.sizeBytes());
    // One oversized KV replaced by another
    EXPECT_FALSE(cache.hasKey(53*8+5));
    EXPECT_TRUE(cache.hasKey(53*9+6));
}

class ExtendedCache : public cache<CacheParam<P, B>> {
public:
    ExtendedCache(BackingStore& b, size_t maxBytes)
        : cache(b, maxBytes),
          _insert_count(0),
          _remove_count(0)
    {}
    size_t _insert_count;
    size_t _remove_count;
private:
    void onRemove(const K&) override {
        _remove_count++;
    }

    void onInsert(const K&) override {
        _insert_count++;
    }
};

TEST_F(CacheTest, insert_and_remove_callbacks_invoked_when_full) {
    ExtendedCache cache(m, 250);
    EXPECT_EQ(0u, cache._insert_count);
    EXPECT_EQ(0u, cache._remove_count);
    cache.write(1, "15 bytes string");
    EXPECT_EQ(1u, cache.size());
    EXPECT_EQ(80u, cache.sizeBytes());
    EXPECT_EQ(1u, cache._insert_count);
    EXPECT_EQ(0u, cache._remove_count);
    cache.write(2, "16 bytes stringg");
    EXPECT_EQ(2u, cache.size());
    EXPECT_EQ(160u, cache.sizeBytes());
    EXPECT_EQ(2u, cache._insert_count);
    EXPECT_EQ(0u, cache._remove_count);
    cache.write(3, "17 bytes stringgg");
    EXPECT_EQ(3u, cache.size());
    EXPECT_EQ(240u, cache.sizeBytes());
    EXPECT_EQ(3u, cache._insert_count);
    EXPECT_EQ(0u, cache._remove_count);
    EXPECT_TRUE(cache.hasKey(1));
    cache.write(4, "18 bytes stringggg");
    EXPECT_EQ(3u, cache.size());
    EXPECT_EQ(240u, cache.sizeBytes());
    EXPECT_EQ(4u, cache._insert_count);
    EXPECT_EQ(1u, cache._remove_count);
    EXPECT_FALSE(cache.hasKey(1));
    cache.invalidate(2);
    EXPECT_EQ(2u, cache.size());
    EXPECT_EQ(160u, cache.sizeBytes());
    EXPECT_EQ(4u, cache._insert_count);
    EXPECT_EQ(2u, cache._remove_count);
    EXPECT_FALSE(cache.hasKey(2));
    cache.invalidate(3);
    EXPECT_EQ(1u, cache.size());
    EXPECT_EQ(80u, cache.sizeBytes());
    EXPECT_EQ(4u, cache._insert_count);
    EXPECT_EQ(3u, cache._remove_count);
    EXPECT_FALSE(cache.hasKey(3));
}

TEST_F(CacheTest, can_forward_arguments_to_backing_store_on_cache_miss) {
    cache<CacheParam<P, B>> cache(m, -1);
    m[123] = "foo";
    EXPECT_EQ(cache.read(123, "hello cache world"), "foo");
    EXPECT_EQ(m._forwarded_arg, "hello cache world");

    // Already cached; no forwarding.
    m._forwarded_arg.clear();
    EXPECT_EQ(cache.read(123, "goodbye cache moon"), "foo");
    EXPECT_EQ(m._forwarded_arg, "");
}

TEST_F(CacheTest, fetching_element_moves_it_to_head_of_lru_list) {
    cache<CacheParam<P, B>> cache(m, -1);
    cache.maxElements(3);
    cache.write(1, "foo");
    cache.write(2, "bar");
    cache.write(3, "baz");
    EXPECT_EQ(cache.size(), 3);
    // Cache now in LIFO order <3, 2, 1>. Bring 1 to the front.
    EXPECT_EQ(cache.read(1), "foo");
    // 2 is now last in line, evict it.
    cache.write(4, "zoid");
    EXPECT_EQ(cache.size(), 3);
    EXPECT_FALSE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(3));
    // Cache now in order <4, 1, 3>. Bring 3 to the front.
    EXPECT_EQ(cache.read(3), "baz");
    // 1 is now last in line, throw it to the electric wolves!
    cache.write(5, "winner winner chicken dinner");
    EXPECT_FALSE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(5));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
}

struct SlruCacheTest : CacheTest {
    template <typename C>
    void assert_segment_capacity_bytes(const C& cache, size_t exp_probationary, size_t exp_protected) {
        ASSERT_EQ(cache.segment_capacity_bytes(CacheSegment::Probationary), exp_probationary);
        ASSERT_EQ(cache.segment_capacity_bytes(CacheSegment::Protected), exp_protected);
    }

    template <typename C>
    void assert_segment_capacities(const C& cache, size_t exp_probationary, size_t exp_protected) {
        ASSERT_EQ(cache.segment_capacity(CacheSegment::Probationary), exp_probationary);
        ASSERT_EQ(cache.segment_capacity(CacheSegment::Protected), exp_protected);
    }

    template <typename C>
    void assert_segment_sizes(const C& cache, size_t exp_probationary, size_t exp_protected) {
        ASSERT_EQ(cache.segment_size(CacheSegment::Probationary), exp_probationary);
        ASSERT_EQ(cache.segment_size(CacheSegment::Protected), exp_protected);
    }

    template <typename C>
    void assert_segment_size_bytes(const C& cache, size_t exp_probationary, size_t exp_protected) {
        ASSERT_EQ(cache.segment_size_bytes(CacheSegment::Probationary), exp_probationary);
        ASSERT_EQ(cache.segment_size_bytes(CacheSegment::Protected), exp_protected);
    }

    template <typename C>
    void assert_segment_lru_keys(C& cache,
                                 const std::vector<typename C::key_type>& exp_probationary_keys,
                                 const std::vector<typename C::key_type>& exp_protected_keys)
    {
        ASSERT_EQ(cache.dump_segment_keys_in_lru_order(CacheSegment::Probationary), exp_probationary_keys);
        ASSERT_EQ(cache.dump_segment_keys_in_lru_order(CacheSegment::Protected),    exp_protected_keys);
    }
};

namespace {
struct SelfAsSize {
    template <typename T>
    constexpr size_t operator()(const T& v) const noexcept {
        return static_cast<size_t>(v);
    }
};
}

TEST_F(SlruCacheTest, zero_sized_protected_segment_implies_lru_semantics) {
    cache<CacheParam<P, B, SelfAsSize, zero<std::string>>> cache(m, 300, 0);

    ASSERT_NO_FATAL_FAILURE(assert_segment_capacity_bytes(cache, 300, 0));

    cache.write(20, "foo");
    EXPECT_EQ(cache.size(), 1);
    EXPECT_EQ(cache.sizeBytes(), 100);
    cache.write(18, "bar");
    EXPECT_EQ(cache.size(), 2);
    EXPECT_EQ(cache.sizeBytes(), 198);
    cache.write(10, "baz");
    EXPECT_EQ(cache.size(), 3);
    EXPECT_EQ(cache.sizeBytes(), 288);
    cache.write(11, "zoid");
    EXPECT_EQ(cache.sizeBytes(), 279);
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 279, 0));
    ASSERT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 3, 0));
    EXPECT_TRUE(cache.hasKey(11));
    EXPECT_TRUE(cache.hasKey(10));
    EXPECT_TRUE(cache.hasKey(18));
    EXPECT_FALSE(cache.hasKey(20));
    // Reading a cached entry does not promote it to protected
    EXPECT_EQ(cache.read(10), "baz");
    ASSERT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 3, 0));
}

TEST_F(SlruCacheTest, cache_elements_are_transitioned_between_segments) {
    cache<CacheParam<P, B, zero<uint32_t>, zero<std::string>>> cache(m, -1, -1); // no size restrictions
    cache.maxElements(2, 1);

    ASSERT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 0, 0));
    ASSERT_NO_FATAL_FAILURE(assert_segment_capacities(cache, 2, 1));
    ASSERT_NO_FATAL_FAILURE(assert_segment_capacity_bytes(cache, -1, -1));

    cache.write(1, "foo");
    cache.write(2, "bar");
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 2, 0));
    // Evicting an entry from probationary does not push it into protected
    cache.write(3, "baz");
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 2, 0));
    // {2, 3} in probationary. Access 2; it should be placed in protected
    EXPECT_EQ(cache.read(2), "bar");
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 1));
    // Reading it again fetches from protected
    m[2] = "backing store should not be consulted for cached entry";
    EXPECT_EQ(cache.read(2), "bar");
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 1));
    // Room for one more in probationary
    cache.write(4, "zoid");
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 2, 1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    // Read 4; it should be placed in protected. This evicts 2 from protected,
    // placing it back at the head of the LRU in probationary for a second chance.
    EXPECT_EQ(cache.read(4), "zoid");
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 2, 1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    // 3 should be the oldest probationary element, and will be kicked out on a new
    // write (_not_ 2, which has been given a new lease on life).
    cache.write(5, "zoid");
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 2, 1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_FALSE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
}

TEST_F(SlruCacheTest, write_through_updates_correct_segment) {
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, -1, -1);

    cache.write(1, "foo");
    cache.write(2, "zoid");
    EXPECT_EQ(cache.read(1), "foo"); // --> protected
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 1));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 84, 83));
    cache.write(1, "a string that takes more memory yes"); // in protected
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 1));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 84, 115));
    EXPECT_EQ(m[1], "a string that takes more memory yes"); // Backing store has been updated

    cache.write(2, "un petit string"); // in probationary
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 1));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 95, 115));
    EXPECT_EQ(m[2], "un petit string");

    cache.erase(1);
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 0));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 95, 0));
    EXPECT_FALSE(m.contains(1));

    cache.erase(2);
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 0, 0));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 0, 0));
    EXPECT_FALSE(m.contains(2));
}

TEST_F(SlruCacheTest, cache_invalidations_update_correct_segment) {
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, -1, -1);

    cache.write(1, "foo");
    cache.write(2, "zoid");
    EXPECT_EQ(cache.read(1), "foo"); // --> protected
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 1));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 84, 83));
    cache.invalidate(2);
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 0, 1));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 0, 83));
    cache.invalidate(1);
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 0, 0));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 0, 0));
    // Backing store remains untouched
    EXPECT_EQ(m[1], "foo");
    EXPECT_EQ(m[2], "zoid");
}

TEST_F(SlruCacheTest, capacity_bytes_change_is_propagated_to_segments) {
    cache<CacheParam<P, B, zero<uint32_t>, zero<std::string>>> cache(m, 200, 400);

    EXPECT_NO_FATAL_FAILURE(assert_segment_capacity_bytes(cache, 200, 400));
    cache.setCapacityBytes(300);
    EXPECT_NO_FATAL_FAILURE(assert_segment_capacity_bytes(cache, 300, 0));
    cache.setCapacityBytes(500, 700);
    EXPECT_NO_FATAL_FAILURE(assert_segment_capacity_bytes(cache, 500, 700));
}

TEST_F(SlruCacheTest, assigning_zero_capacity_of_protected_segment_evicts_all_segment_entries) {
    cache<CacheParam<P, B, SelfAsSize, zero<std::string>>> cache(m, 400, 500);

    cache.write(10, "foo");
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 90, 0));
    EXPECT_NO_FATAL_FAILURE(assert_segment_capacities(cache, -1, -1)); // Unlimited cardinality for both
    cache.write(20, "bar");
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 2, 0));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 190, 0));
    EXPECT_EQ(cache.read(20), "bar");
    // 20 is now in protected segment
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 1));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 90, 100));
    cache.setCapacityBytes(400, 0);
    // Evicting the protected segment drops them on the floor without bringing them
    // back into the probationary segment (at least with the current semantics).
    // Setting byte capacity to zero also implicitly sets max elements to zero.
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 0));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 90, 0));
    ASSERT_NO_FATAL_FAILURE(assert_segment_capacities(cache, -1, 0));
    EXPECT_TRUE(cache.hasKey(10));
    EXPECT_FALSE(cache.hasKey(20));
    // Backing store is untouched by evictions
    EXPECT_EQ(m[20], "bar");
    // Accessing key 10 does not move it to protected
    EXPECT_EQ(cache.read(10), "foo");
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 0));

    // We can turn segmenting back on again
    cache.setCapacityBytes(400, 500);
    EXPECT_EQ(cache.read(10), "foo");
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 0, 1)); // key 10 now moved to protected
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 0, 90));
}

TEST_F(SlruCacheTest, accessing_element_in_protected_segment_moves_to_segment_head) {
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, -1, -1);
    cache.write(1, "a");
    cache.write(2, "b");
    cache.write(3, "c");
    cache.write(4, "d");
    cache.write(5, "e");
    EXPECT_EQ(cache.read(2), "b");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {5, 4, 3, 1}, {2}));
    EXPECT_EQ(cache.read(4), "d");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {5, 3, 1}, {4, 2}));
    EXPECT_EQ(cache.read(1), "a");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {5, 3}, {1, 4, 2}));
    // Bump to LRU head in protected segment
    EXPECT_EQ(cache.read(2), "b");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {5, 3}, {2, 1, 4}));
    EXPECT_EQ(cache.read(4), "d");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {5, 3}, {4, 2, 1}));
    EXPECT_EQ(cache.read(4), "d"); // Idempotent head -> head
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {5, 3}, {4, 2, 1}));
}

GTEST_MAIN_RUN_ALL_TESTS()
