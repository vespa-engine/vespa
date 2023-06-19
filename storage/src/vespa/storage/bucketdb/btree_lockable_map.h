// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "abstract_bucket_map.h"
#include "storagebucketinfo.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <map>
#include <memory>
#include <mutex>
#include <condition_variable>
#include <cassert>
#include <iosfwd>

namespace storage::bucketdb {

template <typename DataStoreTraitsT> class GenericBTreeBucketDatabase;

/*
 * AbstractBucketMap implementation that uses a B-tree bucket database backing structure.
 *
 * Identical global and per-bucket locking semantics as LockableMap.
 */
template <typename T>
class BTreeLockableMap final : public AbstractBucketMap<T> {
    struct ValueTraits;
public:
    using ParentType   = AbstractBucketMap<T>;
    using WrappedEntry = typename ParentType::WrappedEntry;
    using key_type     = typename ParentType::key_type;
    using mapped_type  = typename ParentType::mapped_type;
    using LockId       = typename ParentType::LockId;
    using EntryMap     = typename ParentType::EntryMap;
    using Decision     = typename ParentType::Decision;
    using BucketId     = document::BucketId;

    BTreeLockableMap();
    ~BTreeLockableMap() override;

    size_t size() const noexcept override;
    size_t getMemoryUsage() const noexcept override;
    vespalib::MemoryUsage detailed_memory_usage() const noexcept override;
    bool empty() const noexcept override;
    void swap(BTreeLockableMap&);

    WrappedEntry get(const key_type& key, const char* clientId, bool createIfNonExisting) override;
    WrappedEntry get(const key_type& key, const char* clientId) {
        return get(key, clientId, false);
    }
    bool erase(const key_type& key, const char* clientId, bool has_lock) override;
    void insert(const key_type& key, const mapped_type& value,
                const char* client_id, bool has_lock, bool& pre_existed) override;

    bool erase(const key_type& key, const char* client_id) {
        return erase(key, client_id, false);
    }
    void insert(const key_type& key, const mapped_type& value,
                const char* client_id, bool& pre_existed) {
        return insert(key, value, client_id, false, pre_existed);
    }
    void clear();
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    EntryMap getContained(const BucketId& bucketId, const char* clientId) override;
    EntryMap getAll(const BucketId& bucketId, const char* clientId) override;
    bool isConsistent(const WrappedEntry& entry) const override;
    void showLockClients(vespalib::asciistream & out) const override;

private:
    template <typename T1> friend class StripedBTreeLockableMap;

    struct hasher {
        size_t operator () (const LockId & lid) const { return lid.hash(); }
    };
    class LockIdSet : public vespalib::hash_set<LockId, hasher> {
        using Hash = vespalib::hash_set<LockId, hasher>;
    public:
        LockIdSet();
        ~LockIdSet();
        void print(std::ostream& out, bool verbose, const std::string& indent) const;
        bool exists(const LockId & lid) const { return this->find(lid) != Hash::end(); }
        size_t getMemoryUsage() const;
    };

    class LockWaiters {
        using WaiterMap = vespalib::hash_map<size_t, LockId>;
    public:
        using Key = size_t;
        using const_iterator = typename WaiterMap::const_iterator;
        LockWaiters();
        ~LockWaiters();
        Key insert(const LockId & lid);
        void erase(Key id) { _map.erase(id); }
        const_iterator begin() const { return _map.begin(); }
        const_iterator end() const { return _map.end(); }
    private:
        Key       _id;
        WaiterMap _map;
    };

    class ReadGuardImpl;
    using ImplType = GenericBTreeBucketDatabase<ValueTraits>;

    mutable std::mutex        _lock;
    std::condition_variable   _cond;
    std::unique_ptr<ImplType> _impl;
    LockIdSet                 _lockedKeys;
    LockWaiters               _lockWaiters;

    void unlock(const key_type& key) override;
    bool findNextKey(key_type& key, mapped_type& val, const char* clientId,
                     std::unique_lock<std::mutex> &guard);
    bool handleDecision(key_type& key, mapped_type& val, Decision decision);
    void acquireKey(const LockId & lid, std::unique_lock<std::mutex> &guard);

    void do_for_each_mutable_unordered(std::function<Decision(uint64_t, mapped_type&)> func,
                                       const char* clientId) override;

    void do_for_each(std::function<Decision(uint64_t, const mapped_type&)> func,
                     const char* clientId) override;

    void do_for_each_chunked(std::function<Decision(uint64_t, const mapped_type&)> func,
                             const char* client_id,
                             vespalib::duration yield_time,
                             uint32_t chunk_size) override;

    std::unique_ptr<ReadGuard<T>> do_acquire_read_guard() const override;

    /**
     * Process up to `chunk_size` bucket database entries from--and possibly
     * including--the bucket pointed to by `key`.
     *
     * Returns true if additional chunks may be processed after the call to
     * this function has returned, false if iteration has completed or if
     * `func` returned an abort-decision.
     *
     * Modifies `key` in-place to point to the next key to process for the next
     * invocation of this function.
     */
    bool processNextChunk(std::function<Decision(uint64_t, const mapped_type&)>& func,
                          key_type& key,
                          const char* client_id,
                          uint32_t chunk_size);

    /**
     * Returns the given bucket, its super buckets and its sub buckets.
     */
    void getAllWithoutLocking(const BucketId& bucket,
                              std::vector<BucketId::Type>& keys);

    /**
     * Find the given list of keys in the map and add them to the map of
     * results, locking them in the process.
     */
    void addAndLockResults(const std::vector<BucketId::Type>& keys,
                           const char* clientId,
                           std::map<BucketId, WrappedEntry>& results,
                           std::unique_lock<std::mutex> &guard);
};

}
