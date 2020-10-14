// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * A map wrapper, adding locking to the map entries. It provides the
 * following:
 *
 *   - Guarantees thread safety.
 *   - Each returned value is given within a wrapper. As long as the
 *     wrapper for the value exist, this entry is locked in the map.
 *     This does not prevent other values from being used. Wrappers can
 *     be copied. Reference counting ensures value is locked until last
 *     wrapper copy dies.
 *   - Built in function for iterating taking a functor. Halts when
 *     encountering locked values.
 */
#pragma once

#include "abstract_bucket_map.h"
#include <map>
#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/time.h>
#include <mutex>
#include <condition_variable>
#include <cassert>

namespace storage {

template <typename Map>
class LockableMap final
    : public bucketdb::AbstractBucketMap<typename Map::mapped_type>
{
public:
    using ParentType   = bucketdb::AbstractBucketMap<typename Map::mapped_type>;
    using WrappedEntry = typename ParentType::WrappedEntry;
    using key_type     = typename ParentType::key_type;
    using mapped_type  = typename ParentType::mapped_type;
    using LockId       = typename ParentType::LockId;
    using EntryMap     = typename ParentType::EntryMap;
    using Decision     = typename ParentType::Decision;
    using BucketId     = document::BucketId;

    LockableMap();
    ~LockableMap();
    bool operator==(const LockableMap& other) const;
    bool operator!=(const LockableMap& other) const {
        return ! (*this == other);
    }
    bool operator<(const LockableMap& other) const;
    size_t size() const noexcept override;
    size_t getMemoryUsage() const noexcept override;
    vespalib::MemoryUsage detailed_memory_usage() const noexcept override;
    bool empty() const noexcept override;
    void swap(LockableMap&);

    WrappedEntry get(const key_type& key, const char* clientId, bool createIfNonExisting) override;
    WrappedEntry get(const key_type& key, const char* clientId) {
        return get(key, clientId, false);
    }

    bool erase(const key_type& key, const char* clientId, bool haslock) override;
    void insert(const key_type& key, const mapped_type& value,
                const char* clientId, bool haslock, bool& preExisted) override;

    bool erase(const key_type& key, const char* clientId)
        { return erase(key, clientId, false); }
    void insert(const key_type& key, const mapped_type& value,
                const char* clientId, bool& preExisted)
        { return insert(key, value, clientId, false, preExisted); }
    void clear();

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    /**
     * Returns all buckets in the bucket database that can contain the given
     * bucket. Usually, there should be only one such bucket, but in the case
     * of inconsistent splitting, there may be more than one.
     */
    EntryMap getContained(const BucketId& bucketId, const char* clientId) override;

    /**
     * Returns all buckets in the bucket database that can contain the given
     * bucket, and all buckets that that bucket contains.
     */
    EntryMap getAll(const BucketId& bucketId, const char* clientId) override;

    /**
     * Returns true iff bucket has no superbuckets or sub-buckets in the
     * database. Usage assumption is that any operation that can cause the
     * bucket to become inconsistent will require taking its lock, so by
     * requiring the lock to be provided here we avoid race conditions.
     */
    bool isConsistent(const WrappedEntry& entry) override;

    void showLockClients(vespalib::asciistream & out) const override;

private:
    struct hasher {
        size_t operator () (const LockId & lid) const { return lid.hash(); }
    };
    class LockIdSet : public vespalib::hash_set<LockId, hasher> {
        typedef vespalib::hash_set<LockId, hasher> Hash;
    public:
        LockIdSet();
        ~LockIdSet();
        void print(std::ostream& out, bool verbose, const std::string& indent) const;
        bool exist(const LockId& lid) const { return this->find(lid) != Hash::end(); }
        size_t getMemoryUsage() const;
    };

    class LockWaiters {
        typedef vespalib::hash_map<size_t, LockId> WaiterMap;
    public:
        typedef size_t Key;
        typedef typename WaiterMap::const_iterator const_iterator;
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

    Map                     _map;
    mutable std::mutex      _lock;
    std::condition_variable _cond;
    LockIdSet               _lockedKeys;
    LockWaiters             _lockWaiters;

    void unlock(const key_type& key) override;
    bool findNextKey(key_type& key, mapped_type& val, const char* clientId,
                     std::unique_lock<std::mutex> &guard);
    bool handleDecision(key_type& key, mapped_type& val, Decision decision);
    void acquireKey(const LockId & lid, std::unique_lock<std::mutex> &guard);

    void do_for_each_mutable_unordered(std::function<Decision(uint64_t, mapped_type&)> func,
                                       const char* clientId) override;

    void do_for_each(std::function<Decision(uint64_t, const mapped_type&)> func,
                     const char* clientId,
                     const key_type& first,
                     const key_type& last) override;

    void do_for_each_chunked(std::function<Decision(uint64_t, const mapped_type&)> func,
                             const char* clientId,
                             vespalib::duration yieldTime,
                             uint32_t chunkSize) override;

    std::unique_ptr<bucketdb::ReadGuard<typename Map::mapped_type>> do_acquire_read_guard() const override;

    /**
     * Process up to `chunkSize` bucket database entries from--and possibly
     * including--the bucket pointed to by `key`.
     *
     * Returns true if additional chunks may be processed after the call to
     * this function has returned, false if iteration has completed or if
     * `functor` returned an abort-decision.
     *
     * Modifies `key` in-place to point to the next key to process for the next
     * invocation of this function.
     */
    bool processNextChunk(std::function<Decision(uint64_t, const mapped_type&)>& func,
                          key_type& key,
                          const char* clientId,
                          uint32_t chunkSize);

    /**
     * Returns the given bucket, its super buckets and its sub buckets.
     */
    void getAllWithoutLocking(const BucketId& bucket,
                              std::vector<BucketId::Type>& keys);

    /**
     * Retrieves the most specific bucket id (highest used bits) that matches
     * the given bucket.
     *
     * If a match is found, result is set to the bucket id found, and keyResult
     * is set to the corresponding key (reversed)
     *
     * If not found, nextKey is set to the key after one that could have
     * matched and we return false.
     */
    bool getMostSpecificMatch(const BucketId& bucket,
                              BucketId& result,
                              BucketId::Type& keyResult,
                              BucketId::Type& nextKey);

    /**
     * Finds all buckets that can contain the given bucket, except for the
     * bucket itself (that is, its super buckets)
     */
    void getAllContaining(const BucketId& bucket,
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

} // storage

