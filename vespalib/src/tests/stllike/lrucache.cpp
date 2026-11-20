// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/vespalib/stllike/lrucache_map.hpp>

#include <string>

using namespace vespalib;

TEST(LruCacheMapTest, cache_basics) {
    lrucache_map<LruParam<int, std::string>> cache(7);
    // Verify start conditions.
    EXPECT_EQ(cache.size(), 0);
    cache.insert(1, "First inserted string");
    cache.verifyInternals();
    EXPECT_EQ(cache.size(), 1);
    EXPECT_TRUE(cache.hasKey(1));
    cache.insert(2, "Second inserted string");
    cache.verifyInternals();
    EXPECT_EQ(cache.size(), 2);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    cache.insert(3, "Third inserted string");
    cache.verifyInternals();
    EXPECT_EQ(cache.size(), 3);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    cache.insert(4, "Fourth inserted string");
    cache.verifyInternals();
    EXPECT_EQ(cache.size(), 4);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    cache.insert(5, "Fifth inserted string");
    cache.verifyInternals();
    EXPECT_EQ(cache.size(), 5);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
    cache.insert(6, "Sixt inserted string");
    cache.verifyInternals();
    EXPECT_EQ(cache.size(), 6);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
    EXPECT_TRUE(cache.hasKey(6));
    cache.insert(7, "Seventh inserted string");
    cache.verifyInternals();
    EXPECT_EQ(cache.size(), 7);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
    EXPECT_TRUE(cache.hasKey(6));
    EXPECT_TRUE(cache.hasKey(7));
    cache.insert(8, "Eighth inserted string");
    cache.verifyInternals();
    EXPECT_EQ(cache.size(), 7);
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
    EXPECT_TRUE(cache.hasKey(6));
    EXPECT_TRUE(cache.hasKey(7));
    EXPECT_TRUE(cache.hasKey(8));
    cache.insert(15, "Eighth inserted string");
    cache.verifyInternals();
    EXPECT_EQ(cache.size(), 7);
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
    EXPECT_TRUE(cache.hasKey(6));
    EXPECT_TRUE(cache.hasKey(7));
    EXPECT_TRUE(cache.hasKey(8));
    EXPECT_TRUE(cache.hasKey(15));
    // Test get and erase
    (void)cache.get(3);
    cache.verifyInternals();
    cache.erase(3);
    cache.verifyInternals();
    EXPECT_TRUE(!cache.hasKey(3));
}

using MyKey = std::shared_ptr<std::string>;
using MyData = std::shared_ptr<std::string>;

struct SharedEqual {
    bool operator()(const MyKey& a, const MyKey& b) const noexcept { return ((*a) == (*b)); }
};

struct SharedHash {
    size_t operator()(const MyKey& arg) const noexcept { return arg->size(); }
};

TEST(LruCacheMapTest, cache_insert_over_resize) {
    using LS = std::shared_ptr<std::string>;
    using Cache = lrucache_map<LruParam<int, LS>>;

    Cache  cache(100);
    size_t sum(0);
    for (size_t i(0); i < cache.capacity() * 10; i++) {
        LS s(std::make_shared<std::string>("abc"));
        cache[random()] = s;
        sum += strlen(s->c_str());
        EXPECT_EQ(strlen(s->c_str()), s->size());
    }
    EXPECT_EQ(sum, cache.capacity() * 10 * 3);
}

TEST(LruCacheMapTest, cache_erase_by_key) {
    lrucache_map<LruParam<MyKey, MyData, SharedHash, SharedEqual>> cache(4);

    MyData d(std::make_shared<std::string>("foo"));
    MyKey  k(std::make_shared<std::string>("barlol"));
    // Verify start conditions.
    EXPECT_EQ(cache.size(), 0);
    EXPECT_EQ(d.use_count(), 1);
    EXPECT_EQ(k.use_count(), 1);
    cache.insert(k, d);
    EXPECT_EQ(d.use_count(), 2);
    EXPECT_EQ(k.use_count(), 2);
    cache.erase(k);
    EXPECT_EQ(d.use_count(), 1);
    EXPECT_EQ(k.use_count(), 1);
}

