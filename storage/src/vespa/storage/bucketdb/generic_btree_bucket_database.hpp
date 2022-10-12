// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "generic_btree_bucket_database.h"
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>

namespace storage::bucketdb {

using document::BucketId;

template <typename DataStoreTraitsT>
GenericBTreeBucketDatabase<DataStoreTraitsT>::~GenericBTreeBucketDatabase() {
    // If there was a snapshot reader concurrent with the last modify operation
    // on the DB, it's possible for the hold list to be non-empty. Explicitly
    // clean it up now to ensure that we don't try to destroy any data stores
    // with a non-empty hold list. Failure to do so might trigger an assertion.
    commit_tree_changes();
}

template <typename DataStoreTraitsT>
BucketId GenericBTreeBucketDatabase<DataStoreTraitsT>::bucket_from_valid_iterator(const BTreeConstIterator& iter) {
    return BucketId(BucketId::keyToBucketId(iter.getKey()));
}

template <typename DataStoreTraitsT>
void GenericBTreeBucketDatabase<DataStoreTraitsT>::commit_tree_changes() {
    // TODO break up and refactor
    // TODO verify semantics and usage
    // TODO make BTree wrapping API which abstracts away all this stuff via reader/writer interfaces
    _tree.getAllocator().freeze();

    auto current_gen = _generation_handler.getCurrentGeneration();
    _store.assign_generation(current_gen);
    _tree.getAllocator().assign_generation(current_gen);

    _generation_handler.incGeneration();

    auto used_gen = _generation_handler.get_oldest_used_generation();
    _store.reclaim_memory(used_gen);
    _tree.getAllocator().reclaim_memory(used_gen);
}

template <typename DataStoreTraitsT>
void GenericBTreeBucketDatabase<DataStoreTraitsT>::clear() noexcept {
    _tree.clear();
    commit_tree_changes();
}

template <typename DataStoreTraitsT>
size_t GenericBTreeBucketDatabase<DataStoreTraitsT>::size() const noexcept {
    return _tree.size();
}

template <typename DataStoreTraitsT>
bool GenericBTreeBucketDatabase<DataStoreTraitsT>::empty() const noexcept {
    return !_tree.begin().valid();
}

template <typename DataStoreTraitsT>
vespalib::MemoryUsage GenericBTreeBucketDatabase<DataStoreTraitsT>::memory_usage() const noexcept {
    auto mem_usage = _tree.getMemoryUsage();
    mem_usage.merge(_store.getMemoryUsage());
    return mem_usage;
}

template <typename DataStoreTraitsT>
typename GenericBTreeBucketDatabase<DataStoreTraitsT>::ValueType
GenericBTreeBucketDatabase<DataStoreTraitsT>::entry_from_iterator(const BTreeConstIterator& iter) const {
    if (!iter.valid()) {
        return DataStoreTraitsT::make_invalid_value();
    }
    const auto value = iter.getData().load_acquire();
    return DataStoreTraitsT::unwrap_from_key_value(_store, iter.getKey(), value);
}

template <typename DataStoreTraitsT>
typename GenericBTreeBucketDatabase<DataStoreTraitsT>::ConstValueRef
GenericBTreeBucketDatabase<DataStoreTraitsT>::const_value_ref_from_valid_iterator(const BTreeConstIterator& iter) const {
    const auto value = iter.getData().load_acquire();
    return DataStoreTraitsT::unwrap_const_ref_from_key_value(_store, iter.getKey(), value);
}

template <typename DataStoreTraitsT>
typename GenericBTreeBucketDatabase<DataStoreTraitsT>::BTreeConstIterator
GenericBTreeBucketDatabase<DataStoreTraitsT>::lower_bound(uint64_t key) const noexcept {
    return _tree.lowerBound(key);
}

template <typename DataStoreTraitsT>
typename GenericBTreeBucketDatabase<DataStoreTraitsT>::BTreeConstIterator
GenericBTreeBucketDatabase<DataStoreTraitsT>::upper_bound(uint64_t key) const noexcept {
    return _tree.upperBound(key);
}

template <typename DataStoreTraitsT>
typename GenericBTreeBucketDatabase<DataStoreTraitsT>::BTreeConstIterator
GenericBTreeBucketDatabase<DataStoreTraitsT>::find(uint64_t key) const noexcept {
    return _tree.find(key);
}

template <typename DataStoreTraitsT>
typename GenericBTreeBucketDatabase<DataStoreTraitsT>::BTreeConstIterator
GenericBTreeBucketDatabase<DataStoreTraitsT>::begin() const noexcept {
    return _tree.begin();
}

struct ByValue {
    template <typename DB, typename Iter>
    static typename DB::ValueType apply(const DB& db, const Iter& iter) {
        return db.entry_from_iterator(iter);
    };
};

struct ByConstRef {
    template <typename DB, typename Iter>
    static typename DB::ConstValueRef apply(const DB& db, const Iter& iter) {
        return db.const_value_ref_from_valid_iterator(iter);
    };
};

/*
 * Finding the complete set of parents of a given bucket is not obvious how to
 * do efficiently, as we only know that the parents are ordered before their
 * children, but we do not a-priori know if any exist at all. The Judy DB impl
 * does O(b) explicit point lookups (where b is the number of used bits in the
 * bucket), starting at the leaf bit and working towards the root. To avoid
 * having to re-create iterators and perform a full tree search every time, we
 * turn this on its head and start from the root, progressing towards the leaf.
 * This allows us to reuse a single iterator and to continue seeking forwards
 * from its current position.
 *
 * To speed up the process of converging on the target bucket without needing
 * to check many unrelated subtrees, we let the underlying B-tree automatically
 * aggregate the min/max range of the used-bits of all contained bucket keys.
 * If we e.g. know that the minimum number of used bits in the DB is 16, we can
 * immediately seek to this level in the tree instead of working our way down
 * one bit at a time. By definition, no parents can exist above this level.
 * This is a very important optimization, as bucket trees are usually very well
 * balanced due to randomized distribution of data (combined with a cluster-wide
 * minimum tree level imposed by distribution bits). It is common that the minimum
 * number of used bits == max number of used bits, i.e. a totally even split.
 * This means that for a system without inconsistently split buckets (i.e. no
 * parents) we're highly likely to converge on the target bucket in a single seek.
 *
 * Algorithm:
 *
 *   Core invariant: every subsequent iterator seek performed in this algorithm
 *   is for a key that is strictly higher than the one the iterator is currently at.
 *
 *   1. Lbound seek to the lowest key that is known to exclude all already visited
 *      parents. On the first iteration we use a bit count equal to the minimum number
 *      of key used-bits in the entire DB, allowing us to potentially skip most subtrees.
 *   2. If the current node's key is greater than that of the requested bucket's key,
 *      we've either descended to--or beyond--it in its own subtree or we've entered
 *      a disjoint subtree. Since we know that all parents must sort before any given
 *      child bucket, no more parents may be found at this point. Algorithm terminates.
 *   3. As the main body of the loop is entered, we know one of following must hold:
 *      3.1 The current node is an explicitly present parent of our bucket.
 *      3.2 The current node is contained in a left subtree branch of a parent that
 *          does not have a bucket explicitly present in the tree. It cannot be in
 *          a right subtree of any parent, as that would imply the node is ordered
 *          _after_ our own bucket in an in-order traversal, which would contradict
 *          the check in step 2 above.
 *   4. If the current node contains the requested bucket, we're at a parent
 *      node of the bucket; add it to the result set.
 *      If this is _not_ the case, we're in a different subtree. Example: the
 *      requested bucket has a key whose MSB is 1 but the first bucket in the
 *      tree has a key with an MSB of 0. Either way we need to update our search
 *      key to home in on the target subtree where more parents may be found;
 *   5. Update the seek key to find the next possible parent. To ensure this key is
 *      strictly greater than the iterator's current key we find the largest shared
 *      prefix of bits in common between the current node's key and the requested
 *      bucket's key. The prefix length + 1 is then the depth in the tree at which the
 *      two subtrees branch off and diverge.
 *      The new key is then the MSB prefix length + 1 requested bucket's key with a
 *      matching number of used-bits set. Forward lbound-seek the iterator to this key.
 *      `--> TODO elaborate on prefix semantics when they are equal wrt. min used bits
 *   6. Iff iterator is still valid, go to step 2
 *
 * This algorithm is able to skip through large parts of the tree in a sparsely populated
 * tree, but the number of seeks will trend towards O(b - min_bits) as with the legacy
 * implementation when a tree is densely populated (where `b` is the used-bits count of the
 * most specific node in the tree for the target bucket, and min_bits is the minimum number
 * of used-bits for any key in the database). This because all logical inner nodes in the tree
 * will have subtrees under them. Even in the worst case we should be more efficient than the
 * legacy Judy-based implementation since we've cut any dense search space in half for each
 * invocation of seek() on the iterator.
 */
template <typename DataStoreTraitsT>
template <typename IterValueExtractor, typename Func>
typename GenericBTreeBucketDatabase<DataStoreTraitsT>::BTreeConstIterator
GenericBTreeBucketDatabase<DataStoreTraitsT>::find_parents_internal(
        const typename BTree::FrozenView& frozen_view,
        const BucketId& bucket,
        Func func) const
{
    const uint64_t bucket_key = bucket.toKey();
    if (frozen_view.empty()) {
        return frozen_view.begin(); // Will be invalid.
    }
    const auto min_db_bits = frozen_view.getAggregated().getMin();
    assert(min_db_bits >= static_cast<int32_t>(BucketId::minNumBits));
    assert(min_db_bits <= static_cast<int32_t>(BucketId::maxNumBits));
    // Start at the lowest possible tree level no parents can exist above,
    // descending towards the bucket itself.
    // Note: important to use getId() rather than getRawId(), as min_db_bits may be
    // greater than the used bits of the queried bucket. If we used the raw ID, we'd
    // end up looking at undefined bits.
    const auto first_key = BucketId(min_db_bits, bucket.getId()).toKey();
    auto iter = frozen_view.lowerBound(first_key);
    // Try skipping as many levels of the tree as possible as we go.
    uint32_t bits = min_db_bits;
    while (iter.valid() && (iter.getKey() < bucket_key)) {
        auto candidate = BucketId(BucketId::keyToBucketId(iter.getKey()));
        if (candidate.contains(bucket)) {
            assert(candidate.getUsedBits() >= bits);
            func(iter.getKey(), IterValueExtractor::apply(*this, iter));
        }
        bits = next_parent_bit_seek_level(bits, candidate, bucket);
        const auto parent_key = BucketId(bits, bucket.getRawId()).toKey();
        assert(parent_key > iter.getKey());
        iter.seek(parent_key);
    }
    return iter;
}

template <typename DataStoreTraitsT>
template <typename IterValueExtractor, typename Func>
void GenericBTreeBucketDatabase<DataStoreTraitsT>::find_parents_and_self_internal(
        const typename BTree::FrozenView& frozen_view,
        const BucketId& bucket,
        Func func) const
{
    auto iter = find_parents_internal<IterValueExtractor>(frozen_view, bucket, func);
    if (iter.valid() && iter.getKey() == bucket.toKey()) {
        func(iter.getKey(), IterValueExtractor::apply(*this, iter));
    }
}

template <typename DataStoreTraitsT>
template <typename IterValueExtractor, typename Func>
void GenericBTreeBucketDatabase<DataStoreTraitsT>::find_parents_and_self(
        const document::BucketId& bucket,
        Func func) const
{
    auto view = _tree.getFrozenView();
    find_parents_and_self_internal<IterValueExtractor>(view, bucket, std::move(func));
}

template <typename DataStoreTraitsT>
template <typename IterValueExtractor, typename Func>
void GenericBTreeBucketDatabase<DataStoreTraitsT>::find_parents_self_and_children_internal(
        const typename BTree::FrozenView& frozen_view,
        const BucketId& bucket,
        Func func) const
{
    auto iter = find_parents_internal<IterValueExtractor>(frozen_view, bucket, func);
    // `iter` is already pointing at, or beyond, one of the bucket's subtrees.
    for (; iter.valid(); ++iter) {
        auto candidate = BucketId(BucketId::keyToBucketId(iter.getKey()));
        if (bucket.contains(candidate)) {
            func(iter.getKey(), IterValueExtractor::apply(*this, iter));
        } else {
            break;
        }
    }
}

template <typename DataStoreTraitsT>
template <typename IterValueExtractor, typename Func>
void GenericBTreeBucketDatabase<DataStoreTraitsT>::find_parents_self_and_children(
        const BucketId& bucket,
        Func func) const
{
    auto view = _tree.getFrozenView();
    find_parents_self_and_children_internal<IterValueExtractor>(view, bucket, std::move(func));
}

template <typename DataStoreTraitsT>
typename GenericBTreeBucketDatabase<DataStoreTraitsT>::ValueType
GenericBTreeBucketDatabase<DataStoreTraitsT>::get(const BucketId& bucket) const {
    return entry_from_iterator(_tree.find(bucket.toKey()));
}

template <typename DataStoreTraitsT>
typename GenericBTreeBucketDatabase<DataStoreTraitsT>::ValueType
GenericBTreeBucketDatabase<DataStoreTraitsT>::get_by_raw_key(uint64_t key) const {
    return entry_from_iterator(_tree.find(key));
}

template <typename DataStoreTraitsT>
bool GenericBTreeBucketDatabase<DataStoreTraitsT>::remove_by_raw_key(uint64_t key) {
    auto iter = _tree.find(key);
    if (!iter.valid()) {
        return false;
    }
    const auto value = iter.getData().load_relaxed(); // Called from writer only
    DataStoreTraitsT::remove_by_wrapped_value(_store, value);
    _tree.remove(iter);
    commit_tree_changes();
    return true;
}

template <typename DataStoreTraitsT>
bool GenericBTreeBucketDatabase<DataStoreTraitsT>::remove(const BucketId& bucket) {
    return remove_by_raw_key(bucket.toKey());
}

template <typename DataStoreTraitsT>
bool GenericBTreeBucketDatabase<DataStoreTraitsT>::update_by_raw_key(uint64_t bucket_key,
                                                                     const ValueType& new_entry)
{
    const auto new_value = DataStoreTraitsT::wrap_and_store_value(_store, new_entry);
    auto iter = _tree.lowerBound(bucket_key);
    const bool pre_existed = (iter.valid() && (iter.getKey() == bucket_key));
    if (pre_existed) {
        DataStoreTraitsT::remove_by_wrapped_value(_store, iter.getData().load_relaxed());
        // In-place update of value; does not require tree structure modification
        iter.getWData().store_release(new_value); // Must ensure visibility when new array ref is observed
    } else {
        _tree.insert(iter, bucket_key, AtomicValueWrapper(new_value));
    }
    commit_tree_changes(); // TODO does publishing a new root imply an implicit memory fence?
    return pre_existed;
}

template <typename DataStoreTraitsT>
bool GenericBTreeBucketDatabase<DataStoreTraitsT>::update(const BucketId& bucket,
                                                          const ValueType& new_entry)
{
    return update_by_raw_key(bucket.toKey(), new_entry);
}

template <typename DataStoreTraitsT>
template <typename EntryUpdateProcessor>
void
GenericBTreeBucketDatabase<DataStoreTraitsT>::process_update(const BucketId& bucket, EntryUpdateProcessor& processor, bool create_if_nonexisting)
{
    uint64_t bucket_key = bucket.toKey();
    auto iter = _tree.lowerBound(bucket_key);
    bool found = true;
    if (!iter.valid() || bucket_key < iter.getKey()) {
        if (!create_if_nonexisting) {
            return;
        }
        found = false;
    }
    ValueType entry(found ? entry_from_iterator(iter) : processor.create_entry(bucket));
    bool keep = processor.process_entry(entry);
    if (found) {
        DataStoreTraitsT::remove_by_wrapped_value(_store, iter.getData().load_relaxed()); // Called from writer only
        if (keep) {
            const auto new_value = DataStoreTraitsT::wrap_and_store_value(_store, entry);
            iter.getWData().store_release(new_value);
        } else {
            _tree.remove(iter);
        }
    } else {
        if (keep) {
            const auto new_value = DataStoreTraitsT::wrap_and_store_value(_store, entry);
            _tree.insert(iter, bucket_key, AtomicValueWrapper(new_value));
        }
    }
    commit_tree_changes();
}

/*
 * Returns the bucket ID which, based on the buckets already existing in the DB,
 * is the most specific location in the tree in which it should reside. This may
 * or may not be a bucket that already exists.
 *
 * Example: if there is a single bucket (1, 1) in the tree, a query for (1, 1) or
 * (1, 3) will return (1, 1) as that is the most specific leaf in that subtree.
 * A query for (1, 0) will return (1, 0) even though this doesn't currently exist,
 * as there is no existing bucket that can contain the queried bucket. It is up to
 * the caller to create this bucket according to its needs.
 *
 * Usually this function will be called with an ID whose used-bits is at max (58), in
 * order to find a leaf bucket to route an incoming document operation to.
 *
 * TODO rename this function, it's very much _not_ obvious what an "appropriate" bucket is..!
 * TODO this should be possible to do concurrently
 */
template <typename DataStoreTraitsT>
BucketId GenericBTreeBucketDatabase<DataStoreTraitsT>::getAppropriateBucket(uint16_t minBits, const BucketId& bid) const {
    // The bucket tree is ordered in such a way that it represents a
    // natural in-order traversal of all buckets, with inner nodes being
    // visited before leaf nodes. This means that a lower bound seek will
    // never return a parent of a seeked bucket. The iterator will be pointing
    // to a bucket that is either the actual bucket given as the argument to
    // lowerBound() or the next in-order bucket (or end() if none exists).
    // TODO snapshot
    auto iter = _tree.lowerBound(bid.toKey());
    if (iter.valid()) {
        // Find the first level in the tree where the paths through the bucket tree
        // diverge for the target bucket and the current bucket.
        minBits = getMinDiffBits(minBits, bucket_from_valid_iterator(iter), bid);
    }
    // TODO is it better to copy original iterator and do begin() on the copy?
    auto first_iter = _tree.begin();
    // Original iterator might be in a different subtree than that of our
    // target bucket. If possible, rewind one node to discover any parent or
    // leftmost sibling of our node. If there's no such node, we'll still
    // discover the greatest equal bit prefix.
    if (iter != first_iter) {
        --iter;
        minBits = getMinDiffBits(minBits, bucket_from_valid_iterator(iter), bid);
    }
    return BucketId(minBits, bid.getRawId());
}

/*
 * Enumerate the number of child subtrees under `bucket`. The value returned is in the
 * range [0, 2] regardless of how many subtrees are present further down in the tree.
 *
 * Finding this number is reasonably straight forward; we construct two buckets that
 * represent the key ranges for the left and right subtrees under `bucket` and check
 * if there are any ranges in the tree's keyspace that are contained in these.
 */
template <typename DataStoreTraitsT>
uint32_t GenericBTreeBucketDatabase<DataStoreTraitsT>::child_subtree_count(const BucketId& bucket) const {
    assert(bucket.getUsedBits() < BucketId::maxNumBits);
    BucketId lhs_bucket(bucket.getUsedBits() + 1, bucket.getId());
    BucketId rhs_bucket(bucket.getUsedBits() + 1, (1ULL << bucket.getUsedBits()) | bucket.getId());

    auto iter = _tree.lowerBound(lhs_bucket.toKey());
    if (!iter.valid()) {
        return 0;
    }
    if (lhs_bucket.contains(bucket_from_valid_iterator(iter))) {
        iter.seek(rhs_bucket.toKey());
        if (!iter.valid()) {
            return 1; // lhs subtree only
        }
        return (rhs_bucket.contains(bucket_from_valid_iterator(iter)) ? 2 : 1);
    } else if (rhs_bucket.contains(bucket_from_valid_iterator(iter))) {
        return 1; // rhs subtree only
    }
    return 0;
}

template <typename DataStoreTraitsT>
struct BTreeBuilderMerger final : Merger<typename DataStoreTraitsT::ValueType> {
    using DBType           = GenericBTreeBucketDatabase<DataStoreTraitsT>;
    using ValueType        = typename DataStoreTraitsT::ValueType;
    using BTreeBuilderType = typename DBType::BTree::Builder;

