// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/data/databuffer.h>

#include <vespa/log/log.h>
LOG_SETUP("compression_test");

using namespace vespalib;
using namespace vespalib::compression;

static vespalib::string _G_compressableText("AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
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

TEST("requireThatLZ4CompressFine") {
    CompressionConfig cfg(CompressionConfig::Type::LZ4);
    ConstBufferRef ref(_G_compressableText.c_str(), _G_compressableText.size());
    DataBuffer compressed;
    EXPECT_EQUAL(CompressionConfig::Type::LZ4, compress(cfg, ref, compressed, false));
    EXPECT_EQUAL(66u, compressed.getDataLen());
}

TEST("requireThatZStdCompressFine") {
    CompressionConfig cfg(CompressionConfig::Type::ZSTD);
    ConstBufferRef ref(_G_compressableText.c_str(), _G_compressableText.size());
    DataBuffer compressed;
    EXPECT_EQUAL(CompressionConfig::Type::ZSTD, compress(cfg, ref, compressed, false));
    EXPECT_EQUAL(64u, compressed.getDataLen());
}

TEST("require that no compression/decompression works") {
    CompressionConfig cfg(CompressionConfig::Type::NONE);
    Compress compress(cfg, _G_compressableText.c_str(), _G_compressableText.size());
    EXPECT_EQUAL(CompressionConfig::Type::NONE, compress.type());
    EXPECT_EQUAL(1072u, compress.size());
    Decompress decompress(compress.type(), _G_compressableText.size(), compress.data(), compress.size());
    EXPECT_EQUAL(_G_compressableText, vespalib::string(decompress.data(), decompress.size()));
}

TEST("require that lz4 compression/decompression works") {
    CompressionConfig cfg(CompressionConfig::Type::LZ4);
    Compress compress(cfg, _G_compressableText.c_str(), _G_compressableText.size());
    EXPECT_EQUAL(CompressionConfig::Type::LZ4, compress.type());
    EXPECT_EQUAL(66u, compress.size());
    Decompress decompress(compress.type(), _G_compressableText.size(), compress.data(), compress.size());
    EXPECT_EQUAL(_G_compressableText, vespalib::string(decompress.data(), decompress.size()));
}

TEST("requiret that zstd compression/decompression works") {
    CompressionConfig cfg(CompressionConfig::Type::ZSTD);
    Compress compress(cfg, _G_compressableText.c_str(), _G_compressableText.size());
    EXPECT_EQUAL(CompressionConfig::Type::ZSTD, compress.type());
    EXPECT_EQUAL(64u, compress.size());
    Decompress decompress(compress.type(), _G_compressableText.size(), compress.data(), compress.size());
    EXPECT_EQUAL(_G_compressableText, vespalib::string(decompress.data(), decompress.size()));
}

TEST("require that CompressionConfig is Atomic") {
    EXPECT_EQUAL(8u, sizeof(CompressionConfig));
    EXPECT_TRUE(std::atomic<CompressionConfig>::is_always_lock_free);
}

TEST_MAIN() {
    TEST_RUN_ALL();
}