TEST(LruCacheMapTest, cache_iterator) {
    using Cache = lrucache_map<LruParam<int, std::string>>;
    Cache cache(3);
    cache.insert(1, "first");
    cache.insert(2, "second");
    cache.insert(3, "third");
    Cache::iterator it(cache.begin());
    Cache::iterator mt(cache.end());
    ASSERT_TRUE(it != mt);
    ASSERT_EQ("third", *it);
    ASSERT_TRUE(it != mt);
    ASSERT_EQ("second", *(++it));
    ASSERT_TRUE(it != mt);
    ASSERT_EQ("second", *it++);
    ASSERT_TRUE(it != mt);
    ASSERT_EQ("first", *it);
    ASSERT_TRUE(it != mt);
    ++it;
    ASSERT_TRUE(it == mt);
    cache.insert(4, "fourth");
    Cache::iterator it2(cache.begin());
    Cache::iterator it3(cache.begin());
    ASSERT_EQ("fourth", *it2);
    ASSERT_TRUE(it2 == it3);
    ++it2;
    ASSERT_TRUE(it2 != it3);
    ++it2;
    ++it2;
    ASSERT_TRUE(it2 == mt);
    Cache::iterator it4 = cache.erase(it3);
    ASSERT_EQ("third", *it4);
    ASSERT_EQ("third", *cache.begin());
    Cache::iterator it5(cache.erase(cache.end()));
    ASSERT_TRUE(it5 == cache.end());
}

namespace {

template <typename C> std::string lru_key_order(C& cache) {
    std::string keys;
    for (auto it = cache.begin(); it != cache.end(); ++it) {
        if (!keys.empty()) {
            keys += ' ';
        }
        keys += std::to_string(it.key());
    }
    return keys;
}

} // namespace

TEST(LruCacheMapTest, cache_erase_by_iterator) {
    using Cache = lrucache_map<LruParam<int, std::string>>;
    Cache cache(3);
    cache.insert(1, "first");
    cache.insert(8, "second");
    cache.insert(15, "third");
    cache.insert(15, "third");
    cache.insert(8, "second");
    cache.insert(1, "first");
    EXPECT_EQ(lru_key_order(cache), "1 8 15");
    Cache::iterator it(cache.begin());
    ASSERT_EQ("first", *it);
    ++it;
    ASSERT_EQ("second", *it);
    it = cache.erase(it);
    EXPECT_EQ(lru_key_order(cache), "1 15");
    ASSERT_EQ("third", *it);
    cache.erase(it);
    EXPECT_EQ(lru_key_order(cache), "1");
    cache.verifyInternals();
}

TEST(LruCacheMapTest, erase_if_present_invokes_callback_and_erases_only_existing_keys) {
    using Cache = lrucache_map<LruParam<int, std::string>>;
    Cache cache(4);
    // 1, 8 and 15 share a hash bucket, exercising erase both from chain head and within a chain.
    cache.insert(1, "first");
    cache.insert(8, "second");
    cache.insert(15, "third");
    cache.insert(2, "other");
    EXPECT_EQ(lru_key_order(cache), "2 15 8 1");
    // Verify the collision assumption above still holds for the current table sizing.
    ASSERT_EQ(cache.bucket_of(8), cache.bucket_of(1));
    ASSERT_EQ(cache.bucket_of(15), cache.bucket_of(1));
    ASSERT_NE(cache.bucket_of(2), cache.bucket_of(1));

    std::vector<std::string> seen;
    auto                     record = [&seen](const std::string& v) { seen.push_back(v); };

    EXPECT_TRUE(cache.erase_if_present(8, record));
    EXPECT_EQ(lru_key_order(cache), "2 15 1");
    cache.verifyInternals();

    EXPECT_FALSE(cache.erase_if_present(8, record));
    EXPECT_FALSE(cache.erase_if_present(22, record));
    EXPECT_EQ(lru_key_order(cache), "2 15 1");

    EXPECT_TRUE(cache.erase_if_present(1, record));
    EXPECT_TRUE(cache.erase_if_present(2, record));
    EXPECT_EQ(lru_key_order(cache), "15");
    cache.verifyInternals();

    EXPECT_TRUE(cache.erase_if_present(15, record));
    EXPECT_TRUE(cache.empty());
    cache.verifyInternals();

    EXPECT_EQ(seen, (std::vector<std::string>{"second", "first", "other", "third"}));
}

