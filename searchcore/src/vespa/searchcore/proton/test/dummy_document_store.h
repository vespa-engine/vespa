// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/docstore/cachestats.h>
#include <vespa/searchlib/docstore/idocumentstore.h>

namespace proton::test {

struct DummyDocumentStore : public search::IDocumentStore
{
    vespalib::string _baseDir;

    DummyDocumentStore()
        : _baseDir("")
    {}
    DummyDocumentStore(const vespalib::string &baseDir)
        : _baseDir(baseDir)
    {}
    ~DummyDocumentStore() {}
    virtual document::Document::UP read(search::DocumentIdT,
                                        const document::DocumentTypeRepo &) const override {
        return document::Document::UP();
    }
    virtual void write(uint64_t, const document::Document &, search::DocumentIdT) override {}
    virtual void remove(uint64_t, search::DocumentIdT) override {}
    virtual void flush(uint64_t) override {}
    virtual uint64_t initFlush(uint64_t) override { return 0; }
    virtual void compact(uint64_t) override {}
    virtual uint64_t lastSyncToken() const override { return 0; }
    virtual uint64_t tentativeLastSyncToken() const override { return 0; }
    virtual fastos::TimeStamp getLastFlushTime() const override { return fastos::TimeStamp(); }
    virtual uint64_t nextId() const override { return 0; }
    virtual size_t memoryUsed() const override { return 0; }
    virtual size_t memoryMeta() const override { return 0; }
    virtual size_t getDiskFootprint() const override { return 0; }
    virtual size_t getDiskBloat() const override { return 0; }
    virtual size_t getMaxCompactGain() const override { return getDiskBloat(); }
    virtual search::CacheStats getCacheStats() const override { return search::CacheStats(); }
    virtual const vespalib::string &getBaseDir() const override { return _baseDir; }
    virtual void accept(search::IDocumentStoreReadVisitor &,
                        search::IDocumentStoreVisitorProgress &,
                        const document::DocumentTypeRepo &) override {}

    virtual void accept(search::IDocumentStoreRewriteVisitor &,
                        search::IDocumentStoreVisitorProgress &,
                        const document::DocumentTypeRepo &) override {}

    virtual double getVisitCost() const override { return 1.0; }
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

}
