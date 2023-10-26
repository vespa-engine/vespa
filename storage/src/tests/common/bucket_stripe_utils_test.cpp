// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/bucketid.h>
#include <vespa/storage/common/bucket_stripe_utils.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::BucketId;
using storage::adjusted_num_stripes;
using storage::calc_num_stripe_bits;
using storage::stripe_of_bucket_key;
using storage::tune_num_stripes_based_on_cpu_cores;
constexpr uint8_t MUB = storage::spi::BucketLimits::MinUsedBits;

TEST(BucketStripeUtilsTest, stripe_of_bucket_key)
{
    BucketId id(MUB, std::numeric_limits<uint64_t>::max());
    uint64_t key = id.stripUnused().toKey();
    EXPECT_EQ(0, stripe_of_bucket_key(key, 0));
    EXPECT_EQ(1, stripe_of_bucket_key(key, 1));
    EXPECT_EQ(3, stripe_of_bucket_key(key, 2));
    EXPECT_EQ(127, stripe_of_bucket_key(key, 7));
    EXPECT_EQ(255, stripe_of_bucket_key(key, 8));
}

TEST(BucketStripeUtilsTest, calc_num_stripe_bits)
{
    EXPECT_EQ(0, calc_num_stripe_bits(1));
    EXPECT_EQ(1, calc_num_stripe_bits(2));
    EXPECT_EQ(2, calc_num_stripe_bits(4));
    EXPECT_EQ(7, calc_num_stripe_bits(128));
    EXPECT_EQ(8, calc_num_stripe_bits(256));
}

TEST(BucketStripeUtilsTest, adjusted_num_stripes)
{
    EXPECT_EQ(0, adjusted_num_stripes(0));
    EXPECT_EQ(1, adjusted_num_stripes(1));
    EXPECT_EQ(2, adjusted_num_stripes(2));
    EXPECT_EQ(4, adjusted_num_stripes(3));
    EXPECT_EQ(256, adjusted_num_stripes(255));
    EXPECT_EQ(256, adjusted_num_stripes(256));
    EXPECT_EQ(256, adjusted_num_stripes(257));
}

TEST(BucketStripeUtilsTest, max_stripe_values)
{
    EXPECT_EQ(8, storage::MaxStripeBits);
    EXPECT_EQ(256, storage::MaxStripes);
}

TEST(BucketStripeUtilsTest, num_stripes_tuned_based_on_cpu_cores)
{
    EXPECT_EQ(1, tune_num_stripes_based_on_cpu_cores(0));
    EXPECT_EQ(1, tune_num_stripes_based_on_cpu_cores(1));
    EXPECT_EQ(1, tune_num_stripes_based_on_cpu_cores(16));
    EXPECT_EQ(2, tune_num_stripes_based_on_cpu_cores(17));
    EXPECT_EQ(2, tune_num_stripes_based_on_cpu_cores(64));
    EXPECT_EQ(4, tune_num_stripes_based_on_cpu_cores(65));
}

