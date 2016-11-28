// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <list>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <thread>
#include <chrono>

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

    /** Responsible for releasing lock in map when out of scope. */
    class LockKeeper {
        friend class LockableMap<Map>::WrappedEntry;
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

        mapped_type* operator->() { return &_value; }
        const mapped_type* operator->() const { return &_value; }
        mapped_type& operator*() { return _value; }
        const mapped_type& operator*() const { return _value; }
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
        vespalib::LinkedPtr<LockKeeper> _lockKeeper;
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

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

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
        LockIdSet() : Hash() { }
        void print(std::ostream& out, bool verbose, const std::string& indent) const;
        bool exist(const LockId & lid) const { return this->find(lid) != Hash::end(); }
        size_t getMemoryUsage() const { return Hash::getMemoryConsumption(); }
    };

    class LockWaiters {
        typedef vespalib::hash_map<size_t, LockId> WaiterMap;
    public:
        typedef size_t Key;
        typedef typename WaiterMap::const_iterator const_iterator;
        LockWaiters() : _id(0), _map() { }
        Key insert(const LockId & lid) {
            Key id(_id++);
            _map.insert(typename WaiterMap::value_type(id, lid));
            return id;
        }
        void erase(Key id) { _map.erase(id); }
        const_iterator begin() const { return _map.begin(); }
        const_iterator end() const { return _map.end(); }
    private:
        Key       _id;
        WaiterMap _map;
    };

    Map               _map;
    vespalib::Monitor _lock;
    LockIdSet         _lockedKeys;
    LockWaiters       _lockWaiters;

    bool erase(const key_type& key, const char* clientId, bool haslock);
    void insert(const key_type& key, const mapped_type& value,
                const char* clientId, bool haslock, bool& preExisted);
    void unlock(const key_type& key);
    bool findNextKey(key_type& key, mapped_type& val, const char* clientId,
                     vespalib::MonitorGuard& guard);
    bool handleDecision(key_type& key, mapped_type& val, Decision decision);
    void ackquireKey(const LockId & lid, vespalib::MonitorGuard & guard);

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
                           vespalib::MonitorGuard& guard);
};

template<typename Map>
void
LockableMap<Map>::WrappedEntry::write()
{
    assert(_lockKeeper->_locked);
    assert(_value.verifyLegal());
    bool b;
    _lockKeeper->_map.insert(_lockKeeper->_key, _value, _clientId, true, b);
    _lockKeeper->unlock();
}

template<typename Map>
void
LockableMap<Map>::WrappedEntry::remove()
{
    assert(_lockKeeper->_locked);
    assert(_exists);
    _lockKeeper->_map.erase(_lockKeeper->_key, _clientId, true);
    _lockKeeper->unlock();
}

template<typename Map>
void
LockableMap<Map>::WrappedEntry::unlock()
{
    assert(_lockKeeper->_locked);
    _lockKeeper->unlock();
}

template<typename Map>
LockableMap<Map>::LockableMap()
    : _map(),
      _lock(),
      _lockedKeys(),
      _lockWaiters() {}

template<typename Map>
bool
LockableMap<Map>::operator==(const LockableMap<Map>& other) const
{
    vespalib::LockGuard guard(_lock);
    vespalib::LockGuard guard2(other._lock);
    return (_map == other._map);
}

template<typename Map>
bool
LockableMap<Map>::operator<(const LockableMap<Map>& other) const
{
    vespalib::LockGuard guard(_lock);
    vespalib::LockGuard guard2(other._lock);
    return (_map < other._map);
}

template<typename Map>
typename Map::size_type
LockableMap<Map>::size() const
{
    vespalib::LockGuard guard(_lock);
    return _map.size();
}

template<typename Map>
typename Map::size_type
LockableMap<Map>::getMemoryUsage() const
{
    vespalib::MonitorGuard guard(_lock);
    return _map.getMemoryUsage()
            + _lockedKeys.getMemoryUsage()
            + sizeof(vespalib::Monitor);
}

template<typename Map>
bool
LockableMap<Map>::empty() const
{
    vespalib::LockGuard guard(_lock);
    return _map.empty();
}

template<typename Map>
void
LockableMap<Map>::swap(LockableMap<Map>& other)
{
    vespalib::LockGuard guard(_lock);
    vespalib::LockGuard guard2(other._lock);
    return _map.swap(other._map);
}

