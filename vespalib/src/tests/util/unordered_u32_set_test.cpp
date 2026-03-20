// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/unordered_u32_set.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

namespace vespalib {

using namespace ::testing;

TEST(UnorderedU32SetTest, capacity_is_always_at_least_4) {
    EXPECT_EQ(UnorderedU32Set(0).capacity(), 4);
    EXPECT_EQ(UnorderedU32Set(1).capacity(), 4);
    EXPECT_EQ(UnorderedU32Set(3).capacity(), 4);
    EXPECT_EQ(UnorderedU32Set(4).capacity(), 8); // Adjusted to fit 4 insertions without resize
}

TEST(UnorderedU32SetTest, bit_indices_defined_for_u32_extents_minus_zero) {
    UnorderedU32Set set;
    EXPECT_EQ(set.size(), 0);
    EXPECT_TRUE(set.empty());

    EXPECT_FALSE(set.contains(1));
    EXPECT_TRUE(set.insert(1));
    EXPECT_FALSE(set.insert(1));
    EXPECT_TRUE(set.contains(1));

    EXPECT_EQ(set.size(), 1);
    EXPECT_FALSE(set.empty());

    EXPECT_FALSE(set.contains(UINT32_MAX));
    EXPECT_TRUE(set.insert(UINT32_MAX));
    EXPECT_FALSE(set.insert(UINT32_MAX));
    EXPECT_TRUE(set.contains(UINT32_MAX));

    EXPECT_EQ(set.size(), 2);
}

TEST(UnorderedU32SetTest, can_iterate_over_set_indices) {
    UnorderedU32Set set;
    // We check iterator behavior via gmock matchers that use iterators
    EXPECT_THAT(set, UnorderedElementsAre());
    ASSERT_TRUE(set.insert(1));
    EXPECT_THAT(set, UnorderedElementsAre(1));
    ASSERT_TRUE(set.insert(3));
    EXPECT_THAT(set, UnorderedElementsAre(1, 3));
    ASSERT_TRUE(set.insert(7));
    EXPECT_THAT(set, UnorderedElementsAre(1, 3, 7));
    ASSERT_TRUE(set.insert(UINT32_MAX));
    EXPECT_THAT(set, UnorderedElementsAre(1, 3, 7, UINT32_MAX));
}

namespace {

void set_and_check_bits(UnorderedU32Set& set, uint32_t n) {
    uint32_t n_set = 0;
    for (uint32_t i = 2; i < n; i += 2) {
        ASSERT_FALSE(set.contains(i)) << i;
        ASSERT_TRUE(set.insert(i)) << i;
        ++n_set;
        ASSERT_EQ(set.size(), n_set);
        // Ensure bits that should be set are still set, and vice versa.
        for (uint32_t j = 1; j < n; ++j) {
            if ((j > 1) && (j <= i) && (j % 2 == 0)) {
                ASSERT_TRUE(set.contains(j)) << "i=" << i << ",j=" << j;
            } else {
                ASSERT_FALSE(set.contains(j)) << "i=" << i << ",j=" << j;
            }
        }
    }
}

} // namespace

TEST(UnorderedU32SetTest, set_grows_on_inserts) {
    UnorderedU32Set set(16);
    EXPECT_GE(set.capacity(), 16);
    uint32_t n = 256;
    EXPECT_LT(set.capacity(), n);
    ASSERT_NO_FATAL_FAILURE(set_and_check_bits(set, n));
    EXPECT_GE(set.capacity(), n);
}

TEST(UnorderedU32SetTest, quadratic_probe_sequence_visits_all_values_in_range_exactly_once) {
    // Note: this property holds only for sequences that are a power of two
    constexpr uint32_t n = 1024;
    for (size_t hash : {0, 1, 0x1337, 0x12345678}) { // initial "hash" should not matter
        std::vector<uint32_t> visit_count(n);
        UnorderedU32Set::quadratic_probe_sequence probe_seq(hash, n);
        for (uint32_t i = 0; i < n; ++i) {
            const size_t offset = probe_seq.offset();
            ASSERT_LT(offset, n);
            visit_count[offset]++;
            probe_seq.next();
        }
        EXPECT_THAT(visit_count, Each(Eq(1))) << "for hash " << hash;
    }
}

TEST(UnorderedU32SetTest, prefer_bitvector_helper_considers_max_load_factor) {
    // 1024 elements would use 1024/8 = 128 bytes as a dense bitvector, so that's our baseline.
    EXPECT_FALSE(UnorderedU32Set::prefer_bitvector(0, 1024));
    EXPECT_FALSE(UnorderedU32Set::prefer_bitvector(1, 1024));
    // 25 sparse elements would use 25*5 = 125 bytes due to 3/4 max load factor.
    EXPECT_FALSE(UnorderedU32Set::prefer_bitvector(25, 1024));
    // 26 sparse elements would use 26*5 = 130 bytes, which exceeds the dense bit vector size.
    EXPECT_TRUE(UnorderedU32Set::prefer_bitvector(26, 1024));
}

} // namespace vespalib
