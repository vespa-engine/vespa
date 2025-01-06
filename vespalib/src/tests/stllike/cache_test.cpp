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

class ExtendedCache : public cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> {
public:
    ExtendedCache(BackingStore& b, size_t max_bytes, size_t max_protected_bytes = 0)
        : cache(b, max_bytes, max_protected_bytes),
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
    ExtendedCache cache(m, 300);
    EXPECT_EQ(0u, cache._insert_count);
    EXPECT_EQ(0u, cache._remove_count);
    cache.write(1, "15 bytes string");
    EXPECT_EQ(1u, cache.size());
    EXPECT_EQ(95, cache.sizeBytes());
    EXPECT_EQ(1u, cache._insert_count);
    EXPECT_EQ(0u, cache._remove_count);
    cache.write(2, "16 bytes stringg");
    EXPECT_EQ(2u, cache.size());
    EXPECT_EQ(191, cache.sizeBytes());
    EXPECT_EQ(2u, cache._insert_count);
    EXPECT_EQ(0u, cache._remove_count);
    cache.write(3, "17 bytes stringgg");
    EXPECT_EQ(3u, cache.size());
    EXPECT_EQ(288, cache.sizeBytes());
    EXPECT_EQ(3u, cache._insert_count);
    EXPECT_EQ(0u, cache._remove_count);
    EXPECT_TRUE(cache.hasKey(1));
    cache.write(4, "18 bytes stringggg");
    EXPECT_EQ(3u, cache.size());
    EXPECT_EQ(291, cache.sizeBytes());
    EXPECT_EQ(4u, cache._insert_count);
    EXPECT_EQ(1u, cache._remove_count);
    EXPECT_FALSE(cache.hasKey(1));
    cache.invalidate(2);
    EXPECT_EQ(2u, cache.size());
    EXPECT_EQ(195, cache.sizeBytes());
    EXPECT_EQ(4u, cache._insert_count);
    EXPECT_EQ(2u, cache._remove_count);
    EXPECT_FALSE(cache.hasKey(2));
    cache.invalidate(3);
    EXPECT_EQ(1u, cache.size());
    EXPECT_EQ(98, cache.sizeBytes());
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

TEST_F(SlruCacheTest, assigning_capacity_to_segments_trims_entries) {
    cache<CacheParam<P, B, SelfAsSize, zero<std::string>>> cache(m, 400, 500);

    cache.write(10, "foo");
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 90, 0));
    EXPECT_NO_FATAL_FAILURE(assert_segment_capacities(cache, -1, -1)); // Unlimited cardinality for both
    cache.write(20, "bar");
    cache.write(30, "baz");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {30, 20, 10}, {}));
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 3, 0));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 300, 0));
    EXPECT_EQ(cache.read(20), "bar");
    // 20 is now in protected segment
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {30, 10}, {20}));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 200, 100));
    // Reduce capacities across both segments (for protected, effectively disabling it)
    cache.setCapacityBytes(250, 0);
    // Trimming the protected segment implicitly moves elements to the head of the
    // probationary segment. This may in turn shove old capacity-exceeding elements
    // out of the probationary cache (in this case 10).
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {20, 30}, {}));
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 2, 0));
    // Backing store is untouched by evictions
    EXPECT_EQ(m[10], "foo");
    // Accessing key 30 does not move it to protected (but does update the LRU)
    EXPECT_EQ(cache.read(30), "baz");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {30, 20}, {}));

    // We can turn segmenting back on again
    cache.setCapacityBytes(400, 500);
    EXPECT_EQ(cache.read(20), "bar");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {30}, {20}));
    EXPECT_NO_FATAL_FAILURE(assert_segment_sizes(cache, 1, 1));
    EXPECT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 110, 100));
}

TEST_F(SlruCacheTest, trimming_protected_segment_does_not_invoke_remove_callback) {
    ExtendedCache cache(m, -1, -1);
    cache.write(10, "foo");
    EXPECT_EQ(cache.read(10), "foo"); // ==> protected
    EXPECT_EQ(cache._insert_count, 1);
    EXPECT_EQ(cache._remove_count, 0);
    cache.setCapacityBytes(-1, 0); // ==> back into probationary it goes
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {10}, {}));
    EXPECT_EQ(cache._insert_count, 1);
    EXPECT_EQ(cache._remove_count, 0);
}

TEST_F(SlruCacheTest, transitive_eviction_from_probationary_segment_invokes_remove_callback) {
    ExtendedCache cache(m, 170, 100);
    cache.write(10, "foo");
    ASSERT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 83, 0));
    EXPECT_EQ(cache.read(10), "foo"); // ==> protected
    cache.write(30, "a string that is so large that it will squeeze out other elements");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {30}, {10}));
    (void)cache.read(30); // ==> protected
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {10}, {30})); // the great swaparoo
    EXPECT_EQ(cache._remove_count, 0);
    // Room for another element in probationary
    cache.write(20, "bar");
    ASSERT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 166, 145));
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {20, 10}, {30}));
    EXPECT_EQ(cache._remove_count, 0);
    (void)cache.read(20); // ==> protected, kicks 30 into probationary
    // 30 is too big for both it and 10 to fit into probationary, so 10 is shown the door.
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {30}, {20}));
    EXPECT_EQ(cache._remove_count, 1);
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

struct LfuCacheTest : SlruCacheTest {
    LfuCacheTest() : SlruCacheTest() {
        // Prepopulate backing store
        m[1] = "a";
        m[2] = "b";
        m[3] = "c";
        m[4] = "d";
        m[5] = "e";
    }
};