template<typename Map>
void LockableMap<Map>::ackquireKey(const LockId & lid, vespalib::MonitorGuard & guard)
{
    if (_lockedKeys.exist(lid)) {
        typename LockWaiters::Key waitId(_lockWaiters.insert(lid));
        while (_lockedKeys.exist(lid)) {
            guard.wait();
        }
        _lockWaiters.erase(waitId);
    }
}

template<typename Map>
typename LockableMap<Map>::WrappedEntry
LockableMap<Map>::get(const key_type& key, const char* clientId,
                      bool createIfNonExisting,
                      bool lockIfNonExistingAndNotCreating)
{
    LockId lid(key, clientId);
    vespalib::MonitorGuard guard(_lock);
    ackquireKey(lid, guard);
    bool preExisted = false;
    typename Map::iterator it =
        _map.find(key, createIfNonExisting, preExisted);

    if (it == _map.end()) {
        if (lockIfNonExistingAndNotCreating) {
            return WrappedEntry(*this, key, clientId);
        } else {
            return WrappedEntry();
        }
    }
    _lockedKeys.insert(lid);
    return WrappedEntry(*this, key, it->second, clientId, preExisted);
}

#ifdef ENABLE_BUCKET_OPERATION_LOGGING

namespace bucketdb {
struct StorageBucketInfo;
struct BucketInfo;
}

namespace debug {

template <typename T> struct TypeTag {};
// Storage
void logBucketDbInsert(uint64_t key, const bucketdb::StorageBucketInfo& entry);
void logBucketDbErase(uint64_t key, const TypeTag<bucketdb::StorageBucketInfo>&);

// Distributor
void logBucketDbInsert(uint64_t key, const bucketdb::BucketInfo& entry);
void logBucketDbErase(uint64_t key, const TypeTag<bucketdb::BucketInfo>&);

template <typename DummyValue>
inline void logBucketDbErase(uint64_t, const TypeTag<DummyValue>&) {}
template <typename DummyKey, typename DummyValue>
inline void logBucketDbInsert(const DummyKey&, const DummyValue&) {}

}

#endif // ENABLE_BUCKET_OPERATION_LOGGING

template<typename Map>
bool
LockableMap<Map>::erase(const key_type& key, const char* clientId, bool haslock)
{
    LockId lid(key, clientId);
    vespalib::MonitorGuard guard(_lock);
    if (!haslock) {
        ackquireKey(lid, guard);
    }
#ifdef ENABLE_BUCKET_OPERATION_LOGGING
    debug::logBucketDbErase(key, debug::TypeTag<mapped_type>());
#endif
    return _map.erase(key);
}

template<typename Map>
void
LockableMap<Map>::insert(const key_type& key, const mapped_type& value,
                         const char* clientId, bool haslock, bool& preExisted)
{
    LockId lid(key, clientId);
    vespalib::MonitorGuard guard(_lock);
    if (!haslock) {
        ackquireKey(lid, guard);
    }
#ifdef ENABLE_BUCKET_OPERATION_LOGGING
    debug::logBucketDbInsert(key, value);
#endif
    _map.insert(key, value, preExisted);
}

template<typename Map>
void
LockableMap<Map>::clear()
{
    vespalib::LockGuard guard(_lock);
    _map.clear();
}

template<typename Map>
bool
LockableMap<Map>::findNextKey(key_type& key, mapped_type& val,
                              const char* clientId,
                              vespalib::MonitorGuard& guard)
{
    // Wait for next value to unlock.
    typename Map::iterator it(_map.lower_bound(key));
    while (it != _map.end() && _lockedKeys.exist(LockId(it->first, ""))) {
        typename LockWaiters::Key waitId(_lockWaiters.insert(LockId(it->first, clientId)));
        guard.wait();
        _lockWaiters.erase(waitId);
        it = _map.lower_bound(key);
    }
    if (it == _map.end()) return true;
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
        case UPDATE: _map.insert(key, val, b);
                     break;
        case REMOVE: _map.erase(key);
                     break;
        case ABORT:  return true;
        case CONTINUE: break;
        default: assert(false);
    }
    return false;
}

