// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketdatabase.h"
#include <memory>

namespace storage {

namespace bucketdb {
template <typename DataStoreTraitsT> class GenericBTreeBucketDatabase;
}

/*
 * Bucket database implementation built around lock-free single-writer/multiple-readers B+tree.
 *
 * Since a distributor must be able to handle multiple replicas for a given bucket, these
 * are handled via an ArrayStore indirection. A distributor bucket DB also includes state
 * for the _entire bucket_ itself, not just the replicas; last timestamp of bucket GC. Since
 * this is an uint32 we cheekily mangle it into the value, i.e. each bucket key maps to a
 * composite key of (gc_timestamp_u32 << 32) | array_ref_u32.
 *
 * Readers from contexts that are not guaranteed to be the main distributor thread MUST
 * only access the database via an acquired read guard.
 * Writing MUST only take place from the main distributor thread.
 */
// TODO create and use a new DB interface with better bulk loading, snapshot and iteration support
class BTreeBucketDatabase : public BucketDatabase {
    struct ReplicaValueTraits;
    using ImplType = bucketdb::GenericBTreeBucketDatabase<ReplicaValueTraits>;
    std::unique_ptr<ImplType> _impl;
public:
    BTreeBucketDatabase();
    ~BTreeBucketDatabase() override;

    void merge(MergingProcessor&) override;

    // Ye olde bucket DB API:
    Entry get(const document::BucketId& bucket) const override;
    void remove(const document::BucketId& bucket) override;
    void getParents(const document::BucketId& childBucket,
                    std::vector<Entry>& entries) const override;
    void getAll(const document::BucketId& bucket,
                std::vector<Entry>& entries) const override;
    void update(const Entry& newEntry) override;
    void process_update(const document::BucketId& bucket, EntryUpdateProcessor &processor, bool create_if_nonexisting) override;
    void forEach(EntryProcessor&, const document::BucketId& after) const override;
    Entry upperBound(const document::BucketId& value) const override;
    uint64_t size() const override;
    void clear() override;
    document::BucketId getAppropriateBucket(
            uint16_t minBits,
            const document::BucketId& bid) override;
    uint32_t childCount(const document::BucketId&) const override;
    void print(std::ostream& out, bool verbose,
               const std::string& indent) const override;

private:
    class ReadGuardImpl;
    friend class ReadGuardImpl;
public:
    std::unique_ptr<bucketdb::ReadGuard<Entry, ConstEntryRef>> acquire_read_guard() const override;

    vespalib::MemoryUsage memory_usage() const noexcept override;
};

}
