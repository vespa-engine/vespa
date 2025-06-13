// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_master.hpp>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/data/databuffer.h>
#include <atomic>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP("compression_test");

using namespace vespalib;
using namespace vespalib::compression;

static std::string _G_compressableText("AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "XYZABCDEFGHIJGJMNOPQRSTUVW"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "XYZABCDEFGHIJGJMNOPQRSTUVW");

TEST(CompressionTest, requireThatLZ4CompressFine) {
    CompressionConfig cfg(CompressionConfig::Type::LZ4);
    ConstBufferRef ref(_G_compressableText.c_str(), _G_compressableText.size());
    DataBuffer compressed;
    EXPECT_EQ(CompressionConfig::Type::LZ4, compress(cfg, ref, compressed, false));
    EXPECT_EQ(66u, compressed.getDataLen());
}

TEST(CompressionTest, requireThatZStdCompressFine) {
    CompressionConfig cfg(CompressionConfig::Type::ZSTD);
    ConstBufferRef ref(_G_compressableText.c_str(), _G_compressableText.size());
    DataBuffer compressed;
    EXPECT_EQ(CompressionConfig::Type::ZSTD, compress(cfg, ref, compressed, false));
    EXPECT_EQ(64u, compressed.getDataLen());
}

TEST(CompressionTest, require_that_no_compression_decompression_works) {
    CompressionConfig cfg(CompressionConfig::Type::NONE);
    Compress compress(cfg, _G_compressableText.c_str(), _G_compressableText.size());
    EXPECT_EQ(CompressionConfig::Type::NONE, compress.type());
    EXPECT_EQ(1072u, compress.size());
    Decompress decompress(compress.type(), _G_compressableText.size(), compress.data(), compress.size());
    EXPECT_EQ(_G_compressableText, std::string(decompress.data(), decompress.size()));
}

TEST(CompressionTest, require_that_lz4_compression_decompression_works) {
    CompressionConfig cfg(CompressionConfig::Type::LZ4);
    Compress compress(cfg, _G_compressableText.c_str(), _G_compressableText.size());
    EXPECT_EQ(CompressionConfig::Type::LZ4, compress.type());
    EXPECT_EQ(66u, compress.size());
    Decompress decompress(compress.type(), _G_compressableText.size(), compress.data(), compress.size());
    EXPECT_EQ(_G_compressableText, std::string(decompress.data(), decompress.size()));
}

TEST(CompressionTest, requiret_that_zstd_compression_decompression_works) {
    CompressionConfig cfg(CompressionConfig::Type::ZSTD);
    Compress compress(cfg, _G_compressableText.c_str(), _G_compressableText.size());
    EXPECT_EQ(CompressionConfig::Type::ZSTD, compress.type());
    EXPECT_EQ(64u, compress.size());
    Decompress decompress(compress.type(), _G_compressableText.size(), compress.data(), compress.size());
    EXPECT_EQ(_G_compressableText, std::string(decompress.data(), decompress.size()));
}

TEST(CompressionTest, require_that_CompressionConfig_is_Atomic) {
    EXPECT_EQ(8u, sizeof(CompressionConfig));
    EXPECT_TRUE(std::atomic<CompressionConfig>::is_always_lock_free);
}

GTEST_MAIN_RUN_ALL_TESTS()
