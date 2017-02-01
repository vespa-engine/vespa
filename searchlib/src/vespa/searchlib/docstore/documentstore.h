// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idocumentstore.h"
#include "idatastore.h"

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
        Config() :
            _compression(document::CompressionConfig::LZ4, 9, 70),
            _maxCacheBytes(1000000000),
            _initialCacheEntries(0),
            _allowVisitCaching(false)
        { }
        Config(const document::CompressionConfig & compression, size_t maxCacheBytes, size_t initialCacheEntries) :
            _compression((maxCacheBytes != 0) ? compression : document::CompressionConfig::NONE),
            _maxCacheBytes(maxCacheBytes),
            _initialCacheEntries(initialCacheEntries),
            _allowVisitCaching(false)
        { }
        const document::CompressionConfig & getCompression() const { return _compression; }
        size_t getMaxCacheBytes()   const { return _maxCacheBytes; }
        size_t getInitialCacheEntries() const { return _initialCacheEntries; }
        bool allowVisitCaching() const { return _allowVisitCaching; }
        Config & allowVisitCaching(bool allow) { _allowVisitCaching = allow; return *this; }
    private:
        document::CompressionConfig _compression;
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

    /**
     * Make a Document from a stored serialized data blob.
     * @param lid The local ID associated with the document.
     * @return NULL if there is no document associated with the lid.
     **/
    document::Document::UP read(DocumentIdT lid, const document::DocumentTypeRepo &repo) const override;
    void visit(const LidVector & lids, const document::DocumentTypeRepo &repo, IDocumentVisitor & visitor) const override;

    /**
     * Serialize and store a document.
     * @param doc The document to store
     * @param lid The local ID associated with the document
     **/
    void write(uint64_t synkToken, const document::Document& doc, DocumentIdT lid) override;

    /**
     * Mark a document as removed. A later read() will return NULL for the given lid.
     * @param lid The local ID associated with the document
     **/
    void remove(uint64_t syncToken, DocumentIdT lid) override;

    /**
     * Flush all in-memory updates to disk.
     **/
    void flush(uint64_t syncToken) override;
    uint64_t initFlush(uint64_t synctoken) override;


    /**
     * If possible compact the disk.
     **/
    void compact(uint64_t syncToken) override;

    /**
     * The sync token used for the last successful flush() operation,
     * or 0 if no flush() has been performed yet.
     * @return Last flushed sync token.
     **/
    uint64_t lastSyncToken() const override;
    uint64_t tentativeLastSyncToken() const override;
    fastos::TimeStamp getLastFlushTime() const override;

    /**
     * Get the number of entries (including removed IDs
     * or gaps in the local ID sequence) in the document store.
     * @return The next local ID expected to be used.
     */
    uint64_t nextId() const override { return _backingStore.nextId(); }

    /**
     * Calculate memory used by this instance.  During flush() actual
     * memory usage may be approximately twice the reported amount.
     * @return memory usage (in bytes)
     **/
    size_t        memoryUsed() const override { return _backingStore.memoryUsed(); }
    size_t  getDiskFootprint() const override { return _backingStore.getDiskFootprint(); }
    size_t      getDiskBloat() const override { return _backingStore.getDiskBloat(); }
    size_t getMaxCompactGain() const override { return _backingStore.getMaxCompactGain(); }

    CacheStats getCacheStats() const override;

    /**
     * Calculates memory that is used for meta data by this instance. Calling
     * flush() does not free this memory.
     * @return memory usage (in bytes)
     **/
    size_t memoryMeta() const override { return _backingStore.memoryMeta(); }

    const vespalib::string & getBaseDir() const override { return _backingStore.getBaseDir(); }

    /**
     * Visit all documents found in document store.
     */
    void
    accept(IDocumentStoreReadVisitor &visitor,
           IDocumentStoreVisitorProgress &visitorProgress,
           const document::DocumentTypeRepo &repo) override;

    /**
     * Visit all documents found in document store.
     */
    void
    accept(IDocumentStoreRewriteVisitor &visitor,
           IDocumentStoreVisitorProgress &visitorProgress,
           const document::DocumentTypeRepo &repo) override;

    /**
     * Return cost of visiting all documents found in document store.
     */
    double getVisitCost() const override;

    /*
     * Return brief stats for data store.
     */
    DataStoreStorageStats getStorageStats() const override;

    MemoryUsage getMemoryUsage() const override;

    /*
     * Return detailed stats about underlying files for data store.
     */
    std::vector<DataStoreFileChunkStats> getFileChunkStats() const override;

    /**
     * Implements common::ICompactableLidSpace
     */
    void compactLidSpace(uint32_t wantedDocLidLimit) override;
    bool canShrinkLidSpace() const override;
    void shrinkLidSpace() override;

private:
    bool useCache() const;

    template <class> class WrapVisitor;
    class WrapVisitorProgress;
    Config                         _config;
    IDataStore &                   _backingStore;
    std::unique_ptr<BackingStore>  _store;
    std::shared_ptr<Cache>         _cache;
    std::shared_ptr<VisitCache>    _visitCache;
    mutable volatile uint64_t      _uncached_lookups;
};

} // namespace search

