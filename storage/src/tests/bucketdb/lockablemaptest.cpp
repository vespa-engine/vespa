// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/bucketdb/btree_lockable_map.hpp>
#include <vespa/storage/bucketdb/striped_btree_lockable_map.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

#include <vespa/log/log.h>
LOG_SETUP(".lockable_map_test");

// FIXME these old tests may have the least obvious semantics and worst naming in the entire storage module
// FIXME the non-bucket ID based tests only "accidentally" work with the striped DB implementation
// since they just all happen to look like zero-buckets with count-bits above the minimum threshold.

using namespace ::testing;
using document::BucketId;

namespace storage {

namespace {

struct A {
    int _val1;
    int _val2;
    int _val3;

    A() : _val1(0), _val2(0), _val3(0) {}
    A(int val1, int val2, int val3)
        : _val1(val1), _val2(val2), _val3(val3) {}

    static bool mayContain(const A&) { return true; }
    // Make this type smell more like a proper bucket DB value type.
    constexpr bool verifyLegal() const noexcept { return true; }
    constexpr bool valid() const noexcept { return true; }

    bool operator==(const A& a) const noexcept {
        return (_val1 == a._val1 && _val2 == a._val2 && _val3 == a._val3);
    }
    bool operator!=(const A& a) const noexcept {
        return !(*this == a);
    }
    bool operator<(const A& a) const noexcept {
        if (_val1 != a._val1) return (_val1 < a._val1);
        if (_val2 != a._val2) return (_val2 < a._val2);
        return (_val3 < a._val3);
    }
};

std::ostream& operator<<(std::ostream& out, const A& a) {
    return out << "A(" << a._val1 << ", " << a._val2 << ", " << a._val3 << ")";
}

}

template <typename MapT>
struct LockableMapTest : ::testing::Test {
    using Map = MapT;
};

using MapTypes = ::testing::Types<bucketdb::BTreeLockableMap<A>, bucketdb::StripedBTreeLockableMap<A>>;
TYPED_TEST_SUITE(LockableMapTest, MapTypes);

TYPED_TEST(LockableMapTest, simple_usage) {
    // Tests insert, erase, size, empty, operator[]
    TypeParam map;
    // Do some insertions
    EXPECT_TRUE(map.empty());
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    EXPECT_EQ(false, preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    EXPECT_EQ(false, preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);
    EXPECT_EQ(false, preExisted);
    EXPECT_EQ(map.size(), 3);

    map.insert(11, A(4, 7, 0), "foo", preExisted);
    EXPECT_EQ(true, preExisted);
    EXPECT_EQ(map.size(), 3);
    EXPECT_FALSE(map.empty());

    // Access some elements
    EXPECT_EQ(A(4, 7, 0), *map.get(11, "foo"));
    EXPECT_EQ(A(1, 2, 3), *map.get(16, "foo"));
    EXPECT_EQ(A(42,0, 0), *map.get(14, "foo"));

    // Do removes
    EXPECT_EQ(map.erase(12, "foo"), 0);
    EXPECT_EQ(map.size(), 3);

    EXPECT_EQ(map.erase(14, "foo"), 1);
    EXPECT_EQ(map.size(), 2);

    EXPECT_EQ(map.erase(11, "foo"), 1);
    EXPECT_EQ(map.erase(16, "foo"), 1);
    EXPECT_EQ(map.size(), 0);
    EXPECT_TRUE(map.empty());
}

namespace {

template <typename Map>
struct NonConstProcessor {
    typename Map::Decision operator() (int key, A& a) const noexcept {
        (void) key;
        ++a._val2;
        return Map::UPDATE;
    }
};

template <typename Map>
struct EntryProcessor {
    mutable uint32_t count;
    mutable std::vector<std::string> log;
    mutable std::vector<typename Map::Decision> behaviour;

    EntryProcessor();
    explicit EntryProcessor(const std::vector<typename Map::Decision>& decisions);
    ~EntryProcessor();

    typename Map::Decision operator()(uint64_t key, A& a) const {
        std::ostringstream ost;
        ost << key << " - " << a;
        log.push_back(ost.str());
        typename Map::Decision d = Map::CONTINUE;
        if (behaviour.size() > count) {
            d = behaviour[count++];
        }
        if (d == Map::UPDATE) {
            ++a._val3;
        }
        return d;
    }

    std::string toString() {
        std::ostringstream ost;
        for (const auto & i : log) {
            ost << i << "\n";
        }
        return ost.str();
    }
};

template <typename Map>
EntryProcessor<Map>::EntryProcessor()
    : count(0), log(), behaviour() {}

template <typename Map>
EntryProcessor<Map>::EntryProcessor(const std::vector<typename Map::Decision>& decisions)
    : count(0), log(), behaviour(decisions) {}

template <typename Map>
EntryProcessor<Map>::~EntryProcessor() = default;

template <typename Map>
struct ConstProcessor {
    mutable uint32_t count;
    mutable std::vector<std::string> log;
    mutable std::vector<typename Map::Decision> behaviour;

    ConstProcessor();
    explicit ConstProcessor(std::vector<typename Map::Decision> decisions);
    ~ConstProcessor();

    typename Map::Decision operator()(uint64_t key, const A& a) const {
        std::ostringstream ost;
        ost << key << " - " << a;
        log.push_back(ost.str());
        auto d = Map::Decision::CONTINUE;
        if (behaviour.size() > count) {
            d = behaviour[count++];
        }
        return d;
    }

    std::string toString() {
        std::ostringstream ost;
        for (const auto& entry : log) {
            ost << entry << "\n";
        }
        return ost.str();
    }
};

template <typename Map>
ConstProcessor<Map>::ConstProcessor()
    : count(0), log(), behaviour() {}

template <typename Map>
ConstProcessor<Map>::ConstProcessor(std::vector<typename Map::Decision> decisions)
    : count(0), log(), behaviour(std::move(decisions)) {}

template <typename Map>
ConstProcessor<Map>::~ConstProcessor() = default;

}

TYPED_TEST(LockableMapTest, iterating) {
    TypeParam map;
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);
    // Test that we can use functor with non-const function
    {
        NonConstProcessor<TypeParam> ncproc;
        map.for_each_mutable_unordered(std::ref(ncproc), "foo"); // First round of mutating functor for `all`
        EXPECT_EQ(A(4, 7, 0), *map.get(11, "foo"));
        EXPECT_EQ(A(42,1, 0), *map.get(14, "foo"));
        EXPECT_EQ(A(1, 3, 3), *map.get(16, "foo"));
        map.for_each_mutable_unordered(std::ref(ncproc), "foo"); // Once more, with feeling.
        EXPECT_EQ(A(4, 8, 0), *map.get(11, "foo"));
        EXPECT_EQ(A(42,2, 0), *map.get(14, "foo"));
        EXPECT_EQ(A(1, 4, 3), *map.get(16, "foo"));
    }

    {
        ConstProcessor<TypeParam> cproc;
        map.for_each(std::ref(cproc), "foo");
        std::string expected("11 - A(4, 8, 0)\n"
                             "14 - A(42, 2, 0)\n"
                             "16 - A(1, 4, 3)\n");
        EXPECT_EQ(expected, cproc.toString());
    }
    // Test that we can use const functors directly..
    map.for_each(ConstProcessor<TypeParam>(), "foo");

    // Test that we can abort iterating
    {
        std::vector<typename TypeParam::Decision> decisions;
        decisions.push_back(TypeParam::CONTINUE);
        decisions.push_back(TypeParam::ABORT);
        EntryProcessor<TypeParam> proc(decisions);
        map.for_each_mutable_unordered(std::ref(proc), "foo");
        std::string expected("11 - A(4, 8, 0)\n"
                             "14 - A(42, 2, 0)\n");
        EXPECT_EQ(expected, proc.toString());
    }
    // Test that we can remove during iteration
    {
        std::vector<typename TypeParam::Decision> decisions;
        decisions.push_back(TypeParam::CONTINUE);
        decisions.push_back(TypeParam::REMOVE); // TODO consider removing; not used
        EntryProcessor<TypeParam> proc(decisions);
        map.for_each_mutable_unordered(std::ref(proc), "foo");
        std::string expected("11 - A(4, 8, 0)\n"
                             "14 - A(42, 2, 0)\n"
                             "16 - A(1, 4, 3)\n");
        EXPECT_EQ(expected, proc.toString());
        EXPECT_EQ(2u, map.size());
        EXPECT_EQ(A(4, 8, 0), *map.get(11, "foo"));
        EXPECT_EQ(A(1, 4, 3), *map.get(16, "foo"));
        auto entry = map.get(14, "foo");
        EXPECT_FALSE(entry.exists());
    }
}

TYPED_TEST(LockableMapTest, explicit_iterator_is_key_ordered) {
    TypeParam map;
    bool preExisted;
    map.insert(16, A(16, 0, 0), "foo", preExisted);
    map.insert(18, A(18, 0, 0), "foo", preExisted);
    map.insert(11, A(11, 0, 0), "foo", preExisted);
    map.insert(14, A(14, 0, 0), "foo", preExisted);
    map.insert(20, A(20, 0, 0), "foo", preExisted);

    std::string expected("11 - A(11, 0, 0)\n"
                         "14 - A(14, 0, 0)\n"
                         "16 - A(16, 0, 0)\n"
                         "18 - A(18, 0, 0)\n"
                         "20 - A(20, 0, 0)\n");
    ConstProcessor<TypeParam> cproc;

    auto guard = map.acquire_read_guard();
    for (auto iter = guard->create_iterator(); iter->valid(); iter->next()) {
        cproc(iter->key(), iter->value());
    }
    EXPECT_EQ(expected, cproc.toString());
}

TYPED_TEST(LockableMapTest, chunked_iteration_is_transparent_across_chunk_sizes) {
    TypeParam map;
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);
    std::string expected("11 - A(4, 6, 0)\n"
                         "14 - A(42, 0, 0)\n"
                         "16 - A(1, 2, 3)\n");
    {
        ConstProcessor<TypeParam> cproc;
        // for_each_chunked with chunk size of 1
        map.for_each_chunked(std::ref(cproc), "foo", 1us, 1);
        EXPECT_EQ(expected, cproc.toString());
    }
    {
        ConstProcessor<TypeParam> cproc;
        // for_each_chunked with chunk size larger than db size
        map.for_each_chunked(std::ref(cproc), "foo", 1us, 100);
        EXPECT_EQ(expected, cproc.toString());
    }
}

