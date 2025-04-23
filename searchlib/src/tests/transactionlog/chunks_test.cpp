// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/transactionlog/chunks.h>
#include <atomic>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_master.hpp>

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
    EXPECT_EQ(expected, encoding);
    auto deserialized = IChunk::create(encoding.getRaw());
    deserialized->decode(os);
    EXPECT_TRUE(os.empty());
    EXPECT_EQ(numEntries, deserialized->getEntries().size());
}

TEST(TransactionLogChunksTest, test_serialization_and_deserialization_of_current_default_uncompressed_xxh64) {
    XXH64NoneChunk chunk;
    verifySerializationAndDeserialization(chunk, 1, Encoding(Encoding::Crc::xxh64, Encoding::Compression::none));
}

TEST(TransactionLogChunksTest, test_serialization_and_deserialization_of_legacy_uncompressed_ccittcrc32) {
    CCITTCRC32NoneChunk chunk;
    verifySerializationAndDeserialization(chunk, 1, Encoding(Encoding::Crc::ccitt_crc32, Encoding::Compression::none));
}

TEST(TransactionLogChunksTest, test_serialization_and_deserialization_of_future_multientry_xxh64_lz4_compression) {
    for (size_t level(1); level < 9; level++) {
        XXH64CompressedChunk chunk(CompressionConfig::Type::LZ4, level);
        verifySerializationAndDeserialization(chunk, 100, Encoding(Encoding::Crc::xxh64, Encoding::Compression::lz4));
    }
}

TEST(TransactionLogChunksTest, test_serialization_and_deserialization_of_future_multientry_xxh64_zstd_compression) {
    for (size_t level(1); level < 9; level++) {
        XXH64CompressedChunk chunk(CompressionConfig::Type::ZSTD, level);
        verifySerializationAndDeserialization(chunk, 100, Encoding(Encoding::Crc::xxh64, Encoding::Compression::zstd));
    }
}

TEST(TransactionLogChunksTest, test_serialization_and_deserialization_of_future_multientry_xxh64_no_compression) {
    XXH64CompressedChunk chunk(CompressionConfig::Type::NONE_MULTI, 1);
    verifySerializationAndDeserialization(chunk, 100, Encoding(Encoding::Crc::xxh64, Encoding::Compression::none_multi));
}

TEST(TransactionLogChunksTest, test_serialization_and_deserialization_of_uncompressable_lz4) {
    XXH64CompressedChunk chunk(CompressionConfig::Type::LZ4, 1);
    verifySerializationAndDeserialization(chunk, 1, Encoding(Encoding::Crc::xxh64, Encoding::Compression::none_multi));
}

TEST(TransactionLogChunksTest, test_serialization_and_deserialization_of_uncompressable_zstd) {
    XXH64CompressedChunk chunk(CompressionConfig::Type::ZSTD, 1);
    verifySerializationAndDeserialization(chunk, 1, Encoding(Encoding::Crc::xxh64, Encoding::Compression::none_multi));
}

TEST(TransactionLogChunksTest, test_empty_commitchunk) {
    CommitChunk cc(1,1);
    EXPECT_EQ(0u, cc.sizeBytes());
    EXPECT_EQ(0u, cc.getNumCallBacks());
}

struct Counter : public vespalib::IDestructorCallback {
    std::atomic<uint32_t> & _counter;
    Counter(std::atomic<uint32_t> & counter) noexcept : _counter(counter) { _counter++; }
    ~Counter() override { _counter--; }
};

TEST(TransactionLogChunksTest, test_single_element_commitchunk) {
    std::atomic<uint32_t> counter(0);
    {
        Packet p(100);
        p.add(Packet::Entry(1, 1, ConstBufferRef(TEXT, strlen(TEXT))));
        CommitChunk cc(0, 0);

        cc.add(p, std::make_shared<Counter>(counter));
        EXPECT_EQ(1u, counter);
        EXPECT_EQ(150u, cc.sizeBytes());
        EXPECT_EQ(1u, cc.getNumCallBacks());
    }
    EXPECT_EQ(0u, counter);
}

TEST(TransactionLogChunksTest, test_multi_element_commitchunk) {
    std::atomic<uint32_t> counter(0);
    {
        Packet p(100);
        p.add(Packet::Entry(1, 3, ConstBufferRef(TEXT, strlen(TEXT))));
        CommitChunk cc(1000, 10);

        cc.add(p, std::make_shared<Counter>(counter));
        Packet p2(100);
        p2.add(Packet::Entry(2, 2, ConstBufferRef(TEXT2, strlen(TEXT2))));
        cc.add(p2, std::make_shared<Counter>(counter));
        EXPECT_EQ(2u, counter);
        EXPECT_EQ(180u, cc.sizeBytes());
        EXPECT_EQ(2u, cc.getNumCallBacks());
    }
    EXPECT_EQ(0u, counter);
}

TEST(TransactionLogChunksTest, shrinkToFit_if_difference_is_larger_than_8x) {
    Packet p(16000);
    p.add(Packet::Entry(1, 3, ConstBufferRef(TEXT, strlen(TEXT))));
    EXPECT_EQ(150u, p.sizeBytes());
    EXPECT_EQ(16384u, p.getHandle().capacity());
    p.shrinkToFit();
    EXPECT_EQ(150u, p.sizeBytes());
    EXPECT_EQ(150u, p.getHandle().capacity());
}

TEST(TransactionLogChunksTest, not_shrinkToFit_if_difference_is_less_than_8x) {
    Packet p(1000);
    p.add(Packet::Entry(1, 3, ConstBufferRef(TEXT, strlen(TEXT))));
    EXPECT_EQ(150u, p.sizeBytes());
    EXPECT_EQ(1024u, p.getHandle().capacity());
    p.shrinkToFit();
    EXPECT_EQ(150u, p.sizeBytes());
    EXPECT_EQ(1024u, p.getHandle().capacity());
}

GTEST_MAIN_RUN_ALL_TESTS()
