// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/bucketid.h>
#include <vespa/storage/common/bucket_utils.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::BucketId;
using storage::get_super_bucket_key;
constexpr uint8_t MUB = storage::spi::BucketLimits::MinUsedBits;

TEST(SuperBucketKeyTest, super_bucket_key_is_min_used_bits_of_msb_of_bucket_id_key)
{
    // Note that bits are reversed when creating a key from the bucket id
    EXPECT_EQ(0x0F, get_super_bucket_key(BucketId(MUB, 0x1F0)));
    EXPECT_EQ(0x0F, get_super_bucket_key(BucketId(MUB + 1, 0x1F0)));
    EXPECT_EQ(0x0F, get_super_bucket_key(BucketId(MUB, 0x2F0)));
    EXPECT_EQ(0x0F, get_super_bucket_key(BucketId(MUB + 1, 0x2F0)));

    EXPECT_EQ(0xF4, get_super_bucket_key(BucketId(MUB, 0x12F)));
    EXPECT_EQ(0xF4, get_super_bucket_key(BucketId(MUB + 1, 0x12F)));
    EXPECT_EQ(0xF4, get_super_bucket_key(BucketId(MUB, 0x22F)));
    EXPECT_EQ(0xF4, get_super_bucket_key(BucketId(MUB + 1, 0x22F)));
}

TEST(SuperBucketKeyTest, super_bucket_key_is_zero_when_bucket_id_is_zero)
{
    EXPECT_EQ(0, get_super_bucket_key(BucketId()));
    EXPECT_EQ(0, get_super_bucket_key(BucketId(0)));
}

