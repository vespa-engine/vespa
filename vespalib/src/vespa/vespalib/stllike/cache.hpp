// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cache.h"
#include "cache_stats.h"
#include "lrucache_map.hpp"

namespace vespalib {

template< typename P >
cache<P> &
cache<P>::maxElements(size_t elems) {
    Lru::maxElements(elems);
    return *this;
}

template< typename P >
cache<P> &
cache<P>::setCapacityBytes(size_t sz) {
    _maxBytes.store(sz, std::memory_order_relaxed);
    return *this;
}

template< typename P >
void
cache<P>::invalidate(const K & key) {
    UniqueLock guard(_hashLock);
    invalidate(guard, key);
}

template< typename P >
bool
cache<P>::hasKey(const K & key) const {
    UniqueLock guard(_hashLock);
    return hasKey(guard, key);
}

template< typename P >
cache<P>::~cache() = default;

template< typename P >
cache<P>::cache(BackingStore & b, size_t maxBytes) :
    Lru(Lru::UNLIMITED),
    _maxBytes(maxBytes),
    _sizeBytes(0),
    _hit(0),
    _miss(0),
    _noneExisting(0),
    _race(0),
    _insert(0),
    _write(0),
    _update(0),
    _invalidate(0),
    _lookup(0),
    _store(b)
{ }

template< typename P >
MemoryUsage
cache<P>::getStaticMemoryUsage() const {
    MemoryUsage usage;
    auto cacheGuard = getGuard();
    usage.incAllocatedBytes(sizeof(*this));
    usage.incUsedBytes(sizeof(*this));
    return usage;
}

template< typename P >
bool
cache<P>::removeOldest(const value_type & v) {
    bool remove(Lru::removeOldest(v) || (sizeBytes() >= capacityBytes()));
    if (remove) {
        _sizeBytes.store(sizeBytes() - calcSize(v.first, v.second._value), std::memory_order_relaxed);
    }
    return remove;
}

template< typename P >
std::unique_lock<std::mutex>
cache<P>::getGuard() const {
    return UniqueLock(_hashLock);
}

template< typename P >
typename P::Value
cache<P>::read(const K & key)
{
    {
        std::lock_guard guard(_hashLock);
        if (Lru::hasKey(key)) {
            increment_stat(_hit, guard);
            return (*this)[key];
        } else {
            increment_stat(_miss, guard);
        }
    }

    std::lock_guard storeGuard(getLock(key));
    {
        std::lock_guard guard(_hashLock);
        if (Lru::hasKey(key)) {
            // Somebody else just fetched it ahead of me.
            increment_stat(_race, guard);
            return (*this)[key];
        }
    }
    V value;
    if (_store.read(key, value)) {
        std::lock_guard guard(_hashLock);
        Lru::insert(key, value);
        _sizeBytes.store(sizeBytes() + calcSize(key, value), std::memory_order_relaxed);
        increment_stat(_insert, guard);
    } else {
        _noneExisting.fetch_add(1);
    }
    return value;
}

template< typename P >
void
cache<P>::write(const K & key, V value)
{
    size_t newSize = calcSize(key, value);
    std::lock_guard storeGuard(getLock(key));
    {
        std::lock_guard guard(_hashLock);
        if (Lru::hasKey(key)) {
            _sizeBytes.store(sizeBytes() - calcSize(key, (*this)[key]), std::memory_order_relaxed);
            increment_stat(_update, guard);
        }
    }

    _store.write(key, value);
    {
        std::lock_guard guard(_hashLock);
        (*this)[key] = std::move(value);
        _sizeBytes.store(sizeBytes() + newSize, std::memory_order_relaxed);
        increment_stat(_write, guard);
    }
}

template< typename P >
void
cache<P>::erase(const K & key)
{
    std::lock_guard storeGuard(getLock(key));
    invalidate(key);
    _store.erase(key);
}

template< typename P >
void
cache<P>::invalidate(const UniqueLock & guard, const K & key)
{
    verifyHashLock(guard);
    if (Lru::hasKey(key)) {
        _sizeBytes.store(sizeBytes() - calcSize(key, (*this)[key]), std::memory_order_relaxed);
        increment_stat(_invalidate, guard);
        Lru::erase(key);
    }
}

template< typename P >
bool
cache<P>::hasKey(const UniqueLock & guard, const K & key) const
{
    verifyHashLock(guard);
    increment_stat(_lookup, guard);
    return Lru::hasKey(key);
}

template< typename P >
void
cache<P>::verifyHashLock(const UniqueLock & guard) const {
    assert(guard.mutex() == & _hashLock);
    assert(guard.owns_lock());
}

template <typename P>
CacheStats
cache<P>::get_stats() const
{
    std::lock_guard guard(_hashLock);
    return CacheStats(getHit(), getMiss(), Lru::size(), sizeBytes(), getInvalidate());
}

}

