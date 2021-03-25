// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/unique_store.hpp>
#include <vespa/vespalib/datastore/unique_store_dictionary.hpp>
#include <vespa/vespalib/datastore/simple_hash_map.h>
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
        return rhs.ref();
    }
};

template <typename UniqueStoreDictionaryType>
struct DictionaryReadTest : public ::testing::Test {
    UniqueStoreDictionaryType dict;
    IUniqueStoreDictionary::ReadSnapshot::UP snapshot;

    DictionaryReadTest()
        : dict(std::make_unique<Comparator>(0)),
          snapshot()
    {
    }
    DictionaryReadTest& add(uint32_t value) {
        auto result = dict.add(Comparator(value), [=]() noexcept { return EntryRef(value); });
        assert(result.inserted());
        return *this;
    }
    void take_snapshot() {
        dict.freeze();
        snapshot = dict.get_read_snapshot();
    }
};

using DictionaryReadTestTypes = ::testing::Types<DefaultUniqueStoreDictionary, UniqueStoreDictionary<DefaultDictionary, IUniqueStoreDictionary, SimpleHashMap>>;
VESPA_GTEST_TYPED_TEST_SUITE(DictionaryReadTest, DictionaryReadTestTypes);

// Disable warnings emitted by gtest generated files when using typed tests
#pragma GCC diagnostic push
#ifndef __clang__
#pragma GCC diagnostic ignored "-Wsuggest-override"
#endif

TYPED_TEST(DictionaryReadTest, can_count_occurrences_of_a_key)
{
    this->add(3).add(5).take_snapshot();
    EXPECT_EQ(0, this->snapshot->count(Comparator(2)));
    EXPECT_EQ(1, this->snapshot->count(Comparator(3)));
    EXPECT_EQ(0, this->snapshot->count(Comparator(4)));
    EXPECT_EQ(1, this->snapshot->count(Comparator(5)));
}

TYPED_TEST(DictionaryReadTest, can_count_occurrences_of_keys_in_a_range)
{
    this->add(3).add(5).add(7).add(9).take_snapshot();
    EXPECT_EQ(1, this->snapshot->count_in_range(Comparator(3), Comparator(3)));
    EXPECT_EQ(1, this->snapshot->count_in_range(Comparator(3), Comparator(4)));
    EXPECT_EQ(2, this->snapshot->count_in_range(Comparator(3), Comparator(5)));
    EXPECT_EQ(3, this->snapshot->count_in_range(Comparator(3), Comparator(7)));
    EXPECT_EQ(4, this->snapshot->count_in_range(Comparator(3), Comparator(9)));
    EXPECT_EQ(4, this->snapshot->count_in_range(Comparator(3), Comparator(10)));

    EXPECT_EQ(0, this->snapshot->count_in_range(Comparator(5), Comparator(3)));
}

TYPED_TEST(DictionaryReadTest, can_iterate_all_keys)
{
    using EntryRefVector = std::vector<EntryRef>;
    this->add(3).add(5).add(7).take_snapshot();
    EntryRefVector refs;
    this->snapshot->foreach_key([&](EntryRef ref){ refs.emplace_back(ref); });
    EXPECT_EQ(EntryRefVector({EntryRef(3), EntryRef(5), EntryRef(7)}), refs);
}

TYPED_TEST(DictionaryReadTest, memory_usage_is_reported)
{
    auto initial_usage = this->dict.get_memory_usage();
    this->add(10);
    auto usage = this->dict.get_memory_usage();
    EXPECT_LT(initial_usage.usedBytes(), usage.usedBytes());
    EXPECT_EQ(initial_usage.deadBytes(), usage.deadBytes());
    EXPECT_EQ(0, usage.allocatedBytesOnHold());
}

#pragma GCC diagnostic pop

GTEST_MAIN_RUN_ALL_TESTS()
