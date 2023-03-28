// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btree_bucket_database.h"
#include "generic_btree_bucket_database.hpp"
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/util/size_literals.h>
#include <iostream>

/*
 * Buckets in our tree are represented by their 64-bit numeric key, in what's known as
 * "reversed bit order with appended used-bits" form. I.e. a bucket ID (16, 0xcafe), which
 * in its canonical representation has 16 (the used-bits) in its 6 MSBs and 0xcafe in its
 * LSBs is transformed into 0x7f53000000000010. This key is logically comprised of two parts:
 *   - the reversed bucket ID itself (0xcafe - 0x7f53) with all trailing zeroes for unset bits
 *   - the _non-reversed_ used-bits appended as the LSBs
 *
 * This particular transformation gives us keys with the following invariants:
 *   - all distinct bucket IDs map to exactly 1 key
 *   - buckets with the same ID but different used-bits are ordered in such a way that buckets
 *     with higher used-bits sort after buckets with lower used-bits
 *   - the key ordering represents an implicit in-order traversal of the binary bucket tree
 *     - consequently, all parent buckets are ordered before their child buckets
 *
 * The in-order traversal invariant is fundamental to many of the algorithms that operate
 * on the bucket tree.
 */

namespace storage {

using Entry = BucketDatabase::Entry;
using ConstEntryRef = BucketDatabase::ConstEntryRef;
using vespalib::datastore::EntryRef;
using vespalib::ConstArrayRef;
using document::BucketId;

template <typename ReplicaStore>
vespalib::datastore::ArrayStoreConfig make_default_array_store_config() {
    return ReplicaStore::optimizedConfigForHugePage(1023,
                                                    vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                    vespalib::alloc::MemoryAllocator::PAGE_SIZE,
                                                    8_Ki, 0.2).enable_free_lists(true);
}

namespace {

Entry entry_from_replica_array_ref(const BucketId& id, uint32_t gc_timestamp, ConstArrayRef<BucketCopy> replicas) {
    return Entry(id, BucketInfo(gc_timestamp, std::vector<BucketCopy>(replicas.begin(), replicas.end())));
}

ConstEntryRef const_entry_ref_from_replica_array_ref(const BucketId& id, uint32_t gc_timestamp,
                                                     ConstArrayRef<BucketCopy> replicas)
{
    return ConstEntryRef(id, ConstBucketInfoRef(gc_timestamp, replicas));
}

EntryRef entry_ref_from_value(uint64_t value) {
    return EntryRef(value & 0xffffffffULL);
}

uint32_t gc_timestamp_from_value(uint64_t value) {
    return (value >> 32u);
}

uint64_t value_from(uint32_t gc_timestamp, EntryRef ref) {
    return ((uint64_t(gc_timestamp) << 32u) | ref.ref());
}

}

struct BTreeBucketDatabase::ReplicaValueTraits {
    using ValueType     = Entry;
    using ConstValueRef = ConstEntryRef;
    using DataStoreType = vespalib::datastore::ArrayStore<BucketCopy>;

