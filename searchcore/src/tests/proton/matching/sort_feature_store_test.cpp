// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/sort_feature_store.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <algorithm>
#include <cmath>
#include <limits>
#include <vector>

using proton::matching::SortFeatureStore;

namespace {

// Records the docids in the given order, each with a value derived from its
// docid, and checks that ascending seeks read every docid's own value.
void expect_read_in_docid_order(std::vector<uint32_t> docids) {
    SortFeatureStore    store({"foo"});
    std::vector<double> value(1);
    for (uint32_t docid : docids) {
        value[0] = docid * 0.5;
        store.record(docid, value);
    }
    store.ensure_sorted_for_read();
    std::sort(docids.begin(), docids.end());
    for (uint32_t docid : docids) {
        store.seek(docid);
        ASSERT_FALSE(store.failed()) << "docid " << docid;
        EXPECT_EQ(docid * 0.5, store.get(0)) << "docid " << docid;
    }
}

} // namespace

TEST(SortFeatureStoreTest, rows_recorded_in_docid_order_are_read_in_order) {
    SortFeatureStore store({"foo", "bar"});
    EXPECT_EQ(0u, store.ordinal("foo"));
    EXPECT_EQ(1u, store.ordinal("bar"));
    EXPECT_EQ(INumericSortValueProvider::invalid_ordinal, store.ordinal("missing"));

    const double r0[] = {1.5, 2.5};
    const double r1[] = {3.5, 4.5};
    const double r2[] = {5.5, 6.5};
    store.record(10, r0);
    store.record(20, r1);
    store.record(30, r2);
    EXPECT_EQ(3u, store.num_rows());

    store.ensure_sorted_for_read();
    store.seek(10);
    EXPECT_EQ(1.5, store.get(0));
    EXPECT_EQ(2.5, store.get(1));
    EXPECT_EQ(1.5, store.get(0));
    store.seek(20);
    EXPECT_EQ(3.5, store.get(0));
    EXPECT_EQ(4.5, store.get(1));
    store.seek(30);
    EXPECT_EQ(5.5, store.get(0));
}

TEST(SortFeatureStoreTest, seek_skips_unrequested_rows) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    const double     b[] = {2.0};
    const double     c[] = {3.0};
    store.record(10, a);
    store.record(20, b);
    store.record(30, c);
    store.ensure_sorted_for_read();
    store.seek(10);
    EXPECT_EQ(1.0, store.get(0));
    store.seek(30);
    EXPECT_EQ(3.0, store.get(0));
}

TEST(SortFeatureStoreTest, out_of_order_rows_are_read_in_docid_order) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    const double     b[] = {2.0};
    const double     c[] = {3.0};
    store.record(30, a);
    store.record(10, b);
    store.record(20, c);
    store.ensure_sorted_for_read();
    store.ensure_sorted_for_read(); // Preparation is idempotent.
    store.seek(10);
    EXPECT_EQ(2.0, store.get(0));
    store.seek(20);
    EXPECT_EQ(3.0, store.get(0));
    store.seek(30);
    EXPECT_EQ(1.0, store.get(0));
}

TEST(SortFeatureStoreTest, runs_across_chunks_are_read_in_docid_order) {
    // A first run of 1500 rows, across the 1024-row chunk boundary, then three
    // lower runs placed so that the merges interleave rows. The second run
    // continues above the first, as a low range followed by a high one would.
    std::vector<uint32_t> docids;
    auto add = [&docids](uint32_t first, uint32_t end) {
        for (uint32_t docid = first; docid < end; ++docid) {
            docids.push_back(docid);
        }
    };
    add(1000, 2500);
    add(10, 20);
    add(3000, 3010);
    add(500, 510);
    add(100, 110);
    expect_read_in_docid_order(docids);
}

TEST(SortFeatureStoreTest, descending_rows_are_read_in_docid_order) {
    expect_read_in_docid_order({60, 50, 40, 30, 20, 10});
}