TYPED_TEST(LockableMapTest, can_abort_during_chunked_iteration) {
    TypeParam map;
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);

    std::vector<typename TypeParam::Decision> decisions;
    decisions.push_back(TypeParam::CONTINUE);
    decisions.push_back(TypeParam::ABORT);
    ConstProcessor<TypeParam> proc(std::move(decisions));
    map.for_each_chunked(std::ref(proc), "foo", 1us, 100);
    std::string expected("11 - A(4, 6, 0)\n"
                         "14 - A(42, 0, 0)\n");
    EXPECT_EQ(expected, proc.toString());
}

TYPED_TEST(LockableMapTest, can_iterate_via_read_guard) {
    TypeParam map;
    bool pre_existed;
    map.insert(16, A(1, 2, 3), "foo", pre_existed);
    map.insert(11, A(4, 6, 0), "foo", pre_existed);
    map.insert(14, A(42, 0, 0), "foo", pre_existed);
    std::string expected("11 - A(4, 6, 0)\n"
                         "14 - A(42, 0, 0)\n"
                         "16 - A(1, 2, 3)\n");

    ConstProcessor<TypeParam> cproc;
    auto guard = map.acquire_read_guard();
    guard->for_each(std::ref(cproc));
    EXPECT_EQ(expected, cproc.toString());
}

