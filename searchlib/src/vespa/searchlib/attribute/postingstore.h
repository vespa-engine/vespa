// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enum_store_dictionary.h"
#include "postinglisttraits.h"
#include "posting_store_compaction_spec.h"
#include <set>

namespace search {
    class BitVector;
    class GrowableBitVector;
}

namespace search::fef       { class TermFieldMatchData; }
namespace search::queryeval { class SearchIterator; }

namespace search::attribute {

class Status;
class Config;

class BitVectorEntry
{
public:
    vespalib::datastore::EntryRef _tree; // Daisy chained reference to tree based posting list
    std::shared_ptr<GrowableBitVector> _bv; // bitvector

public:
    BitVectorEntry()
        : _tree(),
          _bv()
    { }
};


class PostingStoreBase2
{
public:
    bool _enableOnlyBitVector;
    bool _isFilter;
protected:
    uint32_t _bvSize;
    uint32_t _bvCapacity;
public:
    uint32_t _minBvDocFreq; // Less than this ==> destroy bv
    uint32_t _maxBvDocFreq; // Greater than or equal to this ==> create bv
protected:
    std::set<uint32_t> _bvs; // Current bitvectors
    IEnumStoreDictionary& _dictionary;
    Status            &_status;
    uint64_t           _bvExtraBytes;
    PostingStoreCompactionSpec _compaction_spec;

