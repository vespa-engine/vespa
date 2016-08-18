// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitcache.h"

namespace search {
namespace docstore {

KeySet::KeySet(const IDocumentStore::LidVector &keys) :
    _keys(keys)
{
    std::sort(_keys.begin(), _keys.end());
}

bool
KeySet::contains(const KeySet &rhs) const {
    if (rhs._keys.size() > _keys.size()) { return false; }

    uint32_t b(0);
    for (uint32_t a(0); a < _keys.size() && b < rhs._keys.size();) {
        if (_keys[a] < rhs._keys[b]) {
            a++;
        } else if (_keys[a] == rhs._keys[b]) {
            a++;
            b++;
        } else {
            return false;
        }
    }
    return b == rhs._keys.size();
}

vespalib::ConstBufferRef
BlobSet::get(uint32_t lid) const
{
    vespalib::ConstBufferRef buf;
    for (LidPosition pos : _positions) {
        if (pos.lid() == lid) {
            buf = vespalib::ConstBufferRef(_buffer.c_str() + pos.offset(), pos.size());
            break;
        }
    }
    return buf;
}

CompressedBlobSet::CompressedBlobSet() :
    _positions(),
    _buffer()
{
}

CompressedBlobSet::CompressedBlobSet(CompressedBlobSet && rhs) :
    _positions(std::move(rhs._positions)),
    _buffer(std::move(rhs._buffer))
{
}

CompressedBlobSet & CompressedBlobSet::operator=(CompressedBlobSet && rhs) {
    _positions = std::move(rhs._positions);
    _buffer = std::move(rhs._buffer);
    return *this;
}

BlobSet
CompressedBlobSet::getBlobSet() const
{
    BlobSet blobSet;
    return blobSet;
}

bool
VisitCache::BackingStore::read(const KeySet &key, CompressedBlobSet &blobs) const {
    (void) key;
    (void) blobs;
    bool retval(false);
    return retval;
}

VisitCache::VisitCache(IDataStore &store, size_t cacheSize, const document::CompressionConfig &compression) :
    _store(store, compression),
    _cache(std::make_unique<Cache>(_store, cacheSize))
{
}

CompressedBlobSet
VisitCache::read(const Keys & keys) const {
    return _cache->read(keys);
}

void
VisitCache::remove(uint32_t key) {
    // shall modify the cached element
    (void) key;
}

}
}