template<typename Map>
template<typename Functor>
void
LockableMap<Map>::each(Functor& functor, const char* clientId,
                       const key_type& first, const key_type& last)
{
    key_type key = first;
    mapped_type val;
    Decision decision;
    {
        vespalib::MonitorGuard guard(_lock);
        if (findNextKey(key, val, clientId, guard) || key > last) return;
        _lockedKeys.insert(LockId(key, clientId));
    }
    try{
        while (true) {
            decision = functor(const_cast<const key_type&>(key), val);
            vespalib::MonitorGuard guard(_lock);
            _lockedKeys.erase(LockId(key, clientId));
            guard.broadcast();
            if (handleDecision(key, val, decision)) return;
            ++key;
            if (findNextKey(key, val, clientId, guard) || key > last) return;
            _lockedKeys.insert(LockId(key, clientId));
        }
    } catch (...) {
            // Assuming only the functor call can throw exceptions, we need
            // to unlock the current key before exiting
        vespalib::MonitorGuard guard(_lock);
        _lockedKeys.erase(LockId(key, clientId));
        guard.broadcast();
        throw;
    }
}

template<typename Map>
template<typename Functor>
void
LockableMap<Map>::each(const Functor& functor, const char* clientId,
                       const key_type& first, const key_type& last)
{
    key_type key = first;
    mapped_type val;
    Decision decision;
    {
        vespalib::MonitorGuard guard(_lock);
        if (findNextKey(key, val, clientId, guard) || key > last) return;
        _lockedKeys.insert(LockId(key, clientId));
    }
    try{
        while (true) {
            decision = functor(const_cast<const key_type&>(key), val);
            vespalib::MonitorGuard guard(_lock);
            _lockedKeys.erase(LockId(key, clientId));
            guard.broadcast();
            if (handleDecision(key, val, decision)) return;
            ++key;
            if (findNextKey(key, val, clientId, guard) || key > last) return;
            _lockedKeys.insert(LockId(key, clientId));
        }
    } catch (...) {
            // Assuming only the functor call can throw exceptions, we need
            // to unlock the current key before exiting
        vespalib::MonitorGuard guard(_lock);
        _lockedKeys.erase(LockId(key, clientId));
        guard.broadcast();
        throw;
    }
}

template<typename Map>
template<typename Functor>
void
LockableMap<Map>::all(Functor& functor, const char* clientId,
                      const key_type& first, const key_type& last)
{
    key_type key = first;
    mapped_type val;
    vespalib::MonitorGuard guard(_lock);
    while (true) {
        if (findNextKey(key, val, clientId, guard) || key > last) return;
        Decision d(functor(const_cast<const key_type&>(key), val));
        if (handleDecision(key, val, d)) return;
        ++key;
    }
}

template<typename Map>
template<typename Functor>
void
LockableMap<Map>::all(const Functor& functor, const char* clientId,
                      const key_type& first, const key_type& last)
{
    key_type key = first;
    mapped_type val;
    vespalib::MonitorGuard guard(_lock);
    while (true) {
        if (findNextKey(key, val, clientId, guard) || key > last) return;
        Decision d(functor(const_cast<const key_type&>(key), val));
        assert(d == ABORT || d == CONTINUE);
        if (handleDecision(key, val, d)) return;
        ++key;
    }
}

template <typename Map>
template <typename Functor>
bool
LockableMap<Map>::processNextChunk(Functor& functor,
                                   key_type& key,
                                   const char* clientId,
                                   const uint32_t chunkSize)
{
    mapped_type val;
    vespalib::MonitorGuard guard(_lock);
    for (uint32_t processed = 0; processed < chunkSize; ++processed) {
        if (findNextKey(key, val, clientId, guard)) {
            return false;
        }
        Decision d(functor(const_cast<const key_type&>(key), val));
        if (handleDecision(key, val, d)) {
            return false;
        }
        ++key;
    }
    return true;
}

template <typename Map>
template <typename Functor>
void
LockableMap<Map>::chunkedAll(Functor& functor,
                             const char* clientId,
                             uint32_t chunkSize)
{
    key_type key{};
    while (processNextChunk(functor, key, clientId, chunkSize)) {
        // Rationale: delay iteration for as short a time as possible while
        // allowing another thread blocked on the main DB mutex to acquire it
        // in the meantime. Simply yielding the thread does not have the
        // intended effect with the Linux scheduler.
        // This is a pragmatic stop-gap solution; a more robust change requires
        // the redesign of bucket DB locking and signalling semantics in the
        // face of blocked point lookups.
        std::this_thread::sleep_for(std::chrono::microseconds(100));
    }
}

