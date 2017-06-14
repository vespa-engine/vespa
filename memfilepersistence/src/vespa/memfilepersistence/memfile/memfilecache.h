// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::slotfile::MemFileCache
 * \ingroup memfile
 *
 * \brief Cache holding onto all mem file objects in memory.
 *
 * This is the global memory file cache keeping track of all the memory files
 * in memory.
 */

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/memfilepersistence/common/types.h>
#include <vespa/memfilepersistence/memfile/memfile.h>
#include <vespa/memfilepersistence/memfile/memfileptr.h>
#include <boost/multi_index_container.hpp>
#include <boost/multi_index/identity.hpp>
#include <boost/multi_index/member.hpp>
#include <boost/multi_index/mem_fun.hpp>
#include <boost/multi_index/ordered_index.hpp>
#include <boost/multi_index/sequenced_index.hpp>
#include <vespa/storageframework/generic/memory/memorymanagerinterface.h>
#include <vespa/storageframework/generic/component/component.h>


namespace storage::memfile {

class MemFilePersistenceCacheMetrics;
class Environment; // Avoid cyclic dependency with environment

class MemFileCache : private framework::Component,
                     private Types
{
public:
    typedef MemSlot::MemoryUsage MemoryUsage;

    struct Statistics
    {
        MemoryUsage _memoryUsage;
        size_t _cacheSize;
        size_t _numEntries;

        Statistics(const MemoryUsage& memoryUsage,
                   size_t cacheSize,
                   size_t numEntries)
            : _memoryUsage(memoryUsage),
              _cacheSize(cacheSize),
              _numEntries(numEntries)
        {}
    };
private:
    class Entry {
    public:
        using SP = std::shared_ptr<Entry>;

        MemFile _file;
        MemoryUsage _cacheSize;
        Environment& _env;
        bool _inUse;
        bool _returnToCacheWhenFinished;

        Entry(const Entry &) = delete;
        Entry & operator = (const Entry &) = delete;
        Entry(FileSpecification& file, Environment& env,
              bool returnToCacheWhenFinished = true)
            : _file(file, env), _env(env), _inUse(true),
              _returnToCacheWhenFinished(returnToCacheWhenFinished)
        {}

        bool isInUse() const {
            return _inUse;
        }

        void setInUse(bool inUse);
    };

    struct EntryWrapper {
        EntryWrapper(
                Entry::SP ptr,
                uint64_t lastUsed,
                const document::BucketId& bid)
            : _ptr(std::move(ptr)), _lastUsed(lastUsed), _bid(bid) {}

        const Entry* operator->() const {
            return _ptr.get();
        };

        Entry* operator->() {
            return _ptr.get();
        };

        Entry::SP _ptr;
        uint64_t _lastUsed;
        document::BucketId _bid;
    };

    struct CacheEntryGuard;

    vespalib::Lock _cacheLock;

    typedef boost::multi_index::ordered_unique<
        boost::multi_index::member<EntryWrapper, BucketId, &EntryWrapper::_bid>
        > BucketIdOrder;

    typedef boost::multi_index::ordered_non_unique<
        boost::multi_index::member<EntryWrapper, uint64_t, &EntryWrapper::_lastUsed>
        > TimeOrder;

    typedef boost::multi_index::multi_index_container<
        EntryWrapper,
        boost::multi_index::indexed_by<
            BucketIdOrder,
            TimeOrder
        >
    > LRUCache;

    typedef boost::multi_index::nth_index<LRUCache, 0>::type BucketIdx;
    typedef boost::multi_index::nth_index<LRUCache, 1>::type TimeIdx;

    class CacheEvictionPolicy
    {
        uint64_t _evictionCursor;
    protected:
        metrics::LongCountMetric& _evictionMetric;
    public:
    CacheEvictionPolicy(metrics::LongCountMetric& evictionMetric)
        : _evictionCursor(0),
          _evictionMetric(evictionMetric)
    {}

        uint64_t getEvictionCursor() const {
            return _evictionCursor;
        }
        void setEvictionCursor(uint64_t cursor) {
            _evictionCursor = cursor;
        }
    };

    class MetaDataEvictionPolicy : public CacheEvictionPolicy
    {
    public:
        MetaDataEvictionPolicy(metrics::LongCountMetric& evictionMetric)
            : CacheEvictionPolicy(evictionMetric) {}

        TimeIdx::iterator evict(
                TimeIdx& lruIndex,
                TimeIdx::iterator& it,
                MemoryUsage& curUsage);

        uint64_t getValue(const MemoryUsage& usage) const {
            return usage.sum();
        }
    };

    class BodyEvictionPolicy : public CacheEvictionPolicy
    {
    public:
        BodyEvictionPolicy(metrics::LongCountMetric& evictionMetric)
            : CacheEvictionPolicy(evictionMetric) {}

        TimeIdx::iterator evict(
                TimeIdx& lruIndex,
                TimeIdx::iterator& it,
                MemoryUsage& curUsage);

        uint64_t getValue(const MemoryUsage& usage) const {
            return usage.bodySize;
        }
    };

    class HeaderEvictionPolicy : public CacheEvictionPolicy
    {
    public:
        HeaderEvictionPolicy(metrics::LongCountMetric& evictionMetric)
            : CacheEvictionPolicy(evictionMetric) {}

        TimeIdx::iterator evict(
                TimeIdx& lruIndex,
                TimeIdx::iterator& it,
                MemoryUsage& curUsage);

        uint64_t getValue(const MemoryUsage& usage) const {
            return usage.headerSize + usage.bodySize;
        }
    };


    MemoryUsage _memoryUsage;

    LRUCache _entries;
    uint64_t _lastUsedCounter;
    const framework::MemoryAllocationType& _allocationType;
    framework::MemoryToken::UP _memoryToken;

    MemFilePersistenceCacheMetrics& _metrics;

    BodyEvictionPolicy _bodyEvicter;
    HeaderEvictionPolicy _headerEvicter;
    MetaDataEvictionPolicy _metaDataEvicter;

    void done(Entry&);
    void move(CacheEntryGuard& source, CacheEntryGuard& target);
    void evictWhileFull();
    void executeEvictionPolicies();
    void returnToCache(MemFileCache::Entry& entry);

    TimeIdx::iterator getLeastRecentlyUsedBucket();

    /**
     * @return Returns the current size of the cache.
     */
    uint64_t size() const;

    void eraseNoLock(const document::BucketId& id);

    friend class CacheEntryGuard;
    friend class MemCacheTest;

    template <typename EvictionPolicy>
    void
    executeCacheEvictionPolicy(EvictionPolicy& policy);

    MemoryUsage _cacheLimit;

public:
    typedef std::unique_ptr<MemFileCache> UP;

    MemFileCache(framework::ComponentRegister& componentRegister,
                 MemFilePersistenceCacheMetrics& metrics);

    /**
     * Get a memfile for the given bucket on the given disk.
     * @param env Needed for cache to be able to create non-existing entries.
     * @param dir If not given, use the default directory from the environment.
     * @param createIfNotInCache If false, the bucket won't be inserted into the
     * cache after, unless it was already cached before this operation.
     */
    MemFilePtr get(const BucketId&,
                   Environment& env,
                   Directory& dir,
                   bool createIfNotInCache = true);

    /**
     * Removes the given bucket id from cache. Bucket must be in use,
     * so erase() will as a consequence not subtract the bucket's cache
     * usage from the total cache usage as that has already been done
     * upon retrieving the bucket in the first place.
     */
    void erase(const document::BucketId& id);

    typedef std::map<document::BucketId, BucketInfo> BucketInfoMap;

    /**
     * This function exists just temporarily for memfile layer to flush all
     * dirty entries found after each operation. This will be removed in favor
     * of another mechanism later.
     */
    BucketInfoMap flushDirtyEntries();

    /**
     * Clears the cache of all non-active entries (flushing dirty entries
     * as necessary).
     */
    void clear();

    /**
     * @return Returns true if the given bucket exists in the cache.
     */
    bool contains(const document::BucketId& bucketId) const;

    /**
     * Used for unit testing only.
     */
    framework::MemoryToken& getMemoryToken() { return *_memoryToken; }
    const MemFilePersistenceCacheMetrics& getMetrics() const {
        return _metrics;
    }

    /**
     * Set maximum cache size.
     */
    void setCacheSize(MemoryUsage limits);

    uint64_t getCacheSize() { return _memoryToken->getSize(); }

    /**
     * NOTE: takes lock, never call from within memfilecache code.
     * @return Statistics over cache memory usage and entry counts
     */
    Statistics getCacheStats() const;

    /**
     * Dump all cache entries as a most recently used-ordered list.
     * Used for verbose status page printing.
     */
    void printCacheEntriesHtml(std::ostream& out) const;
};

}
