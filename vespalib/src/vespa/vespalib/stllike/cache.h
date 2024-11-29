// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "lrucache_map.h"
#include <vespa/vespalib/util/memoryusage.h>
#include <atomic>
#include <mutex>
#include <vector>

namespace vespalib {

struct CacheStats;

template <typename K, typename V>
class NullStore {
public:
    bool read(const K&, V&) const { return false; }
    void write(const K&, const V&) { }
    void erase(const K&) { }
};

/**
 * These are the parameters needed for setting up the cache.
 * @param P is the set of parameters needed for setting up the underlying lrucache. See @ref LruParam
 * @param B is the backing store. That is where the real data is backed up if there are any.
 *          If there is no backing store, or you mix and match yourself, you can give it the @ref NullStore.
 * @param SizeK is the method to get the space needed by the key in addition to what you get with sizeof.
 * @param SizeV is the method to get the space needed by the value in addition to what you get with sizeof.
 */
template <
    typename P,
    typename B,
    typename sizeK = zero<typename P::Key>,
    typename sizeV = zero<typename P::Value>
>
struct CacheParam : P {
    using BackingStore = B;
    using SizeK = sizeK;
    using SizeV = sizeV;
};

enum class CacheSegment {
    Probationary,
    Protected
};

/**
 * This is a cache using the underlying LRU implementation as the store. It is modelled as a
 * pure cache with a backing store underneath it. That backing store is given to the constructor
 * and must of course have proper lifetime. The store must implement the same 3 methods as the
 * @ref NullStore above.
 *
 * Stuff is evicted from the cache if either number of elements or the accounted size passes the
 * limits given. The cache is thread safe by a single lock for accessing the underlying LRU.
 * In addition, a striped locking with 113 locks chosen by the hash of the key to enable a single
 * fetch for any element required by multiple readers.
 *
 * The cache can optionally be constructed (or configured) with a secondary cache capacity; this
 * automatically enables Segmented LRU (SLRU) semantics. When SLRU is enabled, cached elements
 * are present in exactly one out of two segments; probationary or protected.
 *
 * The probationary segment is where all newly cached entries are placed. Entries evicted from
 * this segment are lost to the sands of time. If a probationary element is explicitly fetched
 * it is moved to the protected segment. This may cause the capacity of the protected segment
 * to be exceeded, causing one or more protected entries to be evicted. Entries evicted from the
 * protected segment are reinserted into the probationary segment, giving them a second chance.
 * This reinsertion may in turn evict entries from the probationary segment.
 *
 * Promotion from probationary to protected can be seen as an analogue of a GC algorithm with
 * automatic aging (young gen vs old gen).
 *
 * SLRU involves juggling entries between two separate LRUs, which has higher overhead than a
 * simple single LRU. This means that cache performance will likely _decrease_ slightly if the
 * cache is already large enough to fit all data (in which case eviction policies do not matter
 * because they will not be used in practice). The opposite is expected to be the case if the
 * cache is substantially smaller than the size of all cacheable objects.
 *
 * Note that the regular non-SLRU cache is implemented to reside entirely within the probationary
 * segment.
 */
template <typename P>
class cache {
    using Lru = lrucache_map<P>;
    friend class SizeConstrainedLru;
    friend class ProbationarySegmentLru;

    // Shared parent LRU type used by both probationary and protected segments. In particular,
    // it overrides the `removeOldest` LRU callback function to provide segment-specific
    // capacity limits. Since the entry size calculation may be stateful, there's a slightly
    // awkward back-reference to the owning cache object that holds the calculator.
    class SizeConstrainedLru : protected Lru {
    protected:
        cache&              _owner;
        std::atomic<size_t> _size_bytes;
        std::atomic<size_t> _capacity_bytes;
    public:
        using KeyT   = typename P::Key;
        using ValueT = typename P::Value; // Not to be confused with P::value_type, which is a std::pair

        SizeConstrainedLru(cache& owner, size_t capacity_bytes);
        ~SizeConstrainedLru() override;

        // Callback from `lrucache_map` invoked when an element `kv` is a candidate for eviction.
        bool removeOldest(const typename P::value_type& kv) override;

        [[nodiscard]] bool has_key(const KeyT& key) const noexcept;
        // Precondition: key does not already exist in the mapping
        void insert_and_update_size(const KeyT& key, ValueT value);
        // Fetches an existing key from the cache _without_ updating the LRU ordering.
        [[nodiscard]] const typename P::Value& get_existing(const KeyT& key) const;

        // Returns true iff `key` existed in the mapping prior to the call, which also
        // implies the mapping has been updated by consuming `value` (i.e. its contents
        // has been std::move()'d away and it is now in a logically empty state).
        // Otherwise, both the mapping and `value` remains unmodified and false is returned.
        [[nodiscard]] bool try_replace_and_update_size(const KeyT& key, ValueT& value);
        // Iff `key` was present in the mapping prior to the call, its entry is removed
        // from the mapping and true is returned. Otherwise, the mapping remains unmodified
        // and false is returned.
        [[nodiscard]] bool try_erase_and_update_size(const KeyT& key);
        // Iff element exists in cache, assigns `val_out` the stored value and returns true.
        // This also updates the underlying LRU order.
        // Otherwise, `val_out` is not modified and false is returned.
        [[nodiscard]] bool try_get_and_ref(const KeyT& key, ValueT& val_out);

