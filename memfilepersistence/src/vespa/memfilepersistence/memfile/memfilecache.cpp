// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/memfilepersistence/common/environment.h>
#include <vespa/memfilepersistence/mapper/memfilemapper.h>
#include <vespa/memfilepersistence/memfile/memfilecache.h>
#include <vespa/log/log.h>
#include <vespa/vespalib/util/vstringfmt.h>
#include <vespa/memfilepersistence/spi/memfilepersistenceprovidermetrics.h>

LOG_SETUP(".persistence.memfile.cache");

namespace storage {
namespace memfile {


void
MemFileCache::Entry::setInUse(bool inUse) {
    LOG(debug, "Setting in use to %d for file %s", inUse, _file.toString().c_str());
    _inUse = inUse;
}

void
MemFileCache::returnToCache(MemFileCache::Entry& entry)
{
    // Ensure file descriptor is closed before returning to cache
    entry._file.getMemFileIO().close();
    vespalib::LockGuard lock(_cacheLock);

    BucketInfo info(entry._file.getBucketInfo());
    BucketId id(entry._file.getFile().getBucketId());

    LOG(debug, "%s being returned to cache", id.toString().c_str());

    MemoryUsage newUsage = entry._file.getCacheSize();

    if (_memoryToken->getSize() == 0 || newUsage.sum() == 0) {
        entry._file.flushToDisk();
        eraseNoLock(id);
        return;
    }

    // File must be flushed before being returned to the cache.
    assert(!entry._file.slotsAltered());
    entry.setInUse(false);

    Entry* ptr = 0;
    {
        BucketIdx& bucketIdx = boost::multi_index::get<0>(_entries);
        BucketIdx::iterator it(bucketIdx.find(id));
        assert(it != bucketIdx.end());
        ptr = it->_ptr.get();

        if (entry._returnToCacheWhenFinished) {
            EntryWrapper wrp(it->_ptr, ++_lastUsedCounter, id);
            _entries.replace(it, wrp);
            _memoryUsage.add(newUsage);
            entry._cacheSize = newUsage;
        } else {
            _entries.erase(it);
        }
    }

    LOG(spam,
        "Bucket %s, ptr %p returned to cache: %s with %s. "
        "Total cache size after return: %s",
        id.toString().c_str(),
        ptr,
        info.toString().c_str(),
        newUsage.toString().c_str(),
        _memoryUsage.toString().c_str());

    evictWhileFull();
}

void
MemFileCache::done(MemFileCache::Entry& entry)
{
    LOG(spam, "Finished with file %s",
        entry._file.getFile().toString().c_str());

    try {
        entry._file.verifyConsistent();
    } catch (vespalib::Exception e) {
        LOG(debug,
            "Verification of cache entry %s failed: %s",
            entry._file.getFile().toString().c_str(),
            e.getMessage().c_str());

        entry.setInUse(false);
        throw;
    }

    assert(entry.isInUse());

    returnToCache(entry);
}

struct MemFileCache::CacheEntryGuard : public MemFilePtr::EntryGuard {
    MemFileCache& _cache;
    Environment& _env;
    MemFileCache::Entry* _entry;

    CacheEntryGuard(
            MemFileCache& cache,
            Environment& env,
            MemFileCache::Entry& entry)
        : MemFilePtr::EntryGuard(entry._file),
          _cache(cache),
          _env(env),
          _entry(&entry)
    {
    }
    virtual ~CacheEntryGuard() {
        if (_entry) {
            _cache.done(*_entry);
        }
    }

    MemFile& getFile() {
        return _entry->_file;
    }

    virtual void deleteFile() {
        LOG(debug, "Cache entry guard deleting %s", _file->toString().c_str());
        _env._memFileMapper.deleteFile(*_file, _env);
        erase();
    }

    virtual void erase() {
        LOG(debug, "Cache entry guard erasing %s from cache",
            _file->toString().c_str());
        _cache.erase(document::BucketId(_entry->_file.getFile().getBucketId()));
        _entry = 0;
    }

    virtual void move(EntryGuard& target) {
        LOG(debug, "Cache entry guard moving %s", _file->toString().c_str());
        _cache.move(*this, static_cast<CacheEntryGuard&>(target));
    }

    void moveState(CacheEntryGuard& target) {
        // Move state over to target.
        target._entry = _entry;
        target._file = _file;

        // Invalidate this.
        _entry = NULL;
        _file = NULL;
    }

