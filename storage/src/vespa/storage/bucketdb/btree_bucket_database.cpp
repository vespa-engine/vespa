// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btree_bucket_database.h"
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/datastore/array_store.hpp>
#include <iostream>

// TODO remove once this impl uses the generic bucket B-tree code!
#include "generic_btree_bucket_database.h"
#include <vespa/vespalib/datastore/datastore.h>

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
    return ReplicaStore::optimizedConfigForHugePage(1023, vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                    4 * 1024, 8 * 1024, 0.2).enable_free_lists(true);
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

// TODO dedupe and unify common code
uint8_t
getMinDiffBits(uint16_t minBits, const document::BucketId& a, const document::BucketId& b) {
    for (uint32_t i = minBits; i <= std::min(a.getUsedBits(), b.getUsedBits()); i++) {
        document::BucketId a1(i, a.getRawId());
        document::BucketId b1(i, b.getRawId());
        if (b1.getId() != a1.getId()) {
            return i;
        }
    }
    return minBits;
}

uint8_t next_parent_bit_seek_level(uint8_t minBits, const document::BucketId& a, const document::BucketId& b) {
    const uint8_t min_used = std::min(a.getUsedBits(), b.getUsedBits());
    assert(min_used >= minBits); // Always monotonically descending towards leaves
    for (uint32_t i = minBits; i <= min_used; i++) {
        document::BucketId a1(i, a.getRawId());
        document::BucketId b1(i, b.getRawId());
        if (b1.getId() != a1.getId()) {
            return i;
        }
    }
    // The bit prefix is equal, which means that one node is a parent of the other. In this
    // case we have to force the seek to continue from the next level in the tree.
    return std::max(min_used, minBits) + 1;
}

// TODO getMinDiffBits is hoisted from lockablemap.cpp, could probably be rewritten in terms of xor and MSB bit scan instr
/*
 *       63 -------- ... -> 0
 *     a: 1101111111 ... 0010
 *     b: 1101110010 ... 0011
 * a ^ b: 0000001101 ... 0001
 *              ^- diff bit = 57
 *
 * 63 - vespalib::Optimized::msbIdx(a ^ b) ==> 6
 *
 * what if a == b? special case? not a problem if we can prove this never happens.
 */

}

BTreeBucketDatabase::BTreeBucketDatabase()
    : _tree(),
      _store(make_default_array_store_config<ReplicaStore>()),
      _generation_handler()
{
}

BTreeBucketDatabase::~BTreeBucketDatabase() = default;

void BTreeBucketDatabase::commit_tree_changes() {
    // TODO break up and refactor
    // TODO verify semantics and usage
    // TODO make BTree wrapping API which abstracts away all this stuff via reader/writer interfaces
    _tree.getAllocator().freeze();

    auto current_gen = _generation_handler.getCurrentGeneration();
    _store.transferHoldLists(current_gen);
    _tree.getAllocator().transferHoldLists(current_gen);

    _generation_handler.incGeneration();

    auto used_gen = _generation_handler.getFirstUsedGeneration();
    _store.trimHoldLists(used_gen);
    _tree.getAllocator().trimHoldLists(used_gen);
}

Entry BTreeBucketDatabase::entry_from_value(uint64_t bucket_key, uint64_t value) const {
    const auto replicas_ref = _store.get(entry_ref_from_value(value));
    const auto bucket = BucketId(BucketId::keyToBucketId(bucket_key));
    return entry_from_replica_array_ref(bucket, gc_timestamp_from_value(value), replicas_ref);
}

Entry BTreeBucketDatabase::entry_from_iterator(const BTree::ConstIterator& iter) const {
    if (!iter.valid()) {
        return Entry::createInvalid();
    }
    const auto value = iter.getData();
    std::atomic_thread_fence(std::memory_order_acquire);
    return entry_from_value(iter.getKey(), value);
}

