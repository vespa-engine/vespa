// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "lrucache_map.h"
#include <vespa/vespalib/util/memoryusage.h>
#include <atomic>
#include <mutex>

namespace vespalib {

struct CacheStats;

template<typename K, typename V>
class NullStore {
public:
    bool read(const K &, V &) const { return false; }
    void write(const K &, const V &) { }
    void erase(const K &) { }
};

/**
 * These are the parameters needed for setting up the cache.
 * @param P is the set of parameters needed for setting up the underlying lrucache. See @ref LruParam
 * @param B is the backing store. That is where the real data is backed up if there are any.
 *          If there are no backing store or you mix and match yourself, you can give it the @ref NullStore.
 * @param SizeK is the method to get the space needed by the key in addition to what you get with sizeof.
 * @param SizeV is the method to get the space needed by the value in addition to what you get with sizeof.
 */
template<typename P, typename B, typename sizeK = vespalib::zero<typename P::Key>, typename sizeV = vespalib::zero<typename P::Value> >
struct CacheParam : public P
{
    using BackingStore = B;
    using SizeK = sizeK;
    using SizeV = sizeV;
};

/**
 * This is a cache using the underlying lru implementation as the store. It is modelled as a pure cache
 * with an backing store underneath it. That backing store is given to the constructor and must of course have
 * proper lifetime. The store must implement the same 3 methods as the @ref NullStore above.
 * Stuff is evicted from the cache if either number of elements or the accounted size passes the limits given.
 * The cache is thread safe by a single lock for accessing the underlying Lru. In addition a striped locking with
 * 64 locks chosen by the hash of the key to enable a single fetch for any element required by multiple readers.
 */
template< typename P >
class cache : private lrucache_map<P>
{
    using Lru = lrucache_map<P>;
protected:
    using BackingStore = typename P::BackingStore;
    using Hash = typename P::Hash;
    using K = typename P::Key;
    using V = typename P::Value;
    using SizeK = typename P::SizeK;
    using SizeV = typename P::SizeV;
    using value_type = typename P::value_type;
public:
    /**
     * Will create a cache that populates on demand from the backing store.
     * The cache uses LRU and evicts whne its size in bytes or elements is reached.
     * By max elements is initialized to max bytes.
     *
     * @param backingStore is the store for populating the cache on a cache miss.
     * @maxBytes is the maximum limit of bytes the store can hold, before eviction starts.
     */
    cache(BackingStore & b, size_t maxBytes);
    ~cache() override;
    /**
     * Can be used for controlling max number of elements.
     */
    cache & maxElements(size_t elems);

    cache & setCapacityBytes(size_t sz);

    size_t capacity()                  const { return Lru::capacity(); }
    size_t capacityBytes()             const { return _maxBytes.load(std::memory_order_relaxed); }
    size_t size()                      const { return Lru::size(); }
    size_t sizeBytes()                 const { return _sizeBytes.load(std::memory_order_relaxed); }
    bool empty()                       const { return Lru::empty(); }

    virtual MemoryUsage getStaticMemoryUsage() const;

    /**
     * This simply erases the object.
     * This will also erase from backing store.
     */
    void erase(const K & key);
    /**
     * This simply erases the object from the cache.
     */
    void invalidate(const K & key);

    /**
     * Return the object with the given key. If it does not exist, the backing store will be consulted.
     * and the cache will be updated.
     * If none exist an empty one will be created.
     * Object is then put at head of LRU list.
     */
    V read(const K & key);

    /**
     * Update the cache and write through to backing store.
     * Object is then put at head of LRU list.
     */
    void write(const K & key, V value);

    /**
     * Tell if an object with given key exists in the cache.
     * Does not alter the LRU list.
     */
    bool hasKey(const K & key) const;

    virtual CacheStats get_stats() const;

    size_t          getHit() const { return _hit.load(std::memory_order_relaxed); }
    size_t         getMiss() const { return _miss.load(std::memory_order_relaxed); }
    size_t getNoneExisting() const { return _noneExisting.load(std::memory_order_relaxed); }
    size_t         getRace() const { return _race.load(std::memory_order_relaxed); }
    size_t       getInsert() const { return _insert.load(std::memory_order_relaxed); }
    size_t        getWrite() const { return _write.load(std::memory_order_relaxed); }
    size_t   getInvalidate() const { return _invalidate.load(std::memory_order_relaxed); }
    size_t       getlookup() const { return _lookup.load(std::memory_order_relaxed); }

protected:
    using UniqueLock = std::unique_lock<std::mutex>;
    UniqueLock getGuard() const;
    void invalidate(const UniqueLock & guard, const K & key);
    bool hasKey(const UniqueLock & guard, const K & key) const;
private:
    void verifyHashLock(const UniqueLock & guard) const;
    /**
     * Called when an object is inserted, to see if the LRU should be removed.
     * Default is to obey the maxsize given in constructor.
     * The obvious extension is when you are storing pointers and want to cap
     * on the real size of the object pointed to.
     */
    bool removeOldest(const value_type & v) override;
    size_t calcSize(const K & k, const V & v) const { return sizeof(value_type) + _sizeK(k) + _sizeV(v); }
    std::mutex & getLock(const K & k) {
        size_t h(_hasher(k));
        return _addLocks[h%(sizeof(_addLocks)/sizeof(_addLocks[0]))];
    }

    template <typename V>
    static void increment_stat(std::atomic<V>& v, const std::lock_guard<std::mutex>&) {
        v.store(v.load(std::memory_order_relaxed) + 1, std::memory_order_relaxed);
    }
    template <typename V>
    static void increment_stat(std::atomic<V>& v, const std::unique_lock<std::mutex>&) {
        v.store(v.load(std::memory_order_relaxed) + 1, std::memory_order_relaxed);
    }

    Hash                        _hasher;
    SizeK                       _sizeK;
    SizeV                       _sizeV;
    std::atomic<size_t>         _maxBytes;
    std::atomic<size_t>         _sizeBytes;
    mutable std::atomic<size_t> _hit;
    mutable std::atomic<size_t> _miss;
    std::atomic<size_t>         _noneExisting;
    mutable std::atomic<size_t> _race;
    mutable std::atomic<size_t> _insert;
    mutable std::atomic<size_t> _write;
    mutable std::atomic<size_t> _update;
    mutable std::atomic<size_t> _invalidate;
    mutable std::atomic<size_t> _lookup;
    BackingStore              & _store;
    mutable std::mutex          _hashLock;
    /// Striped locks that can be used for having a locked access to the backing store.
    std::mutex                  _addLocks[113];
};

}
