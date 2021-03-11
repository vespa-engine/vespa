// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/unique_store.hpp>
#include <vespa/vespalib/datastore/unique_store_dictionary.hpp>
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

struct DictionaryReadTest : public ::testing::Test {
    DefaultUniqueStoreDictionary dict;
    IUniqueStoreDictionary::ReadSnapshot::UP snapshot;

    DictionaryReadTest()
        : dict(),
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

TEST_F(DictionaryReadTest, can_count_occurrences_of_a_key)
{
    add(3).add(5).take_snapshot();
    EXPECT_EQ(0, snapshot->count(Comparator(2)));
    EXPECT_EQ(1, snapshot->count(Comparator(3)));
    EXPECT_EQ(0, snapshot->count(Comparator(4)));
    EXPECT_EQ(1, snapshot->count(Comparator(5)));
}

TEST_F(DictionaryReadTest, can_count_occurrences_of_keys_in_a_range)
{
    add(3).add(5).add(7).add(9).take_snapshot();
    EXPECT_EQ(1, snapshot->count_in_range(Comparator(3), Comparator(3)));
    EXPECT_EQ(1, snapshot->count_in_range(Comparator(3), Comparator(4)));
    EXPECT_EQ(2, snapshot->count_in_range(Comparator(3), Comparator(5)));
    EXPECT_EQ(3, snapshot->count_in_range(Comparator(3), Comparator(7)));
    EXPECT_EQ(4, snapshot->count_in_range(Comparator(3), Comparator(9)));
    EXPECT_EQ(4, snapshot->count_in_range(Comparator(3), Comparator(10)));

    EXPECT_EQ(0, snapshot->count_in_range(Comparator(5), Comparator(3)));
}

TEST_F(DictionaryReadTest, can_iterate_all_keys)
{
    using EntryRefVector = std::vector<EntryRef>;
    add(3).add(5).add(7).take_snapshot();
    EntryRefVector refs;
    snapshot->foreach_key([&](EntryRef ref){ refs.emplace_back(ref); });
    EXPECT_EQ(EntryRefVector({EntryRef(3), EntryRef(5), EntryRef(7)}), refs);
}

GTEST_MAIN_RUN_ALL_TESTS()
