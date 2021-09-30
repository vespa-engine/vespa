// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/transactionlog/chunks.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <atomic>

#include <vespa/log/log.h>
LOG_SETUP("translog_chunks_test");

using namespace search::transactionlog;
using vespalib::ConstBufferRef;
using vespalib::nbostream;
using vespalib::compression::CompressionConfig;

constexpr const char * TEXT = "abcdefghijklmnopqrstuvwxyz_abcdefghijklmnopqrstuvwxyz_abcdefghijklmnopqrstuvwxyz_abcdefghijklmnopqrstuvwxyz_abcdefghijklmnopqrstuvwxyz";
constexpr const char * TEXT2 = "something else";

void
verifySerializationAndDeserialization(IChunk & org, size_t numEntries, Encoding expected) {
    for (size_t i(0); i < numEntries; i++) {
        const char *start = TEXT + (i%20);
        Packet::Entry entry(i, i%8, ConstBufferRef(start, strlen(start)));
        org.add(entry);
    }
    nbostream os;

    Encoding encoding = org.encode(os);
    EXPECT_EQUAL(expected, encoding);
    auto deserialized = IChunk::create(encoding.getRaw());
    deserialized->decode(os);
    EXPECT_TRUE(os.empty());
    EXPECT_EQUAL(numEntries, deserialized->getEntries().size());
}

TEST("test serialization and deserialization of current default uncompressed xxh64") {
    XXH64NoneChunk chunk;
    verifySerializationAndDeserialization(chunk, 1, Encoding(Encoding::Crc::xxh64, Encoding::Compression::none));
}

TEST("test serialization and deserialization of legacy uncompressed ccittcrc32") {
    CCITTCRC32NoneChunk chunk;
    verifySerializationAndDeserialization(chunk, 1, Encoding(Encoding::Crc::ccitt_crc32, Encoding::Compression::none));
}

TEST("test serialization and deserialization of future multientry xxh64 lz4 compression") {
    for (size_t level(1); level < 9; level++) {
        XXH64CompressedChunk chunk(CompressionConfig::Type::LZ4, level);
        verifySerializationAndDeserialization(chunk, 100, Encoding(Encoding::Crc::xxh64, Encoding::Compression::lz4));
    }
}

TEST("test serialization and deserialization of future multientry xxh64 zstd compression") {
    for (size_t level(1); level < 9; level++) {
        XXH64CompressedChunk chunk(CompressionConfig::Type::ZSTD, level);
        verifySerializationAndDeserialization(chunk, 100, Encoding(Encoding::Crc::xxh64, Encoding::Compression::zstd));
    }
}

TEST("test serialization and deserialization of future multientry xxh64 no compression") {
    XXH64CompressedChunk chunk(CompressionConfig::Type::NONE_MULTI, 1);
    verifySerializationAndDeserialization(chunk, 100, Encoding(Encoding::Crc::xxh64, Encoding::Compression::none_multi));
}

TEST("test serialization and deserialization of uncompressable lz4") {
    XXH64CompressedChunk chunk(CompressionConfig::Type::LZ4, 1);
    verifySerializationAndDeserialization(chunk, 1, Encoding(Encoding::Crc::xxh64, Encoding::Compression::none_multi));
}

TEST("test serialization and deserialization of uncompressable zstd") {
    XXH64CompressedChunk chunk(CompressionConfig::Type::ZSTD, 1);
    verifySerializationAndDeserialization(chunk, 1, Encoding(Encoding::Crc::xxh64, Encoding::Compression::none_multi));
}

TEST("test empty commitchunk") {
    CommitChunk cc(1,1);
    EXPECT_EQUAL(0u, cc.sizeBytes());
    EXPECT_EQUAL(0u, cc.getNumCallBacks());
}

struct Counter : public vespalib::IDestructorCallback {
    std::atomic<uint32_t> & _counter;
    Counter(std::atomic<uint32_t> & counter) noexcept : _counter(counter) { _counter++; }
    ~Counter() override { _counter--; }
};

TEST("test single element commitchunk") {
    std::atomic<uint32_t> counter(0);
    {
        Packet p(100);
        p.add(Packet::Entry(1, 1, ConstBufferRef(TEXT, strlen(TEXT))));
        CommitChunk cc(0, 0);

        cc.add(p, std::make_shared<Counter>(counter));
        EXPECT_EQUAL(1u, counter);
        EXPECT_EQUAL(150u, cc.sizeBytes());
        EXPECT_EQUAL(1u, cc.getNumCallBacks());
    }
    EXPECT_EQUAL(0u, counter);
}

TEST("test multi element commitchunk") {
    std::atomic<uint32_t> counter(0);
    {
        Packet p(100);
        p.add(Packet::Entry(1, 3, ConstBufferRef(TEXT, strlen(TEXT))));
        CommitChunk cc(1000, 10);

        cc.add(p, std::make_shared<Counter>(counter));
        Packet p2(100);
        p2.add(Packet::Entry(2, 2, ConstBufferRef(TEXT2, strlen(TEXT2))));
        cc.add(p2, std::make_shared<Counter>(counter));
        EXPECT_EQUAL(2u, counter);
        EXPECT_EQUAL(180u, cc.sizeBytes());
        EXPECT_EQUAL(2u, cc.getNumCallBacks());
    }
    EXPECT_EQUAL(0u, counter);
}
TEST_MAIN() { TEST_RUN_ALL(); }
