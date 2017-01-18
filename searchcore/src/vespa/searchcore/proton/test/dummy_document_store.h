// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/docstore/cachestats.h>
#include <vespa/searchlib/docstore/idocumentstore.h>

namespace proton {

namespace test {

struct DummyDocumentStore : public search::IDocumentStore
{
    vespalib::string _baseDir;

    DummyDocumentStore()
        : _baseDir("")
    {}
    DummyDocumentStore(const vespalib::string &baseDir)
        : _baseDir(baseDir)
    {}
    virtual document::Document::UP read(search::DocumentIdT,
                                        const document::DocumentTypeRepo &) const {
        return document::Document::UP();
    }
    virtual void write(uint64_t, const document::Document &, search::DocumentIdT) {}
    virtual void remove(uint64_t, search::DocumentIdT) {}
    virtual void flush(uint64_t) {}
    virtual uint64_t initFlush(uint64_t) { return 0; }
    virtual void compact(uint64_t) {}
    virtual uint64_t lastSyncToken() const { return 0; }
    virtual uint64_t tentativeLastSyncToken() const override { return 0; }
    virtual fastos::TimeStamp getLastFlushTime() const { return fastos::TimeStamp(); }
    virtual uint64_t nextId() const { return 0; }
    virtual size_t memoryUsed() const { return 0; }
    virtual size_t memoryMeta() const { return 0; }
    virtual size_t getDiskFootprint() const { return 0; }
    virtual size_t getDiskBloat() const { return 0; }
    virtual size_t getMaxCompactGain() const { return getDiskBloat(); }
    virtual search::CacheStats getCacheStats() const { return search::CacheStats(); }
    virtual const vespalib::string &getBaseDir() const { return _baseDir; }
    virtual void accept(search::IDocumentStoreReadVisitor &,
                        search::IDocumentStoreVisitorProgress &,
                        const document::DocumentTypeRepo &) {}

    virtual void accept(search::IDocumentStoreRewriteVisitor &,
                        search::IDocumentStoreVisitorProgress &,
                        const document::DocumentTypeRepo &) {}

    virtual double getVisitCost() const { return 1.0; }
    virtual search::DataStoreStorageStats getStorageStats() const override {
        return search::DataStoreStorageStats(0, 0, 0.0, 0, 0);
    }
    virtual search::MemoryUsage getMemoryUsage() const override { return search::MemoryUsage(); }
    virtual std::vector<search::DataStoreFileChunkStats> getFileChunkStats() const override {
        std::vector<search::DataStoreFileChunkStats> result;
        return result;
    }

    virtual void compactLidSpace(uint32_t wantedDocLidLimit) override { (void) wantedDocLidLimit; }
    virtual bool canShrinkLidSpace() const override { return false; }
    virtual void shrinkLidSpace() override {}
};

} // namespace test

} // namespace proton

