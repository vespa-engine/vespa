// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/data/lz4_output_encoder.h>
#include <vespa/vespalib/data/lz4_input_decoder.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/memory_input.h>
#include <vespa/vespalib/test/chunked_input.h>

using namespace vespalib;
using vespalib::test::ChunkedInput;

void transfer(Input &input, Output &output) {
    for (Memory src = input.obtain(); src.size > 0; src = input.obtain()) {
        auto dst = output.reserve(src.size);
        ASSERT_GE(dst.size, src.size);
        memcpy(dst.data, src.data, src.size);
        output.commit(src.size);
        input.evict(src.size);
    }
}

TEST(Lz4EncodeDecodeTest, require_that_lz4_encode_decode_works) {
    SimpleBuffer data;
    for (size_t i = 0; i < 100; ++i) {
        data.add((i % 7) + (i * 5) + (i >> 3));
    }
    SimpleBuffer encoded;
    {
        MemoryInput memory_input(data.get());
        ChunkedInput chunked_input(memory_input, 3);
        Lz4OutputEncoder lz4_encoder(encoded, 10);
        transfer(chunked_input, lz4_encoder);
    }
    SimpleBuffer decoded;
    {
        MemoryInput memory_input(encoded.get());
        ChunkedInput chunked_input(memory_input, 3);
        Lz4InputDecoder input_decoder(chunked_input, 10);
        transfer(input_decoder, decoded);
        EXPECT_TRUE(!input_decoder.failed());
        EXPECT_EQ(input_decoder.reason(), std::string());
    }
    EXPECT_NE(data.get(), encoded.get());
    EXPECT_EQ(data.get(), decoded.get());
}

GTEST_MAIN_RUN_ALL_TESTS()
