// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "striped_btree_lockable_map.h"
#include "btree_lockable_map.hpp"
#include <vespa/storage/common/bucket_stripe_utils.h>
#include <algorithm>
#include <cassert>
#include <queue>

namespace storage::bucketdb {

template <typename T>
StripedBTreeLockableMap<T>::StripedBTreeLockableMap(uint8_t n_stripe_bits)
    : _n_stripe_bits(n_stripe_bits),
      _n_stripes(1ULL << _n_stripe_bits),
      _stripes()
{
    assert(_n_stripe_bits > 0);
    assert(_n_stripe_bits <= MaxStripeBits);
    _stripes.reserve(_n_stripes);
    for (size_t i = 0; i < _n_stripes; ++i) {
        // TODO reduce initial sub-DB data store memory usage based on number of stripes
        _stripes.emplace_back(std::make_unique<BTreeLockableMap<T>>());
    }
}

template <typename T>
StripedBTreeLockableMap<T>::~StripedBTreeLockableMap() = default;

template <typename T>
size_t StripedBTreeLockableMap<T>::stripe_of(key_type key) const noexcept {
    return stripe_of_bucket_key(key, _n_stripe_bits);
}

template <typename T>
typename StripedBTreeLockableMap<T>::StripedDBType&
StripedBTreeLockableMap<T>::db_for(key_type key) noexcept {
    return *_stripes[stripe_of(key)];
}

template <typename T>
const typename StripedBTreeLockableMap<T>::StripedDBType&
StripedBTreeLockableMap<T>::db_for(key_type key) const noexcept {
    return *_stripes[stripe_of(key)];
}

template <typename T>
size_t StripedBTreeLockableMap<T>::size() const noexcept {
    size_t sz = 0;
    for (auto& s : _stripes) {
        sz += s->size();
    }
    return sz;
}

template <typename T>
size_t StripedBTreeLockableMap<T>::getMemoryUsage() const noexcept {
    size_t mem_usage = 0;
    for (auto& s : _stripes) {
        mem_usage += s->getMemoryUsage();
    }
    return mem_usage;
}

template <typename T>
vespalib::MemoryUsage StripedBTreeLockableMap<T>::detailed_memory_usage() const noexcept {
    vespalib::MemoryUsage mem_usage;
    for (auto& s : _stripes) {
        mem_usage.merge(s->detailed_memory_usage());
    }
    return mem_usage;
}

template <typename T>
bool StripedBTreeLockableMap<T>::empty() const noexcept {
    return std::all_of(_stripes.begin(), _stripes.end(), [](auto& s){ return s->empty(); });
}

template <typename T>
typename StripedBTreeLockableMap<T>::WrappedEntry
StripedBTreeLockableMap<T>::get(const key_type& key, const char* clientId, bool createIfNonExisting) {
    return db_for(key).get(key, clientId, createIfNonExisting);
}

template <typename T>
bool StripedBTreeLockableMap<T>::erase(const key_type& key, const char* client_id, bool has_lock) {
    return db_for(key).erase(key, client_id, has_lock);
}

template <typename T>
void StripedBTreeLockableMap<T>::insert(const key_type& key, const mapped_type& value,
                                        const char* clientId, bool has_lock, bool& pre_existed)
{
    db_for(key).insert(key, value, clientId, has_lock, pre_existed);
}

template <typename T>
void StripedBTreeLockableMap<T>::clear() {
    for (auto& s : _stripes) {
        s->clear();
    }
}

template <typename T>
void StripedBTreeLockableMap<T>::print(std::ostream& out, bool verbose,
                                       const std::string& indent) const
{
    // TODO more wrapped printing?
    for (auto& s : _stripes) {
        s->print(out, verbose, indent);
    }
}

template <typename T>
typename StripedBTreeLockableMap<T>::EntryMap
StripedBTreeLockableMap<T>::getContained(const BucketId& bucket, const char* clientId) {
    return db_for(bucket.toKey()).getContained(bucket, clientId);
}

template <typename T>
typename StripedBTreeLockableMap<T>::EntryMap
StripedBTreeLockableMap<T>::getAll(const BucketId& bucket, const char* clientId) {
    return db_for(bucket.toKey()).getAll(bucket, clientId);
}

template <typename T>
bool StripedBTreeLockableMap<T>::isConsistent(const StripedBTreeLockableMap::WrappedEntry& entry) const {
    return db_for(entry.getKey()).isConsistent(entry);
}

template <typename T>
void StripedBTreeLockableMap<T>::showLockClients(vespalib::asciistream& out) const {
    for (auto& s : _stripes) {
        s->showLockClients(out);
    }
}

template <typename T>
void StripedBTreeLockableMap<T>::do_for_each_mutable_unordered(std::function<Decision(uint64_t, mapped_type&)> func,
                                                               const char* client_id)
{
    // This is by definition unordered in terms of bucket keys
    for (auto& stripe : _stripes) {
        // TODO pass functor by ref instead
        stripe->for_each_mutable_unordered(func, client_id);
    }
}

template <typename T>
void StripedBTreeLockableMap<T>::unlock(const key_type& key) {
    db_for(key).unlock(key);
}

template <typename T>
void StripedBTreeLockableMap<T>::do_for_each(std::function<Decision(uint64_t, const mapped_type&)> func,
                                             [[maybe_unused]] const char* client_id)
{
    auto guard = do_acquire_read_guard();
    for (auto iter = guard->create_iterator(); iter->valid(); iter->next()) {
        if (func(iter->key(), iter->value()) != Decision::CONTINUE) {
            break;
        }
    }
}

template <typename T>
void StripedBTreeLockableMap<T>::do_for_each_chunked(std::function<Decision(uint64_t, const mapped_type&)> func,
                                                     const char* client_id,
                                                     [[maybe_unused]] vespalib::duration yield_time,
                                                     [[maybe_unused]] uint32_t chunk_size)
{
    do_for_each(std::move(func), client_id);
}

template <typename T>
class StripedBTreeLockableMap<T>::ReadGuardImpl final
    : public bucketdb::ReadGuard<T, const T&>
{
    const StripedBTreeLockableMap<T>& _db;
    // There is a 1-1 relationship between DB stripes and guards.
    // This is essential to be able to choose the correct guard.
    std::vector<std::unique_ptr<bucketdb::ReadGuard<T, const T&>>> _stripe_guards;
public:
    explicit ReadGuardImpl(const StripedBTreeLockableMap<T>& db);
    ~ReadGuardImpl() override;

    std::vector<T> find_parents_and_self(const document::BucketId& bucket) const override;
    std::vector<T> find_parents_self_and_children(const document::BucketId& bucket) const override;
    void for_each(std::function<void(uint64_t, const T&)> func) const override;
    std::unique_ptr<ConstIterator<const T&>> create_iterator() const override;
    [[nodiscard]] uint64_t generation() const noexcept override { return 0; /*TODO*/ }
};

template <typename T>
StripedBTreeLockableMap<T>::ReadGuardImpl::ReadGuardImpl(const StripedBTreeLockableMap<T>& db)
    : _db(db),
      _stripe_guards()
{
    _stripe_guards.reserve(_db._stripes.size());
    for (auto& s : _db._stripes) {
        _stripe_guards.emplace_back(s->acquire_read_guard());
    }
}

template <typename T>
StripedBTreeLockableMap<T>::ReadGuardImpl::~ReadGuardImpl() = default;

template <typename T>
std::vector<T>
StripedBTreeLockableMap<T>::ReadGuardImpl::find_parents_and_self(const document::BucketId& bucket) const {
    return _stripe_guards[_db.stripe_of(bucket.toKey())]->find_parents_and_self(bucket);
}

template <typename T>
std::vector<T>
StripedBTreeLockableMap<T>::ReadGuardImpl::find_parents_self_and_children(const document::BucketId& bucket) const {
    return _stripe_guards[_db.stripe_of(bucket.toKey())]->find_parents_self_and_children(bucket);
}

template <typename T>
void StripedBTreeLockableMap<T>::ReadGuardImpl::for_each(std::function<void(uint64_t, const T&)> func) const {
    for (auto iter = create_iterator(); iter->valid(); iter->next()) {
        func(iter->key(), iter->value());
    }
}

template <typename T>
std::unique_ptr<ReadGuard<T>> StripedBTreeLockableMap<T>::do_acquire_read_guard() const {
    return std::make_unique<ReadGuardImpl>(*this);
}

namespace {

template <typename T> using Iter = ConstIterator<const T&>;
template <typename T> using KeyAndIterPtr = std::pair<uint64_t, Iter<T>*>;

template <typename T>
struct CompareFirstGreater {
    bool operator()(const KeyAndIterPtr<T>& lhs, const KeyAndIterPtr<T>& rhs) const noexcept {
        return (lhs.first > rhs.first);
    }
};

template <typename T>
class ConstIteratorImpl final : public ConstIterator<const T&> {
    using PriorityQueueType = std::priority_queue<KeyAndIterPtr<T>,
                                                  std::vector<KeyAndIterPtr<T>>,
                                                  CompareFirstGreater<T>>;
    // This is pretty heavy weight, but this iterator is only used for full DB sweeps
    // used by background maintenance operations, not by any realtime traffic.
    std::vector<std::unique_ptr<Iter<T>>> _iters;
    PriorityQueueType                     _iter_queue;
public:
    // Precondition: all iterators must be initially valid
    explicit ConstIteratorImpl(std::vector<std::unique_ptr<Iter<T>>> iters)
        : _iters(std::move(iters))
    {
        for (auto& iter : _iters) {
            _iter_queue.push(std::make_pair(iter->key(), iter.get()));
        }
    }

    void next() noexcept override {
        assert(!_iter_queue.empty());
        auto* top_iter = _iter_queue.top().second;
        top_iter->next();
        _iter_queue.pop();
        if (top_iter->valid()) {
            _iter_queue.push(std::make_pair(top_iter->key(), top_iter));
        }
    }

    bool valid() const noexcept override {
        return !_iter_queue.empty();
    }

    uint64_t key() const noexcept override {
        return _iter_queue.top().first;
    }

    const T& value() const override {
        return _iter_queue.top().second->value();
    }
};

}

template <typename T>
std::unique_ptr<ConstIterator<const T&>>
StripedBTreeLockableMap<T>::ReadGuardImpl::create_iterator() const {
    std::vector<std::unique_ptr<Iter<T>>> iters;
    iters.reserve(_db._n_stripes);
    for (auto& g : _stripe_guards) {
        auto iter = g->create_iterator();
        if (!iter->valid()) {
            continue;
        }
        iters.emplace_back(std::move(iter));
    }
    return std::make_unique<ConstIteratorImpl<T>>(std::move(iters));
}

}