    static constexpr uint32_t BUFFERTYPE_BITVECTOR = 9u;

public:
    PostingStoreBase2(IEnumStoreDictionary& dictionary, Status &status, const Config &config);
    virtual ~PostingStoreBase2();
    bool resizeBitVectors(uint32_t newSize, uint32_t newCapacity);
    virtual bool removeSparseBitVectors() = 0;
};

template <typename DataT>
class PostingStore : public PostingListTraits<DataT>::PostingStoreBase,
    public PostingStoreBase2
{
    vespalib::datastore::BufferType<BitVectorEntry> _bvType;
public:
    using DataType = DataT;
    using Parent = typename PostingListTraits<DataT>::PostingStoreBase;
    using AddIter = typename Parent::AddIter;
    using RemoveIter = typename Parent::RemoveIter;
    using RefType = typename Parent::RefType;
    using BTreeType = typename Parent::BTreeType;
    using Iterator = typename Parent::Iterator;
    using ConstIterator = typename Parent::ConstIterator;
    using KeyDataType = typename Parent::KeyDataType;
    using AggregatedType = typename Parent::AggregatedType;
    using BTreeTypeRefPair = typename Parent::BTreeTypeRefPair;
    using Builder = typename Parent::Builder;
    using CompactionSpec = vespalib::datastore::CompactionSpec;
    using CompactionStrategy = vespalib::datastore::CompactionStrategy;
    using EntryRef = vespalib::datastore::EntryRef;
    using CompareT = std::less<uint32_t>;
    using Parent::applyNewArray;
    using Parent::applyNewTree;
    using Parent::applyCluster;
    using Parent::applyTree;
    using Parent::normalizeTree;
    using Parent::getTypeId;
    using Parent::getClusterSize;
    using Parent::getWTreeEntry;
    using Parent::getTreeEntry;
    using Parent::getKeyDataEntry;
    using Parent::isBTree;
    using Parent::clusterLimit;
    using Parent::allocBTree;
    using Parent::allocBTreeCopy;
    using Parent::allocKeyDataCopy;
    using Parent::_builder;
    using Parent::_store;
    using Parent::_allocator;
    using Parent::_aggrCalc;
    using Parent::BUFFERTYPE_BTREE;
    using BitVectorRefPair = vespalib::datastore::Handle<BitVectorEntry>;


    PostingStore(IEnumStoreDictionary& dictionary, Status &status, const Config &config);
    ~PostingStore();

    bool removeSparseBitVectors() override;
    void consider_remove_sparse_bitvector(std::vector<EntryRef> &refs);
    static bool isBitVector(uint32_t typeId) { return typeId == BUFFERTYPE_BITVECTOR; }

    void applyNew(EntryRef &ref, AddIter a, AddIter ae);

    BitVectorRefPair allocBitVector() {
        return _store.template freeListAllocator<BitVectorEntry,
            vespalib::datastore::DefaultReclaimer<BitVectorEntry> >(BUFFERTYPE_BITVECTOR).alloc();
    }

    BitVectorRefPair allocBitVectorCopy(const BitVectorEntry& bve) {
        return _store.template freeListAllocator<BitVectorEntry,
            vespalib::datastore::DefaultReclaimer<BitVectorEntry> >(BUFFERTYPE_BITVECTOR).alloc(bve);
    }

    /*
     * Recreate btree from bitvector. Weight information is not recreated.
     */
    void makeDegradedTree(EntryRef &ref, const BitVector &bv);
    void dropBitVector(EntryRef &ref);
    void makeBitVector(EntryRef &ref);

    void applyNewBitVector(EntryRef &ref, AddIter aOrg, AddIter ae);
    void apply(BitVector &bv, AddIter a, AddIter ae, RemoveIter r, RemoveIter re);

    /**
     * Apply multiple changes at once.
     *
     * additions and removals should be sorted on key without duplicates.
     * Overlap between additions and removals indicates updates.
     */
    void apply(EntryRef &ref, AddIter a, AddIter ae, RemoveIter r, RemoveIter re);
    void clear(const EntryRef ref);
    size_t size(const EntryRef ref) const {
        if (!ref.valid())
            return 0;
        RefType iRef(ref);
        uint32_t typeId = getTypeId(iRef);
        uint32_t clusterSize = getClusterSize(typeId);
        if (clusterSize == 0) {
            return internalSize(typeId, iRef);
        }
        return clusterSize;
    }

    size_t frozenSize(const EntryRef ref) const {
        if (!ref.valid())
            return 0;
        RefType iRef(ref);
        uint32_t typeId = getTypeId(iRef);
        uint32_t clusterSize = getClusterSize(typeId);
        if (clusterSize == 0) {
            return internalFrozenSize(typeId, iRef);
        }
        return clusterSize;
    }

    Iterator begin(const EntryRef ref) const;
    ConstIterator beginFrozen(const EntryRef ref) const;
    void beginFrozen(const EntryRef ref, std::vector<ConstIterator> &where) const;

    template <typename FunctionType>
    VESPA_DLL_LOCAL void foreach_frozen_key(EntryRef ref, FunctionType func) const;

    template <typename FunctionType>
    VESPA_DLL_LOCAL void foreach_frozen(EntryRef ref, FunctionType func) const;

    AggregatedType getAggregated(const EntryRef ref) const;

    const BitVectorEntry *getBitVectorEntry(RefType ref) const {
        return _store.template getEntry<BitVectorEntry>(ref);
    }

    BitVectorEntry *getWBitVectorEntry(RefType ref) {
        return _store.template getEntry<BitVectorEntry>(ref);
    }

    std::unique_ptr<queryeval::SearchIterator> make_bitvector_iterator(RefType ref, uint32_t doc_id_limit, fef::TermFieldMatchData &match_data, bool strict) const;

    static inline DataT bitVectorWeight();
    vespalib::MemoryUsage getMemoryUsage() const;
    vespalib::MemoryUsage update_stat(const CompactionStrategy& compaction_strategy);

    void move_btree_nodes(const std::vector<EntryRef> &refs);
    void move(std::vector<EntryRef>& refs);

    void compact_worst_btree_nodes(const CompactionStrategy& compaction_strategy);
    void compact_worst_buffers(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy);
    bool consider_compact_worst_btree_nodes(const CompactionStrategy& compaction_strategy);
    bool consider_compact_worst_buffers(const CompactionStrategy& compaction_strategy);
private:
    size_t internalSize(uint32_t typeId, const RefType & iRef) const;
    size_t internalFrozenSize(uint32_t typeId, const RefType & iRef) const;
};

template <>
inline vespalib::btree::BTreeNoLeafData
PostingStore<vespalib::btree::BTreeNoLeafData>::bitVectorWeight()
{
    return vespalib::btree::BTreeNoLeafData();
}

template <>
inline int32_t
PostingStore<int32_t>::bitVectorWeight()
{
    return 1;
}

}
