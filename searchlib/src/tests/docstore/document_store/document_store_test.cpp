// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/searchlib/docstore/logdocumentstore.h>
#include <vespa/searchlib/docstore/value.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/stllike/cache_stats.h>

#include <map>

using namespace search;
using CompressionConfig = vespalib::compression::CompressionConfig;

document::DocumentTypeRepo repo;

struct NullDataStore : IDataStore {
    NullDataStore() : IDataStore("") {}
    ~NullDataStore() override;
    ssize_t read(uint32_t, vespalib::DataBuffer&) const override { return 0; }
    void read(const LidVector&, IBufferVisitor&) const override {}
    void write(uint64_t, uint32_t, const void*, size_t) override {}
    void remove(uint64_t, uint32_t) override {}
    void flush(uint64_t) override {}

    uint64_t initFlush(uint64_t syncToken) override { return syncToken; }

    size_t memoryUsed() const override { return 0; }
    size_t memoryMeta() const override { return 0; }
    size_t getDiskFootprint() const override { return 0; }
    uint64_t get_size_on_disk() const override { return 0; }
    size_t getDiskBloat() const override { return 0; }
    size_t getMaxSpreadAsBloat() const override { return 0; }
    uint64_t lastSyncToken() const override { return 0; }
    uint64_t tentativeLastSyncToken() const override { return 0; }
    vespalib::system_time getLastFlushTime() const override { return vespalib::system_time(); }
    void accept(IDataStoreVisitor&, IDataStoreVisitorProgress&, bool) override {}
    double getVisitCost() const override { return 1.0; }
    DataStoreStorageStats getStorageStats() const override { return DataStoreStorageStats(0, 0, 0, 0.0, 0, 0, 0); }
    vespalib::MemoryUsage getMemoryUsage() const override { return vespalib::MemoryUsage(); }
    std::vector<DataStoreFileChunkStats> getFileChunkStats() const override {
        std::vector<DataStoreFileChunkStats> result;
        return result;
    }
    [[nodiscard]] size_t max_file_size() const noexcept override { return 0; };
    void compactLidSpace(uint32_t wantedDocLidLimit) override { (void)wantedDocLidLimit; }
    bool canShrinkLidSpace() const override { return false; }
    size_t getEstimatedShrinkLidSpaceGain() const override { return 0; }
    void shrinkLidSpace() override {}
};

NullDataStore::~NullDataStore() = default;

TEST(DocumentStoreTest, require_that_uncache_docstore_lookups_are_counted) {
    DocumentStore::Config f1(CompressionConfig::NONE, 0);
    NullDataStore         f2;
    DocumentStore         f3(f1, f2);
    EXPECT_EQ(0u, f3.getCacheStats().misses);
    f3.read(1, repo);
    EXPECT_EQ(1u, f3.getCacheStats().misses);
}

TEST(DocumentStoreTest, require_that_cached_docstore_lookups_are_counted) {
    DocumentStore::Config f1(CompressionConfig::NONE, 100000);
    NullDataStore         f2;
    DocumentStore         f3(f1, f2);
    EXPECT_EQ(0u, f3.getCacheStats().misses);
    f3.read(1, repo);
    EXPECT_EQ(1u, f3.getCacheStats().misses);
}

namespace {

/*
 * Data store keeping serialized documents in memory, counting reads.
 */
struct MemoryDataStore : NullDataStore {
    std::map<uint32_t, std::vector<char>> _docs;
    mutable uint32_t                      _reads = 0;
    ~MemoryDataStore() override;
    ssize_t read(uint32_t lid, vespalib::DataBuffer& buffer) const override {
        ++_reads;
        auto itr = _docs.find(lid);
        if (itr == _docs.end()) {
            return 0;
        }
        buffer.writeBytes(itr->second.data(), itr->second.size());
        return itr->second.size();
    }
    void write(uint64_t, uint32_t lid, const void* buffer, size_t sz) override {
        auto* data = static_cast<const char*>(buffer);
        _docs[lid].assign(data, data + sz);
    }
    void remove(uint64_t, uint32_t lid) override { _docs.erase(lid); }
};

MemoryDataStore::~MemoryDataStore() = default;

std::unique_ptr<document::DocumentTypeRepo> make_test_repo() {
    document::new_config_builder::NewConfigBuilder builder;
    builder.document("test", 4711);
    return std::make_unique<document::DocumentTypeRepo>(builder.config());
}

std::unique_ptr<document::Document> make_test_doc(const document::DocumentTypeRepo& test_repo) {
    return std::make_unique<document::Document>(test_repo, *test_repo.getDocumentType("test"),
                                                document::DocumentId("id:ns:test::1"));
}

} // namespace

