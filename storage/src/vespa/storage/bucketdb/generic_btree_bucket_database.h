// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/minmaxaggregated.h>
#include <vespa/vespalib/btree/minmaxaggrcalc.h>

namespace storage::bucketdb {

/**
 * Database implementation-specific interface for appending entries
 * during a merge() operation.
 */
template <typename ValueT>
struct TrailingInserter {
    virtual ~TrailingInserter() = default;
    /**
     * Insert a new database entry at the end of the current bucket space.
     *
     * Precondition: the bucket ID must sort after all entries that
     * have already been iterated over or inserted via insert_at_end().
     */
    virtual void insert_at_end(const document::BucketId& bucket_id, const ValueT&) = 0;
};

/**
 * Database implementation-specific interface for accessing bucket
 * entries and prepending entries during a merge() operation.
 */
template <typename ValueT>
struct Merger {
    virtual ~Merger() = default;

    // TODO this should ideally be separated into read/write functions, but this
    // will suffice for now to avoid too many changes.

    /**
     * Bucket key/ID of the currently iterated entry. Unless the information stored
     * in the DB Entry is needed, using one of these methods should be preferred to
     * getting the bucket ID via current_entry(). The underlying DB is expected to
     * have cheap access to the ID but _may_ have expensive access to the entry itself.
     */
    [[nodiscard]] virtual uint64_t bucket_key() const noexcept = 0;
    [[nodiscard]] virtual document::BucketId bucket_id() const noexcept = 0;
    /**
     * Returns a mutable representation of the currently iterated database
     * entry. If changes are made to this object, Result::Update must be
     * returned from merge(). Otherwise, mutation visibility is undefined.
     */
    [[nodiscard]] virtual ValueT& current_entry() = 0;
    /**
     * Insert a new entry into the bucket database that is ordered before the
     * currently iterated entry.
     *
     * Preconditions:
     *  - The bucket ID must sort _before_ the currently iterated
     *    entry's bucket ID, in "reversed bits" bucket key order.
     *  - The bucket ID must sort _after_ any entries previously
     *    inserted with insert_before_current().
     *  - The bucket ID must not be the same as a bucket that was
     *    already iterated over as part of the DB merge() call or inserted
     *    via a previous call to insert_before_current().
     *    Such buckets must be handled by explicitly updating the provided
     *    entry for the iterated bucket and returning Result::Update.
     */
    virtual void insert_before_current(const document::BucketId& bucket_id, const ValueT&) = 0;
};

/**
 * Interface to be implemented by callers that wish to receive callbacks
 * during a bucket merge() operation.
 */
template <typename ValueT>
struct MergingProcessor {
    // See merge() for semantics on enum values.
    enum class Result {
        Update,
        KeepUnchanged,
        Skip
    };

    virtual ~MergingProcessor() = default;
    /**
     * Invoked for each existing bucket in the database, in bucket key order.
     * The provided Merge instance may be used to access the current entry
     * and prepend entries to the DB.
     *
     * Return value semantics:
     *  - Result::Update:
     *      when merge() returns, the changes made to the current entry will
     *      become visible in the bucket database.
     *  - Result::KeepUnchanged:
     *      when merge() returns, the entry will remain in the same state as
     *      it was when merge() was originally called.
     *  - Result::Skip:
     *      when merge() returns, the entry will no longer be part of the DB.
     *      Any entries added via insert_before_current() _will_ be present.
     *
     */
    virtual Result merge(Merger<ValueT>&) = 0;
    /**
     * Invoked once after all existing buckets have been iterated over.
     * The provided TrailingInserter instance may be used to append
     * an arbitrary number of entries to the database.
     *
     * This is used to handle elements remaining at the end of a linear
     * merge operation.
     */
    virtual void insert_remaining_at_end(TrailingInserter<ValueT>&) {}
};

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
    using DataStoreType     = typename DataStoreTraitsT::DataStoreType;
    using ValueType         = typename DataStoreTraitsT::ValueType;
    using ConstValueRef     = typename DataStoreTraitsT::ConstValueRef;
    using GenerationHandler = vespalib::GenerationHandler;

    struct KeyUsedBitsMinMaxAggrCalc : vespalib::btree::MinMaxAggrCalc {
        constexpr static bool aggregate_over_values() { return false; }
        constexpr static int32_t getVal(uint64_t key) noexcept {
            static_assert(document::BucketId::CountBits == 6u);
            return static_cast<int32_t>(key & 0b11'1111U); // 6 LSB of key contains used-bits
        }
    };

    using BTree = vespalib::btree::BTree<uint64_t, uint64_t,
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
    {}

    GenericBTreeBucketDatabase(const GenericBTreeBucketDatabase&) = delete;
    GenericBTreeBucketDatabase& operator=(const GenericBTreeBucketDatabase&) = delete;
    GenericBTreeBucketDatabase(GenericBTreeBucketDatabase&&) = delete;
    GenericBTreeBucketDatabase& operator=(GenericBTreeBucketDatabase&&) = delete;

    // TODO move
    struct EntryProcessor {
        virtual ~EntryProcessor() = default;
        /** Return false to stop iterating. */
        virtual bool process(const typename DataStoreTraitsT::ConstValueRef& e) = 0;
    };

    ValueType entry_from_iterator(const BTreeConstIterator& iter) const;
    ConstValueRef const_value_ref_from_valid_iterator(const BTreeConstIterator& iter) const;

    static document::BucketId bucket_from_valid_iterator(const BTreeConstIterator& iter);

    BTreeConstIterator find(uint64_t key) const noexcept;
    BTreeConstIterator lower_bound(uint64_t key) const noexcept;
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

    template <typename Func>
    void find_parents_and_self(const document::BucketId& bucket,
                               Func func) const;

    template <typename Func>
    void find_parents_self_and_children(const document::BucketId& bucket,
                                        Func func) const;

    void for_each(EntryProcessor& proc, const document::BucketId& after) const;

    document::BucketId getAppropriateBucket(uint16_t minBits, const document::BucketId& bid) const;

    [[nodiscard]] uint32_t child_subtree_count(const document::BucketId& bucket) const;

    const DataStoreType& store() const noexcept { return _store; }
    DataStoreType& store() noexcept { return _store; }

    void merge(MergingProcessor<ValueType>& proc);
private:
    // Functor is called for each found element in key order, with raw u64 keys and values.
    template <typename Func>
    BTreeConstIterator find_parents_internal(const typename BTree::FrozenView& frozen_view,
                                             const document::BucketId& bucket,
                                             Func func) const;
    template <typename Func>
    void find_parents_and_self_internal(const typename BTree::FrozenView& frozen_view,
                                        const document::BucketId& bucket,
                                        Func func) const;
    void commit_tree_changes();

    template <typename DataStoreTraitsT2> friend struct BTreeBuilderMerger;
    template <typename DataStoreTraitsT2> friend struct BTreeTrailingInserter;
};

uint8_t getMinDiffBits(uint16_t minBits, const document::BucketId& a, const document::BucketId& b);
uint8_t next_parent_bit_seek_level(uint8_t minBits, const document::BucketId& a, const document::BucketId& b);

}