TEST_F(LfuCacheTest, lfu_gates_probationary_segment_displacing) {
    // Disable protected segment; LRU mode only
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, -1, 0);
    cache.maxElements(3, 0);
    cache.set_frequency_sketch_size(3);
    // Element 1 is the talk of the town. Everybody wants a piece. So popular...!
    ASSERT_EQ(cache.read(1), "a");
    ASSERT_EQ(cache.read(1), "a");
    // Cache still has capacity, so LFU does not gate the insertion
    ASSERT_EQ(cache.read(2), "b");
    ASSERT_EQ(cache.read(3), "c");
    EXPECT_EQ(cache.lfu_dropped(), 0);
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {3, 2, 1}, {}));
    // Attempting to read-through 4 will _not_ insert it into the cache, as doing so
    // would displace a more popular element (1).
    ASSERT_EQ(cache.read(4), "d");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {3, 2, 1}, {}));
    EXPECT_EQ(cache.lfu_dropped(), 1);
    // Reading 4 once more won't make it _more_ popular than 1, so still rejected.
    ASSERT_EQ(cache.read(4), "d");
    EXPECT_EQ(cache.lfu_dropped(), 2);
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {3, 2, 1}, {}));
    // But reading it once again will make it more popular, displacing 1.
    ASSERT_EQ(cache.read(4), "d");
    EXPECT_EQ(cache.lfu_dropped(), 2);
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {4, 3, 2}, {}));
    EXPECT_EQ(cache.lfu_not_promoted(), 0); // Only applies to SLRU
}

TEST_F(LfuCacheTest, lfu_gates_protected_segment_displacing) {
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, -1, -1);
    cache.maxElements(4, 2);
    cache.set_frequency_sketch_size(6);
    ASSERT_EQ(cache.read(1), "a");
    ASSERT_EQ(cache.read(2), "b");
    ASSERT_EQ(cache.read(3), "c");
    ASSERT_EQ(cache.read(4), "d");
    // Move 1+2 into protected. These will now have an estimated frequency of 2.
    ASSERT_EQ(cache.read(1), "a");
    ASSERT_EQ(cache.read(2), "b");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {4, 3}, {2, 1}));
    ASSERT_EQ(cache.read(5), "e");
    // Both 1+2 are trending higher on social media than 3+4. Touching 3+4 will
    // bump them to the head of the LRU, but not into the protected segment (yet).
    EXPECT_EQ(cache.lfu_not_promoted(), 0);
    ASSERT_EQ(cache.read(3), "c");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {3, 5, 4}, {2, 1}));
    EXPECT_EQ(cache.lfu_not_promoted(), 1);
    ASSERT_EQ(cache.read(4), "d");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {4, 3, 5}, {2, 1}));
    EXPECT_EQ(cache.lfu_not_promoted(), 2);
    // 4 just went viral and can enter the protected segment. This displaces the tail (1)
    // of the protected segment back into probationary.
    ASSERT_EQ(cache.read(4), "d");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {1, 3, 5}, {4, 2}));
    EXPECT_EQ(cache.lfu_not_promoted(), 2);
}

TEST_F(LfuCacheTest, lfu_gates_probationary_inserts_on_write_through) {
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, -1, 0);
    cache.maxElements(2, 0);
    cache.set_frequency_sketch_size(2);
    ASSERT_EQ(cache.read(2), "b"); // ==> freq 1
    ASSERT_EQ(cache.read(2), "b"); // ==> freq 2
    cache.write(7, "zoid"); // OK; capacity < max elems
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {7, 2}, {}));
    // 8 is not more popular than 2, so this insertion does not displace it
    cache.write(8, "berg");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {7, 2}, {}));
    // LFU is not updated from writes
    cache.write(8, "hello");
    cache.write(8, "world");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {7, 2}, {}));
    EXPECT_EQ(cache.lfu_dropped(), 3);
}

TEST_F(LfuCacheTest, lfu_gating_considers_capacity_bytes) {
    cache<CacheParam<P, B, SelfAsSize, zero<std::string>>> cache(m, 200, 0);
    cache.maxElements(10, 0); // will be capacity bytes-bound
    cache.set_frequency_sketch_size(10);
    cache.write(100, "foo");
    ASSERT_EQ(cache.read(100), "foo"); // Freq => 1
    ASSERT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 180, 0));
    // Inserting new element 50 would displace more popular 100
    cache.write(50, "bar");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {100}, {}));
    ASSERT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 180, 0));
    ASSERT_EQ(cache.read(50), "bar"); // Freq => 1, still no displacement
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {100}, {}));
    ASSERT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 180, 0));
    ASSERT_EQ(cache.read(50), "bar"); // Freq => 2, rise and shine
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {50}, {}));
    ASSERT_NO_FATAL_FAILURE(assert_segment_size_bytes(cache, 130, 0));
}

TEST_F(LfuCacheTest, resetting_sketch_initializes_new_sketch_with_cached_elems) {
    cache<CacheParam<P, B, zero<uint32_t>, size<std::string>>> cache(m, -1, -1);
    cache.maxElements(2, 1);
    cache.set_frequency_sketch_size(0);
    ASSERT_EQ(cache.read(1), "a");
    ASSERT_EQ(cache.read(2), "b");
    ASSERT_EQ(cache.read(1), "a"); // => protected
    ASSERT_EQ(cache.read(3), "c");
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {3, 2}, {1}));
    cache.set_frequency_sketch_size(10);
    EXPECT_EQ(cache.lfu_dropped(), 0);
    ASSERT_EQ(cache.read(4), "d"); // Not more popular than 2 => not inserted
    ASSERT_NO_FATAL_FAILURE(assert_segment_lru_keys(cache, {3, 2}, {1}));
    EXPECT_EQ(cache.lfu_dropped(), 1);
}

GTEST_MAIN_RUN_ALL_TESTS()
