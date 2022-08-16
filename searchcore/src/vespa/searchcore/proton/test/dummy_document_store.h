// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/cache_stats.h>
#include <vespa/searchlib/docstore/idocumentstore.h>

namespace proton::test {

struct DummyDocumentStore : public search::IDocumentStore
{
    vespalib::string _baseDir;

    DummyDocumentStore() = default;
    DummyDocumentStore(const vespalib::string &baseDir) noexcept
        : _baseDir(baseDir)
    {}
    ~DummyDocumentStore() = default;
    DocumentUP read(search::DocumentIdT, const document::DocumentTypeRepo &) const override {
        return DocumentUP();
    }
    void write(uint64_t, search::DocumentIdT, const document::Document &) override {}
    void write(uint64_t, search::DocumentIdT, const vespalib::nbostream &) override {}
    void remove(uint64_t, search::DocumentIdT) override {}
    void flush(uint64_t) override {}
    uint64_t initFlush(uint64_t) override { return 0; }
    void compactBloat(uint64_t) override {}
    void compactSpread(uint64_t) override {}
    uint64_t lastSyncToken() const override { return 0; }
    uint64_t tentativeLastSyncToken() const override { return 0; }
    vespalib::system_time getLastFlushTime() const override { return vespalib::system_time(); }
    uint32_t getDocIdLimit() const override { return 0; }
    size_t memoryUsed() const override { return 0; }
    size_t memoryMeta() const override { return 0; }
    size_t getDiskFootprint() const override { return 0; }
    size_t getDiskBloat() const override { return 0; }
    size_t getMaxSpreadAsBloat() const override { return getDiskBloat(); }
    vespalib::CacheStats getCacheStats() const override { return vespalib::CacheStats(); }
    const vespalib::string &getBaseDir() const override { return _baseDir; }
    void accept(search::IDocumentStoreReadVisitor &,
                search::IDocumentStoreVisitorProgress &,
                const document::DocumentTypeRepo &) override {}
    void accept(search::IDocumentStoreRewriteVisitor &,
                search::IDocumentStoreVisitorProgress &,
                const document::DocumentTypeRepo &) override {}

    double getVisitCost() const override { return 1.0; }
    search::DataStoreStorageStats getStorageStats() const override {
        return search::DataStoreStorageStats(0, 0, 0.0, 0, 0, 0);
    }
    vespalib::MemoryUsage getMemoryUsage() const override { return vespalib::MemoryUsage(); }
    std::vector<search::DataStoreFileChunkStats> getFileChunkStats() const override {
        std::vector<search::DataStoreFileChunkStats> result;
        return result;
    }

    void compactLidSpace(uint32_t wantedDocLidLimit) override { (void) wantedDocLidLimit; }
    bool canShrinkLidSpace() const override { return false; }
    size_t getEstimatedShrinkLidSpaceGain() const override { return 0; }
    void shrinkLidSpace() override {}
};

}