TYPED_TEST(LockableMapTest, find_buckets_simple) {
    TypeParam map;

    document::BucketId id1(17, 0x0ffff);
    id1 = id1.stripUnused();

    document::BucketId id2(18, 0x1ffff);
    id2 = id2.stripUnused();

    document::BucketId id3(18, 0x3ffff);
    id3 = id3.stripUnused();

    bool preExisted;
    map.insert(id1.toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(22, 0xfffff);
    auto results = map.getContained(id, "foo");

    EXPECT_EQ(1, results.size());
    EXPECT_EQ(A(3,4,5), *results[id3]);
}

TYPED_TEST(LockableMapTest, find_buckets) {
    TypeParam map;

    document::BucketId id1(16, 0x0ffff);
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(17, 0x1ffff);
    document::BucketId id4(19, 0xfffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);
    map.insert(id4.stripUnused().toKey(), A(4,5,6), "foo", preExisted);

    document::BucketId id(22, 0xfffff);
    auto results = map.getContained(id, "foo");

    EXPECT_EQ(3, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]);
    EXPECT_EQ(A(4,5,6), *results[id4.stripUnused()]);
    EXPECT_EQ(A(3,4,5), *results[id3.stripUnused()]);
}

TYPED_TEST(LockableMapTest, find_buckets_2) { // ticket 3121525
    TypeParam map;

    document::BucketId id1(16, 0x0ffff);
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(17, 0x1ffff);
    document::BucketId id4(18, 0x1ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);
    map.insert(id4.stripUnused().toKey(), A(4,5,6), "foo", preExisted);

    document::BucketId id(22, 0x1ffff);
    auto results = map.getContained(id, "foo");

    EXPECT_EQ(3, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]);
    EXPECT_EQ(A(4,5,6), *results[id4.stripUnused()]);
    EXPECT_EQ(A(3,4,5), *results[id3.stripUnused()]);
}

