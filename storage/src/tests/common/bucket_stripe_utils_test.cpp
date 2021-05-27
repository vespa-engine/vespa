// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/bucketid.h>
#include <vespa/storage/common/bucket_stripe_utils.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::BucketId;
using storage::calc_num_stripe_bits;
using storage::stripe_of_bucket_key;
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

TEST(BucketStripeUtilsTest, max_stripe_values)
{
    EXPECT_EQ(8, storage::MaxStripeBits);
    EXPECT_EQ(256, storage::MaxStripes);
}

