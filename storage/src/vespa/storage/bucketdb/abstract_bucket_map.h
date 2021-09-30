// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "read_guard.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/util/time.h>
#include <cassert>
#include <functional>
#include <iosfwd>
#include <map>

namespace storage::bucketdb {

/*
 * Interface for content node bucket database implementations.
 *
 * Allows for multiple divergent implementations to exist of the
 * bucket database in a transition period.
 */
template <typename ValueT>
class AbstractBucketMap {
public:
    using key_type    = uint64_t; // Always a raw u64 bucket key.
    using mapped_type = ValueT;
    using size_type   = size_t;
    using BucketId    = document::BucketId;
    struct WrappedEntry;

    // Responsible for releasing lock in map when out of scope.
    class LockKeeper {
        friend struct WrappedEntry;
        AbstractBucketMap& _map;
        key_type _key;
        bool _locked;

        LockKeeper(AbstractBucketMap& map, key_type key) noexcept
            : _map(map), _key(key), _locked(true) {}
        void unlock() { _map.unlock(_key); _locked = false; }
    public:
        ~LockKeeper() { if (_locked) unlock(); }
    };

    struct WrappedEntry {
        WrappedEntry() noexcept
            : _exists(false),
              _preExisted(false),
              _lockKeeper(),
              _value(),
              _clientId(nullptr)
        {}
        WrappedEntry(AbstractBucketMap& map,
                     const key_type& key, const mapped_type& val,
                     const char* clientId, bool preExisted_)
            : _exists(true),
              _preExisted(preExisted_),
              _lockKeeper(new LockKeeper(map, key)),
              _value(val),
              _clientId(clientId) {}
        WrappedEntry(AbstractBucketMap& map, const key_type& key,
                     const char* clientId)
            : _exists(false),
              _preExisted(false),
              _lockKeeper(new LockKeeper(map, key)),
              _value(),
              _clientId(clientId) {}
        // TODO noexcept on these:
        WrappedEntry(WrappedEntry&&) = default;
        WrappedEntry& operator=(WrappedEntry&&) = default;
        ~WrappedEntry();

        mapped_type* operator->() { return &_value; }
        const mapped_type* operator->() const { return &_value; }
        mapped_type& operator*() { return _value; }
        const mapped_type& operator*() const { return _value; }

        const mapped_type *get() const { return &_value; }
        mapped_type *get() { return &_value; }

        void write();
        void remove();
        void unlock();
        [[nodiscard]] bool exist() const { return _exists; } // TODO rename to exists()
        [[nodiscard]] bool preExisted() const { return _preExisted; }
        [[nodiscard]] bool locked() const { return _lockKeeper.get(); }
        const key_type& getKey() const { return _lockKeeper->_key; };

        BucketId getBucketId() const {
            return BucketId(BucketId::keyToBucketId(getKey()));
        }
    protected:
        bool _exists;
        bool _preExisted;
        std::unique_ptr<LockKeeper> _lockKeeper;
        mapped_type _value;
        const char* _clientId;
        friend class AbstractLockableMap;
    };

    struct LockId {
        key_type _key;
        const char* _owner;

        LockId() noexcept : _key(0), _owner("none - empty token") {}
        LockId(key_type key, const char* owner) noexcept
            : _key(key), _owner(owner)
        {
            assert(_owner);
        }

        size_t hash() const noexcept { return _key; }
        size_t operator%(size_t val) const noexcept { return _key % val; }
        bool operator==(const LockId& id) const noexcept { return (_key == id._key); }
        operator key_type() const { return _key; }
    };

    using EntryMap = std::map<BucketId, WrappedEntry>; // TODO ordered std::vector instead? map interface needed?

    enum Decision { ABORT, UPDATE, REMOVE, CONTINUE, DECISION_COUNT };

    AbstractBucketMap() = default;
    virtual ~AbstractBucketMap() = default;

    virtual void insert(const key_type& key, const mapped_type& value,
                        const char* client_id, bool has_lock,
                        bool& pre_existed) = 0;
    virtual bool erase(const key_type& key, const char* clientId, bool has_lock) = 0;

