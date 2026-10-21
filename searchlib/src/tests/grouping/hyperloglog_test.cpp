// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for hyperloglog.

#include <vespa/searchlib/grouping/hyperloglog.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nboserializer.h>
#include <vespa/vespalib/objects/nbostream.h>

#include <vespa/log/log.h>
LOG_SETUP("hyperloglog_test");

using vespalib::NBOSerializer;
using vespalib::nbostream;
using namespace search;

namespace {

TEST(HyperLogLogTest, require_that_hyperloglog_changes_from_sparse_to_normal_sketch) {
    HyperLogLog<> hll;
    for (size_t i = 0; i < 256; ++i) {
        EXPECT_TRUE(dynamic_cast<const SparseSketch<>*>(&hll.getSketch()));
        EXPECT_EQ(1, hll.aggregate(i));
    }
    EXPECT_TRUE(dynamic_cast<const SparseSketch<>*>(&hll.getSketch()));
    EXPECT_EQ(23, hll.aggregate(256));
    EXPECT_TRUE(dynamic_cast<const NormalSketch<>*>(&hll.getSketch()));
}

TEST(HyperLogLogTest, require_that_hyperloglog_can_be_serialized_and_deserialized) {
    HyperLogLog<> hll;
    for (size_t i = 0; i < 256; ++i) {
        EXPECT_EQ(1, hll.aggregate(i));
    }
    nbostream     stream;
    NBOSerializer serializer(stream);

    // Serializes with sparse sketch
    hll.serialize(serializer);
    HyperLogLog<> hll2;
    hll2.deserialize(serializer);
    EXPECT_TRUE(dynamic_cast<const SparseSketch<>*>(&hll2.getSketch()));
    EXPECT_EQ(hll.getSketch(), hll2.getSketch());

    // Serializes with normal sketch.
    EXPECT_EQ(23, hll2.aggregate(256));
    hll2.serialize(serializer);
    hll.deserialize(serializer);
    EXPECT_TRUE(dynamic_cast<const NormalSketch<>*>(&hll.getSketch()));
    EXPECT_EQ(hll2.getSketch(), hll.getSketch());
}

TEST(HyperLogLogTest, require_that_sparse_hyperloglogs_can_be_merged) {
    HyperLogLog<> hll;
    for (size_t i = 0; i < 100; ++i) {
        EXPECT_EQ(1, hll.aggregate(i));
    }
    HyperLogLog<> hll2;
    for (size_t i = 100; i < 255; ++i) {
        EXPECT_EQ(1, hll2.aggregate(i));
    }
    hll.merge(hll2);
    EXPECT_TRUE(dynamic_cast<const SparseSketch<>*>(&hll.getSketch()));

    EXPECT_EQ(1, hll2.aggregate(255));
    hll.merge(hll2);
    EXPECT_TRUE(dynamic_cast<const NormalSketch<>*>(&hll.getSketch()));
}

bool is_sparse(const HyperLogLog<>& hll) {
    return dynamic_cast<const SparseSketch<>*>(&hll.getSketch()) != nullptr;
}

HyperLogLog<> make_hll(uint32_t limit, uint32_t from, uint32_t to) {
    HyperLogLog<> hll(limit);
    for (uint32_t i = from; i < to; ++i) {
        hll.aggregate(i);
    }
    return hll;
}

TEST(HyperLogLogTest, default_limit_preserves_existing_copy_and_duplicate_boundaries) {
    HyperLogLog<> hll;
    for (uint32_t i = 0; i < 256; ++i) hll.aggregate(i);
    EXPECT_TRUE(is_sparse(hll));
    HyperLogLog<> copy(hll);
    EXPECT_FALSE(is_sparse(copy));
    hll.aggregate(7);
    EXPECT_FALSE(is_sparse(hll));
    EXPECT_EQ(copy.getSketch(), hll.getSketch());
}

TEST(HyperLogLogTest, clear_and_deserialization_preserve_the_default_boundary) {
    HyperLogLog<> hll;
    for (uint32_t i = 0; i < 256; ++i) hll.aggregate(i);
    nbostream stream;
    NBOSerializer serializer(stream);
    hll.serialize(serializer);
    HyperLogLog<> restored;
    restored.deserialize(serializer);
    EXPECT_TRUE(is_sparse(restored));
    restored.aggregate(7);
    EXPECT_FALSE(is_sparse(restored));
    hll.clear();
    for (uint32_t i = 0; i < 256; ++i) hll.aggregate(i);
    EXPECT_TRUE(is_sparse(hll));
    hll.aggregate(7);
    EXPECT_FALSE(is_sparse(hll));
}

TEST(HyperLogLogTest, assignment_and_clear_preserve_an_explicit_limit) {
    auto source = make_hll(512, 0, 512);
    HyperLogLog<> assigned;
    assigned = source;
    EXPECT_TRUE(is_sparse(assigned));
    EXPECT_EQ(0, assigned.aggregate(7));
    EXPECT_TRUE(is_sparse(assigned));
    assigned.clear();
    for (uint32_t i = 0; i < 512; ++i) assigned.aggregate(i);
    EXPECT_TRUE(is_sparse(assigned));
    EXPECT_EQ(0, assigned.aggregate(7));
    EXPECT_TRUE(is_sparse(assigned));
    assigned.aggregate(512);
    EXPECT_FALSE(is_sparse(assigned));
}

TEST(HyperLogLogTest, sparse_limit_is_the_same_for_aggregation_merge_and_copy) {
    for (uint32_t limit : {256u, 512u}) {
        auto at_limit = make_hll(limit, 0, limit);
        EXPECT_TRUE(is_sparse(at_limit));
        HyperLogLog<> copy(at_limit);
        EXPECT_TRUE(is_sparse(copy));
        auto merged = make_hll(limit, 0, limit / 2);
        merged.merge(make_hll(limit, limit / 2, limit));
        EXPECT_TRUE(is_sparse(merged));
        EXPECT_EQ(at_limit.getSketch(), merged.getSketch());

        EXPECT_FALSE(is_sparse(make_hll(limit, 0, limit + 1)));
        auto merged_past_limit = make_hll(limit, 0, limit / 2);
        merged_past_limit.merge(make_hll(limit, limit / 2, limit + 1));
        EXPECT_FALSE(is_sparse(merged_past_limit));
    }
}

TEST(HyperLogLogTest, repeated_hash_at_the_sparse_limit_keeps_the_sparse_sketch) {
    auto hll = make_hll(256, 0, 256);
    EXPECT_EQ(0, hll.aggregate(7));
    EXPECT_TRUE(is_sparse(hll));
    EXPECT_EQ(23, hll.aggregate(256));
    EXPECT_FALSE(is_sparse(hll));
}

TEST(HyperLogLogTest, require_that_mixed_hyperloglogs_can_be_merged) {
    HyperLogLog<> hll;
    for (size_t i = 0; i < 256; ++i) {
        EXPECT_EQ(1, hll.aggregate(i));
    }
    EXPECT_EQ(23, hll.aggregate(256)); // normal
    HyperLogLog<> hll2;
    for (size_t i = 100; i < 255; ++i) {
        EXPECT_EQ(1, hll2.aggregate(i)); // sparse
    }
    hll.merge(hll2); // normal + sparse
    hll2.merge(hll); // sparse + normal
    EXPECT_EQ(hll.getSketch(), hll2.getSketch());

    EXPECT_EQ(23, hll2.aggregate(500));
    hll.merge(hll2); // normal + normal
    EXPECT_EQ(hll.getSketch(), hll2.getSketch());
    EXPECT_EQ(0, hll.aggregate(500));
}

} // namespace

GTEST_MAIN_RUN_ALL_TESTS()