TYPED_TEST(LockableMapTest, find_buckets_3) { // ticket 3121525
    TypeParam map;

    document::BucketId id1(16, 0x0ffff);
    document::BucketId id2(17, 0x0ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);

    document::BucketId id(22, 0x1ffff);
    auto results = map.getContained(id, "foo");

    EXPECT_EQ(1, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]);
}

TYPED_TEST(LockableMapTest, find_buckets_4) { // ticket 3121525
    TypeParam map;

    document::BucketId id1(16, 0x0ffff);
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(19, 0x1ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(18, 0x1ffff);
    auto results = map.getContained(id, "foo");

    EXPECT_EQ(1, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]);
}

TYPED_TEST(LockableMapTest, find_buckets_5) { // ticket 3121525
    TypeParam map;

    document::BucketId id1(16, 0x0ffff);
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(19, 0x5ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(18, 0x1ffff);
    auto results = map.getContained(id, "foo");

    EXPECT_EQ(1, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]);
}

TYPED_TEST(LockableMapTest, find_no_buckets) {
    TypeParam map;

    document::BucketId id(16, 0x0ffff);
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(0, results.size());
}

TYPED_TEST(LockableMapTest, find_all) {
    TypeParam map;

    document::BucketId id1(16, 0x0aaaa); // contains id2-id7
    document::BucketId id2(17, 0x0aaaa); // contains id3-id4
    document::BucketId id3(20, 0xcaaaa);
    document::BucketId id4(20, 0xeaaaa);
    document::BucketId id5(17, 0x1aaaa); // contains id6-id7
    document::BucketId id6(20, 0xdaaaa);
    document::BucketId id7(20, 0xfaaaa);
    document::BucketId id8(20, 0xceaaa);
    document::BucketId id9(17, 0x1ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);
    map.insert(id4.stripUnused().toKey(), A(4,5,6), "foo", preExisted);
    map.insert(id5.stripUnused().toKey(), A(5,6,7), "foo", preExisted);
    map.insert(id6.stripUnused().toKey(), A(6,7,8), "foo", preExisted);
    map.insert(id7.stripUnused().toKey(), A(7,8,9), "foo", preExisted);
    map.insert(id8.stripUnused().toKey(), A(8,9,10), "foo", preExisted);
    map.insert(id9.stripUnused().toKey(), A(9,10,11), "foo", preExisted);

    document::BucketId id(17, 0x1aaaa);
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(4, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    EXPECT_EQ(A(5,6,7), *results[id5.stripUnused()]); // most specific match (exact match)
    EXPECT_EQ(A(6,7,8), *results[id6.stripUnused()]); // sub bucket
    EXPECT_EQ(A(7,8,9), *results[id7.stripUnused()]); // sub bucket

    id = document::BucketId(16, 0xffff);
    results = map.getAll(id, "foo");

    EXPECT_EQ(1, results.size());

    EXPECT_EQ(A(9,10,11), *results[id9.stripUnused()]); // sub bucket

    // Make sure we clear any existing bucket locks before we continue, or test will deadlock
    // if running with legacy (non-snapshot capable) DB implementation.
    results.clear();
    // Results should be equal when using read guard
    auto guard = map.acquire_read_guard();

    auto guard_results = guard->find_parents_self_and_children(BucketId(17, 0x1aaaa));
    EXPECT_THAT(guard_results, ElementsAre(A(1,2,3), A(5,6,7), A(6,7,8), A(7,8,9)));

    guard_results = guard->find_parents_self_and_children(BucketId(16, 0xffff));
    EXPECT_THAT(guard_results, ElementsAre(A(9,10,11)));
}

TYPED_TEST(LockableMapTest, find_all_2) { // Ticket 3121525
    TypeParam map;

    document::BucketId id1(17, 0x00001);
    document::BucketId id2(17, 0x10001);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);

    document::BucketId id(16, 0x00001);
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(2, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]); // sub bucket
    EXPECT_EQ(A(2,3,4), *results[id2.stripUnused()]); // sub bucket
}