ConstEntryRef BTreeBucketDatabase::const_entry_ref_from_iterator(const BTree::ConstIterator& iter) const {
    if (!iter.valid()) {
        return ConstEntryRef::createInvalid();
    }
    const auto value = iter.getData();
    std::atomic_thread_fence(std::memory_order_acquire);
    const auto replicas_ref = _store.get(entry_ref_from_value(value));
    const auto bucket = BucketId(BucketId::keyToBucketId(iter.getKey()));
    return const_entry_ref_from_replica_array_ref(bucket, gc_timestamp_from_value(value), replicas_ref);
}

BucketId BTreeBucketDatabase::bucket_from_valid_iterator(const BTree::ConstIterator& iter) const {
    return BucketId(BucketId::keyToBucketId(iter.getKey()));
}

Entry BTreeBucketDatabase::get(const BucketId& bucket) const {
    return entry_from_iterator(_tree.find(bucket.toKey()));
}

void BTreeBucketDatabase::remove(const BucketId& bucket) {
    auto iter = _tree.find(bucket.toKey());
    if (!iter.valid()) {
        return;
    }
    const auto value = iter.getData();
    _store.remove(entry_ref_from_value(value));
    _tree.remove(iter);
    commit_tree_changes();
}

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
BTreeBucketDatabase::BTree::ConstIterator
BTreeBucketDatabase::find_parents_internal(const BTree::FrozenView& frozen_view,
                                           const document::BucketId& bucket,
                                           std::vector<Entry>& entries) const
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
            entries.emplace_back(entry_from_iterator(iter));
        }
        bits = next_parent_bit_seek_level(bits, candidate, bucket);
        const auto parent_key = BucketId(bits, bucket.getRawId()).toKey();
        assert(parent_key > iter.getKey());
        iter.seek(parent_key);
    }
    return iter;
}

void BTreeBucketDatabase::find_parents_and_self_internal(const BTree::FrozenView& frozen_view,
                                                         const document::BucketId& bucket,
                                                         std::vector<Entry>& entries) const
{
    auto iter = find_parents_internal(frozen_view, bucket, entries);
    if (iter.valid() && iter.getKey() == bucket.toKey()) {
        entries.emplace_back(entry_from_iterator(iter));
    }
}

/*
 * Note: due to legacy API reasons, iff the requested bucket itself exists in the
 * tree, it will be returned in the result set. I.e. it returns all the nodes on
 * the path from _and including_ itself towards the root.
 */
void BTreeBucketDatabase::getParents(const BucketId& bucket,
                                     std::vector<Entry>& entries) const
{
    auto view = _tree.getFrozenView();
    find_parents_and_self_internal(view, bucket, entries);
}

void BTreeBucketDatabase::getAll(const BucketId& bucket,
                                 std::vector<Entry>& entries) const
{
    auto view = _tree.getFrozenView();
    auto iter = find_parents_internal(view, bucket, entries);
    // `iter` is already pointing at, or beyond, one of the bucket's subtrees.
    for (; iter.valid(); ++iter) {
        auto candidate = BucketId(BucketId::keyToBucketId(iter.getKey()));
        if (bucket.contains(candidate)) {
            entries.emplace_back(entry_from_iterator(iter));
        } else {
            break;
        }
    }
}

void BTreeBucketDatabase::update(const Entry& newEntry) {
    assert(newEntry.valid());
    auto replicas_ref = _store.add(newEntry.getBucketInfo().getRawNodes());
    const auto new_value = value_from(newEntry.getBucketInfo().getLastGarbageCollectionTime(), replicas_ref);
    const auto bucket_key = newEntry.getBucketId().toKey();
    auto iter = _tree.lowerBound(bucket_key);
    if (iter.valid() && (iter.getKey() == bucket_key)) {
        _store.remove(entry_ref_from_value(iter.getData()));
        // In-place update of value; does not require tree structure modification
        std::atomic_thread_fence(std::memory_order_release); // Must ensure visibility when new array ref is observed
        iter.writeData(new_value);
    } else {
        _tree.insert(iter, bucket_key, new_value);
    }
    commit_tree_changes(); // TODO does publishing a new root imply an implicit memory fence?
}

