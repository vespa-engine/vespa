// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/u32_set.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace vespalib {

TEST(U32SetTest, bit_indices_defined_for_u32_extents_minus_zero) {
    U32Set set(16);
    EXPECT_EQ(set.size(), 0);

    EXPECT_FALSE(set.is_set(1));
    EXPECT_TRUE(set.try_set(1));
    EXPECT_FALSE(set.try_set(1));
    EXPECT_TRUE(set.is_set(1));

    EXPECT_EQ(set.size(), 1);

    EXPECT_FALSE(set.is_set(UINT32_MAX));
    EXPECT_TRUE(set.try_set(UINT32_MAX));
    EXPECT_FALSE(set.try_set(UINT32_MAX));
    EXPECT_TRUE(set.is_set(UINT32_MAX));

    EXPECT_EQ(set.size(), 2);
}

namespace {

void set_and_check_bits(U32Set& set, uint32_t n) {
    uint32_t n_set = 0;
    for (uint32_t i = 2; i < n; i += 2) {
        ASSERT_FALSE(set.is_set(i)) << i;
        ASSERT_TRUE(set.try_set(i)) << i;
        ++n_set;
        ASSERT_EQ(set.size(), n_set);
        // Ensure we don't cross any streams when growing
        for (uint32_t j = 1; j < n; ++j) {
            if ((j > 1) && (j <= i) && (j % 2 == 0)) {
                ASSERT_TRUE(set.is_set(j)) << "i=" << i << ",j=" << j;
            } else {
                ASSERT_FALSE(set.is_set(j)) << "i=" << i << ",j=" << j;
            }
        }
    }
}

}

TEST(U32SetTest, set_grows_on_inserts) {
    U32Set set(16);
    EXPECT_EQ(set.capacity(), 16);
    uint32_t n = 256;
    ASSERT_NO_FATAL_FAILURE(set_and_check_bits(set, n));
    EXPECT_GE(set.capacity(), n);
}

TEST(U32SetTest, set_converts_to_dense_bitvector_when_large) {
    U32Set set(U32Set::dense_set_capacity_threshold()/2);
    ASSERT_TRUE(set.is_sparse());
    constexpr uint32_t max_n = U32Set::dense_set_capacity_threshold();
    // Push it over the limit
    uint32_t i = 1;
    for (; i <= max_n; ++i) {
        ASSERT_TRUE(set.try_set(i)) << i;
        if (!set.is_sparse()) {
            break;
        }
    }
    ASSERT_FALSE(set.is_sparse());

    EXPECT_EQ(set.capacity(), UINT32_MAX); // Now dense
    // All already set values should be toggled
    for (uint32_t j = 1; j <= i; ++j) {
        ASSERT_TRUE(set.is_set(j)) << j;
    }
    ASSERT_FALSE(set.is_set(i+1)) << i+1;
}

TEST(U32SetTest, can_be_constructed_as_dense) {
    U32Set set(U32Set::dense_set_capacity_threshold());
    EXPECT_FALSE(set.is_sparse());
    EXPECT_EQ(set.capacity(), UINT32_MAX);

    uint32_t n = 1024;
    ASSERT_NO_FATAL_FAILURE(set_and_check_bits(set, n));
}

}
