// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idocumentstore.h"
#include "cachestats.h"
#include <vespa/vespalib/stllike/cache.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/bytebuffer.h>

namespace search::docstore {

/**
 * Represents a unique set of keys that together acts as a key in a map.
 **/
class KeySet {
public:
    KeySet() : _keys() { }
    KeySet(uint32_t key);
    explicit KeySet(const IDocumentStore::LidVector &keys);
    uint32_t hash() const { return _keys.empty() ? 0 : _keys[0]; }
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
        LidPosition(uint32_t lid, uint32_t offset, uint32_t size) : _lid(lid), _offset(offset), _size(size) { }
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
    BlobSet(const Positions & positions, vespalib::alloc::Alloc && buffer);
    BlobSet(BlobSet &&) = default;
    BlobSet &operator = (BlobSet &&) = default;
    ~BlobSet();
    void append(uint32_t lid, vespalib::ConstBufferRef blob);
    void remove(uint32_t lid);
    const Positions & getPositions() const { return _positions; }
    vespalib::ConstBufferRef get(uint32_t lid) const;
    vespalib::ConstBufferRef getBuffer() const { return vespalib::ConstBufferRef(_buffer.c_str(), _buffer.size()); }
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
    CompressedBlobSet();
    CompressedBlobSet(const CompressionConfig &compression, const BlobSet & uncompressed);
    CompressedBlobSet(CompressedBlobSet && rhs) = default;
    CompressedBlobSet & operator=(CompressedBlobSet && rhs) = default;
    CompressedBlobSet(const CompressedBlobSet & rhs) = default;
    CompressedBlobSet & operator=(const CompressedBlobSet & rhs) = default;
    ~CompressedBlobSet();
    size_t size() const;
    bool empty() const { return _positions.empty(); }
    BlobSet getBlobSet() const;
private:
    CompressionConfig::Type _compression;
    BlobSet::Positions      _positions;
    std::shared_ptr<vespalib::MallocPtr> _buffer;
};

/**
 * Caches a set of objects as a set.
 * The objects are compressed together as a set.
 * The whole set is invalidated when one object of its objects are removed.
 **/
class VisitCache {
public:
    using CompressionConfig = vespalib::compression::CompressionConfig;
    VisitCache(IDataStore &store, size_t cacheSize, const CompressionConfig &compression);

    CompressedBlobSet read(const IDocumentStore::LidVector & keys) const;
    void remove(uint32_t key);
    void invalidate(uint32_t key) { remove(key); }

    CacheStats getCacheStats() const;
    void reconfigure(size_t cacheSize, const CompressionConfig &compression);
private:
    /**
     * This implments the interface the cache uses when it has a cache miss.
     * It wraps an IDataStore. Given a set of lids it will visit all objects
     * and compress them as a complete set to maximize compression rate.
     * As this is a readonly cache the write/erase methods are noops.
     */
    class BackingStore {
    public:
        BackingStore(IDataStore &store, const CompressionConfig &compression) :
            _backingStore(store),
            _compression(compression)
        { }
        bool read(const KeySet &key, CompressedBlobSet &blobs) const;
        void write(const KeySet &, const CompressedBlobSet &) { }
        void erase(const KeySet &) { }
        void reconfigure(const CompressionConfig &compression);

    private:
        IDataStore        &_backingStore;
        CompressionConfig  _compression;
    };

    using CacheParams = vespalib::CacheParam<
                            vespalib::LruParam<KeySet, CompressedBlobSet>,
                            BackingStore,
                            vespalib::zero<KeySet>,
                            vespalib::size<CompressedBlobSet>
                        >;

    /**
     * This extends the default thread safe cache implementation so that
     * it will correctly invalidate the cached sets when objects are removed/updated.
     * It will also detect the addition of new objects to any of the sets upon first
     * usage of the set and then invalidate and perform fresh visit of the backing store.
     */
    class Cache : public vespalib::cache<CacheParams> {
    public:
        Cache(BackingStore & b, size_t maxBytes);
        ~Cache();
        CompressedBlobSet readSet(const KeySet & keys);
        void removeKey(uint32_t key);
    private:
        void locateAndInvalidateOtherSubsets(const vespalib::LockGuard & cacheGuard, const KeySet & keys);
        using IdSet = vespalib::hash_set<uint64_t>;
        using Parent = vespalib::cache<CacheParams>;
        using LidUniqueKeySetId = vespalib::hash_map<uint32_t, uint64_t>;
        using IdKeySetMap = vespalib::hash_map<uint64_t, KeySet>;
        IdSet findSetsContaining(const vespalib::LockGuard &, const KeySet & keys) const;
        void onInsert(const K & key) override;
        void onRemove(const K & key) override;
        LidUniqueKeySetId _lid2Id;
        IdKeySetMap       _id2KeySet;
    };

    BackingStore            _store;
    std::unique_ptr<Cache>  _cache;
};

}