TYPED_TEST(LockableMapTest, find_all_unused_bit_is_set) { // ticket 2938896
    TypeParam map;

    document::BucketId id1(24, 0x000dc7089);
    document::BucketId id2(33, 0x0053c7089);
    document::BucketId id3(33, 0x1053c7089);
    document::BucketId id4(24, 0x000bc7089);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);
    map.insert(id4.stripUnused().toKey(), A(4,5,6), "foo", preExisted);

    document::BucketId id(33, 0x1053c7089);
    id.setUsedBits(32); // Bit 33 is set, but unused
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(2, results.size());

    EXPECT_EQ(A(2,3,4), *results[id2.stripUnused()]); // sub bucket
    EXPECT_EQ(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
}

TYPED_TEST(LockableMapTest, find_all_inconsistently_split) { // Ticket 2938896
    TypeParam map;

    document::BucketId id1(16, 0x00001); // contains id2-id3
    document::BucketId id2(17, 0x00001);
    document::BucketId id3(17, 0x10001);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(16, 0x00001);
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(3, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]); // most specific match (exact match)
    EXPECT_EQ(A(2,3,4), *results[id2.stripUnused()]); // sub bucket
    EXPECT_EQ(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
}

TYPED_TEST(LockableMapTest, find_all_inconsistently_split_2) { // ticket 3121525
    TypeParam map;

    document::BucketId id1(17, 0x10000);
    document::BucketId id2(27, 0x007228034); // contains id3
    document::BucketId id3(29, 0x007228034);
    document::BucketId id4(17, 0x1ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);
    map.insert(id4.stripUnused().toKey(), A(4,5,6), "foo", preExisted);

    document::BucketId id(32, 0x027228034);
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(2, results.size());

    EXPECT_EQ(A(2,3,4), *results[id2.stripUnused()]); // super bucket
    EXPECT_EQ(A(3,4,5), *results[id3.stripUnused()]); // most specific match (super bucket)
}

