// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_interval_store.

#include <vespa/log/log.h>
LOG_SETUP("predicate_interval_store_test");

#include <vespa/searchlib/predicate/predicate_interval_store.h>

#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vector>

using namespace search;
using namespace search::predicate;
using std::vector;

namespace {

TEST("require that empty interval list gives invalid ref") {
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
    TEST_STATE(ost.str().c_str());
    PredicateIntervalStore store;
    auto ref = store.insert(interval_list);
    ASSERT_TRUE(ref.valid());

    uint32_t size;
    IntervalT single;
    const IntervalT *intervals = store.get(ref, size, &single);
    EXPECT_EQUAL(interval_list.size(), size);
    ASSERT_TRUE(intervals);
    for (size_t i = 0; i < interval_list.size(); ++i) {
        EXPECT_EQUAL(interval_list[i], intervals[i]);
    }
}

TEST("require that single interval entry can be inserted") {
    testInsertAndRetrieve<Interval>({{0x0001ffff}});
    testInsertAndRetrieve<IntervalWithBounds>({{0x0001ffff, 0x3}});
}

TEST("require that multi-interval entry can be inserted") {
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

TEST("require that multiple multi-interval entries can be retrieved") {
    PredicateIntervalStore store;
    auto ref = store.insert<Interval>({{1}, {2}});
    ASSERT_TRUE(ref.valid());
    ref = store.insert<Interval>({{3}, {4}});
    ASSERT_TRUE(ref.valid());

    uint32_t size;
    const Interval *intervals = store.get(ref, size, &single_buf);
    EXPECT_EQUAL(2u, size);
    ASSERT_TRUE(intervals);
    EXPECT_EQUAL(3u, intervals[0].interval);
    EXPECT_EQUAL(4u, intervals[1].interval);
}

/*
TEST("require that entries can be removed and reused") {
    GenerationHandler gen_handler;
    PredicateIntervalStore store(gen_handler);
    auto ref = store.insert<IntervalWithBounds>({{0x0001ffff, 5}});
    ASSERT_TRUE(ref.valid());
    store.remove(ref);

    auto ref2 = store.insert<Interval>({{1}, {2}, {3}, {4}, {5},
                                        {6}, {7}, {8}, {9}});
    ASSERT_TRUE(ref2.valid());
    store.remove(ref2);
    store.commit();

    auto ref3 = store.insert<IntervalWithBounds>({{0x0002ffff, 10}});
    ASSERT_EQUAL(ref.ref(), ref3.ref());

    uint32_t size;
    IntervalWithBounds single;
    const IntervalWithBounds *bounds = store.get(ref3, size, &single);
    EXPECT_EQUAL(1u, size);
    EXPECT_EQUAL(0x0002ffffu, bounds->interval);
    EXPECT_EQUAL(10u, bounds->bounds);

    auto ref4 = store.insert<Interval>({{2}, {3}, {4}, {5},
                                        {6}, {7}, {8}, {9}, {10}});
    ASSERT_EQUAL(ref2.ref(), ref4.ref());

    const Interval *intervals = store.get(ref4, size, &single_buf);
    EXPECT_EQUAL(9u, size);
    EXPECT_EQUAL(2u, intervals[0].interval);
    EXPECT_EQUAL(10u, intervals[8].interval);
}
*/

TEST("require that single interval entries are optimized") {
    PredicateIntervalStore store;
    auto ref = store.insert<Interval>({{0x0001ffff}});
    ASSERT_TRUE(ref.valid());
    ASSERT_EQUAL(0x0001ffffu, ref.ref());

    uint32_t size;
    const Interval *intervals = store.get(ref, size, &single_buf);
    ASSERT_EQUAL(intervals, &single_buf);
    EXPECT_EQUAL(0x0001ffffu, single_buf.interval);

    store.remove(ref);  // Should do nothing
}

TEST("require that interval refs are reused for identical data.") {
    PredicateIntervalStore store;
    auto ref = store.insert<Interval>({{0x00010001}, {0x0002ffff}});
    ASSERT_TRUE(ref.valid());
    ASSERT_EQUAL(0x02000001u, ref.ref());

    auto ref2 = store.insert<Interval>({{0x00010001}, {0x0002ffff}});
    EXPECT_EQUAL(ref.ref(), ref2.ref());

    uint32_t size;
    const Interval *intervals = store.get(ref, size, &single_buf);
    EXPECT_EQUAL(0x00010001u, intervals[0].interval);
    EXPECT_EQUAL(0x0002ffffu, intervals[1].interval);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
