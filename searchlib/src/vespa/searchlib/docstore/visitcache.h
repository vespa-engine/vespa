// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idocumentstore.h"
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace search::docstore {

/**
 * Represents a unique set of keys that together acts as a key in a map.
 **/
class KeySet {
public:
    KeySet() noexcept : _keys() { }
    KeySet(uint32_t key);
    explicit KeySet(const IDocumentStore::LidVector &keys);
    uint32_t hash() const noexcept { return _keys.empty() ? 0 : _keys[0]; }
    bool operator==(const KeySet &rhs) const { return _keys == rhs._keys; }
    bool operator<(const KeySet &rhs) const { return _keys < rhs._keys; }
    bool contains(const KeySet &rhs) const;
    const IDocumentStore::LidVector & getKeys() const { return _keys; }
    bool empty() const { return _keys.empty(); }
private:
    IDocumentStore::LidVector _keys;
};

/**
 * Stores blobs compact. These blobs can be retrieved by a numeric key.
 **/
class BlobSet {
public:
    class LidPosition {
    public:
        LidPosition(uint32_t lid, uint32_t offset, uint32_t size) noexcept : _lid(lid), _offset(offset), _size(size) { }
        uint32_t    lid() const { return _lid; }
        uint32_t offset() const { return _offset; }
        uint32_t   size() const { return _size; }
    private:
        uint32_t _lid;
        uint32_t _offset;
        uint32_t _size;
    };

    using Positions = std::vector<LidPosition>;
    BlobSet();
    BlobSet(Positions positions, vespalib::alloc::Alloc && buffer) noexcept;
    BlobSet(BlobSet &&) noexcept = default;
    BlobSet &operator = (BlobSet &&) noexcept = default;
    ~BlobSet();
    void reserve(size_t elems) { _positions.reserve(elems);}
    void append(uint32_t lid, vespalib::ConstBufferRef blob);
    const Positions & getPositions() const { return _positions; }
    Positions && stealPositions() { return std::move(_positions); }
    vespalib::ConstBufferRef get(uint32_t lid) const;
    vespalib::ConstBufferRef getBuffer() const { return vespalib::ConstBufferRef(_buffer.data(), _buffer.size()); }
private:
    Positions           _positions;
    vespalib::nbostream _buffer;
};

/**
 * This is a compressed representation of the above BlobSet.
 * It carries everything necessary to regenerate a BlobSet.
 * It has efficient move constructor/operator since they will be stored
 * in stl containers.
 **/
class CompressedBlobSet {
public:
    using CompressionConfig = vespalib::compression::CompressionConfig;
    CompressedBlobSet() noexcept;
    CompressedBlobSet(CompressionConfig compression, BlobSet uncompressed);
    CompressedBlobSet(CompressedBlobSet && rhs) noexcept = default;
    CompressedBlobSet & operator=(CompressedBlobSet && rhs) noexcept = default;
    CompressedBlobSet(const CompressedBlobSet & rhs) = default;
    CompressedBlobSet & operator=(const CompressedBlobSet & rhs) = default;
    ~CompressedBlobSet();
    size_t bytesAllocated() const;
    bool empty() const { return _positions.empty(); }
    BlobSet getBlobSet() const;
private:
    using Alloc = vespalib::alloc::Alloc;
    BlobSet::Positions      _positions;
    std::shared_ptr<Alloc>  _buffer;
    uint32_t                 _used;
    CompressionConfig::Type _compression;
};

/**
 * Caches a set of objects as a set.
 * The objects are compressed together as a set.
 * The whole set is invalidated when one object of its objects are removed.
 **/
class VisitCache {
public:
    using CompressionConfig = vespalib::compression::CompressionConfig;
    VisitCache(IDataStore &store, size_t cacheSize, CompressionConfig compression);
    ~VisitCache();

    CompressedBlobSet read(const IDocumentStore::LidVector & keys) const;
    void remove(uint32_t key);
    void invalidate(uint32_t key) { remove(key); }

    vespalib::CacheStats getCacheStats() const;
    vespalib::MemoryUsage getStaticMemoryUsage() const;
    void reconfigure(size_t cacheSize, CompressionConfig compression);

    /**
 * This implments the interface the cache uses when it has a cache miss.
 * It wraps an IDataStore. Given a set of lids it will visit all objects
 * and compress them as a complete set to maximize compression rate.
 * As this is a readonly cache the write/erase methods are noops.
 */
    class BackingStore {
    public:
        BackingStore(IDataStore &store, CompressionConfig compression)
            : _backingStore(store),
              _compression(compression)
        { }
        bool read(const KeySet &key, CompressedBlobSet &blobs) const;
        void write(const KeySet &, const CompressedBlobSet &) { }
        void erase(const KeySet &) { }
        void reconfigure(CompressionConfig compression);

    private:
        IDataStore        &_backingStore;
        std::atomic<CompressionConfig>  _compression;
    };
private:

    class Cache;
    BackingStore            _store;
    std::unique_ptr<Cache>  _cache;
};

}
