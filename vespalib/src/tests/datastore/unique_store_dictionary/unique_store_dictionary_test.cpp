// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/datastore/unique_store.hpp>
#include <vespa/vespalib/datastore/unique_store_dictionary.hpp>
#include <vespa/vespalib/datastore/sharded_hash_map.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("unique_store_dictionary_test");

using namespace vespalib::datastore;
using namespace vespalib::datastore::uniquestore;

class Comparator : public EntryComparator {
private:
    EntryRef _to_find;

    EntryRef resolve(EntryRef ref) const {
        if (ref == EntryRef()) {
            return _to_find;
        }
        return ref;
    }

public:
    Comparator(uint32_t to_find)
        : _to_find(to_find)
    {}
    bool less(const EntryRef lhs, const EntryRef rhs) const override {
        return resolve(lhs).ref() < resolve(rhs).ref();
    }
    bool equal(const EntryRef lhs, const EntryRef rhs) const override {
        return resolve(lhs).ref() == resolve(rhs).ref();
    }
    size_t hash(const EntryRef rhs) const override {
        return rhs.valid() ? rhs.ref() : _to_find.ref();
    }
};

template <typename UniqueStoreDictionaryType>
struct UniqueStoreDictionaryTest : public ::testing::Test {
    UniqueStoreDictionaryType dict;
    std::unique_ptr<IUniqueStoreDictionaryReadSnapshot> snapshot;
    vespalib::GenerationHandler gen_handler;

    UniqueStoreDictionaryTest()
        : dict(std::make_unique<Comparator>(0)),
          snapshot(),
          gen_handler()
    {
    }
    UniqueStoreDictionaryTest& add(uint32_t value) {
        auto result = dict.add(Comparator(value), [=]() noexcept { return EntryRef(value); });
        assert(result.inserted());
        return *this;
    }
    UniqueStoreDictionaryTest& remove(uint32_t value) {
        dict.remove(Comparator(value), EntryRef(value));
        return *this;
    }
    void inc_generation() {
        dict.freeze();
        dict.assign_generation(gen_handler.getCurrentGeneration());
        gen_handler.incGeneration();
        dict.reclaim_memory(gen_handler.get_oldest_used_generation());
    }
    void take_snapshot() {
        dict.freeze();
        snapshot = dict.get_read_snapshot();
        snapshot->fill();
        snapshot->sort();
    }
};

using UniqueStoreDictionaryTestTypes = ::testing::Types<DefaultUniqueStoreDictionary, UniqueStoreDictionary<DefaultDictionary, IUniqueStoreDictionary, ShardedHashMap>, UniqueStoreDictionary<NoBTreeDictionary, IUniqueStoreDictionary, ShardedHashMap>>;
VESPA_GTEST_TYPED_TEST_SUITE(UniqueStoreDictionaryTest, UniqueStoreDictionaryTestTypes);

// Disable warnings emitted by gtest generated files when using typed tests
#pragma GCC diagnostic push
#ifndef __clang__
#pragma GCC diagnostic ignored "-Wsuggest-override"
#endif

TYPED_TEST(UniqueStoreDictionaryTest, can_count_occurrences_of_a_key)
{
    this->add(3).add(5).take_snapshot();
    EXPECT_EQ(0, this->snapshot->count(Comparator(2)));
    EXPECT_EQ(1, this->snapshot->count(Comparator(3)));
    EXPECT_EQ(0, this->snapshot->count(Comparator(4)));
    EXPECT_EQ(1, this->snapshot->count(Comparator(5)));
}

TYPED_TEST(UniqueStoreDictionaryTest, can_count_occurrences_of_keys_in_a_range)
{
    if (!this->dict.get_has_btree_dictionary()) {
        return;
    }
    this->add(3).add(5).add(7).add(9).take_snapshot();
    EXPECT_EQ(1, this->snapshot->count_in_range(Comparator(3), Comparator(3)));
    EXPECT_EQ(1, this->snapshot->count_in_range(Comparator(3), Comparator(4)));
    EXPECT_EQ(2, this->snapshot->count_in_range(Comparator(3), Comparator(5)));
    EXPECT_EQ(3, this->snapshot->count_in_range(Comparator(3), Comparator(7)));
    EXPECT_EQ(4, this->snapshot->count_in_range(Comparator(3), Comparator(9)));
    EXPECT_EQ(4, this->snapshot->count_in_range(Comparator(3), Comparator(10)));

    EXPECT_EQ(0, this->snapshot->count_in_range(Comparator(5), Comparator(3)));
}

TYPED_TEST(UniqueStoreDictionaryTest, can_iterate_all_keys)
{
    using EntryRefVector = std::vector<EntryRef>;
    this->add(3).add(5).add(7).take_snapshot();
    EntryRefVector refs;
    this->snapshot->foreach_key([&](AtomicEntryRef ref){ refs.emplace_back(ref.load_relaxed()); });
    EXPECT_EQ(EntryRefVector({EntryRef(3), EntryRef(5), EntryRef(7)}), refs);
}

TYPED_TEST(UniqueStoreDictionaryTest, memory_usage_is_reported)
{
    auto initial_usage = this->dict.get_memory_usage();
    this->add(10);
    auto usage = this->dict.get_memory_usage();
    EXPECT_LT(initial_usage.usedBytes(), usage.usedBytes());
    EXPECT_EQ(initial_usage.deadBytes(), usage.deadBytes());
    EXPECT_EQ(0, usage.allocatedBytesOnHold());
}

TYPED_TEST(UniqueStoreDictionaryTest, compaction_works)
{
    for (uint32_t i = 1; i < 100; ++i) {
        this->add(i);
    }
    for (uint32_t i = 10; i < 100; ++i) {
        this->remove(i);
    }
    this->inc_generation();
    auto btree_memory_usage_before = this->dict.get_btree_memory_usage();
    auto hash_memory_usage_before = this->dict.get_hash_memory_usage();
    CompactionStrategy compaction_strategy;
    for (uint32_t i = 0; i < 15; ++i) {
        this->dict.compact_worst(true, true, compaction_strategy);
    }
    this->inc_generation();
    auto btree_memory_usage_after = this->dict.get_btree_memory_usage();
    auto hash_memory_usage_after = this->dict.get_hash_memory_usage();
    if (this->dict.get_has_btree_dictionary()) {
        EXPECT_LT(btree_memory_usage_after.deadBytes(), btree_memory_usage_before.deadBytes());
    } else {
        EXPECT_EQ(btree_memory_usage_after.deadBytes(), btree_memory_usage_before.deadBytes());
    }
    if (this->dict.get_has_hash_dictionary()) {
        EXPECT_LT(hash_memory_usage_after.deadBytes(), hash_memory_usage_before.deadBytes());
    } else {
        EXPECT_EQ(hash_memory_usage_after.deadBytes(), hash_memory_usage_before.deadBytes());
    }
    std::vector<EntryRef> exp_refs;
    for (uint32_t i = 1; i < 10; ++i) {
        exp_refs.emplace_back(EntryRef(i));
    }
    this->take_snapshot();
    std::vector<EntryRef> refs;
    this->snapshot->foreach_key([&](const AtomicEntryRef& ref){ refs.emplace_back(ref.load_relaxed()); });
    EXPECT_EQ(exp_refs, refs);
}

#pragma GCC diagnostic pop

GTEST_MAIN_RUN_ALL_TESTS()
