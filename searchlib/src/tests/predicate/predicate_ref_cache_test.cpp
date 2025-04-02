// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_ref_cache.

#include <vespa/searchlib/predicate/predicate_ref_cache.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cassert>
#include <vector>

using namespace search;
using namespace search::predicate;

namespace {

struct MyBufferStore {
    std::vector<uint32_t> store;
    const uint32_t *getBuffer(uint32_t ref) const {
        assert(ref < store.size());
        return &store[ref];
    }
    uint32_t insert(uint32_t value) {
        size_t size = store.size();
        store.push_back(value);
        return size | 0x01000000;  // size = 1
    }
    uint32_t insert(std::vector<uint32_t> data) {
        size_t size = store.size();
        uint8_t data_size = data.size();
        if (data.size() >= 0xff) {
            store.push_back(data.size());
            data_size = 0xff;
        }
        store.insert(store.end(), data.begin(), data.end());
        return size | (data_size << 24);
    }
};

TEST(PredicateRefCacheTest, require_that_single_entries_are_cached) {
    MyBufferStore store;
    PredicateRefCache<MyBufferStore> cache(store);

    uint32_t ref = store.insert(42);
    uint32_t new_ref = cache.insert(ref);
    EXPECT_EQ(ref, new_ref);

    uint32_t ref2 = store.insert(42);
    new_ref = cache.insert(ref2);
    EXPECT_EQ(ref, new_ref);

    uint32_t ref3 = store.insert(44);
    new_ref = cache.insert(ref3);
    EXPECT_EQ(ref3, new_ref);
}

TEST(PredicateRefCacheTest, require_that_multivalue_entries_are_cached) {
    MyBufferStore store;
    PredicateRefCache<MyBufferStore> cache(store);

    std::vector<uint32_t> data1 = {1, 2, 3, 4, 5};
    std::vector<uint32_t> data2 = {1, 2, 3, 4, 6};
    uint32_t ref = store.insert(data1);
    uint32_t new_ref = cache.insert(ref);
    EXPECT_EQ(ref, new_ref);

    uint32_t ref2 = store.insert(data1);
    new_ref = cache.insert(ref2);
    EXPECT_EQ(ref, new_ref);

    uint32_t ref3 = store.insert(data2);
    new_ref = cache.insert(ref3);
    EXPECT_EQ(ref3, new_ref);
}

TEST(PredicateRefCacheTest, require_that_entries_can_be_looked_up) {
    MyBufferStore store;
    PredicateRefCache<MyBufferStore> cache(store);

    uint32_t data = 42;
    EXPECT_EQ(0u, cache.find(&data, 1));
    uint32_t ref = store.insert(42);
    cache.insert(ref);
    EXPECT_EQ(ref, cache.find(&data, 1));
}

TEST(PredicateRefCacheTest, require_that_cache_handles_large_entries) {
    MyBufferStore store;
    PredicateRefCache<MyBufferStore> cache(store);

    std::vector<uint32_t> data1(300);
    std::vector<uint32_t> data2(300);
    data2.back() = 42;
    uint32_t ref1 = store.insert(data1);
    cache.insert(ref1);
    EXPECT_EQ(ref1, cache.find(&data1[0], data1.size()));
    EXPECT_EQ(0u, cache.find(&data2[0], data2.size()));
    uint32_t ref2 = store.insert(data2);
    uint32_t ref = cache.insert(ref2);
    EXPECT_EQ(ref, ref2);
    EXPECT_EQ(ref2, cache.find(&data2[0], data2.size()));
}

}  // namespace
