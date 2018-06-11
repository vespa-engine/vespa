// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idocumentstore.h"
#include <vespa/vespalib/util/compressionconfig.h>


namespace search {

namespace docstore {
    class VisitCache;
    class BackingStore;
    class Cache;
}
using docstore::VisitCache;
using docstore::BackingStore;
using docstore::Cache;

/**
 * Simple document store that contains serialized Document instances.
 * updates will be held in memory until flush() is called.
 * Uses a Local ID as key.
 **/
class DocumentStore : public IDocumentStore
{
public:
    class Config {
    public:
        using CompressionConfig = vespalib::compression::CompressionConfig;
        Config() :
            _compression(CompressionConfig::LZ4, 9, 70),
            _maxCacheBytes(1000000000),
            _initialCacheEntries(0),
            _allowVisitCaching(false)
        { }
        Config(const CompressionConfig & compression, size_t maxCacheBytes, size_t initialCacheEntries) :
            _compression((maxCacheBytes != 0) ? compression : CompressionConfig::NONE),
            _maxCacheBytes(maxCacheBytes),
            _initialCacheEntries(initialCacheEntries),
            _allowVisitCaching(false)
        { }
        const CompressionConfig & getCompression() const { return _compression; }
        size_t getMaxCacheBytes()   const { return _maxCacheBytes; }
        size_t getInitialCacheEntries() const { return _initialCacheEntries; }
        bool allowVisitCaching() const { return _allowVisitCaching; }
        Config & allowVisitCaching(bool allow) { _allowVisitCaching = allow; return *this; }
        bool operator == (const Config &) const;
    private:
        CompressionConfig _compression;
        size_t _maxCacheBytes;
        size_t _initialCacheEntries;
        bool   _allowVisitCaching;
    };

    /**
     * Construct a document store.
     * If the "simpledocstore.dat" data file exists, reads meta-data (offsets) into memory.
     *
     * @throws vespalib::IoException if the file is corrupt or other IO problems occur.
     * @param baseDir  The path to a directory where "simpledocstore.dat" will exist.
     **/
    DocumentStore(const Config & config, IDataStore & store);
    ~DocumentStore();

    DocumentUP read(DocumentIdT lid, const document::DocumentTypeRepo &repo) const override;
    void visit(const LidVector & lids, const document::DocumentTypeRepo &repo, IDocumentVisitor & visitor) const override;
    void write(uint64_t synkToken, DocumentIdT lid, const document::Document& doc) override;
    void write(uint64_t synkToken, DocumentIdT lid, const vespalib::nbostream & os) override;
    void remove(uint64_t syncToken, DocumentIdT lid) override;
    void flush(uint64_t syncToken) override;
    uint64_t initFlush(uint64_t synctoken) override;
    void compact(uint64_t syncToken) override;
    uint64_t lastSyncToken() const override;
    uint64_t tentativeLastSyncToken() const override;
    fastos::TimeStamp getLastFlushTime() const override;
    uint32_t getDocIdLimit() const override { return _backingStore.getDocIdLimit(); }
    size_t        memoryUsed() const override { return _backingStore.memoryUsed(); }
    size_t  getDiskFootprint() const override { return _backingStore.getDiskFootprint(); }
    size_t      getDiskBloat() const override { return _backingStore.getDiskBloat(); }
    size_t getMaxCompactGain() const override { return _backingStore.getMaxCompactGain(); }
    CacheStats getCacheStats() const override;
    size_t memoryMeta() const override { return _backingStore.memoryMeta(); }
    const vespalib::string & getBaseDir() const override { return _backingStore.getBaseDir(); }
    void
    accept(IDocumentStoreReadVisitor &visitor,
           IDocumentStoreVisitorProgress &visitorProgress,
           const document::DocumentTypeRepo &repo) override;
    void
    accept(IDocumentStoreRewriteVisitor &visitor,
           IDocumentStoreVisitorProgress &visitorProgress,
           const document::DocumentTypeRepo &repo) override;
    double getVisitCost() const override;
    DataStoreStorageStats getStorageStats() const override;
    MemoryUsage getMemoryUsage() const override;
    std::vector<DataStoreFileChunkStats> getFileChunkStats() const override;

    /**
     * Implements common::ICompactableLidSpace
     */
    void compactLidSpace(uint32_t wantedDocLidLimit) override;
    bool canShrinkLidSpace() const override;
    size_t getEstimatedShrinkLidSpaceGain() const override;
    void shrinkLidSpace() override;
    void reconfigure(const Config & config);

private:
    bool useCache() const;

    template <class> class WrapVisitor;
    class WrapVisitorProgress;
    Config                         _config;
    IDataStore &                   _backingStore;
    std::unique_ptr<BackingStore>  _store;
    std::shared_ptr<Cache>         _cache;
    std::shared_ptr<VisitCache>    _visitCache;
    mutable std::atomic<uint64_t>  _uncached_lookups;
};

} // namespace search
