// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "lockablemap.h"
#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <thread>
#include <chrono>
#include <ostream>

namespace storage {

template<typename Map>
LockableMap<Map>::LockIdSet::LockIdSet() : Hash() { }

template<typename Map>
LockableMap<Map>::LockIdSet::~LockIdSet() = default;

template<typename Map>
size_t
LockableMap<Map>::LockIdSet::getMemoryUsage() const {
    return Hash::getMemoryConsumption();
}

template<typename Map>
LockableMap<Map>::LockWaiters::LockWaiters() : _id(0), _map() { }

template<typename Map>
LockableMap<Map>::LockWaiters::~LockWaiters() = default;

template<typename Map>
size_t
LockableMap<Map>::LockWaiters::insert(const LockId & lid) {
    Key id(_id++);
    _map.insert(typename WaiterMap::value_type(id, lid));
    return id;
}

template<typename Map>
LockableMap<Map>::LockableMap()
    : _map(),
      _lock(),
      _cond(),
      _lockedKeys(),
      _lockWaiters()
{}

template<typename Map>
LockableMap<Map>::~LockableMap() = default;

template<typename Map>
bool
LockableMap<Map>::operator==(const LockableMap<Map>& other) const
{
    std::lock_guard<std::mutex> guard(_lock);
    std::lock_guard<std::mutex> guard2(other._lock);
    return (_map == other._map);
}

template<typename Map>
bool
LockableMap<Map>::operator<(const LockableMap<Map>& other) const
{
    std::lock_guard<std::mutex> guard(_lock);
    std::lock_guard<std::mutex> guard2(other._lock);
    return (_map < other._map);
}

template<typename Map>
size_t
LockableMap<Map>::size() const noexcept
{
    std::lock_guard<std::mutex> guard(_lock);
    return _map.size();
}

template<typename Map>
size_t
LockableMap<Map>::getMemoryUsage() const noexcept
{
    std::lock_guard<std::mutex> guard(_lock);
    return _map.getMemoryUsage() + _lockedKeys.getMemoryUsage() +
        sizeof(std::mutex) + sizeof(std::condition_variable);
}

template <typename Map>
vespalib::MemoryUsage LockableMap<Map>::detailed_memory_usage() const noexcept {
    // We don't have any details for this map type, just count everything as "allocated".
    size_t used = getMemoryUsage();
    vespalib::MemoryUsage mem_usage;
    mem_usage.incAllocatedBytes(used);
    mem_usage.incUsedBytes(used);
    return mem_usage;
}

template<typename Map>
bool
LockableMap<Map>::empty() const noexcept
{
    std::lock_guard<std::mutex> guard(_lock);
    return _map.empty();
}

template<typename Map>
void
LockableMap<Map>::swap(LockableMap<Map>& other)
{
    std::lock_guard<std::mutex> guard(_lock);
    std::lock_guard<std::mutex> guard2(other._lock);
    return _map.swap(other._map);
}

template<typename Map>
void LockableMap<Map>::acquireKey(const LockId & lid, std::unique_lock<std::mutex> &guard)
{
    if (_lockedKeys.exist(lid)) {
        typename LockWaiters::Key waitId(_lockWaiters.insert(lid));
        while (_lockedKeys.exist(lid)) {
            _cond.wait(guard);
        }
        _lockWaiters.erase(waitId);
    }
}

template<typename Map>
typename LockableMap<Map>::WrappedEntry
LockableMap<Map>::get(const key_type& key, const char* clientId, bool createIfNonExisting)
{
    LockId lid(key, clientId);
    std::unique_lock<std::mutex> guard(_lock);
    acquireKey(lid, guard);
    bool preExisted = false;
    typename Map::iterator it =
        _map.find(key, createIfNonExisting, preExisted);

    if (it == _map.end()) {
        return WrappedEntry();
    }
    _lockedKeys.insert(lid);
    return WrappedEntry(*this, key, it->second, clientId, preExisted);
}

template<typename Map>
bool
LockableMap<Map>::erase(const key_type& key, const char* client_id, bool has_lock)
{
    LockId lid(key, client_id);
    std::unique_lock<std::mutex> guard(_lock);
    if (!has_lock) {
        acquireKey(lid, guard);
    }
    return _map.erase(key);
}

template<typename Map>
void
LockableMap<Map>::insert(const key_type& key, const mapped_type& value,
                         const char* client_id, bool has_lock, bool& preExisted)
{
    LockId lid(key, client_id);
    std::unique_lock<std::mutex> guard(_lock);
    if (!has_lock) {
        acquireKey(lid, guard);
    }
    _map.insert(key, value, preExisted);
}

template<typename Map>
void
LockableMap<Map>::clear()
{
    std::lock_guard<std::mutex> guard(_lock);
    _map.clear();
}

template<typename Map>
bool
LockableMap<Map>::findNextKey(key_type& key, mapped_type& val,
                              const char* clientId,
                              std::unique_lock<std::mutex> &guard)
{
    // Wait for next value to unlock.
    typename Map::iterator it(_map.lower_bound(key));
    while (it != _map.end() && _lockedKeys.exist(LockId(it->first, ""))) {
        auto wait_id = _lockWaiters.insert(LockId(it->first, clientId));
        _cond.wait(guard);
        _lockWaiters.erase(wait_id);
        it = _map.lower_bound(key);
    }
    if (it == _map.end()) {
        return true;
    }
    key = it->first;
    val = it->second;
    return false;
}

template<typename Map>
bool
LockableMap<Map>::handleDecision(key_type& key, mapped_type& val,
                                 Decision decision)
{
    bool b;
    switch (decision) {
    case Decision::UPDATE:
        _map.insert(key, val, b);
        break;
    case Decision::REMOVE:
        _map.erase(key);
        break;
    case Decision::ABORT:
        return true;
    case Decision::CONTINUE:
        break;
    default:
        HDR_ABORT("should not be reached");
    }
    return false;
}

template<typename Map>
void LockableMap<Map>::do_for_each_mutable(std::function<Decision(uint64_t, mapped_type&)> func,
                                           const char* clientId,
                                           const key_type& first,
                                           const key_type& last)
{
    key_type key = first;
    mapped_type val;
    std::unique_lock<std::mutex> guard(_lock);
    while (true) {
        if (findNextKey(key, val, clientId, guard) || key > last) return;
        Decision d(func(const_cast<const key_type&>(key), val));
        if (handleDecision(key, val, d)) return;
        ++key;
    }
}

template<typename Map>
void LockableMap<Map>::do_for_each(std::function<Decision(uint64_t, const mapped_type&)> func,
                                   const char* clientId,
                                   const key_type& first,
                                   const key_type& last)
{
    key_type key = first;
    mapped_type val;
    std::unique_lock<std::mutex> guard(_lock);
    while (true) {
        if (findNextKey(key, val, clientId, guard) || key > last) return;
        Decision d(func(const_cast<const key_type&>(key), val));
        assert(d == Decision::ABORT || d == Decision::CONTINUE);
        if (handleDecision(key, val, d)) return;
        ++key;
    }
}

template <typename Map>
bool
LockableMap<Map>::processNextChunk(std::function<Decision(uint64_t, const mapped_type&)>& func,
                                   key_type& key,
                                   const char* clientId,
                                   const uint32_t chunkSize)
{
    mapped_type val;
    std::unique_lock<std::mutex> guard(_lock);
    for (uint32_t processed = 0; processed < chunkSize; ++processed) {
        if (findNextKey(key, val, clientId, guard)) {
            return false;
        }
        Decision d(func(const_cast<const key_type&>(key), val));
        if (handleDecision(key, val, d)) {
            return false;
        }
        ++key;
    }
    return true;
}

template <typename Map>
void LockableMap<Map>::do_for_each_chunked(std::function<Decision(uint64_t, const mapped_type&)> func,
                                           const char* clientId,
                                           vespalib::duration yieldTime,
                                           uint32_t chunkSize)
{
    key_type key{};
    while (processNextChunk(func, key, clientId, chunkSize)) {
        // Rationale: delay iteration for as short a time as possible while
        // allowing another thread blocked on the main DB mutex to acquire it
        // in the meantime. Simply yielding the thread does not have the
        // intended effect with the Linux scheduler.
        // This is a pragmatic stop-gap solution; a more robust change requires
        // the redesign of bucket DB locking and signalling semantics in the
        // face of blocked point lookups.
        std::this_thread::sleep_for(yieldTime);
    }
}

// TODO This is a placeholder that has to work around the const-ness and type quirks of
// the legacy LockableMap implementation. In particular, it offers no snapshot isolation
// at all, nor does it support the "get parents and self" bucket lookup operation.
template <typename Map>
class LockableMap<Map>::ReadGuardImpl final
    : public bucketdb::ReadGuard<typename Map::mapped_type>
{
    const LockableMap<Map>& _map;
public:
    using mapped_type = typename Map::mapped_type;

    explicit ReadGuardImpl(const LockableMap<Map>& map) : _map(map) {}
    ~ReadGuardImpl() override = default;

    std::vector<mapped_type> find_parents_and_self(const document::BucketId&) const override {
        abort(); // Finding just parents+self isn't supported by underlying legacy DB API!
    }

    std::vector<mapped_type> find_parents_self_and_children(const document::BucketId& bucket) const override {
        auto& mutable_map = const_cast<LockableMap<Map>&>(_map); // _map is thread safe.
        auto locked_entries = mutable_map.getAll(bucket, "ReadGuardImpl::find_parents_self_and_children");
        std::vector<mapped_type> entries;
        entries.reserve(locked_entries.size());
        for (auto& e : locked_entries) {
            entries.emplace_back(*e.second);
        }
        return entries;
    }

    void for_each(std::function<void(uint64_t, const mapped_type&)> func) const override {
        auto decision_wrapper = [&func](uint64_t key, const mapped_type& value) -> Decision {
            func(key, value);
            return Decision::CONTINUE;
        };
        auto& mutable_map = const_cast<LockableMap<Map>&>(_map); // _map is thread safe.
        mutable_map.for_each_chunked(std::move(decision_wrapper), "ReadGuardImpl::for_each");
    }

    [[nodiscard]] uint64_t generation() const noexcept override {
        return 0;
    }
};

template <typename Map>
std::unique_ptr<bucketdb::ReadGuard<typename Map::mapped_type>>
LockableMap<Map>::do_acquire_read_guard() const {
    return std::make_unique<ReadGuardImpl>(*this);
}

template<typename Map>
void
LockableMap<Map>::print(std::ostream& out, bool verbose,
                        const std::string& indent) const
{
    std::lock_guard<std::mutex> guard(_lock);
    out << "LockableMap {\n" << indent << "  ";

    if (verbose) {
        for (const auto entry : _map) {
            out << "Key: " << BucketId(BucketId::keyToBucketId(entry.first))
                << " Value: " << entry.second << "\n" << indent << "  ";
        }

        out << "\n" << indent << "  Locked keys: ";
        _lockedKeys.print(out, verbose, indent + "  ");
    }
    out << "} : ";

    out << _map;
}

template<typename Map>
void
LockableMap<Map>::LockIdSet::print(std::ostream& out, bool verbose,
                        const std::string& indent) const
{
    out << "hash {";
    for (typename Hash::const_iterator it(Hash::begin()), mt(Hash::end()); it != mt; it++) {
        if (verbose) {
            out << "\n" << indent << "  ";
        } else {
            out << " ";
        }

        out << *it;
    }
    if (verbose) out << "\n" << indent;
    out << " }";
}

template<typename Map>
void
LockableMap<Map>::unlock(const key_type& key)
{
    std::lock_guard<std::mutex> guard(_lock);
    _lockedKeys.erase(LockId(key, ""));
    _cond.notify_all();
}

/**
 * Check whether the given key contains the given bucket.
 * Sets result to the bucket corresponding to the key, and keyResult
 * to the key if true.
 */
bool
checkContains(document::BucketId::Type key, const document::BucketId& bucket,
              document::BucketId& result, document::BucketId::Type& keyResult);

/**
 * Retrieves the most specific bucket id (highest used bits) that contains
 * the given bucket.
 *
 * If a match is found, result is set to the bucket id found, and keyResult is
 * set to the corresponding key (reversed)
 *
 * If not found, nextKey is set to the key after one that could have matched
 * and we return false.
 */
template<typename Map>
bool
LockableMap<Map>::getMostSpecificMatch(const BucketId& bucket,
                                       BucketId& result,
                                       BucketId::Type& keyResult,
                                       BucketId::Type& nextKey)
{
    typename Map::const_iterator iter = _map.lower_bound(bucket.toKey());

    nextKey = 0;

    // We should now have either the bucket we are looking for
    // (if the exact bucket exists), or one right after.
    if (iter != _map.end()) {
        nextKey = iter->first;

        if (checkContains(iter->first, bucket, result, keyResult)) {
            return true;
        }
    }

    if (iter != _map.begin()) {
        --iter; // If iter was map.end(), we should now end up at the last item in the map
        nextKey = iter->first;

        if (checkContains(iter->first, bucket, result, keyResult)) {
            return true;
        }
    }

    return false;
}

/**
 * Finds all buckets that can contain the given bucket, except for the bucket
 * itself.
 */
template<typename Map>
void
LockableMap<Map>::getAllContaining(const BucketId& bucket,
                                   std::vector<BucketId::Type>& keys)
{
    BucketId id = bucket;

    // Find other buckets that contain this bucket.
    // TODO: Optimize?
    while (id.getUsedBits() > 1) {
        id.setUsedBits(id.getUsedBits() - 1);
        id = id.stripUnused();
        BucketId::Type key = id.toKey();

        typename Map::const_iterator iter = _map.find(key);
        if (iter != _map.end()) {
            keys.push_back(key);
        }
    }
}

template<typename Map>
void
LockableMap<Map>::addAndLockResults(
        const std::vector<BucketId::Type>& keys,
        const char* clientId,
        std::map<BucketId, WrappedEntry>& results,
        std::unique_lock<std::mutex> &guard)
{
    // Wait until all buckets are free to be added, then add them all.
    while (true) {
        bool allOk = true;
        key_type waitingFor(0);

        for (uint32_t i=0; i<keys.size(); i++) {
            if (_lockedKeys.exist(LockId(keys[i], clientId))) {
                waitingFor = keys[i];
                allOk = false;
                break;
            }
        }

        if (!allOk) {
            typename LockWaiters::Key waitId(_lockWaiters.insert(LockId(waitingFor, clientId)));
            _cond.wait(guard);
            _lockWaiters.erase(waitId);
        } else {
            for (uint32_t i=0; i<keys.size(); i++) {
                typename Map::iterator it = _map.find(keys[i]);
                if (it != _map.end()) {
                    _lockedKeys.insert(LockId(keys[i], clientId));
                    results[BucketId(BucketId::keyToBucketId(keys[i]))]
                          = WrappedEntry(*this, keys[i], it->second,
                                         clientId, true);
                }
            }
            break;
        }
    }
}

uint8_t getMinDiffBits(uint16_t minBits, const document::BucketId& a, const document::BucketId& b);

template<typename Map>
typename LockableMap<Map>::EntryMap
LockableMap<Map>::getContained(const BucketId& bucket,
                               const char* clientId)
{
    std::unique_lock<std::mutex> guard(_lock);
    std::map<BucketId, WrappedEntry> results;

    BucketId result;
    BucketId::Type keyResult;
    BucketId::Type nextKey;

    std::vector<BucketId::Type> keys;

    if (getMostSpecificMatch(bucket, result, keyResult, nextKey)) {
        keys.push_back(keyResult);

        // Find the super buckets for the most specific match
        getAllContaining(result, keys);
    } else {
        // Find the super buckets for the input bucket
        // because getMostSpecificMatch() might not find the most specific
        // match in all cases of inconsistently split buckets
        getAllContaining(bucket, keys);
    }

    if (!keys.empty()) {
        addAndLockResults(keys, clientId, results, guard);
    }

    return results;
}

template<typename Map>
void
LockableMap<Map>::getAllWithoutLocking(const BucketId& bucket,
                                       std::vector<BucketId::Type>& keys)
{
    BucketId result;
    BucketId::Type keyResult;
    BucketId::Type nextKey;

    typename Map::iterator it = _map.end();

    if (getMostSpecificMatch(bucket, result, keyResult, nextKey)) {
        keys.push_back(keyResult);

        // Find the super buckets for the most specific match
        getAllContaining(result, keys);

        it = _map.find(keyResult);
        if (it != _map.end()) {
            // Skipping nextKey, since it was equal to keyResult
            ++it;
        }
    } else {
        // Find the super buckets for the input bucket
        // because getMostSpecificMatch() might not find the most specific
        // match in all cases of inconsistently split buckets
        getAllContaining(bucket, keys);

        it = _map.find(nextKey);
        if (it != _map.end()) {
            // Nextkey might be contained in the imput bucket,
            // e.g. if it is the first bucket in bucketdb
            BucketId id = BucketId(BucketId::keyToBucketId(it->first));
            if (!bucket.contains(id)) {
                ++it;
            }
        }
    }

    // Buckets contained in the found bucket will come immediately after it.
    // Traverse the map to find them.
    for (; it != _map.end(); ++it) {
        BucketId id(BucketId(BucketId::keyToBucketId(it->first)));

        if (bucket.contains(id)) {
            keys.push_back(it->first);
        } else {
            break;
        }
    }
}

/**
 * Returns the given bucket, its super buckets and its sub buckets.
 */
template<typename Map>
typename LockableMap<Map>::EntryMap
LockableMap<Map>::getAll(const BucketId& bucket, const char* clientId)
{
    std::unique_lock<std::mutex> guard(_lock);

    std::map<BucketId, WrappedEntry> results;
    std::vector<BucketId::Type> keys;

    getAllWithoutLocking(bucket, keys);

    addAndLockResults(keys, clientId, results, guard);

    return results;
}

template<typename Map>
bool
LockableMap<Map>::isConsistent(const typename LockableMap<Map>::WrappedEntry& entry)
{
    std::lock_guard<std::mutex> guard(_lock);

    std::vector<BucketId::Type> keys;

    getAllWithoutLocking(entry.getBucketId(), keys);
    assert(keys.size() >= 1);
    assert(keys.size() != 1 || keys[0] == entry.getKey());

    return keys.size() == 1;
}

template<typename Map>
void
LockableMap<Map>::showLockClients(vespalib::asciistream & out) const
{
    std::lock_guard<std::mutex> guard(_lock);
    out << "Currently grabbed locks:";
    for (typename LockIdSet::const_iterator it = _lockedKeys.begin();
         it != _lockedKeys.end(); ++it)
    {
        out << "\n  "
            << BucketId(BucketId::keyToBucketId(it->_key))
            << " - " << it->_owner;
    }
    out << "\nClients waiting for keys:";
    for (typename LockWaiters::const_iterator it = _lockWaiters.begin();
         it != _lockWaiters.end(); ++it)
    {
        out << "\n  "
            << BucketId(BucketId::keyToBucketId(it->second._key))
            << " - " << it->second._owner;
    }
}

}
