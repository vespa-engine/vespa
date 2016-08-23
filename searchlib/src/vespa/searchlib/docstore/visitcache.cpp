// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitcache.h"

namespace search {
namespace docstore {

KeySet::KeySet(uint32_t key) :
    _keys()
{
    _keys.push_back(key);
}

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

BlobSet::BlobSet() :
    _positions(),
    _buffer()
{ }

namespace {

size_t getTotalSize(const BlobSet::Positions & p) {
    return p.empty() ? 0 : p.back().offset() + p.back().size();
}

}

BlobSet::BlobSet(const Positions & positions, vespalib::DefaultAlloc && buffer) :
    _positions(positions),
    _buffer(std::move(buffer), getTotalSize(_positions))
{
}

void
BlobSet::append(uint32_t lid, vespalib::ConstBufferRef blob) {
    _positions.emplace_back(lid, getTotalSize(_positions), blob.size());
    _buffer.write(blob.c_str(), blob.size());
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
    _compression(document::CompressionConfig::Type::LZ4),
    _positions(),
    _buffer()
{
}

CompressedBlobSet::CompressedBlobSet(CompressedBlobSet && rhs) :
    _compression(std::move(rhs._compression)),
    _positions(std::move(rhs._positions)),
    _buffer(std::move(rhs._buffer))
{
}

CompressedBlobSet & CompressedBlobSet::operator=(CompressedBlobSet && rhs) {
    _compression = std::move(rhs._compression);
    _positions = std::move(rhs._positions);
    _buffer = std::move(rhs._buffer);
    return *this;
}

CompressedBlobSet::CompressedBlobSet(const document::CompressionConfig &compression, const BlobSet & uncompressed) :
    _compression(compression.type),
    _positions(uncompressed.getPositions()),
    _buffer()
{
    if ( ! _positions.empty() ) {
        vespalib::DataBuffer compressed;
        vespalib::ConstBufferRef org = uncompressed.getBuffer();
        _compression = document::compress(compression, org, compressed, false);
        _buffer.resize(compressed.getDataLen());
        memcpy(_buffer, compressed.getData(), compressed.getDataLen());
    }
}

void CompressedBlobSet::swap(CompressedBlobSet & rhs) {
    std::swap(_compression, rhs._compression);
    _positions.swap(rhs._positions);
    _buffer.swap(rhs._buffer);
}

BlobSet
CompressedBlobSet::getBlobSet() const
{
    vespalib::DataBuffer uncompressed;
    if ( ! _positions.empty() ) {
        document::decompress(_compression, getTotalSize(_positions), vespalib::ConstBufferRef(_buffer.c_str(), _buffer.size()), uncompressed, false);
    }
    return BlobSet(_positions, uncompressed.stealBuffer());
}

namespace {

class VisitCollector : public IBufferVisitor
{
public:
    VisitCollector() :
        _blobSet()
    { }
    void visit(uint32_t lid, vespalib::ConstBufferRef buf) override;
    const BlobSet & getBlobSet() const { return _blobSet; }
private:
    BlobSet _blobSet;
};

void
VisitCollector::visit(uint32_t lid, vespalib::ConstBufferRef buf) {
    if (buf.size() > 0) {
        _blobSet.append(lid, buf);
    }
}

}

bool
VisitCache::BackingStore::read(const KeySet &key, CompressedBlobSet &blobs) const {
    VisitCollector collector;
    _backingStore.read(key.getKeys(), collector);
    CompressedBlobSet(_compression, collector.getBlobSet()).swap(blobs);
    return ! blobs.empty();
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

