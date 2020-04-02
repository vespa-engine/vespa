// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketdatabase.h"
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/datastore/array_store.h>

namespace storage {

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
    // Mapping from u64: bucket key -> <MSB u32: gc timestamp, LSB u32: ArrayStore ref>
    using BTree        = search::btree::BTree<uint64_t, uint64_t>;
    using ReplicaStore = search::datastore::ArrayStore<BucketCopy>;
    using GenerationHandler = vespalib::GenerationHandler;

    BTree _tree;
    ReplicaStore _store;
    GenerationHandler _generation_handler;
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
    Entry entry_from_value(uint64_t bucket_key, uint64_t value) const;
    Entry entry_from_iterator(const BTree::ConstIterator& iter) const;
    ConstEntryRef const_entry_ref_from_iterator(const BTree::ConstIterator& iter) const;
    document::BucketId bucket_from_valid_iterator(const BTree::ConstIterator& iter) const;
    void commit_tree_changes();
    BTree::ConstIterator find_parents_internal(const BTree::FrozenView& frozen_view,
                                               const document::BucketId& bucket,
                                               std::vector<Entry>& entries) const;
    void find_parents_and_self_internal(const BTree::FrozenView& frozen_view,
                                        const document::BucketId& bucket,
                                        std::vector<Entry>& entries) const;

    static search::datastore::ArrayStoreConfig make_default_array_store_config();

    class ReadGuardImpl : public ReadGuard {
        const BTreeBucketDatabase* _db;
        GenerationHandler::Guard   _guard;
        BTree::FrozenView          _frozen_view;
    public:
        explicit ReadGuardImpl(const BTreeBucketDatabase& db);
        ~ReadGuardImpl() override;

        void find_parents_and_self(const document::BucketId& bucket,
                                   std::vector<Entry>& entries) const override;
        uint64_t generation() const noexcept override;
    };

    friend class ReadGuardImpl;
    friend struct BTreeBuilderMerger;
    friend struct BTreeTrailingInserter;
public:
    std::unique_ptr<ReadGuard> acquire_read_guard() const override {
        return std::make_unique<ReadGuardImpl>(*this);
    }

    vespalib::MemoryUsage memory_usage() const noexcept override;
};

}