    MemFile* operator->() {
        return &_entry->_file;
    }
};

MemFileCache::MemFileCache(framework::ComponentRegister& componentRegister,
                           MemFilePersistenceCacheMetrics& metrics)
    : Component(componentRegister, "memfilecache"),
      _lastUsedCounter(0),
      _allocationType(getMemoryManager().registerAllocationType(
            framework::MemoryAllocationType(
                    "memfilecache", framework::MemoryAllocationType::CACHE))),
      _memoryToken(getMemoryManager().allocate(_allocationType, 0, 0, 200)),
      _metrics(metrics),
      _bodyEvicter(_metrics.body_evictions),
      _headerEvicter(_metrics.header_evictions),
      _metaDataEvicter(_metrics.meta_evictions)
{
};

void
MemFileCache::setCacheSize(MemoryUsage cacheSize)
{
    vespalib::LockGuard lock(_cacheLock);

    _cacheLimit = cacheSize;

    _memoryToken->resize(std::min(_memoryToken->getSize(), _cacheLimit.sum()),
                         _cacheLimit.sum());

    evictWhileFull();
}

MemFilePtr
MemFileCache::get(const BucketId& id, Environment& env, Directory& dir,
                  bool createIfNotExisting)
{
    vespalib::LockGuard lock(_cacheLock);

    BucketIdx& bucketIdx = boost::multi_index::get<0>(_entries);

    BucketIdx::iterator it(bucketIdx.find(id));
    if (it == bucketIdx.end()) {
        LOG(debug,
            "Bucket %s was not in cache. Creating cache entry.",
            id.toString().c_str());

        FileSpecification file(id, dir, env.calculatePathInDir(id, dir));
        const uint64_t counter(++_lastUsedCounter);
        lock.unlock();
        // Create memfile outside lock, since this will involve disk reads
        // in the common case that there's a bucket file on the disk. The
        // content layer shall guarantee that no concurrent operations happen
        // for a single bucket, so this should be fully thread safe.
        auto entry = std::make_shared<Entry>(file, env, createIfNotExisting);

        vespalib::LockGuard reLock(_cacheLock);
        std::pair<LRUCache::iterator, bool> inserted(
                _entries.insert(EntryWrapper(entry, counter, id)));
        assert(inserted.second);
        _metrics.misses.inc();

        return MemFilePtr(MemFilePtr::EntryGuard::SP(
                                  new CacheEntryGuard(*this, env, *entry)));
    } else {
        if (it->_ptr->isInUse()) {
            LOG(error,
                "Bug! File %s, ptr %p was in use while in the file cache",
                it->_ptr->_file.toString(true).c_str(), it->_ptr.get());
            assert(false);
        }

        it->_ptr->setInUse(true);
        _memoryUsage.sub(it->_ptr->_cacheSize);
        EntryWrapper wrp(it->_ptr, ++_lastUsedCounter, id);
        _entries.replace(it, wrp);
        _metrics.hits.inc();
    }
    LOG(debug,
        "Bucket %s was already in cache. Returning cache entry with "
        "memory usage %s, new total memory usage: %s",
        id.toString().c_str(),
        it->_ptr->_cacheSize.toString().c_str(),
        _memoryUsage.toString().c_str());

    return MemFilePtr(MemFilePtr::EntryGuard::SP(
                              new CacheEntryGuard(*this, env, *it->_ptr)));
}

// TODO: can this be removed??
MemFileCache::BucketInfoMap
MemFileCache::flushDirtyEntries()
{
    vespalib::LockGuard lock(_cacheLock);
    BucketInfoMap retVal;

    uint32_t total = 0, count = 0;
    BucketIdx& bucketIdx = boost::multi_index::get<0>(_entries);
    for (BucketIdx::iterator it = bucketIdx.begin(); it != bucketIdx.end(); ++it) {
        ++total;
        if (!it->_ptr->isInUse()) {
            retVal[it->_ptr->_file.getFile().getBucketId()] =
                it->_ptr->_file.getBucketInfo();

            it->_ptr->_file.flushToDisk();
            // For now, close all files after done flushing, to avoid getting
            // too many open at the same time. Later cache may cache limited
            // amount of file handles
            it->_ptr->_file.getMemFileIO().close();

            ++count;
        }
    }
    LOG(debug, "Flushed %u of %u entries in cache. Rest are in use", count, total);

    return retVal;
}

void
MemFileCache::clear()
{
    vespalib::LockGuard lock(_cacheLock);

    uint32_t total = 0, count = 0;
    BucketIdx& bucketIdx = boost::multi_index::get<0>(_entries);
    for (BucketIdx::iterator it = bucketIdx.begin();
         it != bucketIdx.end();)
    {
        ++total;
        if (!it->_ptr->isInUse()) {
            // Any file not in use should have been flushed to disk already.
            assert(!it->_ptr->_file.slotsAltered());
            _memoryUsage.sub(it->_ptr->_cacheSize);
            it = bucketIdx.erase(it);
            ++count;
        } else {
            ++it;
        }
    }
    LOG(debug, "Flushed and cleared %u of %u entries in cache. Rest are in use",
        count, total);
}

void
MemFileCache::eraseNoLock(const document::BucketId& id)
{
    BucketIdx& bucketIdx = boost::multi_index::get<0>(_entries);
    BucketIdx::iterator iter = bucketIdx.find(id);

    assert(iter != bucketIdx.end());
    assert(iter->_ptr->isInUse());
    //assert(!iter->_ptr->_file.slotsAltered());
    LOG(debug, "Removing %s from cache", id.toString().c_str());
    bucketIdx.erase(iter);
}

void
MemFileCache::erase(const document::BucketId& id) {
    vespalib::LockGuard lock(_cacheLock);
    eraseNoLock(id);
}

void
MemFileCache::move(CacheEntryGuard& source, CacheEntryGuard& target)
{
    vespalib::LockGuard lock(_cacheLock);
    assert(target->empty());

    document::BucketId sourceId = source->getFile().getBucketId();
    document::BucketId targetId = target->getFile().getBucketId();

    LOG(debug, "Renaming file %s to %s",
        source->toString().c_str(),
        target->toString().c_str());
    source->move(target->getFile());
    source.moveState(target);

    BucketIdx& bucketIdx = boost::multi_index::get<0>(_entries);
    BucketIdx::iterator sourceIt(bucketIdx.find(sourceId));
    BucketIdx::iterator targetIt(bucketIdx.find(targetId));
    assert(sourceIt != bucketIdx.end());
    assert(targetIt != bucketIdx.end());

    EntryWrapper wrp(sourceIt->_ptr, sourceIt->_lastUsed, targetId);
    bucketIdx.erase(sourceIt);
    _entries.replace(targetIt, wrp);
}

MemFileCache::TimeIdx::iterator
MemFileCache::getLeastRecentlyUsedBucket()
{
    return boost::multi_index::get<1>(_entries).begin();

}

uint64_t
MemFileCache::size() const
{
    LOG(spam, "memory usage is now %s (total is %zu)",
        _memoryUsage.toString().c_str(), _memoryUsage.sum());
    return _memoryUsage.sum();
}

bool
MemFileCache::contains(const document::BucketId& bucketId) const
{
    vespalib::LockGuard lock(_cacheLock);
    const BucketIdx& bucketIdx = boost::multi_index::get<0>(_entries);
    return bucketIdx.find(bucketId) != bucketIdx.end();
}

MemFileCache::TimeIdx::iterator
MemFileCache::MetaDataEvictionPolicy::evict(
        MemFileCache::TimeIdx& lruIndex,
        MemFileCache::TimeIdx::iterator& it,
        MemFileCache::MemoryUsage& curUsage)
{
    LOG(debug, "Evicting entire memfile for %s from cache. %s held",
        it->_bid.toString().c_str(),
        it->_ptr->_cacheSize.toString().c_str());
    curUsage.sub(it->_ptr->_cacheSize);
    _evictionMetric.inc();
    return lruIndex.erase(it);
}

MemFileCache::TimeIdx::iterator
MemFileCache::BodyEvictionPolicy::evict(
        MemFileCache::TimeIdx& /*lruIndex*/,
        MemFileCache::TimeIdx::iterator& it,
        MemFileCache::MemoryUsage& curUsage)
{
    LOG(debug, "Removing body of %s from cache. %s held",
        it->_bid.toString().c_str(),
        it->_ptr->_cacheSize.toString().c_str());

    if (it->_ptr->_cacheSize.bodySize) {
        it->_ptr->_file.clearCache(BODY);
        curUsage.bodySize -= it->_ptr->_cacheSize.bodySize;
        it->_ptr->_cacheSize.bodySize = 0;
        _evictionMetric.inc();
    }
    return ++it;
}

MemFileCache::TimeIdx::iterator
MemFileCache::HeaderEvictionPolicy::evict(
        MemFileCache::TimeIdx& /*lruIndex*/,
        MemFileCache::TimeIdx::iterator& it,
        MemFileCache::MemoryUsage& curUsage)
{
    LOG(debug, "Removing header and body of %s from cache. %s held",
        it->_bid.toString().c_str(),
        it->_ptr->_cacheSize.toString().c_str());

    if (it->_ptr->_cacheSize.headerSize) {
        it->_ptr->_file.clearCache(HEADER);
        it->_ptr->_file.clearCache(BODY);
        curUsage.headerSize -= it->_ptr->_cacheSize.headerSize;
        curUsage.bodySize -= it->_ptr->_cacheSize.bodySize;
        it->_ptr->_cacheSize.headerSize = 0;
        it->_ptr->_cacheSize.bodySize = 0;
        _evictionMetric.inc();
    }
    return ++it;
}

template <typename EvictionPolicy>
void
MemFileCache::executeCacheEvictionPolicy(EvictionPolicy& policy)
{
    MemFileCache::TimeIdx& timeIdx = boost::multi_index::get<1>(_entries);
    for (MemFileCache::TimeIdx::iterator
             i(timeIdx.upper_bound(policy.getEvictionCursor())),
             e(timeIdx.end());
         i != e;)
    {
        if (_memoryUsage.sum() <= _cacheLimit.sum()
            || (policy.getValue(_memoryUsage)
                <= policy.getValue(_cacheLimit)))
        {
            LOG(spam, "Aborting current policy because "
                "memory usage %s is less than soft limit %s",
                _memoryUsage.toString().c_str(),
                _cacheLimit.toString().c_str());

            return;
        }

        LOG(spam, "Need to evict more data as memory usage is %zu, hard limit is %zu",
            _memoryUsage.sum(), _cacheLimit.sum());

        // If memfile is in use, skip. It will be readded with new
        // timestamp once it's done being used, which means the
        // invariant of there not being any files < the cursor holding
        // cached data of the policy's type will be maintained.
        if (i->_ptr->isInUse()) {
            LOG(spam, "Not evicting %s as it is currently active",
                i->_bid.toString().c_str());
            ++i;
            continue;
        }
        policy.setEvictionCursor(i->_lastUsed);
        i = policy.evict(timeIdx, i, _memoryUsage);
    }
}

void
MemFileCache::executeEvictionPolicies()
{
    executeCacheEvictionPolicy(_bodyEvicter);
    if (_memoryUsage.sum() <= _cacheLimit.sum()) {
        return;
    }
    executeCacheEvictionPolicy(_headerEvicter);
    if (_memoryUsage.sum() <= _cacheLimit.sum()) {
        return;
    }
    executeCacheEvictionPolicy(_metaDataEvicter);
}

void
MemFileCache::evictWhileFull()
{
    if (size() > _cacheLimit.sum()) {
        LOG(debug, "Before cache eviction, cache usage was %s"
            ", new max size is %" PRIu64,
            _memoryUsage.toString().c_str(), _cacheLimit.sum());

        executeEvictionPolicies();

        LOG(spam, "After cache eviction, memory usage is %s",
            _memoryUsage.toString().c_str());
    } else {
        LOG(spam, "Max cache size is %" PRIu64 " bytes, but cache "
            "only using %" PRIu64 " bytes, so not evicting anything",
            _cacheLimit.sum(), _memoryUsage.sum());
    }

    _metrics.files.set(_entries.size());
    _metrics.meta.set(_memoryUsage.metaSize);
    _metrics.header.set(_memoryUsage.headerSize);
    _metrics.body.set(_memoryUsage.bodySize);
}

MemFileCache::Statistics
MemFileCache::getCacheStats() const
{
    vespalib::LockGuard lock(_cacheLock);
    return Statistics(_memoryUsage, _memoryToken->getSize(), _entries.size());
}

void
MemFileCache::printCacheEntriesHtml(std::ostream& out) const
{
    vespalib::LockGuard lock(_cacheLock);
    out << "<p>Cache entries (most recently used first):</p>\n"
        << "<ol>\n";
    const MemFileCache::TimeIdx& timeIdx(boost::multi_index::get<1>(_entries));
    for (MemFileCache::TimeIdx::const_reverse_iterator
             it(timeIdx.rbegin()), e(timeIdx.rend());
         it != e; ++it)
    {
        out << "<li>";
        out << it->_bid << ": ";
        if (!it->_ptr->isInUse()) {
            out << it->_ptr->_cacheSize.toString();
        } else {
            out << "<em>(in use)</em>";
        }
        out << "</li>\n";
    }
    out << "</ol>\n";
}

} // memfile

} // storage

