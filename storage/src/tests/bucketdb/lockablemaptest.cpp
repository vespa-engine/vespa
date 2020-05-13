// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/storage/bucketdb/judymultimap.h>
#include <vespa/storage/bucketdb/judymultimap.hpp>
#include <vespa/storage/bucketdb/lockablemap.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <boost/operators.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".lockable_map_test");

// FIXME these old tests may have the least obvious semantics and worst naming in the entire storage module

using namespace ::testing;

namespace storage {

namespace {
    struct A : public boost::operators<A> {
        int _val1;
        int _val2;
        int _val3;

        A() : _val1(0), _val2(0), _val3(0) {}
        A(int val1, int val2, int val3)
            : _val1(val1), _val2(val2), _val3(val3) {}

        static bool mayContain(const A&) { return true; }

        bool operator==(const A& a) const {
            return (_val1 == a._val1 && _val2 == a._val2 && _val3 == a._val3);
        }
        bool operator<(const A& a) const {
            if (_val1 != a._val1) return (_val1 < a._val1);
            if (_val2 != a._val2) return (_val2 < a._val2);
            return (_val3 < a._val3);
        }
    };

    std::ostream& operator<<(std::ostream& out, const A& a) {
        return out << "A(" << a._val1 << ", " << a._val2 << ", " << a._val3 << ")";
    }

