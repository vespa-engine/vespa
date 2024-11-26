// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cache.h"
#include "cache_stats.h"
#include "lrucache_map.hpp"

namespace vespalib {

template <typename P>
cache<P>::SizeConstrainedLru::SizeConstrainedLru(cache& owner, size_t capacity_bytes)
    : Lru(Lru::UNLIMITED),
      _owner(owner),
      _size_bytes(0),
      _capacity_bytes(capacity_bytes)
{
}

template <typename P>
cache<P>::SizeConstrainedLru::~SizeConstrainedLru() = default;

template <typename P>
bool
cache<P>::SizeConstrainedLru::removeOldest(const typename P::value_type& kv) {
    const size_t sz_now = size_bytes();
    // TODO shouldn't this be size > capacity, not >= ? Not touching it for now...
    const bool remove = (Lru::removeOldest(kv) || (sz_now >= capacity_bytes()));
    if (remove) {
        sub_size_bytes(_owner.calcSize(kv.first, kv.second._value));
    }
    return remove;
}

template <typename P>
bool
cache<P>::SizeConstrainedLru::has_key(const KeyT& key) const noexcept {
    return Lru::hasKey(key);
}

template <typename P>
void
cache<P>::SizeConstrainedLru::insert_and_update_size(const KeyT& key, ValueT value) {
    // Account for added size _prior_ to inserting into the LRU so that we'll trigger
    // an eviction of existing entries that would otherwise cause the segment to get
    // overfull once the insertion has been completed.
    add_size_bytes(_owner.calcSize(key, value));
    auto insert_res = Lru::insert(key, std::move(value));
    assert(insert_res.second);

}

template <typename P>
bool
cache<P>::SizeConstrainedLru::try_replace_and_update_size(const KeyT& key, ValueT& value) {
    auto* maybe_existing = Lru::find_and_ref(key); // Bump to LRU head iff already existing
    if (maybe_existing) {
        const auto new_kv_size = _owner.calcSize(key, value);
        sub_size_bytes(_owner.calcSize(key, *maybe_existing));
        add_size_bytes(new_kv_size);
        *maybe_existing = std::move(value);
        return true;
    }
    return false;
}

template <typename P>
bool
cache<P>::SizeConstrainedLru::try_erase_and_update_size(const KeyT& key) {
    auto iter = Lru::find_no_ref(key);
    if (iter != Lru::end()) {
        sub_size_bytes(_owner.calcSize(key, *iter)); // Must be prior to erase()!
        Lru::erase(iter);
        return true;
    }
    return false;
}

template <typename P>
const typename P::Value&
cache<P>::SizeConstrainedLru::get_existing(const KeyT& key) const {
    return Lru::get(key);
}

template <typename P>
bool
cache<P>::SizeConstrainedLru::try_get_and_ref(const KeyT& key, ValueT& val_out) {
    const auto* maybe_val = Lru::find_and_ref(key);
    if (maybe_val) {
        val_out = *maybe_val;
        return true;
    }
    return false;
}

template <typename P>
void
cache<P>::SizeConstrainedLru::evict_all() {
    // There's no `clear()` on lrucache_map, so do it the hard way.
    auto iter = Lru::begin();
    while (iter != Lru::end()) {
        // Entries will not be transitioned out of the cache via the probationary segment,
        // so invoke the removal callback directly.
        _owner.onRemove(iter.key());
        iter = Lru::erase(iter); // This does _not_ invoke `removeOldest()`
    }
    set_size_bytes(0);
}

template <typename P>
std::vector<typename P::Key>
cache<P>::SizeConstrainedLru::dump_segment_keys_in_lru_order() {
    std::vector<KeyT> lru_keys;
    lru_keys.reserve(size());
    for (auto it = Lru::begin(); it != Lru::end(); ++it) {
        lru_keys.emplace_back(it.key());
    }
    return lru_keys;
}

template <typename P>
cache<P>::ProbationarySegmentLru::ProbationarySegmentLru(cache& owner, size_t capacity_bytes)
    : SizeConstrainedLru(owner, capacity_bytes)
{
}

template <typename P>
cache<P>::ProbationarySegmentLru::~ProbationarySegmentLru() = default;

template <typename P>
bool
cache<P>::ProbationarySegmentLru::removeOldest(const typename P::value_type& kv) {
    const bool remove = SizeConstrainedLru::removeOldest(kv);
    if (remove) {
        SizeConstrainedLru::_owner.onRemove(kv.first);
    }
    return remove;
}

template <typename P>
cache<P>::ProtectedSegmentLru::ProtectedSegmentLru(cache& owner, size_t capacity_bytes)
    : SizeConstrainedLru(owner, capacity_bytes)
{
}

template <typename P>
cache<P>::ProtectedSegmentLru::~ProtectedSegmentLru() = default;

template <typename P>
bool
cache<P>::ProtectedSegmentLru::removeOldest(const typename P::value_type& kv) {
    const bool remove = SizeConstrainedLru::removeOldest(kv); // Updates own size if `remove == true`
    if (remove) {
        // Move back into probationary segment. This may shove the oldest entry out of it,
        // which evicts it from the cache completely (no second chances for probationary elements).
        // Note that we intercept this in removeOldest() and _not_ onRemove() since the latter
        // only provides the _key_ of the element being removed, while removeOldest provides both
        // the key and value. It is not obviously safe to resolve the key -> value from onRemove().
        SizeConstrainedLru::_owner._probationary_segment.insert_and_update_size(kv.first, kv.second._value);
    }
    return remove;
}

template <typename P>
cache<P>&
cache<P>::maxElements(size_t probationary_elems, size_t protected_elems) {
    std::lock_guard guard(_hashLock);
    _probationary_segment.set_max_elements(probationary_elems);
    _protected_segment.set_max_elements(protected_elems);
    if (protected_elems == 0) { // If max protected elems == 0, this disables SLRU semantics
        disable_slru();
    }
    return *this;
}

template <typename P>
cache<P>&
cache<P>::maxElements(size_t elems) {
    maxElements(elems, 0);
    return *this;
}

template <typename P>
cache<P>&
cache<P>::setCapacityBytes(size_t probationary_sz, size_t protected_sz) {
    std::lock_guard guard(_hashLock);
    _probationary_segment.set_capacity_bytes(probationary_sz);
    _protected_segment.set_capacity_bytes(protected_sz);
    if (protected_sz == 0) { // If max protected size == 0, this disables SLRU semantics
        disable_slru();
    }
    return *this;
}

template <typename P>
cache<P>&
cache<P>::setCapacityBytes(size_t sz) {
    setCapacityBytes(sz, 0);
    return *this;
}

template <typename P>
void
cache<P>::disable_slru() {
    _protected_segment.set_max_elements(0);
    _protected_segment.set_capacity_bytes(0);
    // This has the not-entirely-optimal(tm) side effect of evicting the elements
    // we consider the most important (i.e. the protected ones), but this exists
    // only so that live-disabling SLRU entirely will be _correct_, not that it
    // will be objectively _efficient_.
    // TODO expose lrucache_map trimming and use this here instead.
    _protected_segment.evict_all();
}

template <typename P>
void
cache<P>::invalidate(const K& key) {
    UniqueLock guard(_hashLock);
    invalidate(guard, key);
}

template <typename P>
bool
cache<P>::hasKey(const K& key) const {
    UniqueLock guard(_hashLock);
    return hasKey(guard, key);
}

template <typename P>
cache<P>::~cache() = default;

template <typename P>
cache<P>::cache(BackingStore& backing_store,
                size_t max_probationary_bytes,
                size_t max_protected_bytes) :
    _hit(0),
    _miss(0),
    _non_existing(0),
    _race(0),
    _insert(0),
    _write(0),
    _update(0),
    _invalidate(0),
    _lookup(0),
    _store(backing_store),
    _probationary_segment(*this, max_probationary_bytes),
    _protected_segment(*this, max_protected_bytes)
{}

template <typename P>
cache<P>::cache(BackingStore& backing_store, size_t max_bytes)
    : cache(backing_store, max_bytes, 0)
{}

template <typename P>
MemoryUsage
cache<P>::getStaticMemoryUsage() const {
    MemoryUsage usage;
    auto cacheGuard = getGuard();
    usage.incAllocatedBytes(sizeof(*this));
    usage.incUsedBytes(sizeof(*this));
    return usage;
}

template <typename P>
std::unique_lock<std::mutex>
cache<P>::getGuard() const {
    return UniqueLock(_hashLock);
}

template <typename P>
template <typename... BackingStoreArgs>
typename P::Value
cache<P>::read(const K& key, BackingStoreArgs&&... backing_store_args)
{
    V value;
    {
        std::lock_guard guard(_hashLock);
        if (try_fill_from_cache(key, value)) {
            increment_stat(_hit, guard);
            return value;
        } else {
            increment_stat(_miss, guard);
        }
    }

    std::lock_guard store_guard(getLock(key));
    {
        std::lock_guard guard(_hashLock);
        if (try_fill_from_cache(key, value)) {
            increment_stat(_race, guard); // Somebody else just fetched it ahead of me.
            return value;
        }
    }
    if (_store.read(key, value, std::forward<BackingStoreArgs>(backing_store_args)...)) {
        std::lock_guard guard(_hashLock);
        _probationary_segment.insert_and_update_size(key, value);
        onInsert(key);
        increment_stat(_insert, guard);
    } else {
        _non_existing.fetch_add(1, std::memory_order_relaxed);
    }
    return value;
}

template <typename P>
bool
cache<P>::try_fill_from_cache(const K& key, V& val_out) {
    if (_probationary_segment.try_get_and_ref(key, val_out)) {
        if (multi_segment()) {
            // Hit on probationary item; move to protected segment
            const bool erased = _probationary_segment.try_erase_and_update_size(key);
            assert(erased);
            _protected_segment.insert_and_update_size(key, val_out);
        }
        return true;
    } else if (multi_segment() && _protected_segment.try_get_and_ref(key, val_out)) {
        return true;
    }
    return false;
}

template <typename P>
void
cache<P>::write(const K& key, V value)
{
    std::lock_guard storeGuard(getLock(key));
    _store.write(key, value);
    {
        std::lock_guard guard(_hashLock);
        // Important: `try_replace_and_update_size()` consumes `value` if replacing took place
        if (_probationary_segment.try_replace_and_update_size(key, value)) {
            increment_stat(_update, guard);
        } else if (multi_segment() && _protected_segment.try_replace_and_update_size(key, value)) {
            increment_stat(_update, guard);
        } else {
            // Always insert into probationary first
            _probationary_segment.insert_and_update_size(key, std::move(value));
            onInsert(key);
        }
        increment_stat(_write, guard); // TODO only increment when not updating?
    }
}

template <typename P>
void
cache<P>::erase(const K& key)
{
    std::lock_guard storeGuard(getLock(key));
    invalidate(key);
    _store.erase(key);
}

template <typename P>
void
cache<P>::invalidate(const UniqueLock& guard, const K& key)
{
    verifyHashLock(guard);
    if (!(_probationary_segment.try_erase_and_update_size(key) ||
         (multi_segment() && _protected_segment.try_erase_and_update_size(key))))
    {
        return; // No cache entry found for `key`
    }
    onRemove(key); // Not transitively invoked by erase_and_update_size()
    increment_stat(_invalidate, guard);
}

template <typename P>
bool
cache<P>::hasKey(const UniqueLock& guard, const K& key) const
{
    verifyHashLock(guard);
    increment_stat(_lookup, guard);
    if (_probationary_segment.has_key(key)) {
        return true;
    }
    return multi_segment() && _protected_segment.has_key(key);
}

template <typename P>
void
cache<P>::verifyHashLock(const UniqueLock& guard) const {
    assert(guard.mutex() == &_hashLock);
    assert(guard.owns_lock());
}

template <typename P>
CacheStats
cache<P>::get_stats() const
{
    std::lock_guard guard(_hashLock);
    return CacheStats(getHit(), getMiss(), size(), sizeBytes(), getInvalidate());
}

template <typename P>
std::vector<typename P::Key>
cache<P>::dump_segment_keys_in_lru_order(CacheSegment seg) {
    std::lock_guard guard(_hashLock);
    return (seg == CacheSegment::Probationary) ? _probationary_segment.dump_segment_keys_in_lru_order()
                                               : _protected_segment.dump_segment_keys_in_lru_order();
}

template <typename P>
size_t
cache<P>::segment_size(CacheSegment seg) const noexcept {
    std::lock_guard guard(_hashLock);
    return (seg == CacheSegment::Probationary) ? _probationary_segment.size()
                                               : _protected_segment.size();
}

template <typename P>
size_t
cache<P>::segment_size_bytes(CacheSegment seg) const noexcept {
    std::lock_guard guard(_hashLock);
    return (seg == CacheSegment::Probationary) ? _probationary_segment.size_bytes()
                                               : _protected_segment.size_bytes();
}

template <typename P>
size_t
cache<P>::segment_capacity(CacheSegment seg) const noexcept {
    std::lock_guard guard(_hashLock);
    return (seg == CacheSegment::Probationary) ? _probationary_segment.capacity()
                                               : _protected_segment.capacity();
}

template <typename P>
size_t
cache<P>::segment_capacity_bytes(CacheSegment seg) const noexcept {
    std::lock_guard guard(_hashLock);
    return (seg == CacheSegment::Probationary) ? _probationary_segment.capacity_bytes()
                                               : _protected_segment.capacity_bytes();
}

}