    static void init_data_store(DataStoreType&) {
        // No-op; initialized via config provided to ArrayStore constructor.
    }
    static ValueType make_invalid_value() {
        return Entry::createInvalid();
    }
    static uint64_t wrap_and_store_value(DataStoreType& store, const Entry& entry) noexcept {
        auto replicas_ref = store.add(entry.getBucketInfo().getRawNodes());
        return value_from(entry.getBucketInfo().getLastGarbageCollectionTime(), replicas_ref);
    }
    static void remove_by_wrapped_value(DataStoreType& store, uint64_t value) noexcept {
        store.remove(entry_ref_from_value(value));
    }
    static ValueType unwrap_from_key_value(const DataStoreType& store, uint64_t key, uint64_t value) {
        const auto replicas_ref = store.get(entry_ref_from_value(value));
        const auto bucket = BucketId(BucketId::keyToBucketId(key));
        return entry_from_replica_array_ref(bucket, gc_timestamp_from_value(value), replicas_ref);
    }
    static ConstValueRef unwrap_const_ref_from_key_value(const DataStoreType& store, uint64_t key, uint64_t value) {
        const auto replicas_ref = store.get(entry_ref_from_value(value));
        const auto bucket = BucketId(BucketId::keyToBucketId(key));
        return const_entry_ref_from_replica_array_ref(bucket, gc_timestamp_from_value(value), replicas_ref);
    }
};

template class bucketdb::GenericBTreeBucketDatabase<BTreeBucketDatabase::ReplicaValueTraits>;

BTreeBucketDatabase::BTreeBucketDatabase()
    : _impl(std::make_unique<ImplType>(make_default_array_store_config<ReplicaValueTraits::DataStoreType>(), std::shared_ptr<vespalib::alloc::MemoryAllocator>()))
{
}

BTreeBucketDatabase::~BTreeBucketDatabase() = default;

Entry BTreeBucketDatabase::get(const BucketId& bucket) const {
    return _impl->get(bucket);
}

void BTreeBucketDatabase::remove(const BucketId& bucket) {
    _impl->remove(bucket);
}

using bucketdb::ByValue;

/*
 * Note: due to legacy API reasons, iff the requested bucket itself exists in the
 * tree, it will be returned in the result set. I.e. it returns all the nodes on
 * the path from _and including_ itself towards the root.
 */
void BTreeBucketDatabase::getParents(const BucketId& bucket,
                                     std::vector<Entry>& entries) const
{
    _impl->find_parents_and_self<ByValue>(bucket, [&entries]([[maybe_unused]] uint64_t key, Entry entry){
        entries.emplace_back(std::move(entry));
    });
}

void BTreeBucketDatabase::getAll(const BucketId& bucket,
                                 std::vector<Entry>& entries) const
{
    _impl->find_parents_self_and_children<ByValue>(bucket, [&entries]([[maybe_unused]] uint64_t key, Entry entry){
        entries.emplace_back(std::move(entry));
    });
}

void BTreeBucketDatabase::update(const Entry& newEntry) {
    assert(newEntry.valid());
    _impl->update(newEntry.getBucketId(), newEntry);
}

void
BTreeBucketDatabase::process_update(const document::BucketId& bucket, EntryUpdateProcessor &processor, bool create_if_nonexisting)
{
    _impl->process_update(bucket, processor, create_if_nonexisting);
}

// TODO need snapshot read with guarding
void BTreeBucketDatabase::for_each_lower_bound(EntryProcessor& proc, const BucketId& at_or_after) const {
    for (auto iter = _impl->lower_bound(at_or_after.toKey()); iter.valid(); ++iter) {
        if (!proc.process(_impl->const_value_ref_from_valid_iterator(iter))) {
            break;
        }
    }
}

// TODO need snapshot read with guarding
void BTreeBucketDatabase::for_each_upper_bound(EntryProcessor& proc, const BucketId& after) const {
    for (auto iter = _impl->upper_bound(after.toKey()); iter.valid(); ++iter) {
        if (!proc.process(_impl->const_value_ref_from_valid_iterator(iter))) {
            break;
        }
    }
}

void BTreeBucketDatabase::merge(MergingProcessor& proc) {
    _impl->merge(proc);
}

Entry BTreeBucketDatabase::upperBound(const BucketId& bucket) const {
    return _impl->entry_from_iterator(_impl->upper_bound(bucket.toKey()));
}

uint64_t BTreeBucketDatabase::size() const {
    return _impl->size();
}

void BTreeBucketDatabase::clear() {
    _impl->clear();
}

BucketId BTreeBucketDatabase::getAppropriateBucket(uint16_t minBits, const BucketId& bid) {
    return _impl->getAppropriateBucket(minBits, bid);
}

/*
 * Enumerate the number of child subtrees under `bucket`. The value returned is in the
 * range [0, 2] regardless of how many subtrees are present further down in the tree.
 *
 * Finding this number is reasonably straight forward; we construct two buckets that
 * represent the key ranges for the left and right subtrees under `bucket` and check
 * if there are any ranges in the tree's keyspace that are contained in these.
 */
// TODO rename/clarify to indicate this is child _subtrees_, not explicit child _buckets_!
uint32_t BTreeBucketDatabase::childCount(const BucketId& bucket) const {
    return _impl->child_subtree_count(bucket);
}

void BTreeBucketDatabase::print(std::ostream& out, bool verbose,
                                const std::string& indent) const
{
    out << "BTreeBucketDatabase(" << size() << " buckets)";
    (void)verbose;
    (void)indent;
}

vespalib::MemoryUsage BTreeBucketDatabase::memory_usage() const noexcept {
    return _impl->memory_usage();
}

class BTreeBucketDatabase::ReadGuardImpl final
    : public bucketdb::ReadGuard<Entry, ConstEntryRef>
{
    ImplType::ReadSnapshot _snapshot;
public:
    explicit ReadGuardImpl(const BTreeBucketDatabase& db);
    ~ReadGuardImpl() override;

    std::vector<Entry> find_parents_and_self(const document::BucketId& bucket) const override;
    std::vector<Entry> find_parents_self_and_children(const document::BucketId& bucket) const override;
    void for_each(std::function<void(uint64_t, const Entry&)> func) const override;
    std::unique_ptr<bucketdb::ConstIterator<ConstEntryRef>> create_iterator() const override;
    [[nodiscard]] uint64_t generation() const noexcept override;
};

BTreeBucketDatabase::ReadGuardImpl::ReadGuardImpl(const BTreeBucketDatabase& db)
    : _snapshot(*db._impl)
{}

BTreeBucketDatabase::ReadGuardImpl::~ReadGuardImpl() = default;

std::vector<Entry>
BTreeBucketDatabase::ReadGuardImpl::find_parents_and_self(const document::BucketId& bucket) const {
    std::vector<Entry> entries;
    _snapshot.find_parents_and_self<ByValue>(bucket, [&entries]([[maybe_unused]] uint64_t key, Entry entry){
        entries.emplace_back(std::move(entry));
    });
    return entries;
}

std::vector<Entry>
BTreeBucketDatabase::ReadGuardImpl::find_parents_self_and_children(const document::BucketId& bucket) const {
    std::vector<Entry> entries;
    _snapshot.find_parents_self_and_children<ByValue>(bucket, [&entries]([[maybe_unused]] uint64_t key, Entry entry){
        entries.emplace_back(std::move(entry));
    });
    return entries;
}

void BTreeBucketDatabase::ReadGuardImpl::for_each(std::function<void(uint64_t, const Entry&)> func) const {
    _snapshot.for_each<ByValue>(std::move(func));
}

std::unique_ptr<bucketdb::ConstIterator<ConstEntryRef>>
BTreeBucketDatabase::ReadGuardImpl::create_iterator() const {
    return _snapshot.create_iterator(); // TODO test
}

uint64_t BTreeBucketDatabase::ReadGuardImpl::generation() const noexcept {
    return _snapshot.generation();
}

std::unique_ptr<bucketdb::ReadGuard<Entry, ConstEntryRef>>
BTreeBucketDatabase::acquire_read_guard() const {
    return std::make_unique<ReadGuardImpl>(*this);
}

}
