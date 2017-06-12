// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for annotation serialization.

#include <vespa/log/log.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/util/compressor.h>
#include <vespa/vespalib/data/databuffer.h>

LOG_SETUP("compression_test");

using namespace document;
using namespace vespalib;

static vespalib::string _G_compressableText("AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE"
                                            "AAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCDDDDDDDDDDDDDDDDDDDDDDDDDEEEEEEEEEEEEEEE");

TEST("requireThatLZ4CompressFine") {
    CompressionConfig cfg(CompressionConfig::Type::LZ4);
    ConstBufferRef ref(_G_compressableText.c_str(), _G_compressableText.size());
    DataBuffer compressed;
    EXPECT_EQUAL(CompressionConfig::Type::LZ4, compress(cfg, ref, compressed, false));
}

TEST_MAIN() {
    TEST_RUN_ALL();
}
