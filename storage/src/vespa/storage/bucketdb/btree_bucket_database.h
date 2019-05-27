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
 * This is _not_ yet production ready, for several reasons:
 *   - Underlying ArrayStore does not have freelists enabled for replica entry reuse
 *   - Current API design for mutable forEach requires O(n) tree structure mutations instead
 *     of changing the tree in bulk and reusing ArrayStore refs et al. Needs a redesign.
 *
 * Also note that the DB is currently _not_ thread safe, as read snapshotting is not yet defined
 * or exposed.
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

    // Ye olde bucket DB API:
    Entry get(const document::BucketId& bucket) const override;
    void remove(const document::BucketId& bucket) override;
    void getParents(const document::BucketId& childBucket,
                    std::vector<Entry>& entries) const override;
    void getAll(const document::BucketId& bucket,
                std::vector<Entry>& entries) const override;
    void update(const Entry& newEntry) override;
    void forEach(EntryProcessor&, const document::BucketId& after) const override;
    void forEach(MutableEntryProcessor&, const document::BucketId& after) override;
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
    Entry entry_from_iterator(const BTree::ConstIterator& iter) const;
    document::BucketId bucket_from_valid_iterator(const BTree::ConstIterator& iter) const;
    void commit_tree_changes();
    BTree::ConstIterator find_parents_internal(const document::BucketId& bucket,
                                               std::vector<Entry>& entries) const;
};

}