template<typename Map>
void
LockableMap<Map>::print(std::ostream& out, bool verbose,
                        const std::string& indent) const
{
    vespalib::LockGuard guard(_lock);
    out << "LockableMap {\n" << indent << "  ";

    if (verbose) {
        for (const auto & entry : _map) {
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
    vespalib::MonitorGuard guard(_lock);
    _lockedKeys.erase(LockId(key, ""));
    guard.broadcast();
}

namespace {

/**
 * Check whether the given key contains the given bucket.
 * Sets result to the bucket corresponding to the key, and keyResult
 * to the key if true.
 */
bool
checkContains(document::BucketId::Type key, const document::BucketId& bucket,
              document::BucketId& result, document::BucketId::Type& keyResult)
{
    document::BucketId id = document::BucketId(document::BucketId::keyToBucketId(key));
    if (id.contains(bucket)) {
        result = id;
        keyResult = key;
        return true;
    }

    return false;
}

} // anon namespace

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
        const std::vector<BucketId::Type> keys,
        const char* clientId,
        std::map<BucketId, WrappedEntry>& results,
        vespalib::MonitorGuard& guard)
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
            guard.wait();
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

namespace {

uint8_t getMinDiffBits(uint16_t minBits, const document::BucketId& a, const document::BucketId& b) {
    for (uint32_t i = minBits; i <= std::min(a.getUsedBits(), b.getUsedBits()); i++) {
        document::BucketId a1(i, a.getRawId());
        document::BucketId b1(i, b.getRawId());
        if (b1.getId() != a1.getId()) {
            return i;
        }
    }
    return minBits;
};

}

template<typename Map>
typename LockableMap<Map>::WrappedEntry
LockableMap<Map>::createAppropriateBucket(
        uint16_t newBucketBits,
        const char* clientId,
        const BucketId& bucket)
{
    vespalib::MonitorGuard guard(_lock);
    typename Map::const_iterator iter = _map.lower_bound(bucket.toKey());

    // Find the two buckets around the possible new bucket. The new
    // bucket's used bits should be the highest used bits it can be while
    // still being different from both of these.
    if (iter != _map.end()) {
        newBucketBits = getMinDiffBits(newBucketBits, BucketId(BucketId::keyToBucketId(iter->first)), bucket);
    }

    if (iter != _map.begin()) {
        --iter;
        newBucketBits = getMinDiffBits(newBucketBits, BucketId(BucketId::keyToBucketId(iter->first)), bucket);
    }

    BucketId newBucket(newBucketBits, bucket.getRawId());
    newBucket.setUsedBits(newBucketBits);
    BucketId::Type key = newBucket.stripUnused().toKey();

    LockId lid(key, clientId);
    ackquireKey(lid, guard);
    bool preExisted;
    typename Map::iterator it = _map.find(key, true, preExisted);
    _lockedKeys.insert(LockId(key, clientId));
    return WrappedEntry(*this, key, it->second, clientId, preExisted);
}

template<typename Map>
std::map<document::BucketId, typename LockableMap<Map>::WrappedEntry>
LockableMap<Map>::getContained(const BucketId& bucket,
                               const char* clientId)
{
    vespalib::MonitorGuard guard(_lock);
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
                                       const BucketId& sibling,
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

    if (sibling.getRawId() != 0) {
        keys.push_back(sibling.toKey());
    }
}

/**
 * Returns the given bucket, its super buckets and its sub buckets.
 */
template<typename Map>
std::map<document::BucketId, typename LockableMap<Map>::WrappedEntry>
LockableMap<Map>::getAll(const BucketId& bucket, const char* clientId,
                         const BucketId& sibling)
{
    vespalib::MonitorGuard guard(_lock);

    std::map<BucketId, WrappedEntry> results;
    std::vector<BucketId::Type> keys;

    getAllWithoutLocking(bucket, sibling, keys);

    addAndLockResults(keys, clientId, results, guard);

    return results;
}

template<typename Map>
bool
LockableMap<Map>::isConsistent(const typename LockableMap<Map>::WrappedEntry& entry)
{
    vespalib::MonitorGuard guard(_lock);

    BucketId sibling(0);
    std::vector<BucketId::Type> keys;

    getAllWithoutLocking(entry.getBucketId(), sibling, keys);
    assert(keys.size() >= 1);
    assert(keys.size() != 1 || keys[0] == entry.getKey());

    return keys.size() == 1;
}

template<typename Map>
void
LockableMap<Map>::showLockClients(vespalib::asciistream & out) const
{
    vespalib::MonitorGuard guard(_lock);
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

} // storage