TEST(DocumentStoreTest, require_that_move_keeps_cached_document_cached_under_new_lid) {
    auto                  test_repo = make_test_repo();
    DocumentStore::Config config(CompressionConfig::NONE, 100000);
    MemoryDataStore       backing_store;
    DocumentStore         store(config, backing_store);
    store.write(1, 2, *make_test_doc(*test_repo));
    EXPECT_TRUE(store.read(2, *test_repo));
    EXPECT_TRUE(store.read(2, *test_repo));
    EXPECT_EQ(1u, backing_store._reads);
    EXPECT_EQ(1u, store.getCacheStats().hits);
    EXPECT_EQ(1u, store.getCacheStats().elements);

    // Move document from lid 2 to lid 1, as done by lid space compaction
    store.move(2, 2, 1, *make_test_doc(*test_repo));
    EXPECT_EQ(1u, backing_store._docs.count(1)); // Written through to backing store
    store.remove(3, 2);
    EXPECT_EQ(1u, store.getCacheStats().elements);

    auto doc = store.read(1, *test_repo);
    ASSERT_TRUE(doc);
    EXPECT_EQ("id:ns:test::1", doc->getId().toString());
    EXPECT_EQ(1u, backing_store._reads); // Served from cache
    EXPECT_EQ(2u, store.getCacheStats().hits);
    EXPECT_FALSE(store.read(2, *test_repo));
    EXPECT_EQ(2u, backing_store._reads);
}

TEST(DocumentStoreTest, require_that_move_of_uncached_lid_is_plain_write) {
    auto                  test_repo = make_test_repo();
    DocumentStore::Config config(CompressionConfig::NONE, 100000);
    MemoryDataStore       backing_store;
    DocumentStore         store(config, backing_store);
    store.write(1, 2, *make_test_doc(*test_repo));
    store.move(2, 2, 1, *make_test_doc(*test_repo));
    store.remove(3, 2);
    EXPECT_EQ(0u, store.getCacheStats().elements);
    EXPECT_TRUE(store.read(1, *test_repo));
    EXPECT_EQ(1u, backing_store._reads);
    EXPECT_EQ(0u, store.getCacheStats().hits);
}

TEST(DocumentStoreTest, require_that_move_works_when_cache_is_disabled) {
    auto                  test_repo = make_test_repo();
    DocumentStore::Config config(CompressionConfig::NONE, 0);
    MemoryDataStore       backing_store;
    DocumentStore         store(config, backing_store);
    store.write(1, 2, *make_test_doc(*test_repo));
    EXPECT_TRUE(store.read(2, *test_repo));
    store.move(2, 2, 1, *make_test_doc(*test_repo));
    store.remove(3, 2);
    EXPECT_TRUE(store.read(1, *test_repo));
    EXPECT_EQ(2u, backing_store._reads);
}

TEST(DocumentStoreTest, require_that_DocumentStore_Config_equality_operator_detects_inequality) {
    using C = DocumentStore::Config;
    EXPECT_TRUE(C() == C());
    EXPECT_TRUE(C(CompressionConfig::NONE, 100000) == C(CompressionConfig::NONE, 100000));
    EXPECT_FALSE(C(CompressionConfig::NONE, 100000) == C(CompressionConfig::NONE, 100001));
    EXPECT_FALSE(C(CompressionConfig::NONE, 100000) == C(CompressionConfig::LZ4, 100000));
}

