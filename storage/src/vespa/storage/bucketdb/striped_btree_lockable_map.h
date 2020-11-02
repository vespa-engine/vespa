// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "btree_lockable_map.h"
#include <memory>
#include <vector>

namespace storage::bucketdb {

/**
 * Bucket database implementation that stripes all superbuckets across
 * a set of disjoint sub-DBs. All locking is handled by the individual
 * sub-DBs, meaning that accessing one does not cause contention for
 * readers/writers accessing another.
 * Ordered iteration is transparently provided by the const for_each method
 * and by read guards.
 */
template <typename T>
class StripedBTreeLockableMap final : public AbstractBucketMap<T> {
public:
    using ParentType   = AbstractBucketMap<T>;
    using WrappedEntry = typename ParentType::WrappedEntry;
    using key_type     = typename ParentType::key_type;
    using mapped_type  = typename ParentType::mapped_type;
    using LockId       = typename ParentType::LockId;
    using EntryMap     = typename ParentType::EntryMap;
    using Decision     = typename ParentType::Decision;
    using BucketId     = document::BucketId;

    constexpr static uint8_t MaxStripeBits = 8;
private:
    using StripedDBType = BTreeLockableMap<T>;
    uint8_t _n_stripe_bits;
    size_t _n_stripes;
    std::vector<std::unique_ptr<StripedDBType>> _stripes;
public:
    explicit StripedBTreeLockableMap(uint8_t n_stripe_bits = 4);
    ~StripedBTreeLockableMap() override;

    size_t size() const noexcept override;
    size_t getMemoryUsage() const noexcept override;
    vespalib::MemoryUsage detailed_memory_usage() const noexcept override;
    bool empty() const noexcept override;

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
    bool isConsistent(const WrappedEntry& entry) override;
    void showLockClients(vespalib::asciistream & out) const override;

private:
    class ReadGuardImpl;

    void unlock(const key_type& key) override;

    void do_for_each_mutable_unordered(std::function<Decision(uint64_t, mapped_type&)> func,
                                       const char* client_id) override;

    void do_for_each(std::function<Decision(uint64_t, const mapped_type&)> func,
                     const char* client_id) override;

    void do_for_each_chunked(std::function<Decision(uint64_t, const mapped_type&)> func,
                             const char* client_id,
                             vespalib::duration yield_time,
                             uint32_t chunk_size) override;

    std::unique_ptr<ReadGuard<T>> do_acquire_read_guard() const override;

    [[nodiscard]] size_t stripe_of(key_type) const noexcept;
    [[nodiscard]] StripedDBType& db_for(key_type) noexcept;
    [[nodiscard]] const StripedDBType& db_for(key_type) const noexcept;
};

}