    DBType&           _db;
    BTreeBuilderType& _builder;
    uint64_t          _current_key;
    uint64_t          _current_value;
    ValueType         _cached_value;
    bool              _valid_cached_value;

    BTreeBuilderMerger(DBType& db, BTreeBuilderType& builder)
        : _db(db),
          _builder(builder),
          _current_key(0),
          _current_value(0),
          _cached_value(),
          _valid_cached_value(false)
    {}
    ~BTreeBuilderMerger() override = default;

    uint64_t bucket_key() const noexcept override {
        return _current_key;
    }
    BucketId bucket_id() const noexcept override {
        return BucketId(BucketId::keyToBucketId(_current_key));
    }
    ValueType& current_entry() override {
        if (!_valid_cached_value) {
            _cached_value = DataStoreTraitsT::unwrap_from_key_value(_db.store(), _current_key, _current_value);
            _valid_cached_value = true;
        }
        return _cached_value;
    }
    void insert_before_current(const BucketId& bucket_id, const ValueType& e) override {
        const uint64_t bucket_key = bucket_id.toKey();
        assert(bucket_key < _current_key);
        const auto new_value = DataStoreTraitsT::wrap_and_store_value(_db.store(), e);
        _builder.insert(bucket_key, vespalib::datastore::AtomicValueWrapper<uint64_t>(new_value));
    }

