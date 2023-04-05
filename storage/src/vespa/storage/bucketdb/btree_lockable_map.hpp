// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "btree_lockable_map.h"
#include "generic_btree_bucket_database.hpp"
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/datastore/datastore.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <thread>
#include <sstream>

// Major TODOs in the short term:
//  - Introduce snapshotting for readers
//  - Greatly improve performance for DB iteration for readers by avoiding
//    requirement to lock individual buckets and perform O(n) lbound seeks
//    just to do a sweep.

namespace storage::bucketdb {

using vespalib::datastore::EntryRef;
using vespalib::ConstArrayRef;
using document::BucketId;

template <typename T>
struct BTreeLockableMap<T>::ValueTraits {
    using ValueType     = T;
    using ConstValueRef = const T&;
    using DataStoreType = vespalib::datastore::DataStore<ValueType>;

    static void init_data_store(DataStoreType& store) {
        store.enableFreeLists();
    }
    static EntryRef entry_ref_from_value(uint64_t value) {
        return EntryRef(value & 0xffffffffULL);
    }
    static ValueType make_invalid_value() {
        return ValueType();
    }
    static uint64_t wrap_and_store_value(DataStoreType& store, const ValueType& value) noexcept {
        return store.addEntry(value).ref();
    }
    static void remove_by_wrapped_value(DataStoreType& store, uint64_t value) noexcept {
        store.hold_entry(entry_ref_from_value(value));
    }
    static ValueType unwrap_from_key_value(const DataStoreType& store, [[maybe_unused]] uint64_t key, uint64_t value) {
        return store.getEntry(entry_ref_from_value(value));
    }
    static ConstValueRef unwrap_const_ref_from_key_value(const DataStoreType& store, [[maybe_unused]] uint64_t key, uint64_t value) {
        return store.getEntry(entry_ref_from_value(value));
    }
};

template <typename T>
BTreeLockableMap<T>::BTreeLockableMap()
    : _impl(std::make_unique<GenericBTreeBucketDatabase<ValueTraits>>(1024/*data store array count*/))
{}

template <typename T>
BTreeLockableMap<T>::~BTreeLockableMap() = default;

template <typename T>
BTreeLockableMap<T>::LockIdSet::LockIdSet() : Hash() {}

template <typename T>
BTreeLockableMap<T>::LockIdSet::~LockIdSet() = default;

template <typename T>
size_t BTreeLockableMap<T>::LockIdSet::getMemoryUsage() const {
    return Hash::getMemoryConsumption();
}

template <typename T>
BTreeLockableMap<T>::LockWaiters::LockWaiters() : _id(0), _map() {}

template <typename T>
BTreeLockableMap<T>::LockWaiters::~LockWaiters() = default;

template <typename T>
size_t BTreeLockableMap<T>::LockWaiters::insert(const LockId & lid) {
    Key id(_id++);
    _map.insert(typename WaiterMap::value_type(id, lid));
    return id;
}

template <typename T>
bool BTreeLockableMap<T>::operator==(const BTreeLockableMap& other) const {
    std::lock_guard guard(_lock);
    std::lock_guard guard2(other._lock);
    if (_impl->size() != other._impl->size()) {
        return false;
    }
    auto lhs = _impl->begin();
    auto rhs = other._impl->begin();
    for (; lhs.valid(); ++lhs, ++rhs) {
        assert(rhs.valid());
        if (lhs.getKey() != rhs.getKey()) {
            return false;
        }
        if (_impl->const_value_ref_from_valid_iterator(lhs)
            != other._impl->const_value_ref_from_valid_iterator(rhs))
        {
            return false;
        }
    }
    return true;
}

template <typename T>
bool BTreeLockableMap<T>::operator<(const BTreeLockableMap& other) const {
    std::lock_guard guard(_lock);
    std::lock_guard guard2(other._lock);
    auto lhs = _impl->begin();
    auto rhs = other._impl->begin();
    for (; lhs.valid() && rhs.valid(); ++lhs, ++rhs) {
        if (lhs.getKey() != rhs.getKey()) {
            return (lhs.getKey() < rhs.getKey());
        }
        if (_impl->const_value_ref_from_valid_iterator(lhs)
            != other._impl->const_value_ref_from_valid_iterator(rhs))
        {
            return (_impl->const_value_ref_from_valid_iterator(lhs)
                    < other._impl->const_value_ref_from_valid_iterator(rhs));
        }
    }
    if (lhs.valid() == rhs.valid()) {
        return false; // All keys are equal in maps of equal size.
    }
    return rhs.valid(); // Rhs still valid, lhs is not; ergo lhs is "less".
}

template <typename T>
size_t BTreeLockableMap<T>::size() const noexcept {
    std::lock_guard guard(_lock);
    return _impl->size();
}

template <typename T>
size_t BTreeLockableMap<T>::getMemoryUsage() const noexcept {
    std::lock_guard guard(_lock);
    const auto impl_usage = _impl->memory_usage();
    return (impl_usage.allocatedBytes() + _lockedKeys.getMemoryUsage() +
            sizeof(std::mutex) + sizeof(std::condition_variable));
}

template <typename T>
vespalib::MemoryUsage BTreeLockableMap<T>::detailed_memory_usage() const noexcept {
    std::lock_guard guard(_lock);
    return _impl->memory_usage();
}

template <typename T>
bool BTreeLockableMap<T>::empty() const noexcept {
    std::lock_guard guard(_lock);
    return _impl->empty();
}

template <typename T>
void BTreeLockableMap<T>::swap(BTreeLockableMap& other) {
    std::lock_guard guard(_lock);
    std::lock_guard guard2(other._lock);
    _impl.swap(other._impl);
}

template <typename T>
void BTreeLockableMap<T>::acquireKey(const LockId& lid, std::unique_lock<std::mutex>& guard) {
    if (_lockedKeys.exists(lid)) {
        auto waitId = _lockWaiters.insert(lid);
        while (_lockedKeys.exists(lid)) {
            _cond.wait(guard);
        }
        _lockWaiters.erase(waitId);
    }
}

template <typename T>
typename BTreeLockableMap<T>::WrappedEntry
BTreeLockableMap<T>::get(const key_type& key, const char* clientId, bool createIfNonExisting) {
    LockId lid(key, clientId);
    std::unique_lock guard(_lock);
    acquireKey(lid, guard);
    auto iter = _impl->find(key);
    bool preExisted = iter.valid();

    if (!preExisted && createIfNonExisting) {
        _impl->update_by_raw_key(key, mapped_type());
        // TODO avoid double lookup, though this is in an unlikely branch so shouldn't matter much.
        iter = _impl->find(key);
        assert(iter.valid());
    }
    if (!iter.valid()) {
        return WrappedEntry();
    }
    _lockedKeys.insert(lid);
    return WrappedEntry(*this, key, _impl->entry_from_iterator(iter), clientId, preExisted);
}

template <typename T>
bool BTreeLockableMap<T>::erase(const key_type& key, const char* client_id, bool has_lock) {
    LockId lid(key, client_id);
    std::unique_lock guard(_lock);
    if (!has_lock) {
        acquireKey(lid, guard);
    }
    return _impl->remove_by_raw_key(key);
}

template <typename T>
void BTreeLockableMap<T>::insert(const key_type& key, const mapped_type& value,
                                 const char* clientId, bool has_lock, bool& pre_existed)
{
    LockId lid(key, clientId);
    std::unique_lock guard(_lock);
    if (!has_lock) {
        acquireKey(lid, guard);
    }
    pre_existed = _impl->update_by_raw_key(key, value);
}

template <typename T>
void BTreeLockableMap<T>::clear() {
    std::lock_guard guard(_lock);
    _impl->clear();
}

template <typename T>
bool BTreeLockableMap<T>::findNextKey(key_type& key, mapped_type& val,
                                      const char* clientId,
                                      std::unique_lock<std::mutex> &guard)
{
    // Wait for next value to unlock.
    auto it = _impl->lower_bound(key);
    while (it.valid() && _lockedKeys.exists(LockId(it.getKey(), ""))) {
        auto wait_id = _lockWaiters.insert(LockId(it.getKey(), clientId));
        _cond.wait(guard);
        _lockWaiters.erase(wait_id);
        it = _impl->lower_bound(key);
    }
    if (!it.valid()) {
        return true;
    }
    key = it.getKey();
    val = _impl->entry_from_iterator(it);
    return false;
}

template <typename T>
bool BTreeLockableMap<T>::handleDecision(key_type& key, mapped_type& val,
                                         Decision decision)
{
    switch (decision) {
    case Decision::UPDATE:
        _impl->update_by_raw_key(key, val);
        break;
    case Decision::REMOVE:
        // Invalidating is fine, since the caller doesn't hold long-lived iterators.
        _impl->remove_by_raw_key(key);
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

template <typename T>
void BTreeLockableMap<T>::do_for_each_mutable_unordered(std::function<Decision(uint64_t, mapped_type&)> func,
                                                        const char* clientId)
{
    key_type key = 0;
    mapped_type val;
    std::unique_lock guard(_lock);
    while (true) {
        if (findNextKey(key, val, clientId, guard)) {
            return;
        }
        Decision d(func(key, val));
        if (handleDecision(key, val, d)) {
            return;
        }
        ++key;
    }
}

template <typename T>
void BTreeLockableMap<T>::do_for_each(std::function<Decision(uint64_t, const mapped_type&)> func,
                                      const char* clientId)
{
    key_type key = 0;
    mapped_type val;
    std::unique_lock guard(_lock);
    while (true) {
        if (findNextKey(key, val, clientId, guard)) {
            return;
        }
        Decision d(func(key, val));
        assert(d == Decision::ABORT || d == Decision::CONTINUE);
        if (handleDecision(key, val, d)) {
            return;
        }
        ++key;
    }
}

template <typename T>
bool BTreeLockableMap<T>::processNextChunk(std::function<Decision(uint64_t, const mapped_type&)>& func,
                                           key_type& key,
                                           const char* client_id,
                                           const uint32_t chunk_size)
{
    mapped_type val;
    std::unique_lock guard(_lock);
    for (uint32_t processed = 0; processed < chunk_size; ++processed) {
        if (findNextKey(key, val, client_id, guard)) {
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

template <typename T>
void BTreeLockableMap<T>::do_for_each_chunked(std::function<Decision(uint64_t, const mapped_type&)> func,
                                              const char* client_id,
                                              vespalib::duration yield_time,
                                              uint32_t chunk_size)
{
    key_type key{};
    while (processNextChunk(func, key, client_id, chunk_size)) {
        // Rationale: delay iteration for as short a time as possible while
        // allowing another thread blocked on the main DB mutex to acquire it
        // in the meantime. Simply yielding the thread does not have the
        // intended effect with the Linux scheduler.
        // This is a pragmatic stop-gap solution; a more robust change requires
        // the redesign of bucket DB locking and signalling semantics in the
        // face of blocked point lookups.
        std::this_thread::sleep_for(yield_time);
    }
}

template <typename T>
class BTreeLockableMap<T>::ReadGuardImpl final : public bucketdb::ReadGuard<T> {
    typename ImplType::ReadSnapshot _snapshot;
public:
    explicit ReadGuardImpl(const BTreeLockableMap<T>& db);
    ~ReadGuardImpl() override;

    std::vector<T> find_parents_and_self(const document::BucketId& bucket) const override;
    std::vector<T> find_parents_self_and_children(const document::BucketId& bucket) const override;
    void for_each(std::function<void(uint64_t, const T&)> func) const override;
    std::unique_ptr<ConstIterator<const T&>> create_iterator() const override;
    [[nodiscard]] uint64_t generation() const noexcept override;
};

template <typename T>
BTreeLockableMap<T>::ReadGuardImpl::ReadGuardImpl(const BTreeLockableMap<T>& db)
    : _snapshot(*db._impl)
{}

template <typename T>
BTreeLockableMap<T>::ReadGuardImpl::~ReadGuardImpl() = default;

template <typename T>
std::vector<T>
BTreeLockableMap<T>::ReadGuardImpl::find_parents_and_self(const document::BucketId& bucket) const {
    std::vector<T> entries;
    _snapshot.template find_parents_and_self<ByConstRef>(
            bucket,
            [&entries]([[maybe_unused]] uint64_t key, const T& entry){
                entries.emplace_back(entry);
            });
    return entries;
}

template <typename T>
std::vector<T>
BTreeLockableMap<T>::ReadGuardImpl::find_parents_self_and_children(const document::BucketId& bucket) const {
    std::vector<T> entries;
    _snapshot.template find_parents_self_and_children<ByConstRef>(
            bucket,
            [&entries]([[maybe_unused]] uint64_t key, const T& entry){
                entries.emplace_back(entry);
            });
    return entries;
}

template <typename T>
void BTreeLockableMap<T>::ReadGuardImpl::for_each(std::function<void(uint64_t, const T&)> func) const {
    _snapshot.template for_each<ByConstRef>(std::move(func));
}

template <typename T>
std::unique_ptr<ConstIterator<const T&>>
BTreeLockableMap<T>::ReadGuardImpl::create_iterator() const {
    return _snapshot.create_iterator(); // TODO test
}

template <typename T>
uint64_t BTreeLockableMap<T>::ReadGuardImpl::generation() const noexcept {
    return _snapshot.generation();
}

template <typename T>
std::unique_ptr<ReadGuard<T>> BTreeLockableMap<T>::do_acquire_read_guard() const {
    return std::make_unique<ReadGuardImpl>(*this);
}

template <typename T>
void BTreeLockableMap<T>::print(std::ostream& out, bool verbose,
                                const std::string& indent) const
{
    std::lock_guard guard(_lock);
    out << "BTreeLockableMap {\n" << indent << "  ";

    if (verbose) {
        for (auto it = _impl->begin(); it.valid(); ++it) {
            out << "Key: " << BucketId(BucketId::keyToBucketId(it.getKey()))
                << " Value: " << _impl->entry_from_iterator(it) << "\n" << indent << "  ";
        }
        out << "\n" << indent << "  Locked keys: ";
        _lockedKeys.print(out, verbose, indent + "  ");
    }
    out << "} : ";
}

template <typename T>
void BTreeLockableMap<T>::LockIdSet::print(std::ostream& out, bool verbose,
                                           const std::string& indent) const
{
    out << "hash {";
    for (const auto& entry : *this) {
        if (verbose) {
            out << "\n" << indent << "  ";
        } else {
            out << " ";
        }
        out << entry;
    }
    if (verbose) {
        out << "\n" << indent;
    }
    out << " }";
}



template <typename T>
void BTreeLockableMap<T>::unlock(const key_type& key) {
    std::lock_guard guard(_lock);
    _lockedKeys.erase(LockId(key, ""));
    _cond.notify_all();
}

template <typename T>
void BTreeLockableMap<T>::addAndLockResults(
        const std::vector<BucketId::Type>& keys,
        const char* clientId,
        std::map<BucketId, WrappedEntry>& results,
        std::unique_lock<std::mutex> &guard)
{
    // Wait until all buckets are free to be added, then add them all.
    while (true) {
        bool allOk = true;
        key_type waitingFor(0);

        for (const auto key : keys) {
            if (_lockedKeys.exists(LockId(key, clientId))) {
                waitingFor = key;
                allOk = false;
                break;
            }
        }

        if (!allOk) {
            auto waitId = _lockWaiters.insert(LockId(waitingFor, clientId));
            _cond.wait(guard);
            _lockWaiters.erase(waitId);
        } else {
            for (const auto key : keys) {
                auto iter = _impl->find(key);
                if (iter.valid()) {
                    _lockedKeys.insert(LockId(key, clientId));
                    results[BucketId(BucketId::keyToBucketId(key))] = WrappedEntry(
                            *this, key, _impl->entry_from_iterator(iter), clientId, true);
                }
            }
            break;
        }
    }
}

template <typename T>
typename BTreeLockableMap<T>::EntryMap
BTreeLockableMap<T>::getContained(const BucketId& bucket,
                                  const char* clientId)
{
    std::unique_lock guard(_lock);
    std::map<BucketId, WrappedEntry> results;

    std::vector<BucketId::Type> keys;
    _impl->template find_parents_and_self<ByConstRef>(bucket, [&keys](uint64_t key, [[maybe_unused]]const auto& value){
        keys.emplace_back(key);
    });

    if (!keys.empty()) {
        addAndLockResults(keys, clientId, results, guard);
    }

    return results;
}

template <typename T>
void BTreeLockableMap<T>::getAllWithoutLocking(const BucketId& bucket,
                                               std::vector<BucketId::Type>& keys)
{
    _impl->template find_parents_self_and_children<ByConstRef>(bucket, [&keys](uint64_t key, [[maybe_unused]]const auto& value){
        keys.emplace_back(key);
    });
}

/**
 * Returns the given bucket, its super buckets and its sub buckets.
 */
template <typename T>
typename BTreeLockableMap<T>::EntryMap
BTreeLockableMap<T>::getAll(const BucketId& bucket, const char* clientId) {
    std::unique_lock guard(_lock);

    std::map<BucketId, WrappedEntry> results;
    std::vector<BucketId::Type> keys;

    getAllWithoutLocking(bucket, keys);
    addAndLockResults(keys, clientId, results, guard);

    return results;
}

template <typename T>
bool BTreeLockableMap<T>::isConsistent(const BTreeLockableMap::WrappedEntry& entry) {
    std::lock_guard guard(_lock);
    uint64_t n_buckets = 0;
    _impl->template find_parents_self_and_children<ByConstRef>(entry.getBucketId(),
            [&n_buckets]([[maybe_unused]] uint64_t key, [[maybe_unused]] const auto& value) {
                ++n_buckets;
            });
    return (n_buckets == 1);
}

template <typename T>
void BTreeLockableMap<T>::showLockClients(vespalib::asciistream& out) const {
    std::lock_guard guard(_lock);
    out << "Currently grabbed locks:";
    for (const auto& locked : _lockedKeys) {
        out << "\n  "
            << BucketId(BucketId::keyToBucketId(locked._key))
            << " - " << locked._owner;
    }
    out << "\nClients waiting for keys:";
    for (const auto& waiter : _lockWaiters) {
        out << "\n  "
            << BucketId(BucketId::keyToBucketId(waiter.second._key))
            << " - " << waiter.second._owner;
    }
}

}