// TODO need snapshot read with guarding
// FIXME semantics of for-each in judy and bit tree DBs differ, former expects lbound, latter ubound..!
// FIXME but bit-tree code says "lowerBound" in impl and "after" in declaration???
void BTreeBucketDatabase::forEach(EntryProcessor& proc, const BucketId& after) const {
    for (auto iter = _tree.upperBound(after.toKey()); iter.valid(); ++iter) {
        if (!proc.process(const_entry_ref_from_iterator(iter))) {
            break;
        }
    }
}

struct BTreeBuilderMerger final : BucketDatabase::Merger {
    BTreeBucketDatabase& _db;
    BTreeBucketDatabase::BTree::Builder& _builder;
    uint64_t _current_key;
    uint64_t _current_value;
    Entry _cached_entry;
    bool _valid_cached_entry;

    BTreeBuilderMerger(BTreeBucketDatabase& db,
                       BTreeBucketDatabase::BTree::Builder& builder)
        : _db(db),
          _builder(builder),
          _current_key(0),
          _current_value(0),
          _cached_entry(),
          _valid_cached_entry(false)
    {}
    ~BTreeBuilderMerger() override = default;

    uint64_t bucket_key() const noexcept override {
        return _current_key;
    }
    BucketId bucket_id() const noexcept override {
        return BucketId(BucketId::keyToBucketId(_current_key));
    }
    Entry& current_entry() override {
        if (!_valid_cached_entry) {
            _cached_entry = _db.entry_from_value(_current_key, _current_value);
            _valid_cached_entry = true;
        }
        return _cached_entry;
    }
    void insert_before_current(const Entry& e) override {
        const uint64_t bucket_key = e.getBucketId().toKey();
        assert(bucket_key < _current_key);

        auto replicas_ref = _db._store.add(e.getBucketInfo().getRawNodes());
        const auto new_value = value_from(e.getBucketInfo().getLastGarbageCollectionTime(), replicas_ref);

        _builder.insert(bucket_key, new_value);
    }

    void update_iteration_state(uint64_t key, uint64_t value) {
        _current_key = key;
        _current_value = value;
        _valid_cached_entry = false;
    }
};

struct BTreeTrailingInserter final : BucketDatabase::TrailingInserter {
    BTreeBucketDatabase& _db;
    BTreeBucketDatabase::BTree::Builder& _builder;

    BTreeTrailingInserter(BTreeBucketDatabase& db,
                          BTreeBucketDatabase::BTree::Builder& builder)
        : _db(db),
          _builder(builder)
    {}

    ~BTreeTrailingInserter() override = default;

    void insert_at_end(const Entry& e) override {
        const uint64_t bucket_key = e.getBucketId().toKey();
        const auto replicas_ref = _db._store.add(e.getBucketInfo().getRawNodes());
        const auto new_value = value_from(e.getBucketInfo().getLastGarbageCollectionTime(), replicas_ref);
        _builder.insert(bucket_key, new_value);
    }
};

// TODO lbound arg?
void BTreeBucketDatabase::merge(MergingProcessor& proc) {
    BTreeBucketDatabase::BTree::Builder builder(_tree.getAllocator());
    BTreeBuilderMerger merger(*this, builder);

    // TODO for_each instead?
    for (auto iter = _tree.begin(); iter.valid(); ++iter) {
        const uint64_t key = iter.getKey();
        const uint64_t value = iter.getData();
        merger.update_iteration_state(key, value);

        auto result = proc.merge(merger);

        if (result == MergingProcessor::Result::KeepUnchanged) {
            builder.insert(key, value); // Reuse array store ref with no changes
        } else if (result == MergingProcessor::Result::Update) {
            assert(merger._valid_cached_entry); // Must actually have been touched
            assert(merger._cached_entry.valid());
            _store.remove(entry_ref_from_value(value));
            auto new_replicas_ref = _store.add(merger._cached_entry.getBucketInfo().getRawNodes());
            const auto new_value = value_from(merger._cached_entry.getBucketInfo().getLastGarbageCollectionTime(), new_replicas_ref);
            builder.insert(key, new_value);
        } else if (result == MergingProcessor::Result::Skip) {
            _store.remove(entry_ref_from_value(value));
        } else {
            abort();
        }
    }
    BTreeTrailingInserter inserter(*this, builder);
    proc.insert_remaining_at_end(inserter);

    _tree.assign(builder);
    commit_tree_changes();
}