    void update_iteration_state(uint64_t key, uint64_t value) {
        _current_key = key;
        _current_value = value;
        _valid_cached_value = false;
    }
};

template <typename DataStoreTraitsT>
struct BTreeTrailingInserter final : TrailingInserter<typename DataStoreTraitsT::ValueType> {
    using DBType           = GenericBTreeBucketDatabase<DataStoreTraitsT>;
    using ValueType        = typename DataStoreTraitsT::ValueType;
    using BTreeBuilderType = typename DBType::BTree::Builder;

    DBType&           _db;
    BTreeBuilderType& _builder;

    BTreeTrailingInserter(DBType& db, BTreeBuilderType& builder)
        : _db(db),
          _builder(builder)
    {}

    ~BTreeTrailingInserter() override = default;

    void insert_at_end(const BucketId& bucket_id, const ValueType& e) override {
        const uint64_t bucket_key = bucket_id.toKey();
        const auto new_value = DataStoreTraitsT::wrap_and_store_value(_db.store(), e);
        _builder.insert(bucket_key, vespalib::datastore::AtomicValueWrapper<uint64_t>(new_value));
    }
};

// TODO lbound arg?
template <typename DataStoreTraitsT>
void GenericBTreeBucketDatabase<DataStoreTraitsT>::merge(MergingProcessor<ValueType>& proc) {
    typename BTree::Builder builder(_tree.getAllocator());
    BTreeBuilderMerger<DataStoreTraitsT> merger(*this, builder);

    // TODO for_each instead?
    for (auto iter = _tree.begin(); iter.valid(); ++iter) {
        const uint64_t key = iter.getKey();
        const uint64_t value = iter.getData().load_relaxed(); // Only called from writer
        merger.update_iteration_state(key, value);

        auto result = proc.merge(merger);

        if (result == MergingProcessor<ValueType>::Result::KeepUnchanged) {
            builder.insert(key, AtomicValueWrapper(value)); // Reuse array store ref with no changes
        } else if (result == MergingProcessor<ValueType>::Result::Update) {
            assert(merger._valid_cached_value); // Must actually have been touched
            assert(merger._cached_value.valid());
            DataStoreTraitsT::remove_by_wrapped_value(_store, value);
            const auto new_value = DataStoreTraitsT::wrap_and_store_value(_store, merger._cached_value);
            builder.insert(key, AtomicValueWrapper(new_value));
        } else if (result == MergingProcessor<ValueType>::Result::Skip) {
            DataStoreTraitsT::remove_by_wrapped_value(_store, value);
        } else {
            abort();
        }
    }
    BTreeTrailingInserter<DataStoreTraitsT> inserter(*this, builder);
    proc.insert_remaining_at_end(inserter);

    _tree.assign(builder);
    commit_tree_changes();
}

template <typename DataStoreTraitsT>
GenericBTreeBucketDatabase<DataStoreTraitsT>::ReadSnapshot::ReadSnapshot(
        const GenericBTreeBucketDatabase<DataStoreTraitsT>& db)
    : _db(&db),
      _guard(_db->_generation_handler.takeGuard()),
      _frozen_view(_db->_tree.getFrozenView())
{
}

template <typename DataStoreTraitsT>
GenericBTreeBucketDatabase<DataStoreTraitsT>::ReadSnapshot::~ReadSnapshot() = default;

template <typename DataStoreTraitsT>
template <typename IterValueExtractor, typename Func>
void GenericBTreeBucketDatabase<DataStoreTraitsT>::ReadSnapshot::find_parents_and_self(
        const BucketId& bucket,
        Func func) const
{
    _db->find_parents_and_self_internal<IterValueExtractor>(_frozen_view, bucket, std::move(func));
}

template <typename DataStoreTraitsT>
template <typename IterValueExtractor, typename Func>
void GenericBTreeBucketDatabase<DataStoreTraitsT>::ReadSnapshot::find_parents_self_and_children(
        const BucketId& bucket,
        Func func) const
{
    _db->find_parents_self_and_children_internal<IterValueExtractor>(_frozen_view, bucket, std::move(func));
}

template <typename DataStoreTraitsT>
template <typename IterValueExtractor, typename Func>
void GenericBTreeBucketDatabase<DataStoreTraitsT>::ReadSnapshot::for_each(Func func) const {
    for (auto iter = _frozen_view.begin(); iter.valid(); ++iter) {
        // Iterator value extractor implicitly inserts any required memory fences for value.
        func(iter.getKey(), IterValueExtractor::apply(*_db, iter));
    }
}

template <typename DataStoreTraitsT>
class GenericBTreeBucketDatabase<DataStoreTraitsT>::ReadSnapshot::ConstIteratorImpl
    : public ConstIterator<typename DataStoreTraitsT::ConstValueRef>
{
    const typename GenericBTreeBucketDatabase<DataStoreTraitsT>::ReadSnapshot& _snapshot;
    typename BTree::ConstIterator _iter;
public:
    using ConstValueRef = typename DataStoreTraitsT::ConstValueRef;

    explicit ConstIteratorImpl(const GenericBTreeBucketDatabase<DataStoreTraitsT>::ReadSnapshot& snapshot)
        : _snapshot(snapshot),
          _iter(_snapshot._frozen_view.begin())
    {}
    ~ConstIteratorImpl() override = default;

    void next() noexcept override {
        ++_iter;
    }
    bool valid() const noexcept override {
        return _iter.valid();
    }
    uint64_t key() const noexcept override {
        return _iter.getKey();
    }
    ConstValueRef value() const override {
        return ByConstRef::apply(*_snapshot._db, _iter);
    }
};

template <typename DataStoreTraitsT>
std::unique_ptr<ConstIterator<typename DataStoreTraitsT::ConstValueRef>>
GenericBTreeBucketDatabase<DataStoreTraitsT>::ReadSnapshot::create_iterator() const {
    return std::make_unique<ConstIteratorImpl>(*this);
}

template <typename DataStoreTraitsT>
uint64_t GenericBTreeBucketDatabase<DataStoreTraitsT>::ReadSnapshot::generation() const noexcept {
    return _guard.getGeneration();
}

}