    typedef LockableMap<JudyMultiMap<A> > Map;
}

TEST(LockableMapTest, simple_usage) {
    // Tests insert, erase, size, empty, operator[]
    Map map;
    // Do some insertions
    EXPECT_TRUE(map.empty());
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    EXPECT_EQ(false, preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    EXPECT_EQ(false, preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);
    EXPECT_EQ(false, preExisted);
    EXPECT_EQ((Map::size_type) 3, map.size()) << map.toString();

    map.insert(11, A(4, 7, 0), "foo", preExisted);
    EXPECT_EQ(true, preExisted);
    EXPECT_EQ((Map::size_type) 3, map.size());
    EXPECT_FALSE(map.empty());

    // Access some elements
    EXPECT_EQ(A(4, 7, 0), *map.get(11, "foo"));
    EXPECT_EQ(A(1, 2, 3), *map.get(16, "foo"));
    EXPECT_EQ(A(42,0, 0), *map.get(14, "foo"));

    // Do removes
    EXPECT_EQ(map.erase(12, "foo"), 0);
    EXPECT_EQ((Map::size_type) 3, map.size());

    EXPECT_EQ(map.erase(14, "foo"), 1);
    EXPECT_EQ((Map::size_type) 2, map.size());

    EXPECT_EQ(map.erase(11, "foo"), 1);
    EXPECT_EQ(map.erase(16, "foo"), 1);
    EXPECT_EQ((Map::size_type) 0, map.size());
    EXPECT_TRUE(map.empty());
}

TEST(LockableMapTest, comparison) {
    Map map1;
    Map map2;
    bool preExisted;

    // Check empty state is correct
    EXPECT_EQ(map1, map2);
    EXPECT_FALSE(map1 < map2);
    EXPECT_FALSE(map1 != map2);

    // Check that different lengths are ok
    map1.insert(4, A(1, 2, 3), "foo", preExisted);
    EXPECT_FALSE(map1 == map2);
    EXPECT_FALSE(map1 < map2);
    EXPECT_LT(map2, map1);
    EXPECT_NE(map1, map2);

    // Check that equal elements are ok
    map2.insert(4, A(1, 2, 3), "foo", preExisted);
    EXPECT_EQ(map1, map2);
    EXPECT_FALSE(map1 < map2);
    EXPECT_FALSE(map1 != map2);

    // Check that non-equal values are ok
    map1.insert(6, A(1, 2, 6), "foo", preExisted);
    map2.insert(6, A(1, 2, 3), "foo", preExisted);
    EXPECT_FALSE(map1 == map2);
    EXPECT_FALSE(map1 < map2);
    EXPECT_LT(map2, map1);
    EXPECT_NE(map1, map2);

    // Check that non-equal keys are ok
    map1.erase(6, "foo");
    map1.insert(7, A(1, 2, 3), "foo", preExisted);
    EXPECT_FALSE(map1 == map2);
    EXPECT_FALSE(map1 < map2);
    EXPECT_LT(map2, map1);
    EXPECT_NE(map1, map2);
}

namespace {
    struct NonConstProcessor {
        Map::Decision operator()(int key, A& a) {
            (void) key;
            ++a._val2;
            return Map::UPDATE;
        }
    };
    struct EntryProcessor {
        mutable uint32_t count;
        mutable std::vector<std::string> log;
        mutable std::vector<Map::Decision> behaviour;

        EntryProcessor();
        EntryProcessor(const std::vector<Map::Decision>& decisions);
        ~EntryProcessor();

        Map::Decision operator()(uint64_t key, A& a) const {
            std::ostringstream ost;
            ost << key << " - " << a;
            log.push_back(ost.str());
            Map::Decision d = Map::CONTINUE;
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
            for (uint32_t i=0; i<log.size(); ++i) {
                ost << log[i] << "\n";
            }
            return ost.str();
        }
    };
}

EntryProcessor::EntryProcessor() : count(0), log(), behaviour() {}
EntryProcessor::EntryProcessor(const std::vector<Map::Decision>& decisions)
        : count(0), log(), behaviour(decisions) {}
EntryProcessor::~EntryProcessor() = default;

TEST(LockableMapTest, iterating) {
    Map map;
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);
        // Test that we can use functor with non-const function
    {
        NonConstProcessor ncproc;
        map.each(ncproc, "foo"); // Locking both for each element
        EXPECT_EQ(A(4, 7, 0), *map.get(11, "foo"));
        EXPECT_EQ(A(42,1, 0), *map.get(14, "foo"));
        EXPECT_EQ(A(1, 3, 3), *map.get(16, "foo"));
        map.all(ncproc, "foo"); // And for all
        EXPECT_EQ(A(4, 8, 0), *map.get(11, "foo"));
        EXPECT_EQ(A(42,2, 0), *map.get(14, "foo"));
        EXPECT_EQ(A(1, 4, 3), *map.get(16, "foo"));
    }
        // Test that we can use const functors directly..
    map.each(EntryProcessor(), "foo");

        // Test iterator bounds
    {
        EntryProcessor proc;
        map.each(proc, "foo", 11, 16);
        std::string expected("11 - A(4, 8, 0)\n"
                             "14 - A(42, 2, 0)\n"
                             "16 - A(1, 4, 3)\n");
        EXPECT_EQ(expected, proc.toString());

        EntryProcessor proc2;
        map.each(proc2, "foo", 12, 15);
        expected = "14 - A(42, 2, 0)\n";
        EXPECT_EQ(expected, proc2.toString());
    }
        // Test that we can abort iterating
    {
        std::vector<Map::Decision> decisions;
        decisions.push_back(Map::CONTINUE);
        decisions.push_back(Map::ABORT);
        EntryProcessor proc(decisions);
        map.each(proc, "foo");
        std::string expected("11 - A(4, 8, 0)\n"
                             "14 - A(42, 2, 0)\n");
        EXPECT_EQ(expected, proc.toString());
    }
        // Test that we can remove during iteration
    {
        std::vector<Map::Decision> decisions;
        decisions.push_back(Map::CONTINUE);
        decisions.push_back(Map::REMOVE);
        EntryProcessor proc(decisions);
        map.each(proc, "foo");
        std::string expected("11 - A(4, 8, 0)\n"
                             "14 - A(42, 2, 0)\n"
                             "16 - A(1, 4, 3)\n");
        EXPECT_EQ(expected, proc.toString());
        EXPECT_EQ((Map::size_type) 2, map.size()) << map.toString();
        EXPECT_EQ(A(4, 8, 0), *map.get(11, "foo"));
        EXPECT_EQ(A(1, 4, 3), *map.get(16, "foo"));
        Map::WrappedEntry entry = map.get(14, "foo");
        EXPECT_FALSE(entry.exist());
    }
}

TEST(LockableMapTest, chunked_iteration_is_transparent_across_chunk_sizes) {
    Map map;
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);
    NonConstProcessor ncproc; // Increments 2nd value in all entries.
    // chunkedAll with chunk size of 1
    map.chunkedAll(ncproc, "foo", 1us, 1);
    EXPECT_EQ(A(4, 7, 0), *map.get(11, "foo"));
    EXPECT_EQ(A(42, 1, 0), *map.get(14, "foo"));
    EXPECT_EQ(A(1, 3, 3), *map.get(16, "foo"));
    // chunkedAll with chunk size larger than db size
    map.chunkedAll(ncproc, "foo", 1us, 100);
    EXPECT_EQ(A(4, 8, 0), *map.get(11, "foo"));
    EXPECT_EQ(A(42, 2, 0), *map.get(14, "foo"));
    EXPECT_EQ(A(1, 4, 3), *map.get(16, "foo"));
}

TEST(LockableMapTest, can_abort_during_chunked_iteration) {
    Map map;
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);

    std::vector<Map::Decision> decisions;
    decisions.push_back(Map::CONTINUE);
    decisions.push_back(Map::ABORT);
    EntryProcessor proc(decisions);
    map.chunkedAll(proc, "foo", 1us, 100);
    std::string expected("11 - A(4, 6, 0)\n"
            "14 - A(42, 0, 0)\n");
    EXPECT_EQ(expected, proc.toString());
}