        // Clears the entire cache segment. removeOldest() is not invoked for any entries.
        // _size_bytes will be reset to zero after invocation.
        void evict_all();

        [[nodiscard]] std::vector<KeyT> dump_segment_keys_in_lru_order();

        using Lru::empty;
        using Lru::size;
        using Lru::capacity;

        void set_max_elements(size_t max_elems) { Lru::maxElements(max_elems); }
        void set_capacity_bytes(size_t capacity_bytes) noexcept { _capacity_bytes = capacity_bytes; }

        [[nodiscard]] size_t size_bytes() const noexcept {
            return _size_bytes.load(std::memory_order_relaxed);
        }
        [[nodiscard]] size_t capacity_bytes() const noexcept {
            return _capacity_bytes.load(std::memory_order_relaxed);
        }
    private:
        void set_size_bytes(size_t new_sz) noexcept {
            _size_bytes.store(new_sz, std::memory_order_relaxed);
        }
        void add_size_bytes(size_t pos_delta) noexcept {
            set_size_bytes(size_bytes() + pos_delta);
        }
        void sub_size_bytes(size_t neg_delta) noexcept {
            set_size_bytes(size_bytes() - neg_delta);
        }
    };

    class ProbationarySegmentLru final : public SizeConstrainedLru {
    public:
        using KeyT = typename SizeConstrainedLru::KeyT;

        ProbationarySegmentLru(cache& owner, size_t capacity_bytes);
        ~ProbationarySegmentLru() override;

        // Elements are always inserted into the probationary segment first, and
        // removed from the probationary segment last. Forward final removal events
        // to the owner.
        bool removeOldest(const typename P::value_type& kv) override;
    };

    class ProtectedSegmentLru final : public SizeConstrainedLru {
    public:
        ProtectedSegmentLru(cache& owner, size_t capacity_bytes);
        ~ProtectedSegmentLru() override;
        // Reinserts element evicted from protected segment back into probationary segment.
        bool removeOldest(const typename P::value_type& kv) override;
    };

protected:
    using BackingStore = typename P::BackingStore;
    using Hash         = typename P::Hash;
    using K            = typename P::Key;
    using V            = typename P::Value;
    using SizeK        = typename P::SizeK;
    using SizeV        = typename P::SizeV;
    using value_type   = typename P::value_type;
public:
    using key_type     = K;

    cache(BackingStore& backing_store, size_t max_probationary_bytes, size_t max_protected_bytes);

    /**
     * Will create a cache that populates on demand from the backing store.
     * The cache uses LRU and evicts when its size in bytes or elements is reached.
     * By max elements is initialized to max bytes.
     *
     * @param backing_store is the store for populating the cache on a cache miss.
     * @param max_bytes is the maximum limit of bytes the store can hold, before eviction starts.
     */
    cache(BackingStore& backing_store, size_t max_bytes);
    virtual ~cache();
    /**
     * Can be used for controlling max number of elements.
     *
     * Note that explicitly (or implicitly) configuring the protected segment to zero
     * entries triggers a full eviction of all entries cached within it.
     */
    cache& maxElements(size_t elems);
    cache& maxElements(size_t probationary_elems, size_t protected_elems);

    /**
     * Sets a soft max capacity of bytes used by the underlying LRU cache.
     * If used when in SLRU mode, the protected segment is cleared and the cache
     * enters single LRU mode.
     */
    cache& setCapacityBytes(size_t sz);
    /**
     * Sets a soft max capacity of bytes used by the underlying LRU cache segments
     * individually. A `protected_sz` of 0 implicitly disables SLRU mode, any higher
     * values enable it.
     */
    cache& setCapacityBytes(size_t probationary_sz, size_t protected_sz);

    // Thread safe
    [[nodiscard]] size_t capacity() const noexcept {
        return _probationary_segment.capacity() + _protected_segment.capacity();
    }
    // Thread safe
    [[nodiscard]] size_t capacityBytes() const noexcept {
        return _probationary_segment.capacity_bytes() + _protected_segment.capacity_bytes();
    }
    // Thread safe
    [[nodiscard]] size_t size() const noexcept {
        return _probationary_segment.size() + _protected_segment.size();
    }
    // Thread safe
    [[nodiscard]] size_t sizeBytes() const noexcept {
        return _probationary_segment.size_bytes() + _protected_segment.size_bytes();
    }
    // _Not_ thread safe
    [[nodiscard]] bool empty() const noexcept {
        return _probationary_segment.empty() && _protected_segment.empty();
    }

    [[nodiscard]] size_t segment_size(CacheSegment seg) const noexcept;
    [[nodiscard]] size_t segment_size_bytes(CacheSegment seg) const noexcept;
    [[nodiscard]] size_t segment_capacity(CacheSegment seg) const noexcept;
    [[nodiscard]] size_t segment_capacity_bytes(CacheSegment seg) const noexcept;