TEST(SortFeatureStoreTest, sanitizes_non_finite_values_and_consumed_clears_storage) {
    SortFeatureStore store({"foo"});
    const double     nan[] = {std::numeric_limits<double>::quiet_NaN()};
    const double     inf[] = {std::numeric_limits<double>::infinity()};
    store.record(1, nan);
    store.record(2, inf);
    store.ensure_sorted_for_read();
    store.seek(1);
    EXPECT_EQ(-HUGE_VAL, store.get(0));
    store.seek(2);
    EXPECT_EQ(-HUGE_VAL, store.get(0));
    store.consumed();
    EXPECT_EQ(0u, store.num_rows());
}

TEST(SortFeatureStoreTest, grows_across_chunk_boundaries) {
    SortFeatureStore    store({"foo"});
    std::vector<double> value(1);
    // Private chunk size is 1024; one extra chunk of 3 rows.
    uint32_t n = 1024 + 3;
    for (uint32_t i = 0; i < n; ++i) {
        value[0] = i;
        store.record(i + 1, value);
    }
    EXPECT_EQ(n, store.num_rows());
    store.ensure_sorted_for_read();
    store.seek(1);
    EXPECT_EQ(0.0, store.get(0));
    store.seek(n);
    EXPECT_EQ(double(n - 1), store.get(0));
}

TEST(SortFeatureStoreTest, successful_consumption_is_not_a_failure) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    store.record(10, a);
    store.ensure_sorted_for_read();
    store.seek(10);
    EXPECT_EQ(1.0, store.get(0));
    EXPECT_FALSE(store.failed());
    store.consumed();
    EXPECT_FALSE(store.failed());
}

TEST(SortFeatureStoreTest, seek_for_an_unrecorded_docid_fails_gracefully) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    const double     b[] = {2.0};
    store.record(10, a);
    store.record(30, b);
    store.ensure_sorted_for_read();
    store.seek(20);
    EXPECT_TRUE(store.failed());
    EXPECT_EQ(-HUGE_VAL, store.get(0));
}

TEST(SortFeatureStoreTest, backwards_seek_fails_gracefully) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    const double     b[] = {2.0};
    store.record(10, a);
    store.record(20, b);
    store.ensure_sorted_for_read();
    store.seek(20);
    EXPECT_FALSE(store.failed());
    store.seek(10);
    EXPECT_TRUE(store.failed());
    EXPECT_EQ(-HUGE_VAL, store.get(0));
}

TEST(SortFeatureStoreTest, seek_past_the_last_recorded_row_fails_gracefully) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    store.record(10, a);
    store.ensure_sorted_for_read();
    store.seek(20);
    EXPECT_TRUE(store.failed());
    EXPECT_EQ(-HUGE_VAL, store.get(0));
}

TEST(SortFeatureStoreTest, seek_without_any_recorded_row_fails_gracefully) {
    SortFeatureStore store({"foo"});
    store.ensure_sorted_for_read();
    store.seek(1);
    EXPECT_TRUE(store.failed());
    EXPECT_EQ(-HUGE_VAL, store.get(0));
}

TEST(SortFeatureStoreTest, seek_after_release_fails_gracefully) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    store.record(10, a);
    store.ensure_sorted_for_read();
    store.consumed();
    store.seek(10);
    EXPECT_TRUE(store.failed());
    EXPECT_EQ(-HUGE_VAL, store.get(0));
}

TEST(SortFeatureStoreTest, seek_before_preparing_for_read_fails_gracefully) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    store.record(10, a);
    store.seek(10);
    EXPECT_TRUE(store.failed());
    EXPECT_EQ(-HUGE_VAL, store.get(0));
}

TEST(SortFeatureStoreTest, failure_is_sticky_across_later_seeks_and_release) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    const double     b[] = {2.0};
    store.record(10, a);
    store.record(20, b);
    store.ensure_sorted_for_read();
    store.seek(15);
    EXPECT_TRUE(store.failed());
    // A subsequent seek that would have been satisfiable does not clear the failure.
    store.seek(20);
    EXPECT_TRUE(store.failed());
    EXPECT_EQ(-HUGE_VAL, store.get(0));
    store.consumed();
    EXPECT_TRUE(store.failed());
    // Failure does not keep the storage alive for the rest of the query.
    EXPECT_EQ(0u, store.num_rows());
}

GTEST_MAIN_RUN_ALL_TESTS()