TYPED_TEST(LockableMapTest, find_all_inconsistently_split_3) { // ticket 3121525
    TypeParam map;

    document::BucketId id1(16, 0x0ffff); // contains id2
    document::BucketId id2(17, 0x0ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);

    document::BucketId id(22, 0x1ffff);
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(1, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]); // super bucket
}

TYPED_TEST(LockableMapTest, find_all_inconsistently_split_4) { // ticket 3121525
    TypeParam map;

    document::BucketId id1(16, 0x0ffff); // contains id2-id3
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(19, 0x1ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(18, 0x1ffff);
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(2, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    EXPECT_EQ(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
}

TYPED_TEST(LockableMapTest, find_all_inconsistently_split_5) { // ticket 3121525
    TypeParam map;

    document::BucketId id1(16, 0x0ffff); // contains id2-id3
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(19, 0x5ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(18, 0x1ffff);
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(2, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    EXPECT_EQ(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
}

TYPED_TEST(LockableMapTest, find_all_inconsistently_split_6) {
    TypeParam map;

    document::BucketId id1(16, 0x0ffff); // contains id2-id3
    document::BucketId id2(18, 0x1ffff);
    document::BucketId id3(19, 0x7ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(18, 0x3ffff);
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(2, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    EXPECT_EQ(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
}

TYPED_TEST(LockableMapTest, find_all_inconsistent_below_16_bits) {
    TypeParam map;

    document::BucketId id1(8,  0b0000'0000'0001); // contains id2-id3
    document::BucketId id2(10, 0b0011'0000'0001);
    document::BucketId id3(11, 0b0101'0000'0001);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(10, 0b0001'0000'0001);

    auto results = map.getAll(id, "foo");

    EXPECT_EQ(2, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    EXPECT_EQ(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
}

TYPED_TEST(LockableMapTest, is_consistent) {
    TypeParam map;
    document::BucketId id1(16, 0x00001); // contains id2-id3
    document::BucketId id2(17, 0x00001);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    {
        auto entry = map.get(id1.stripUnused().toKey(), "foo", true);
        EXPECT_TRUE(map.isConsistent(entry));
    }
    map.insert(id2.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    {
        auto entry = map.get(id1.stripUnused().toKey(), "foo", true);
        EXPECT_FALSE(map.isConsistent(entry));
    }
}

TYPED_TEST(LockableMapTest, get_without_auto_create_does_not_implicitly_lock_bucket) {
    TypeParam map;
    BucketId id(16, 0x00001);

    auto entry = map.get(id.toKey(), "foo", false);
    EXPECT_FALSE(entry.exists());
    EXPECT_FALSE(entry.preExisted());
    EXPECT_FALSE(entry.locked());
}

TYPED_TEST(LockableMapTest, get_with_auto_create_returns_default_constructed_entry_if_missing) {
    TypeParam map;
    BucketId id(16, 0x00001);

    auto entry = map.get(id.toKey(), "foo", true);
    EXPECT_TRUE(entry.exists());
    EXPECT_FALSE(entry.preExisted());
    EXPECT_TRUE(entry.locked());
    EXPECT_EQ(*entry, A());
    *entry = A(1, 2, 3);
    entry.write(); // Implicit unlock (!)

    // Should now exist
    entry = map.get(id.toKey(), "foo", true);
    EXPECT_TRUE(entry.exists());
    EXPECT_TRUE(entry.preExisted());
    EXPECT_TRUE(entry.locked());
    EXPECT_EQ(*entry, A(1, 2, 3));
}

TYPED_TEST(LockableMapTest, entry_changes_not_visible_if_write_not_invoked_on_guard) {
    TypeParam map;
    BucketId id(16, 0x00001);
    auto entry = map.get(id.toKey(), "foo", true);
    *entry = A(1, 2, 3);
    // No write() call on guard
    entry.unlock();

    entry = map.get(id.toKey(), "foo", true);
    EXPECT_EQ(*entry, A());
}

TYPED_TEST(LockableMapTest, track_sizes) {
    TypeParam map;
    EXPECT_EQ(48ul, sizeof(typename TypeParam::WrappedEntry));
}

} // storage
