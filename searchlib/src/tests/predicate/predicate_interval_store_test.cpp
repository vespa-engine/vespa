// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_interval_store.

#include <vespa/searchlib/predicate/predicate_interval_store.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

using namespace search;
using namespace search::predicate;
using std::vector;

namespace {

TEST(PredicateIntervalStoreTest, require_that_empty_interval_list_gives_invalid_ref) {
    PredicateIntervalStore store;
    vector<Interval> interval_list;
    auto ref = store.insert(interval_list);
    ASSERT_FALSE(ref.valid());
}

Interval single_buf;

template <typename IntervalT>
void testInsertAndRetrieve(const std::vector<IntervalT> &interval_list) {
    std::ostringstream ost;
    ost << "Type name: " << typeid(IntervalT).name() << ", intervals:";
    for (auto &i : interval_list) {
        ost << " 0x" << std::hex << i.interval;
    }
    SCOPED_TRACE(ost.str());
    PredicateIntervalStore store;
    auto ref = store.insert(interval_list);
    ASSERT_TRUE(ref.valid());

    uint32_t size;
    IntervalT single;
    const IntervalT *intervals = store.get(ref, size, &single);
    EXPECT_EQ(interval_list.size(), size);
    ASSERT_TRUE(intervals);
    for (size_t i = 0; i < interval_list.size(); ++i) {
        EXPECT_EQ(interval_list[i], intervals[i]);
    }
}

TEST(PredicateIntervalStoreTest, require_that_single_interval_entry_can_be_inserted) {
    testInsertAndRetrieve<Interval>({{0x0001ffff}});
    testInsertAndRetrieve<IntervalWithBounds>({{0x0001ffff, 0x3}});
}

TEST(PredicateIntervalStoreTest, require_that_multi_interval_entry_can_be_inserted) {
    testInsertAndRetrieve<Interval>({{0x00010001}, {0x0002ffff}});
    testInsertAndRetrieve<Interval>(
        {{0x00010001}, {0x00020002}, {0x0003ffff}});
    testInsertAndRetrieve<Interval>(
        {{0x00010001}, {0x00020002}, {0x00030003}, {0x00040004},
         {0x00050005}, {0x00060006}, {0x00070007}, {0x00080008},
         {0x0009ffff}});
    testInsertAndRetrieve<IntervalWithBounds>(
        {{0x00010001, 0x4}, {0x0002ffff, 0x10}});
    testInsertAndRetrieve<IntervalWithBounds>(
        {{0x00010001, 0x4}, {0x00020002, 0x10}, {0x00030003, 0x20},
         {0x00040004, 0x6}, {0x0005ffff, 0x7}});
}

TEST(PredicateIntervalStoreTest, require_that_multiple_multi_interval_entries_can_be_retrieved) {
    PredicateIntervalStore store;
    auto ref = store.insert<Interval>({{1}, {2}});
    ASSERT_TRUE(ref.valid());
    ref = store.insert<Interval>({{3}, {4}});
    ASSERT_TRUE(ref.valid());

    uint32_t size;
    const Interval *intervals = store.get(ref, size, &single_buf);
    EXPECT_EQ(2u, size);
    ASSERT_TRUE(intervals);
    EXPECT_EQ(3u, intervals[0].interval);
    EXPECT_EQ(4u, intervals[1].interval);
}

TEST(PredicateIntervalStoreTest, require_that_single_interval_entries_are_optimized) {
    PredicateIntervalStore store;
    auto ref = store.insert<Interval>({{0x0001ffff}});
    ASSERT_TRUE(ref.valid());
    ASSERT_EQ(0x0001ffffu, ref.ref());

    uint32_t size;
    const Interval *intervals = store.get(ref, size, &single_buf);
    ASSERT_EQ(intervals, &single_buf);
    EXPECT_EQ(0x0001ffffu, single_buf.interval);

    store.remove(ref);  // Should do nothing
}

TEST(PredicateIntervalStoreTest, require_that_interval_refs_are_reused_for_identical_data) {
    PredicateIntervalStore store;
    auto ref = store.insert<Interval>({{0x00010001}, {0x0002ffff}});
    ASSERT_TRUE(ref.valid());
    ASSERT_EQ(0x02000001u, ref.ref());

    auto ref2 = store.insert<Interval>({{0x00010001}, {0x0002ffff}});
    EXPECT_EQ(ref.ref(), ref2.ref());

    uint32_t size;
    const Interval *intervals = store.get(ref, size, &single_buf);
    EXPECT_EQ(0x00010001u, intervals[0].interval);
    EXPECT_EQ(0x0002ffffu, intervals[1].interval);
}

}  // namespace
