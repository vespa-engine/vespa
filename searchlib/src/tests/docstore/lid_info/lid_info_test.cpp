// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/docstore/lid_info.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("lid_info_test");

using namespace search;

TEST(LidInfoTest, require_that_LidInfo_orders_file_chunk_size)
{
    EXPECT_TRUE(LidInfo(1, 1, 1) == LidInfo(1, 1, 1));
    EXPECT_FALSE(LidInfo(1, 1, 1) < LidInfo(1, 1, 1));

    EXPECT_FALSE(LidInfo(1, 1, 1) == LidInfo(2, 1, 1));
    EXPECT_TRUE(LidInfo(1, 1, 1) < LidInfo(2, 1, 1));
    EXPECT_TRUE(LidInfo(1, 2, 1) < LidInfo(2, 1, 1));
    EXPECT_TRUE(LidInfo(1, 1, 2) < LidInfo(2, 1, 1));
}

TEST(LidInfoTest, require_that_LidInfo_has_8_bytes_size_and_that_it_can_represent_the_numbers_correctly)
{
    EXPECT_EQ(8u, sizeof(LidInfo));
    LidInfo a(0,0,0);
    EXPECT_EQ(0u, a.getFileId());
    EXPECT_EQ(0u, a.getChunkId());
    EXPECT_EQ(0u, a.size());
    EXPECT_TRUE(a.valid());
    EXPECT_TRUE(a.empty());
    a = LidInfo(1,1,1);
    EXPECT_EQ(1u, a.getFileId());
    EXPECT_EQ(1u, a.getChunkId());
    EXPECT_EQ(64u, a.size());
    EXPECT_TRUE(a.valid());
    EXPECT_FALSE(a.empty());
    a = LidInfo(1,1,63);
    EXPECT_EQ(1u, a.getFileId());
    EXPECT_EQ(1u, a.getChunkId());
    EXPECT_EQ(64u, a.size());
    EXPECT_TRUE(a.valid());
    EXPECT_FALSE(a.empty());
    a = LidInfo(1,1,64);
    EXPECT_EQ(1u, a.getFileId());
    EXPECT_EQ(1u, a.getChunkId());
    EXPECT_EQ(64u, a.size());
    EXPECT_TRUE(a.valid());
    EXPECT_FALSE(a.empty());
    a = LidInfo(1,1,65);
    EXPECT_EQ(1u, a.getFileId());
    EXPECT_EQ(1u, a.getChunkId());
    EXPECT_EQ(128u, a.size());
    EXPECT_TRUE(a.valid());
    EXPECT_FALSE(a.empty());
    a = LidInfo(0xffff,0x3fffff,0xffffff80u);
    EXPECT_EQ(0xffffu, a.getFileId());
    EXPECT_EQ(0x3fffffu, a.getChunkId());
    EXPECT_EQ(0xffffff80u, a.size());
    EXPECT_TRUE(a.valid());
    EXPECT_FALSE(a.empty());
    VESPA_EXPECT_EXCEPTION(a = LidInfo(0x10000,0x3fffff,1), std::runtime_error,
                           "LidInfo(fileId=65536, chunkId=4194303, size=1) has invalid fileId larger than 65535");
    VESPA_EXPECT_EXCEPTION(a = LidInfo(0xffff,0x400000,1), std::runtime_error,
                           "LidInfo(fileId=65535, chunkId=4194304, size=1) has invalid chunkId larger than 4194303");
    VESPA_EXPECT_EXCEPTION(a = LidInfo(0xffff,0x3fffff,0xffffff81u), std::runtime_error,
                           "LidInfo(fileId=65535, chunkId=4194303, size=4294967169) has too large size larger than 4294967168");
}

GTEST_MAIN_RUN_ALL_TESTS()