    [[nodiscard]] virtual MemoryUsage getStaticMemoryUsage() const;

    /**
     * Listeners for insertion and removal events that may be overridden by a subclass.
     * Important: implementations should never directly or indirectly modify the cache
     * from a listener, or they risk triggering an "uncontrolled" recursion chain.
     */
    virtual void onRemove(const K&) {}
    virtual void onInsert(const K&) {}

    /**
     * This simply erases the object.
     * This will also erase from backing store.
     */
    void erase(const K& key);
    /**
     * This simply erases the object from the cache.
     */
    void invalidate(const K& key);

    // TODO predicate invalidation?

    /**
     * Return the object with the given key. If it does not exist, the backing store will be consulted.
     * and the cache will be updated.
     * If none exist an empty one will be created.
     * Object is then put at head of LRU list.
     *
     * If any more arguments than `key` are present, they will be forwarded verbatim to the
     * backing store in case of a cache miss. They are entirely ignored if there is a cache hit.
     */
    template <typename... BackingStoreArgs>
    [[nodiscard]] V read(const K& key, BackingStoreArgs&&... backing_store_args);

    /**
     * Update the cache and write through to backing store.
     * Object is then put at head of LRU list.
     */
    void write(const K& key, V value);

    /**
     * Tell if an object with given key exists in the cache.
     * Does not alter the LRU list.
     */
    [[nodiscard]] bool hasKey(const K& key) const;

    [[nodiscard]] virtual CacheStats get_stats() const;

    size_t         getHit() const noexcept { return _hit.load(std::memory_order_relaxed); }
    size_t        getMiss() const noexcept { return _miss.load(std::memory_order_relaxed); }
    size_t getNonExisting() const noexcept { return _non_existing.load(std::memory_order_relaxed); }
    size_t        getRace() const noexcept { return _race.load(std::memory_order_relaxed); }
    size_t      getInsert() const noexcept { return _insert.load(std::memory_order_relaxed); }
    size_t       getWrite() const noexcept { return _write.load(std::memory_order_relaxed); }
    size_t  getInvalidate() const noexcept { return _invalidate.load(std::memory_order_relaxed); }
    size_t      getLookup() const noexcept { return _lookup.load(std::memory_order_relaxed); }

    /**
     * Returns the number of bytes that are always implicitly added for each element
     * present in the cache.
    */
    [[nodiscard]] constexpr static size_t per_element_fixed_overhead() noexcept {
        return sizeof(value_type);
    }

    // For testing. Not const since backing lrucache_map does not have const_iterator
    [[nodiscard]] std::vector<K> dump_segment_keys_in_lru_order(CacheSegment segment);

protected:
    using UniqueLock = std::unique_lock<std::mutex>;
    [[nodiscard]] UniqueLock getGuard() const;
    void invalidate(const UniqueLock& guard, const K& key);
    [[nodiscard]] bool hasKey(const UniqueLock& guard, const K& key) const;
private:
    // Implicitly updates LRU segment(s) on hit.
    // Precondition: _hashLock is held.
    [[nodiscard]] bool try_fill_from_cache(const K& key, V& val_out);

    [[nodiscard]] bool multi_segment() const noexcept { return _protected_segment.capacity_bytes() != 0; }
    void disable_slru();
    void verifyHashLock(const UniqueLock& guard) const;
    [[nodiscard]] size_t calcSize(const K& k, const V& v) const noexcept {
        return per_element_fixed_overhead() + _sizeK(k) + _sizeV(v);
    }
    [[nodiscard]] std::mutex& getLock(const K& k) noexcept {
        size_t h(_hasher(k));
        return _addLocks[h%(sizeof(_addLocks)/sizeof(_addLocks[0]))];
    }

    template <typename V>
    static void increment_stat(std::atomic<V>& v, const std::lock_guard<std::mutex>&) noexcept {
        v.store(v.load(std::memory_order_relaxed) + 1, std::memory_order_relaxed);
    }
    template <typename V>
    static void increment_stat(std::atomic<V>& v, const std::unique_lock<std::mutex>&) noexcept {
        v.store(v.load(std::memory_order_relaxed) + 1, std::memory_order_relaxed);
    }

    [[no_unique_address]] Hash  _hasher;
    [[no_unique_address]] SizeK _sizeK;
    [[no_unique_address]] SizeV _sizeV;
    std::atomic<size_t>         _size_bytes;
    mutable std::atomic<size_t> _hit;
    mutable std::atomic<size_t> _miss;
    std::atomic<size_t>         _non_existing;
    mutable std::atomic<size_t> _race;
    mutable std::atomic<size_t> _insert;
    mutable std::atomic<size_t> _write;
    mutable std::atomic<size_t> _update;
    mutable std::atomic<size_t> _invalidate;
    mutable std::atomic<size_t> _lookup;
    BackingStore&               _store;

    ProbationarySegmentLru      _probationary_segment;
    ProtectedSegmentLru         _protected_segment;

    mutable std::mutex          _hashLock;
    // Striped locks that can be used for having locked access to the backing store.
    std::mutex                  _addLocks[113];
};

}
