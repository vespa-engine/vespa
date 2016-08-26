// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idocumentstore.h"
#include "idatastore.h"
#include "visitcache.h"

namespace search {

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
    virtual DataStoreStorageStats getStorageStats() const override;

    /*
     * Return detailed stats about underlying files for data store.
     */
    virtual std::vector<DataStoreFileChunkStats>
    getFileChunkStats() const override;

private:
    template <class> class WrapVisitor;
    class WrapVisitorProgress;
    class Value {
    public:
        typedef std::unique_ptr<Value> UP;
        Value() : _compressedSize(0), _uncompressedSize(0), _compression(document::CompressionConfig::NONE) { }

        Value(Value && rhs) :
            _compressedSize(rhs._compressedSize),
            _uncompressedSize(rhs._uncompressedSize),
            _compression(rhs._compression),
            _buf(std::move(rhs._buf))
        { }

        Value(const Value & rhs) :
            _compressedSize(rhs._compressedSize),
            _uncompressedSize(rhs._uncompressedSize),
            _compression(rhs._compression),
            _buf(rhs.size())
        {
            memcpy(get(), rhs.get(), size());
        }
        Value & operator = (Value && rhs) {
            _buf = std::move(rhs._buf);
            _compressedSize = rhs._compressedSize;
            _uncompressedSize = rhs._uncompressedSize;
            _compression = rhs._compression;
            return *this;
        }
        void setCompression(document::CompressionConfig::Type comp, size_t uncompressedSize) {
            _compression = comp;
            _uncompressedSize = uncompressedSize;
        }
        document::CompressionConfig::Type getCompression() const { return _compression; }
        size_t getUncompressedSize() const { return _uncompressedSize; }

        /**
         * Compress buffer into temporary buffer and copy temporary buffer to
         * value along with compression config.
         */
        void set(vespalib::DataBuffer && buf, ssize_t len, const document::CompressionConfig &compression);

        /**
         * Decompress value into temporary buffer and deserialize document from
         * the temporary buffer.
         */
        document::Document::UP deserializeDocument(const document::DocumentTypeRepo &repo);

        size_t size() const { return _compressedSize; }
        bool empty() const { return size() == 0; }
        operator const void * () const { return _buf.get(); }
        const void * get() const { return _buf.get(); }
        void * get() { return _buf.get(); }

    private:
        size_t _compressedSize;
        size_t _uncompressedSize;
        document::CompressionConfig::Type _compression;
        vespalib::DefaultAlloc _buf;
    };
    class BackingStore {
    public:
        BackingStore(IDataStore & store, const document::CompressionConfig & compression) :
            _backingStore(store),
            _compression(compression)
        { }
        bool read(DocumentIdT key, Value & value) const;
        void visit(const LidVector & lids, const document::DocumentTypeRepo &repo, IDocumentVisitor & visitor) const;
        void write(DocumentIdT, const Value &) { }
        void erase(DocumentIdT ) { }
 
        const document::CompressionConfig & getCompression(void) const { return _compression; }
    private:
        IDataStore & _backingStore;
        const document::CompressionConfig _compression;
    };
    bool useCache() const { return (_cache->capacityBytes() != 0) && (_cache->capacity() != 0); }
    using CacheParams = vespalib::CacheParam<
                            vespalib::LruParam<DocumentIdT, Value>,
                            BackingStore,
                            vespalib::zero<DocumentIdT>,
                            vespalib::size<Value>
                        >;
    using Cache = vespalib::cache<CacheParams>;
    using VisitCache = docstore::VisitCache;

    Config                         _config;
    IDataStore &                   _backingStore;
    BackingStore                   _store;
    std::shared_ptr<Cache>         _cache;
    std::shared_ptr<VisitCache>    _visitCache;
};

} // namespace search