TEST(LruCacheMapTest, find_no_ref_returns_ptr_if_present_and_does_not_update_lru) {
    using Cache = lrucache_map<LruParam<int, std::string>>;
    Cache cache(3);
    cache.insert(1, "ichi");
    cache.insert(2, "ni");
    cache.insert(3, "san");
    EXPECT_EQ(lru_key_order(cache), "3 2 1");

    std::string* ptr = cache.find_no_ref(1);
    ASSERT_TRUE(ptr != nullptr);
    EXPECT_EQ(*ptr, "ichi");
    EXPECT_EQ(lru_key_order(cache), "3 2 1");

    *ptr = "one";
    const Cache&       const_cache = cache;
    const std::string* cptr = const_cache.find_no_ref(1);
    ASSERT_TRUE(cptr != nullptr);
    EXPECT_EQ(*cptr, "one");
    EXPECT_EQ(lru_key_order(cache), "3 2 1");

    cptr = const_cache.find_no_ref(2);
    ASSERT_TRUE(cptr != nullptr);
    EXPECT_EQ(*cptr, "ni");
    EXPECT_EQ(lru_key_order(cache), "3 2 1");

    EXPECT_EQ(cache.find_no_ref(4), nullptr);
    EXPECT_EQ(const_cache.find_no_ref(4), nullptr);
    EXPECT_EQ(lru_key_order(cache), "3 2 1");
}

TEST(LruCacheMapTest, find_and_lazy_ref_elides_updating_LRU_head_when_less_than_half_full) {
    using Cache = lrucache_map<LruParam<int, std::string>>;
    Cache cache(6);
    cache.insert(1, "a");
    cache.insert(2, "b");
    EXPECT_EQ(lru_key_order(cache), "2 1");
    EXPECT_NE(cache.find_and_lazy_ref(1), nullptr);
    EXPECT_EQ(lru_key_order(cache), "2 1"); // Not updated
    cache.insert(3, "c");
    EXPECT_EQ(lru_key_order(cache), "3 2 1");
    EXPECT_NE(cache.find_and_lazy_ref(1), nullptr);
    EXPECT_EQ(lru_key_order(cache), "3 2 1"); // Still not > capacity/2
    cache.insert(4, "c");
    EXPECT_EQ(lru_key_order(cache), "4 3 2 1");
    EXPECT_NE(cache.find_and_lazy_ref(1), nullptr);
    EXPECT_EQ(lru_key_order(cache), "1 4 3 2");     // At long last, our time to LRU shine
    EXPECT_EQ(cache.find_and_lazy_ref(5), nullptr); // Key not found
    EXPECT_EQ(lru_key_order(cache), "1 4 3 2");
}

TEST(LruCacheMapTest, eager_find_and_ref_always_moves_to_LRU_head) {
    using Cache = lrucache_map<LruParam<int, std::string>>;
    Cache cache(6);
    cache.insert(1, "a");
    cache.insert(2, "b");
    cache.insert(3, "c");
    cache.insert(4, "d");
    cache.insert(5, "e");
    cache.insert(6, "f");
    EXPECT_EQ(lru_key_order(cache), "6 5 4 3 2 1");
    EXPECT_NE(cache.find_and_ref(2), nullptr);
    EXPECT_EQ(lru_key_order(cache), "2 6 5 4 3 1");
    EXPECT_NE(cache.find_and_ref(5), nullptr);
    EXPECT_EQ(lru_key_order(cache), "5 2 6 4 3 1");
    EXPECT_NE(cache.find_and_ref(1), nullptr);
    EXPECT_EQ(lru_key_order(cache), "1 5 2 6 4 3");
    EXPECT_EQ(cache.find_and_ref(7), nullptr); // Key not found; no touching the shiny happy LRU
    EXPECT_EQ(lru_key_order(cache), "1 5 2 6 4 3");
}