Entry BTreeBucketDatabase::upperBound(const BucketId& bucket) const {
    return entry_from_iterator(_tree.upperBound(bucket.toKey()));
}

uint64_t BTreeBucketDatabase::size() const {
    return _tree.size();
}

void BTreeBucketDatabase::clear() {
    _tree.clear();
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
BucketId BTreeBucketDatabase::getAppropriateBucket(uint16_t minBits, const BucketId& bid) {
    // The bucket tree is ordered in such a way that it represents a
    // natural in-order traversal of all buckets, with inner nodes being
    // visited before leaf nodes. This means that a lower bound seek will
    // never return a parent of a seeked bucket. The iterator will be pointing
    // to a bucket that is either the actual bucket given as the argument to
    // lowerBound() or the next in-order bucket (or end() if none exists).
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
// TODO rename/clarify to indicate this is child _subtrees_, not explicit child _buckets_!
uint32_t BTreeBucketDatabase::childCount(const BucketId& bucket) const {
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

void BTreeBucketDatabase::print(std::ostream& out, bool verbose,
                                const std::string& indent) const
{
    out << "BTreeBucketDatabase(" << size() << " buckets)";
    (void)verbose;
    (void)indent;
}

vespalib::MemoryUsage BTreeBucketDatabase::memory_usage() const noexcept {
    auto mem_usage = _tree.getMemoryUsage();
    mem_usage.merge(_store.getMemoryUsage());
    return mem_usage;
}

BTreeBucketDatabase::ReadGuardImpl::ReadGuardImpl(const BTreeBucketDatabase& db)
    : _db(&db),
      _guard(_db->_generation_handler.takeGuard()),
      _frozen_view(_db->_tree.getFrozenView())
{}

BTreeBucketDatabase::ReadGuardImpl::~ReadGuardImpl() = default;

void BTreeBucketDatabase::ReadGuardImpl::find_parents_and_self(const document::BucketId& bucket,
                                                               std::vector<Entry>& entries) const
{
    _db->find_parents_and_self_internal(_frozen_view, bucket, entries);
}

uint64_t BTreeBucketDatabase::ReadGuardImpl::generation() const noexcept {
    return _guard.getGeneration();
}

// TODO replace existing distributor DB code with generic impl.
// This is to ensure the generic implementation compiles with an ArrayStore backing in
// the meantime.
struct BTreeBucketDatabase2 {
    struct ReplicaValueTraits {
        using ValueType     = Entry;
        using ConstValueRef = ConstEntryRef;
        using DataStoreType = vespalib::datastore::ArrayStore<BucketCopy>;

        static ValueType make_invalid_value() {
            return Entry::createInvalid();
        }
        static uint64_t store_and_wrap_value(DataStoreType& store, const Entry& entry) noexcept {
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

    using BTreeImpl = bucketdb::GenericBTreeBucketDatabase<ReplicaValueTraits>;
    BTreeImpl _impl;

    BTreeBucketDatabase2()
        : _impl(make_default_array_store_config<ReplicaValueTraits::DataStoreType>())
    {}
};

template class bucketdb::GenericBTreeBucketDatabase<BTreeBucketDatabase2::ReplicaValueTraits>;

}
