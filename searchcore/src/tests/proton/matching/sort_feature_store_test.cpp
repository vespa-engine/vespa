// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/sort_feature_store.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <cmath>
#include <limits>
#include <vector>

using proton::matching::SortFeatureStore;

TEST(SortFeatureStoreTest, records_and_consumes_monotonic_rows_in_order) {
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
    EXPECT_TRUE(store.monotonic());
    EXPECT_EQ(3u, store.num_rows());

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

TEST(SortFeatureStoreTest, sequential_consume_skips_unrequested_rows) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    const double     b[] = {2.0};
    const double     c[] = {3.0};
    store.record(10, a);
    store.record(20, b);
    store.record(30, c);
    store.seek(10);
    EXPECT_EQ(1.0, store.get(0));
    store.seek(30);
    EXPECT_EQ(3.0, store.get(0));
}

TEST(SortFeatureStoreTest, permutation_is_built_only_for_non_monotonic_rows) {
    SortFeatureStore store({"foo"});
    const double     a[] = {1.0};
    const double     b[] = {2.0};
    const double     c[] = {3.0};
    store.record(30, a);
    store.record(10, b);
    store.record(20, c);
    EXPECT_FALSE(store.monotonic());
    store.finalize_permutation();
    store.seek(10);
    EXPECT_EQ(2.0, store.get(0));
    store.seek(20);
    EXPECT_EQ(3.0, store.get(0));
    store.seek(30);
    EXPECT_EQ(1.0, store.get(0));
}

TEST(SortFeatureStoreTest, sanitizes_non_finite_values_and_consumed_clears_storage) {
    SortFeatureStore store({"foo"});
    const double     nan[] = {std::numeric_limits<double>::quiet_NaN()};
    const double     inf[] = {std::numeric_limits<double>::infinity()};
    store.record(1, nan);
    store.record(2, inf);
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
    store.seek(1);
    EXPECT_EQ(0.0, store.get(0));
    store.seek(n);
    EXPECT_EQ(double(n - 1), store.get(0));
}

GTEST_MAIN_RUN_ALL_TESTS()
