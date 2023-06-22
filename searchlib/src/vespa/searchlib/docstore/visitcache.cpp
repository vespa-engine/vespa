// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitcache.h"
#include "ibucketizer.h"
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/cache.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/stllike/cache.hpp>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search::docstore {

using vespalib::CacheStats;
using vespalib::ConstBufferRef;
using vespalib::DataBuffer;
using vespalib::alloc::Alloc;
using vespalib::alloc::MemoryAllocator;

KeySet::KeySet(uint32_t key)
    : _keys()
{
    _keys.push_back(key);
}

KeySet::KeySet(const IDocumentStore::LidVector &keys)
    : _keys(keys)
{
    std::sort(_keys.begin(), _keys.end());
}

bool
KeySet::contains(const KeySet &rhs) const {
    return std::includes(_keys.begin(), _keys.end(), rhs._keys.begin(), rhs._keys.end());
}

BlobSet::BlobSet()
    : _positions(),
      _buffer(Alloc::alloc(0, 16 * MemoryAllocator::HUGEPAGE_SIZE), 0)
{ }

BlobSet::~BlobSet() = default;

namespace {

size_t
getBufferSize(const BlobSet::Positions & p) {
    return p.empty() ? 0 : p.back().offset() + p.back().size();
}

}

BlobSet::BlobSet(Positions positions, Alloc && buffer) noexcept
    : _positions(std::move(positions)),
      _buffer(std::move(buffer), getBufferSize(_positions))
{ }

void
BlobSet::append(uint32_t lid, ConstBufferRef blob) {
    _positions.emplace_back(lid, getBufferSize(_positions), blob.size());
    _buffer.write(blob.c_str(), blob.size());
}

ConstBufferRef
BlobSet::get(uint32_t lid) const
{
    ConstBufferRef buf;
    for (LidPosition pos : _positions) {
        if (pos.lid() == lid) {
            buf = ConstBufferRef(_buffer.data() + pos.offset(), pos.size());
            break;
        }
    }
    return buf;
}

CompressedBlobSet::CompressedBlobSet() noexcept
    : _positions(),
      _buffer(),
      _used(0),
      _compression(CompressionConfig::Type::LZ4)
{ }

CompressedBlobSet::~CompressedBlobSet() = default;

CompressedBlobSet::CompressedBlobSet(CompressionConfig compression, BlobSet uncompressed)
    : _positions(uncompressed.stealPositions()),
      _buffer(),
      _used(0),
      _compression(compression.type)
{
    if ( ! _positions.empty() ) {
        DataBuffer compressed;
        ConstBufferRef org = uncompressed.getBuffer();
        _compression = vespalib::compression::compress(compression, org, compressed, false);
        _used = compressed.getDataLen();
        _buffer = std::make_shared<Alloc>(Alloc::alloc(_used));
        memcpy(_buffer->get(), compressed.getData(), _used);
    } else {
        _buffer = std::make_shared<Alloc>();
    }
}

BlobSet
CompressedBlobSet::getBlobSet() const
{
    using vespalib::compression::decompress;
    // These are frequent lage allocations that are to expensive to mmap.
    DataBuffer uncompressed(0, 1, Alloc::alloc(0, 16 * MemoryAllocator::HUGEPAGE_SIZE));
    if ( ! _positions.empty() ) {
        decompress(_compression, getBufferSize(_positions),
                   ConstBufferRef(_buffer->get(), _used), uncompressed, false);
    }
    return BlobSet(_positions, std::move(uncompressed).stealBuffer());
}

size_t
CompressedBlobSet::bytesAllocated() const {
    return _positions.capacity() * sizeof(BlobSet::Positions::value_type) + _buffer->size();
}

namespace {

class VisitCollector : public IBufferVisitor
{
public:
    VisitCollector(BlobSet & blobSet) : _blobSet(blobSet) { }
    void visit(uint32_t lid, ConstBufferRef buf) override;
private:
    BlobSet & _blobSet;
};

void
VisitCollector::visit(uint32_t lid, ConstBufferRef buf) {
    if (buf.size() > 0) {
        _blobSet.append(lid, buf);
    }
}

struct ByteSize {
    size_t operator() (const CompressedBlobSet & arg) const noexcept { return arg.bytesAllocated(); }
};

}

using CacheParams = vespalib::CacheParam<
        vespalib::LruParam<KeySet, CompressedBlobSet>,
        VisitCache::BackingStore,
        vespalib::zero<KeySet>,
        ByteSize
>;

/**
 * This extends the default thread safe cache implementation so that
 * it will correctly invalidate the cached sets when objects are removed/updated.
 * It will also detect the addition of new objects to any of the sets upon first
 * usage of the set and then invalidate and perform fresh visit of the backing store.
 */
class VisitCache::Cache : public vespalib::cache<CacheParams> {
public:
    Cache(BackingStore & b, size_t maxBytes);
    ~Cache() override;
    CompressedBlobSet readSet(const KeySet & keys);
    void removeKey(uint32_t key);
    vespalib::MemoryUsage getStaticMemoryUsage() const override;
    CacheStats get_stats() const override;
private:
    void locateAndInvalidateOtherSubsets(const UniqueLock & cacheGuard, const KeySet & keys);
    using IdSet = vespalib::hash_set<uint64_t>;
    using Parent = vespalib::cache<CacheParams>;
    using LidUniqueKeySetId = vespalib::hash_map<uint32_t, uint64_t>;
    using IdKeySetMap = vespalib::hash_map<uint64_t, KeySet>;
    IdSet findSetsContaining(const UniqueLock &, const KeySet & keys) const;
    void onInsert(const K & key) override;
    void onRemove(const K & key) override;
    LidUniqueKeySetId _lid2Id;
    IdKeySetMap       _id2KeySet;
};

bool
VisitCache::BackingStore::read(const KeySet &key, CompressedBlobSet &blobs) const {
    BlobSet blobSet;
    blobSet.reserve(key.getKeys().size());
    VisitCollector collector(blobSet);
    _backingStore.read(key.getKeys(), collector);
    blobs = CompressedBlobSet(_compression.load(std::memory_order_relaxed), std::move(blobSet));
    return ! blobs.empty();
}

void
VisitCache::BackingStore::reconfigure(CompressionConfig compression) {
    _compression.store(compression, std::memory_order_relaxed);
}


VisitCache::VisitCache(IDataStore &store, size_t cacheSize, CompressionConfig compression) :
    _store(store, compression),
    _cache(std::make_unique<Cache>(_store, cacheSize))
{ }

VisitCache::~VisitCache() = default;

void
VisitCache::reconfigure(size_t cacheSize, CompressionConfig compression) {
    _store.reconfigure(compression);
    _cache->setCapacityBytes(cacheSize);
}

vespalib::MemoryUsage
VisitCache::getStaticMemoryUsage() const {
    return _cache->getStaticMemoryUsage();
}

VisitCache::Cache::IdSet
VisitCache::Cache::findSetsContaining(const UniqueLock &, const KeySet & keys) const {
    IdSet found;
    for (uint32_t subKey : keys.getKeys()) {
        const auto foundLid = _lid2Id.find(subKey);
        if (foundLid != _lid2Id.end()) {
            found.insert(foundLid->second);
        }
    }
    return found;
}

CompressedBlobSet
VisitCache::Cache::readSet(const KeySet & key)
{
    if (!key.empty()) {
        {
            auto cacheGuard = getGuard();
            if (!hasKey(cacheGuard, key)) {
                locateAndInvalidateOtherSubsets(cacheGuard, key);
            }
        }
        return read(key);
    }
    return CompressedBlobSet();
}

void
VisitCache::Cache::locateAndInvalidateOtherSubsets(const UniqueLock & cacheGuard, const KeySet & keys)
{
    // Due to the implementation of insert where the global lock is released and the fact
    // that 2 overlapping keysets kan have different keys and use different ValueLock
    // We do have a theoretical issue.
    // The reason it is theoretical is that for all practical purpose this inconsitency
    // is prevented by the storage layer above alloing only one visit/mutating operation to a single bucket.
    // So for that reason we will just merge this one to get testing started. 
    // The final fix will come in 2 days.
    IdSet otherSubSets = findSetsContaining(cacheGuard, keys);
    for (uint64_t keyId : otherSubSets) {
        invalidate(cacheGuard, _id2KeySet[keyId]);
    }
}

CompressedBlobSet
VisitCache::read(const IDocumentStore::LidVector & lids) const {
    return _cache->readSet(KeySet(lids));
}

void
VisitCache::remove(uint32_t key) {
    _cache->removeKey(key);
}

CacheStats
VisitCache::getCacheStats() const {
    CacheStats stats = _cache->get_stats();
    return stats;
}

VisitCache::Cache::Cache(BackingStore & b, size_t maxBytes) :
    Parent(b, maxBytes)
{ }

VisitCache::Cache::~Cache() = default;

void
VisitCache::Cache::removeKey(uint32_t subKey) {
    // Need to take hashLock
    auto cacheGuard = getGuard();
    const auto foundLid = _lid2Id.find(subKey);
    if (foundLid != _lid2Id.end()) {
        invalidate(cacheGuard, _id2KeySet[foundLid->second]);
    }
}

void
VisitCache::Cache::onInsert(const K & key) {
    uint32_t first(key.getKeys().front());
    _id2KeySet[first] = key;
    for(uint32_t subKey : key.getKeys()) {
        _lid2Id[subKey] = first;
    }
}

void
VisitCache::Cache::onRemove(const K & key) {
    for (uint32_t subKey : key.getKeys()) {
        _lid2Id.erase(subKey);
    }
    _id2KeySet.erase(key.getKeys().front());
}

vespalib::MemoryUsage
VisitCache::Cache::getStaticMemoryUsage() const {
    vespalib::MemoryUsage usage = Parent::getStaticMemoryUsage();
    size_t baseSelf = sizeof(_lid2Id) + sizeof(_id2KeySet);
    usage.incAllocatedBytes(baseSelf);
    usage.incUsedBytes(baseSelf);
    return usage;
}

CacheStats
VisitCache::Cache::get_stats() const {
    CacheStats stats = Parent::get_stats();
    auto cacheGuard = getGuard();
    stats.memory_used += _lid2Id.capacity() * sizeof(LidUniqueKeySetId::value_type);
    stats.memory_used += _id2KeySet.capacity() * sizeof(IdKeySetMap::value_type);
    for (const auto & entry: _id2KeySet) {
        stats.memory_used = entry.second.getKeys().capacity() * sizeof(uint32_t);
    }
    return stats;
}

}
