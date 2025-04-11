// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for sketch.

#include <vespa/searchlib/grouping/sketch.h>
#include <vespa/vespalib/objects/nboserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("sketch_test");

using vespalib::NBOSerializer;
using vespalib::nbostream;
using namespace search;
using vespalib::make_string;

namespace {

TEST(SketchTest, require_that_normal_sketch_is_initialized) {
    NormalSketch<> sketch;
    for (size_t i = 0; i < sketch.BUCKET_COUNT; ++i) {
        EXPECT_EQ(0, sketch.bucket[i]);
    }
}

template <typename NormalSketch>
void checkBucketValue(NormalSketch &sketch, size_t bucket, uint32_t value) {
#pragma GCC diagnostic push
#if !defined(__clang__) && defined(__GNUC__) && (__GNUC__ >= 11 || __GNUC__ <= 12)
//TODO Remove when compiler bug has been fixed...
#pragma GCC diagnostic ignored "-Warray-bounds"
#endif
    EXPECT_EQ(value, static_cast<size_t>(sketch.bucket[bucket]));
}

template <int BucketBits, typename HashT>
void checkCountPrefixZeros() {
    SCOPED_TRACE(make_string("BucketBits: %d, HashBits: %d",
                           BucketBits, int(sizeof(HashT) * 8)));
    NormalSketch<BucketBits, HashT> sketch;
    const uint32_t prefix_bits = sizeof(HashT) * 8 - BucketBits;
    const uint32_t hash_width = sizeof(HashT) * 8;
    for (size_t i = 0; i < prefix_bits ; ++i) {
        int increase = sketch.aggregate(HashT(1) << (hash_width - 1 - i));
        EXPECT_EQ(1, increase);  // bucket increases by 1 for each call
        checkBucketValue(sketch, 0, i + 1);
    }
    sketch.aggregate(0);
    checkBucketValue(sketch, prefix_bits + 1, 0);

    checkBucketValue(sketch, HashT(1) << (BucketBits - 1), 0);
    sketch.aggregate(HashT(1) << (hash_width - 1 - prefix_bits));
    checkBucketValue(sketch, 0, prefix_bits + 1);
    checkBucketValue(sketch, HashT(1) << (BucketBits - 1), prefix_bits + 1);
}

TEST(SketchTest, require_that_prefix_zeros_are_counted) {
    checkCountPrefixZeros<10, uint32_t>();
    checkCountPrefixZeros<12, uint32_t>();
    checkCountPrefixZeros<10, uint64_t>();
    checkCountPrefixZeros<12, uint64_t>();
}

TEST(SketchTest, require_that_aggregate_returns_bucket_increase) {
    NormalSketch<> sketch;
    int increase = sketch.aggregate(-1);
    EXPECT_EQ(1, increase);
    increase = sketch.aggregate(1023);
    EXPECT_EQ(22, increase);
    increase = sketch.aggregate(0);
    EXPECT_EQ(23, increase);
}

TEST(SketchTest, require_that_instances_can_be_merged) {
    NormalSketch<> sketch;
    sketch.aggregate(0);
    NormalSketch<> sketch2;
    sketch2.aggregate(-1);
    sketch.merge(sketch2);
    checkBucketValue(sketch, 0, 23);
    checkBucketValue(sketch, 1023, 1);
}

TEST(SketchTest, require_that_different_sketch_type_instances_can_be_merged) {
    NormalSketch<> sketch;
    sketch.aggregate(0);
    SparseSketch<> sketch2;
    sketch2.aggregate(-1);
    sketch.merge(sketch2);
    checkBucketValue(sketch, 0, 23);
    checkBucketValue(sketch, 1023, 1);
}

TEST(SketchTest, require_that_normal_sketch_can_be_serialized_and_deserialized) {
    NormalSketch<> sketch;
    for (size_t i = 0; i < sketch.BUCKET_COUNT; ++i) {
        sketch.aggregate(i | (1 << ((i % sketch.bucketBits) +
                                    sketch.bucketBits)));
    }
    nbostream stream;
    NBOSerializer serializer(stream);
    sketch.serialize(serializer);
    EXPECT_EQ(31u, stream.size());
    uint32_t val;
    stream >> val;
    EXPECT_TRUE(sketch.BUCKET_COUNT == val);
    stream >> val;
    EXPECT_EQ(23u, val);
    stream.adjustReadPos(-2 * sizeof(uint32_t));
    NormalSketch<> sketch2;
    sketch2.deserialize(serializer);
    EXPECT_EQ(sketch, sketch2);
}

TEST(SketchTest, require_that_uncompressed_data_in_normal_sketch_can_be_deserialized) {
    NormalSketch<> sketch;
    nbostream stream;
    NBOSerializer serializer(stream);
    stream << sketch.BUCKET_COUNT;
    stream << sketch.BUCKET_COUNT;
    const int hash_bits = sizeof(NormalSketch<>::hash_type) * 8;
    const int value_bits = hash_bits - sketch.bucketBits;
    for (size_t i = 0; i < sketch.BUCKET_COUNT; ++i) {
        char bucket_val = (i % value_bits) + 1;
        stream << bucket_val;
        sketch.aggregate(i | (1 << (hash_bits - bucket_val)));
    }
    NormalSketch<> sketch2;
    sketch2.deserialize(serializer);
    EXPECT_EQ(sketch, sketch2);
}

TEST(SketchTest, require_that_sparse_sketch_can_be_serialized_and_deserialized) {
    SparseSketch<> sketch;
    const uint32_t hash_count = 10;
    for (size_t hash = 0; hash < hash_count; ++hash) {
        sketch.aggregate(hash);
    }
    nbostream stream;
    NBOSerializer serializer(stream);
    sketch.serialize(serializer);
    EXPECT_EQ(4 * hash_count + 4u, stream.size());
    uint32_t val;
    stream >> val;
    EXPECT_EQ(hash_count, val);
    stream.adjustReadPos(-1 * sizeof(uint32_t));
    SparseSketch<> sketch2;
    sketch2.deserialize(serializer);
    EXPECT_EQ(sketch, sketch2);
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
