// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/transactionlog/chunks.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("translog_chunks_test");

using namespace search::transactionlog;
using vespalib::ConstBufferRef;
using vespalib::nbostream;
using vespalib::compression::CompressionConfig;

constexpr const char * TEXT = "abcdefghijklmnopqrstuvwxyz_abcdefghijklmnopqrstuvwxyz_abcdefghijklmnopqrstuvwxyz_abcdefghijklmnopqrstuvwxyz_abcdefghijklmnopqrstuvwxyz";

void
verifySerializationAndDeserialization(IChunk & org, size_t numEntries) {
    for (size_t i(0); i < numEntries; i++) {
        const char *start = TEXT + (i%20);
        Packet::Entry entry(i, i%8, ConstBufferRef(start, strlen(start)));
        org.add(entry);
    }
    nbostream os;

    Encoding encoding = org.encode(os);
    auto deserialized = IChunk::create(encoding.getRaw());
    deserialized->decode(os);
    EXPECT_TRUE(os.empty());
    EXPECT_EQUAL(numEntries, deserialized->getEntries().size());
}

TEST("test serialization and deserialization of current default uncompressed xxh64") {
    XXH64NoneChunk chunk;
    verifySerializationAndDeserialization(chunk, 1);
}

TEST("test serialization and deserialization of legacy uncompressed ccittcrc32") {
    CCITTCRC32NoneChunk chunk;
    verifySerializationAndDeserialization(chunk, 1);
}

TEST("test serialization and deserialization of future multientry xxh64 lz4 compression") {
    for (size_t level(1); level < 9; level++) {
        XXH64CompressedChunk chunk(CompressionConfig::Type::LZ4, level);
        verifySerializationAndDeserialization(chunk, 100);
    }
}

TEST("test serialization and deserialization of future multientry xxh64 zstd compression") {
    for (size_t level(1); level < 9; level++) {
        XXH64CompressedChunk chunk(CompressionConfig::Type::ZSTD, level);
        verifySerializationAndDeserialization(chunk, 100);
    }
}

TEST("test serialization and deserialization of future multientry xxh64 no compression") {
    XXH64CompressedChunk chunk(CompressionConfig::Type::NONE_MULTI, 1);
    verifySerializationAndDeserialization(chunk, 100);
}

TEST("test serialization and deserialization of uncompressable lz4") {
    XXH64CompressedChunk chunk(CompressionConfig::Type::LZ4, 1);
    verifySerializationAndDeserialization(chunk, 1);
}

TEST_MAIN() { TEST_RUN_ALL(); }