TEST(LruCacheMapTest, trimming_removes_old_entries_until_within_capacity) {
    using Cache = lrucache_map<LruParam<int, std::string>>;
    Cache cache(5);
    cache.insert(1, "a");
    cache.insert(2, "b");
    cache.insert(3, "c");
    cache.insert(4, "d");
    // Cache is below capacity, trimming should do nothing
    cache.trim();
    EXPECT_EQ(lru_key_order(cache), "4 3 2 1");
    cache.verifyInternals();

    cache.insert(5, "e");
    // Cache is at capacity, trimming should do nothing
    cache.trim();
    EXPECT_EQ(lru_key_order(cache), "5 4 3 2 1");
    cache.verifyInternals();

    cache.maxElements(3);
    // maxElements() doesn't trim anything by itself (checking this here in case it changes)
    EXPECT_EQ(lru_key_order(cache), "5 4 3 2 1");
    // But trimming should do the deed
    cache.trim();
    EXPECT_EQ(lru_key_order(cache), "5 4 3");
    cache.verifyInternals();

    // Trimming should allow going down to zero size
    cache.maxElements(0);
    EXPECT_EQ(lru_key_order(cache), "5 4 3");
    cache.trim();
    EXPECT_EQ(cache.size(), 0);
    EXPECT_EQ(lru_key_order(cache), "");
    cache.verifyInternals();
}

TEST(LruCacheMapTest, implicit_lru_trimming_on_oversized_insert_does_not_remove_head_element) {
    using Cache = lrucache_map<LruParam<int, std::string>>;
    Cache cache(0);
    cache.insert(1, "sneaky");
    EXPECT_EQ(cache.size(), 1);
    EXPECT_EQ(lru_key_order(cache), "1");
    // But head element can be replaced
    cache.insert(2, "stuff");
    EXPECT_EQ(cache.size(), 1);
    EXPECT_EQ(lru_key_order(cache), "2");
}

TEST(LruCacheMapTest, can_get_iter_to_last_element) {
    using Cache = lrucache_map<LruParam<int, std::string>>;
    Cache cache(5);
    // Returned iterator is end() if the map is empty
    EXPECT_TRUE(cache.iter_to_last() == cache.end());
    cache.insert(1, "a");
    ASSERT_TRUE(cache.iter_to_last() != cache.end());
    EXPECT_EQ(cache.iter_to_last().key(), 1);
    cache.insert(2, "b");
    ASSERT_TRUE(cache.iter_to_last() != cache.end());
    EXPECT_EQ(cache.iter_to_last().key(), 1); // LRU tail is still 1
    cache.insert(3, "c");
    cache.insert(4, "d");
    ASSERT_TRUE(cache.iter_to_last() != cache.end());
    EXPECT_EQ(cache.iter_to_last().key(), 1); // ... and still 1.
    // Move 1 to LRU head. Tail is now 2.
    ASSERT_TRUE(cache.find_and_ref(1));
    ASSERT_TRUE(cache.iter_to_last() != cache.end());
    EXPECT_EQ(cache.iter_to_last().key(), 2);
    // Move 3 to LRU head. Tail is still 2.
    ASSERT_TRUE(cache.find_and_ref(3));
    ASSERT_TRUE(cache.iter_to_last() != cache.end());
    EXPECT_EQ(cache.iter_to_last().key(), 2);
    // Move 2 to LRU head. Tail is now 4.
    ASSERT_TRUE(cache.find_and_ref(2));
    ASSERT_TRUE(cache.iter_to_last() != cache.end());
    EXPECT_EQ(cache.iter_to_last().key(), 4);

    EXPECT_EQ(lru_key_order(cache), "2 3 1 4");

    cache.erase(4);
    ASSERT_TRUE(cache.iter_to_last() != cache.end());
    EXPECT_EQ(cache.iter_to_last().key(), 1);
    cache.erase(3);
    cache.erase(2);
    cache.erase(1);
    ASSERT_TRUE(cache.iter_to_last() == cache.end());
}

GTEST_MAIN_RUN_ALL_TESTS()
