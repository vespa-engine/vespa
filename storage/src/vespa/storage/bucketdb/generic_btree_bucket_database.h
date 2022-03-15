// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "const_iterator.h"
#include "db_merger.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/minmaxaggregated.h>
#include <vespa/vespalib/btree/minmaxaggrcalc.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>

namespace storage::bucketdb {

/*
 * Bucket database implementation built around lock-free single-writer/multiple-readers B+tree.
 *
 * Key is always treated as a 64-bit uint bucket ID key.
 * Value is a 64-bit uint whose semantics are handled by the provided DataStoreTraitsT.
 * All DataStore access and value type (un)marshalling is deferred to the traits type,
 * allowing this class to be used for both fixed-sized and dynamic-sized value types.
 *
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
template <typename DataStoreTraitsT>
class GenericBTreeBucketDatabase {
public:
    using DataStoreType      = typename DataStoreTraitsT::DataStoreType;
    using ValueType          = typename DataStoreTraitsT::ValueType;
    using ConstValueRef      = typename DataStoreTraitsT::ConstValueRef;
    using GenerationHandler  = vespalib::GenerationHandler;
    using AtomicValueWrapper = vespalib::datastore::AtomicValueWrapper<uint64_t>;

    struct KeyUsedBitsMinMaxAggrCalc : vespalib::btree::MinMaxAggrCalc {
        constexpr static bool aggregate_over_values() { return false; }
        constexpr static int32_t getVal(uint64_t key) noexcept {
            static_assert(document::BucketId::CountBits == 6u);
            return static_cast<int32_t>(key & 0b11'1111U); // 6 LSB of key contains used-bits
        }
    };

    // Rationale for using an atomic u64 value type:
    // It is expected that the set of bucket keys is much less frequently updated than their
    // corresponding values. Since values must be stable for concurrent readers, all written values
    // are _immutable_ once created. Consequently, every single mutation of a bucket will replace its
    // value with a new (immutable) entry. For distributors, this replaces an entire array of values.
    // Instead of constantly thawing and freezing subtrees for each bucket update, we atomically
    // replace the value to point to a new u32 EntryRef mangled together with an u32 timestamp.
    // This means updates that don't change the set of buckets leave the B-tree node structure
    // itself entirely untouched.
    // This requires great care to be taken when writing and reading to ensure memory visibility.
    using BTree = vespalib::btree::BTree<uint64_t,
                                         vespalib::datastore::AtomicValueWrapper<uint64_t>,
                                         vespalib::btree::MinMaxAggregated,
                                         std::less<>,
                                         vespalib::btree::BTreeDefaultTraits,
                                         KeyUsedBitsMinMaxAggrCalc>;
    using BTreeConstIterator = typename BTree::ConstIterator;

    BTree             _tree;
    DataStoreType     _store;
    GenerationHandler _generation_handler;

    template <typename... DataStoreArgs>
    explicit GenericBTreeBucketDatabase(DataStoreArgs&&... data_store_args)
        : _store(std::forward<DataStoreArgs>(data_store_args)...)
    {
        DataStoreTraitsT::init_data_store(_store);
    }

    GenericBTreeBucketDatabase(const GenericBTreeBucketDatabase&) = delete;
    GenericBTreeBucketDatabase& operator=(const GenericBTreeBucketDatabase&) = delete;
    GenericBTreeBucketDatabase(GenericBTreeBucketDatabase&&) = delete;
    GenericBTreeBucketDatabase& operator=(GenericBTreeBucketDatabase&&) = delete;

    ~GenericBTreeBucketDatabase();

    ValueType entry_from_iterator(const BTreeConstIterator& iter) const;
    ConstValueRef const_value_ref_from_valid_iterator(const BTreeConstIterator& iter) const;

    static document::BucketId bucket_from_valid_iterator(const BTreeConstIterator& iter);

    BTreeConstIterator find(uint64_t key) const noexcept;
    BTreeConstIterator lower_bound(uint64_t key) const noexcept;
    BTreeConstIterator upper_bound(uint64_t key) const noexcept;
    BTreeConstIterator begin() const noexcept;

    void clear() noexcept;
    [[nodiscard]] size_t size() const noexcept;
    [[nodiscard]] bool empty() const noexcept;
    [[nodiscard]] vespalib::MemoryUsage memory_usage() const noexcept;

    ValueType get(const document::BucketId& bucket) const;
    ValueType get_by_raw_key(uint64_t key) const;
    // Return true if bucket existed in DB, false otherwise.
    bool remove(const document::BucketId& bucket);
    bool remove_by_raw_key(uint64_t key);
    // Returns true if bucket pre-existed in the DB, false otherwise
    bool update(const document::BucketId& bucket, const ValueType& new_entry);
    bool update_by_raw_key(uint64_t bucket_key, const ValueType& new_entry);
    template <typename EntryUpdateProcessor>
    void process_update(const document::BucketId &bucket, EntryUpdateProcessor& processor, bool create_if_nonexisting);

    template <typename IterValueExtractor, typename Func>
    void find_parents_and_self(const document::BucketId& bucket, Func func) const;

    template <typename IterValueExtractor, typename Func>
    void find_parents_self_and_children(const document::BucketId& bucket, Func func) const;

    document::BucketId getAppropriateBucket(uint16_t minBits, const document::BucketId& bid) const;

    [[nodiscard]] uint32_t child_subtree_count(const document::BucketId& bucket) const;

    const DataStoreType& store() const noexcept { return _store; }
    DataStoreType& store() noexcept { return _store; }

    void merge(MergingProcessor<ValueType>& proc);

    friend class ReadSnapshot;
    // See ReadGuard class comments for semantics.
    class ReadSnapshot {
        const GenericBTreeBucketDatabase*  _db;
        vespalib::GenerationHandler::Guard _guard;
        typename BTree::FrozenView         _frozen_view;

        class ConstIteratorImpl;
    public:
        explicit ReadSnapshot(const GenericBTreeBucketDatabase& db);
        ~ReadSnapshot();

        ReadSnapshot(const ReadSnapshot&) = delete;
        ReadSnapshot& operator=(const ReadSnapshot&) = delete;

        template <typename IterValueExtractor, typename Func>
        void find_parents_and_self(const document::BucketId& bucket, Func func) const;
        template <typename IterValueExtractor, typename Func>
        void find_parents_self_and_children(const document::BucketId& bucket, Func func) const;
        template <typename IterValueExtractor, typename Func>
        void for_each(Func func) const;
        std::unique_ptr<ConstIterator<ConstValueRef>> create_iterator() const;
        [[nodiscard]] uint64_t generation() const noexcept;
    };
private:
    // Functor is called for each found element in key order, with raw u64 keys and values.
    template <typename IterValueExtractor, typename Func>
    BTreeConstIterator find_parents_internal(const typename BTree::FrozenView& frozen_view,
                                             const document::BucketId& bucket,
                                             Func func) const;
    template <typename IterValueExtractor, typename Func>
    void find_parents_and_self_internal(const typename BTree::FrozenView& frozen_view,
                                        const document::BucketId& bucket,
                                        Func func) const;
    template <typename IterValueExtractor, typename Func>
    void find_parents_self_and_children_internal(const typename BTree::FrozenView& frozen_view,
                                                 const document::BucketId& bucket,
                                                 Func func) const;

    void commit_tree_changes();

    template <typename DataStoreTraitsT2> friend struct BTreeBuilderMerger;
    template <typename DataStoreTraitsT2> friend struct BTreeTrailingInserter;
};

uint8_t getMinDiffBits(uint16_t minBits, const document::BucketId& a, const document::BucketId& b);
uint8_t next_parent_bit_seek_level(uint8_t minBits, const document::BucketId& a, const document::BucketId& b);

}