TEST(LockableMapTest, find_buckets_simple) {
    Map map;

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

TEST(LockableMapTest, find_buckets) {
    Map map;

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

TEST(LockableMapTest, find_buckets_2) { // ticket 3121525
    Map map;

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

TEST(LockableMapTest, find_buckets_3) { // ticket 3121525
    Map map;

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

TEST(LockableMapTest, find_buckets_4) { // ticket 3121525
    Map map;

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

TEST(LockableMapTest, find_buckets_5) { // ticket 3121525
    Map map;

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

TEST(LockableMapTest, find_no_buckets) {
    Map map;

    document::BucketId id(16, 0x0ffff);
    auto results = map.getAll(id, "foo");

    EXPECT_EQ(0, results.size());
}

TEST(LockableMapTest, find_all) {
    Map map;

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
}

TEST(LockableMapTest, find_all_2) { // Ticket 3121525
    Map map;

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

TEST(LockableMapTest, find_all_unused_bit_is_set) { // ticket 2938896
    Map map;

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

TEST(LockableMapTest, find_all_inconsistently_split) { // Ticket 2938896
    Map map;

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

TEST(LockableMapTest, find_all_inconsistently_split_2) { // ticket 3121525
    Map map;

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

TEST(LockableMapTest, find_all_inconsistently_split_3) { // ticket 3121525
    Map map;

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

TEST(LockableMapTest, find_all_inconsistently_split_4) { // ticket 3121525
    Map map;

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

TEST(LockableMapTest, find_all_inconsistently_split_5) { // ticket 3121525
    Map map;

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

TEST(LockableMapTest, find_all_inconsistently_split_6) {
    Map map;

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

TEST(LockableMapTest, find_all_inconsistent_below_16_bits) {
    Map map;

    document::BucketId id1(1, 0x1); // contains id2-id3
    document::BucketId id2(3, 0x1);
    document::BucketId id3(4, 0xD);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(3, 0x5);

    auto results = map.getAll(id, "foo");

    EXPECT_EQ(2, results.size());

    EXPECT_EQ(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    EXPECT_EQ(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
}

TEST(LockableMapTest, create) {
    Map map;
    {
        document::BucketId id1(58, 0x43d6c878000004d2ull);

        auto entries = map.getContained(id1, "foo");

        EXPECT_EQ(0, entries.size());

        Map::WrappedEntry entry = map.createAppropriateBucket(36, "", id1);
        EXPECT_EQ(document::BucketId(36,0x8000004d2ull), entry.getBucketId());
    }
    {
        document::BucketId id1(58, 0x423bf1e0000004d2ull);

        auto entries = map.getContained(id1, "foo");
        EXPECT_EQ(0, entries.size());

        Map::WrappedEntry entry = map.createAppropriateBucket(36, "", id1);
        EXPECT_EQ(document::BucketId(36,0x0000004d2ull), entry.getBucketId());
    }

    EXPECT_EQ(2, map.size());
}

TEST(LockableMapTest, create_2) {
    Map map;
    {
        document::BucketId id1(58, 0xeaf77782000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(58, 0x00000000000004d2);
        auto entries = map.getContained(id1, "foo");

        EXPECT_EQ(0, entries.size());

        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);

        EXPECT_EQ(document::BucketId(34, 0x0000004d2ull), entry.getBucketId());
    }

    EXPECT_EQ(2, map.size());
}

TEST(LockableMapTest, create_3) {
    Map map;
    {
        document::BucketId id1(58, 0xeaf77780000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(58, 0xeaf77782000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(58, 0x00000000000004d2);
        auto entries = map.getContained(id1, "foo");

        EXPECT_EQ(0, entries.size());

        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);
        EXPECT_EQ(document::BucketId(40, 0x0000004d2ull), entry.getBucketId());
    }
}

TEST(LockableMapTest, create_4) {
    Map map;
    {
        document::BucketId id1(16, 0x00000000000004d1);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(40, 0x00000000000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(58, 0x00000000010004d2);
        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);

        EXPECT_EQ(document::BucketId(25, 0x0010004d2ull), entry.getBucketId());
    }
}

TEST(LockableMapTest, create_5) {
    Map map;
    {
        document::BucketId id1(0x8c000000000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }

    {
        document::BucketId id1(0xeb54b3ac000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }

    {
        document::BucketId id1(0x88000002000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(0x84000001000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(0xe9944a44000004d2);
        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);
        EXPECT_EQ(document::BucketId(0x90000004000004d2), entry.getBucketId());
    }
}

TEST(LockableMapTest, create_6) {
    Map map;
    {
        document::BucketId id1(58, 0xeaf77780000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(40, 0x00000000000004d1);

        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(58, 0x00000000010004d2);
        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);
        EXPECT_EQ(document::BucketId(25, 0x0010004d2ull), entry.getBucketId());
    }
}

TEST(LockableMapTest, create_empty) {
    Map map;
    {
        document::BucketId id1(58, 0x00000000010004d2);
        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);
        EXPECT_EQ(document::BucketId(16, 0x0000004d2ull), entry.getBucketId());
    }
}

TEST(LockableMapTest, is_consistent) {
    Map map;
    document::BucketId id1(16, 0x00001); // contains id2-id3
    document::BucketId id2(17, 0x00001);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    {
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
        EXPECT_TRUE(map.isConsistent(entry));
    }
    map.insert(id2.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    {
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
        EXPECT_FALSE(map.isConsistent(entry));
    }
}

} // storage