    virtual WrappedEntry get(const key_type& key, const char* clientId, bool createIfNonExisting) = 0;
    WrappedEntry get(const key_type& key, const char* clientId) {
        return get(key, clientId, false);
    }
    /**
     * Returns all buckets in the bucket database that can contain the given
     * bucket, and all buckets that that bucket contains.
     */
    virtual EntryMap getAll(const BucketId& bucketId, const char* clientId) = 0;
    /**
     * Returns all buckets in the bucket database that can contain the given
     * bucket. Usually, there should be only one such bucket, but in the case
     * of inconsistent splitting, there may be more than one.
     */
    virtual EntryMap getContained(const BucketId& bucketId, const char* clientId) = 0;
    /**
     * Returns true iff bucket has no superbuckets or sub-buckets in the
     * database. Usage assumption is that any operation that can cause the
     * bucket to become inconsistent will require taking its lock, so by
     * requiring the lock to be provided here we avoid race conditions.
     */
    virtual bool isConsistent(const WrappedEntry& entry) = 0; // TODO const

    static constexpr uint32_t DEFAULT_CHUNK_SIZE = 1000;

    /**
     * Iterate over the entire database contents, holding the global database
     * mutex for `chunkSize` processed entries at a time, yielding the current
     * thread between each chunk to allow other threads to get a chance at
     * acquiring a bucket lock.
     *
     * TODO deprecate in favor of snapshotting once fully on B-tree DB
     *
     * Type erasure of functor needed due to virtual indirection.
     */
    void for_each_chunked(std::function<Decision(uint64_t, const ValueT&)> func,
                          const char* clientId,
                          vespalib::duration yieldTime = 10us,
                          uint32_t chunkSize = DEFAULT_CHUNK_SIZE)
    {
        do_for_each_chunked(std::move(func), clientId, yieldTime, chunkSize);
    }

    void for_each_mutable_unordered(std::function<Decision(uint64_t, ValueT&)> func,
                                    const char* clientId)
    {
        do_for_each_mutable_unordered(std::move(func), clientId);
    }

    void for_each(std::function<Decision(uint64_t, const ValueT&)> func, const char* clientId) {
        do_for_each(std::move(func), clientId);
    }

    std::unique_ptr<bucketdb::ReadGuard<ValueT>> acquire_read_guard() const {
        return do_acquire_read_guard();
    }

    [[nodiscard]] virtual size_type size() const noexcept = 0;
    [[nodiscard]] virtual size_type getMemoryUsage() const noexcept = 0;
    [[nodiscard]] virtual vespalib::MemoryUsage detailed_memory_usage() const noexcept = 0;
    [[nodiscard]] virtual bool empty() const noexcept = 0;

    virtual void showLockClients(vespalib::asciistream& out) const = 0;

    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const = 0;
private:
    virtual void unlock(const key_type& key) = 0; // Only for bucket lock guards
    virtual void do_for_each_chunked(std::function<Decision(uint64_t, const ValueT&)> func,
                                     const char* clientId,
                                     vespalib::duration yieldTime,
                                     uint32_t chunkSize) = 0;
    virtual void do_for_each_mutable_unordered(std::function<Decision(uint64_t, ValueT&)> func,
                                               const char* clientId) = 0;
    virtual void do_for_each(std::function<Decision(uint64_t, const ValueT&)> func,
                             const char* clientId) = 0;
    virtual std::unique_ptr<bucketdb::ReadGuard<ValueT>> do_acquire_read_guard() const = 0;
};

template <typename ValueT>
std::ostream& operator<<(std::ostream& os, const AbstractBucketMap<ValueT>& map) {
    map.print(os, false, "");
    return os;
}

template <typename ValueT>
AbstractBucketMap<ValueT>::WrappedEntry::~WrappedEntry() = default;

template <typename ValueT>
void AbstractBucketMap<ValueT>::WrappedEntry::write() {
    assert(_lockKeeper->_locked);
    assert(_value.verifyLegal());
    bool b;
    _lockKeeper->_map.insert(_lockKeeper->_key, _value, _clientId, true, b);
    _lockKeeper->unlock();
}

template <typename ValueT>
void AbstractBucketMap<ValueT>::WrappedEntry::remove() {
    assert(_lockKeeper->_locked);
    assert(_exists);
    _lockKeeper->_map.erase(_lockKeeper->_key, _clientId, true);
    _lockKeeper->unlock();
}

template <typename ValueT>
void AbstractBucketMap<ValueT>::WrappedEntry::unlock() {
    assert(_lockKeeper->_locked);
    _lockKeeper->unlock();
}

}
