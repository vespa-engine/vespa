// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idocumentstore.h"
#include <vespa/vespalib/stllike/cache.h>
#include <vespa/vespalib/util/alloc.h>

namespace search {
namespace docstore {

class KeySet {
public:
    KeySet() : _keys() { }
    KeySet(uint32_t key);
    KeySet(const IDocumentStore::LidVector &keys);
    uint32_t hash() const { return _keys.empty() ? 0 : _keys[0]; }
    bool operator==(const KeySet &rhs) const { return _keys == rhs._keys; }
    bool operator<(const KeySet &rhs) const { return _keys < rhs._keys; }
    bool contains(const KeySet &rhs) const;
    const IDocumentStore::LidVector & getKeys() const { return _keys; }
private:
    IDocumentStore::LidVector _keys;
};

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

    typedef std::vector<LidPosition> Positions;
    BlobSet();
    BlobSet(const Positions & positions, vespalib::DefaultAlloc && buffer);
    void append(uint32_t lid, vespalib::ConstBufferRef blob);
    void remove(uint32_t lid);
    const Positions & getPositions() const { return _positions; }
    vespalib::ConstBufferRef get(uint32_t lid) const;
    vespalib::ConstBufferRef getBuffer() const { return vespalib::ConstBufferRef(_buffer.c_str(), _buffer.size()); }
private:
    Positions           _positions;
    vespalib::nbostream _buffer;
};

class CompressedBlobSet {
public:
    CompressedBlobSet();
    CompressedBlobSet(const document::CompressionConfig &compression, const BlobSet & uncompressed);
    CompressedBlobSet(CompressedBlobSet && rhs);
    CompressedBlobSet & operator=(CompressedBlobSet && rhs);
    CompressedBlobSet(const CompressedBlobSet & rhs) = default;
    CompressedBlobSet & operator=(const CompressedBlobSet & rhs) = default;
    void swap(CompressedBlobSet & rhs);
    size_t size() const { return _positions.capacity() * sizeof(BlobSet::Positions::value_type) + _buffer.size(); }
    bool empty() const { return _positions.empty(); }
    BlobSet getBlobSet() const;
private:
    document::CompressionConfig::Type _compression;
    BlobSet::Positions                _positions;
    vespalib::MallocPtr               _buffer;
};

class VisitCache {
public:
    using Keys=IDocumentStore::LidVector;

    VisitCache(IDataStore &store, size_t cacheSize, const document::CompressionConfig &compression);

    CompressedBlobSet read(const Keys & keys) const;
    void remove(uint32_t key);

private:
    class BackingStore {
    public:
        BackingStore(IDataStore &store, const document::CompressionConfig &compression) :
            _backingStore(store),
            _compression(compression)
        { }
        bool read(const KeySet &key, CompressedBlobSet &blobs) const;
        void write(const KeySet &, const CompressedBlobSet &) { }
        void erase(const KeySet &) { }
        const document::CompressionConfig &getCompression(void) const { return _compression; }
    private:
        IDataStore                        &_backingStore;
        const document::CompressionConfig  _compression;
    };

    typedef vespalib::CacheParam<vespalib::LruParam<KeySet, CompressedBlobSet>,
        BackingStore,
        vespalib::zero<KeySet>,
        vespalib::size<CompressedBlobSet> > CacheParams;

    class Cache : public vespalib::cache<CacheParams> {
    public:
        Cache(BackingStore & b, size_t maxBytes);
        void removeKey(uint32_t);
    private:
        typedef vespalib::cache<CacheParams> Parent;
        typedef vespalib::hash_map<uint32_t, uint64_t> LidUniqueKeySetId;
        typedef vespalib::hash_map<uint64_t, KeySet> IdKeySetMap;
        void onInsert(const K & key) override;
        void onRemove(const K & key) override;
        LidUniqueKeySetId _lid2Id;
        IdKeySetMap       _id2KeySet;
    };

    BackingStore            _store;
    std::unique_ptr<Cache>  _cache;
};

}
}
