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

#include <map>
#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/document/bucket/bucketid.h>
#include <mutex>
#include <condition_variable>
#include <cassert>

namespace storage {

template<typename Map>
class LockableMap : public vespalib::Printable
{
public:
    typedef typename Map::key_type key_type;
    typedef typename Map::mapped_type mapped_type;
    typedef typename Map::value_type value_type;
    typedef typename Map::size_type size_type;
    using BucketId = document::BucketId;
    struct WrappedEntry;

    /** Responsible for releasing lock in map when out of scope. */
    class LockKeeper {
        friend struct WrappedEntry;
        LockableMap<Map>& _map;
        key_type _key;
        bool _locked;

        LockKeeper(LockableMap<Map>& map, key_type key)
            : _map(map), _key(key), _locked(true) {}
        void unlock() { _map.unlock(_key); _locked = false;}
    public:
        ~LockKeeper() { if (_locked) unlock(); }
    };

    struct WrappedEntry {
        WrappedEntry() : _exists(false), _lockKeeper(), _value() {}
        WrappedEntry(WrappedEntry &&) = default;
        WrappedEntry & operator = (WrappedEntry &&) = default;
        ~WrappedEntry() { }

        mapped_type* operator->() { return &_value; }
        const mapped_type* operator->() const { return &_value; }
        mapped_type& operator*() { return _value; }
        const mapped_type& operator*() const { return _value; }

        const mapped_type *get() const { return &_value; }
        mapped_type *get() { return &_value; }

        void write();
        void remove();
        void unlock();
        bool exist() const { return _exists; }
        bool preExisted() const { return _preExisted; }
        bool locked() const { return _lockKeeper.get(); }
        const key_type& getKey() const { return _lockKeeper->_key; };

        BucketId getBucketId() const {
            return BucketId(BucketId::keyToBucketId(getKey()));
        }

    protected:
        WrappedEntry(LockableMap<Map>& map,
                     const key_type& key, const mapped_type& val,
                     const char* clientId, bool preExisted_)
            : _exists(true),
              _preExisted(preExisted_),
              _lockKeeper(new LockKeeper(map, key)),
              _value(val),
              _clientId(clientId) {}
        WrappedEntry(LockableMap<Map>& map, const key_type& key,
                     const char* clientId)
            : _exists(false),
              _preExisted(false),
              _lockKeeper(new LockKeeper(map, key)),
              _value(),
              _clientId(clientId) {}

        bool _exists;
        bool _preExisted;
        std::unique_ptr<LockKeeper> _lockKeeper;
        mapped_type _value;
        const char* _clientId;
        friend class LockableMap<Map>;
    };

    struct LockId {
        key_type _key;
        const char* _owner;

        LockId() : _key(0), _owner("none - empty token") {}
        LockId(key_type key, const char* owner)
            : _key(key), _owner(owner)
        {
            assert(_owner != 0);
        }

        size_t hash() const { return _key; }
        size_t operator%(size_t val) const { return _key % val; }
        bool operator==(const LockId& id) const { return (_key == id._key); }
        operator key_type() const { return _key; }
    };

    LockableMap();
    ~LockableMap();
    bool operator==(const LockableMap& other) const;
    bool operator!=(const LockableMap& other) const {
        return ! (*this == other);
    }
    bool operator<(const LockableMap& other) const;
    typename Map::size_type size() const;
    size_type getMemoryUsage() const;
    bool empty() const;
    void swap(LockableMap&);

    WrappedEntry get(const key_type& key, const char* clientId,
                     bool createIfNonExisting = false,
                     bool lockIfNonExistingAndNotCreating = false);
    bool erase(const key_type& key, const char* clientId)
        { return erase(key, clientId, false); }
    void insert(const key_type& key, const mapped_type& value,
                const char* clientId, bool& preExisted)
        { return insert(key, value, clientId, false, preExisted); }
    void clear();

    enum Decision { ABORT, UPDATE, REMOVE, CONTINUE, DECISION_COUNT };

    template<typename Functor>
    void each(Functor& functor, const char* clientId,
              const key_type& first = key_type(),
              const key_type& last = key_type() - 1 );

    template<typename Functor>
    void each(const Functor& functor, const char* clientId,
              const key_type& first = key_type(),
              const key_type& last = key_type() - 1 );

    template<typename Functor>
    void all(Functor& functor, const char* clientId,
             const key_type& first = key_type(),
             const key_type& last = key_type()-1);

    template<typename Functor>
    void all(const Functor& functor, const char* clientId,
             const key_type& first = key_type(),
             const key_type& last = key_type() - 1 );

    static constexpr uint32_t DEFAULT_CHUNK_SIZE = 10000;

    /**
     * Iterate over the entire database contents, holding the global database
     * mutex for `chunkSize` processed entries at a time, yielding the current
     * thread between each such such to allow other threads to get a chance
     * at acquiring a bucket lock.
     */
    template <typename Functor>
    void chunkedAll(Functor& functor,
                    const char* clientId,
                    uint32_t chunkSize = DEFAULT_CHUNK_SIZE);

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    /**
     * Returns all buckets in the bucket database that can contain the given
     * bucket. Usually, there should be only one such bucket, but in the case
     * of inconsistent splitting, there may be more than one.
     */
    std::map<BucketId, WrappedEntry>
    getContained(const BucketId& bucketId, const char* clientId);

    WrappedEntry
    createAppropriateBucket(uint16_t newBucketBits,
                            const char* clientId,
                            const BucketId& bucket);

    typedef std::map<BucketId, WrappedEntry> EntryMap;

    /**
     * Returns all buckets in the bucket database that can contain the given
     * bucket, and all buckets that that bucket contains.
     *
     * If sibling is != 0, also fetch that bucket if possible.
     */
    EntryMap getAll(const BucketId& bucketId, const char* clientId, const BucketId& sibling = BucketId(0));

    /**
     * Returns true iff bucket has no superbuckets or sub-buckets in the
     * database. Usage assumption is that any operation that can cause the
     * bucket to become inconsistent will require taking its lock, so by
     * requiring the lock to be provided here we avoid race conditions.
     */
    bool isConsistent(const WrappedEntry& entry);

    void showLockClients(vespalib::asciistream & out) const;

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
        bool exist(const LockId & lid) const { return this->find(lid) != Hash::end(); }
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

    Map               _map;
    mutable std::mutex      _lock;
    std::condition_variable _cond;
    LockIdSet         _lockedKeys;
    LockWaiters       _lockWaiters;

    bool erase(const key_type& key, const char* clientId, bool haslock);
    void insert(const key_type& key, const mapped_type& value,
                const char* clientId, bool haslock, bool& preExisted);
    void unlock(const key_type& key);
    bool findNextKey(key_type& key, mapped_type& val, const char* clientId,
                     std::unique_lock<std::mutex> &guard);
    bool handleDecision(key_type& key, mapped_type& val, Decision decision);
    void acquireKey(const LockId & lid, std::unique_lock<std::mutex> &guard);

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
    template <typename Functor>
    bool processNextChunk(Functor& functor,
                          key_type& key,
                          const char* clientId,
                          const uint32_t chunkSize);

    /**
     * Returns the given bucket, its super buckets and its sub buckets.
     */
    void getAllWithoutLocking(const BucketId& bucket,
                              const BucketId& sibling,
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
    void addAndLockResults(const std::vector<BucketId::Type> keys,
                           const char* clientId,
                           std::map<BucketId, WrappedEntry>& results,
                           std::unique_lock<std::mutex> &guard);
};

} // storage