TEST(DocumentStoreTest, require_that_LogDocumentStore_Config_equality_operator_detects_inequality) {
    using C = LogDocumentStore::Config;
    using LC = LogDataStore::Config;
    using DC = DocumentStore::Config;
    EXPECT_TRUE(C() == C());
    EXPECT_FALSE(C() != C());
    EXPECT_FALSE(C(DC(CompressionConfig::NONE, 100000), LC()) == C());
    EXPECT_FALSE(C(DC(), LC().setMaxBucketSpread(7)) == C());
}

using search::docstore::Value;
std::string_view
    S1("this is a string long enough to be compressed and is just used for sanity checking of compression"
       "Adding some repeatble sequences like aaaaaaaaaaaaaaaaaaaaaa bbbbbbbbbbbbbbbbbbbbbbb to ensure compression"
       "xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz xyz");

Value createValue(std::string_view s, CompressionConfig cfg) {
    Value                v(7);
    vespalib::DataBuffer input;
    input.writeBytes(s.data(), s.size());
    v.set(std::move(input), s.size(), cfg);
    return v;
}
void verifyValue(std::string_view s, const Value& v) {
    Value::Result result = v.decompressed();
    ASSERT_TRUE(result.second);
    EXPECT_EQ(s.size(), v.getUncompressedSize());
    EXPECT_EQ(7u, v.getSyncToken());
    EXPECT_EQ(0, memcmp(s.data(), result.first.getData(), result.first.getDataLen()));
}

TEST(DocumentStoreTest, require_that_Value_and_cache_entries_have_expected_size) {
    using pair = std::pair<DocumentIdT, Value>;
    using Node = vespalib::hash_node<pair>;
    EXPECT_EQ(48ul, sizeof(Value));
    EXPECT_EQ(56ul, sizeof(pair));
    EXPECT_EQ(64ul, sizeof(Node));
}

TEST(DocumentStoreTest, require_that_Value_can_store_uncompressed_data) {
    Value v = createValue(S1, CompressionConfig::NONE);
    verifyValue(S1, v);
}

TEST(DocumentStoreTest, require_that_Value_can_be_moved) {
    Value v = createValue(S1, CompressionConfig::NONE);
    Value m = std::move(v);
    verifyValue(S1, m);
}

TEST(DocumentStoreTest, require_that_Value_can_be_copied) {
    Value v = createValue(S1, CompressionConfig::NONE);
    Value copy(v);
    verifyValue(S1, v);
    verifyValue(S1, copy);
}

TEST(DocumentStoreTest, require_that_Value_can_store_lz4_compressed_data) {
    Value v = createValue(S1, CompressionConfig::LZ4);
    EXPECT_EQ(CompressionConfig::LZ4, v.getCompression());
    EXPECT_EQ(164u, v.size());
    verifyValue(S1, v);
}

TEST(DocumentStoreTest, require_that_Value_can_store_zstd_compressed_data) {
    Value v = createValue(S1, CompressionConfig::ZSTD);
    EXPECT_EQ(CompressionConfig::ZSTD, v.getCompression());
    EXPECT_EQ(128u, v.size());
    verifyValue(S1, v);
}

TEST(DocumentStoreTest, require_that_Value_is_shrunk_to_fit_compressed_data) {
    Value v = createValue(S1, CompressionConfig::ZSTD);
    EXPECT_EQ(CompressionConfig::ZSTD, v.getCompression());
    EXPECT_EQ(128u, v.size());
    EXPECT_EQ(128u, v.capacity());
    EXPECT_EQ(297u, v.getUncompressedSize());
    verifyValue(S1, v);
}

TEST(DocumentStoreTest, require_that_Value_can_detect_if_output_not_equal_to_input) {
    Value v = createValue(S1, CompressionConfig::NONE);
    const_cast<uint8_t*>(static_cast<const uint8_t*>(v.get()))[8] ^= 0xff;
    EXPECT_FALSE(v.decompressed().second);
}

GTEST_MAIN_RUN_ALL_TESTS()
