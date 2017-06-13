// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "postinglisttraits.h"
#include "enumstorebase.h"
#include <set>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/growablebitvector.h>

namespace search {

namespace attribute {

class Status;
class Config;

class BitVectorEntry
{
public:
    datastore::EntryRef _tree; // Daisy chained reference to tree based posting list
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
    bool _enableBitVectors;
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
    EnumPostingTree &_dict;
    Status &_status;
    uint64_t _bvExtraBytes;

    static constexpr uint32_t BUFFERTYPE_BITVECTOR = 9u;

public:
    PostingStoreBase2(EnumPostingTree &dict, Status &status, const Config &config);
    virtual ~PostingStoreBase2();
    bool resizeBitVectors(uint32_t newSize, uint32_t newCapacity);
    virtual bool removeSparseBitVectors() = 0;
};

template <typename DataT>
class PostingStore : public PostingListTraits<DataT>::PostingStoreBase,
    public PostingStoreBase2
{
    datastore::BufferType<BitVectorEntry> _bvType;
public:
    typedef DataT DataType;
    typedef typename PostingListTraits<DataT>::PostingStoreBase Parent;
    typedef typename Parent::AddIter AddIter;
    typedef typename Parent::RemoveIter RemoveIter;
    typedef typename Parent::RefType RefType;
    typedef typename Parent::BTreeType BTreeType;
    typedef typename Parent::Iterator Iterator;
    typedef typename Parent::ConstIterator ConstIterator;
    typedef typename Parent::KeyDataType KeyDataType;
    typedef typename Parent::AggregatedType AggregatedType;
    typedef typename Parent::BTreeTypeRefPair BTreeTypeRefPair;
    typedef typename Parent::Builder Builder;
    typedef datastore::EntryRef EntryRef;
    typedef std::less<uint32_t> CompareT;
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
    using Parent::clusterLimit;
    using Parent::allocBTree;
    using Parent::_builder;
    using Parent::_store;
    using Parent::_allocator;
    using Parent::_aggrCalc;
    using Parent::BUFFERTYPE_BTREE;
    typedef datastore::Handle<BitVectorEntry> BitVectorRefPair;
    

    PostingStore(EnumPostingTree &dict, Status &status, const Config &config);
    ~PostingStore();

    bool removeSparseBitVectors() override;
    static bool isBitVector(uint32_t typeId) { return typeId == BUFFERTYPE_BITVECTOR; }
    static bool isBTree(uint32_t typeId) { return typeId == BUFFERTYPE_BTREE; }
    bool isBTree(RefType ref) const { return isBTree(getTypeId(ref)); }

    void applyNew(EntryRef &ref, AddIter a, AddIter ae);

    BitVectorRefPair allocBitVector() {
        return _store.template freeListAllocator<BitVectorEntry,
            btree::DefaultReclaimer<BitVectorEntry> >(BUFFERTYPE_BITVECTOR).alloc();
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
        return _store.template getBufferEntry<BitVectorEntry>(ref.bufferId(),
                                                              ref.offset());
    }

    BitVectorEntry *getWBitVectorEntry(RefType ref) {
        return _store.template getBufferEntry<BitVectorEntry>(ref.bufferId(),
                                                              ref.offset());
    }

    static inline DataT bitVectorWeight();
    MemoryUsage getMemoryUsage() const;

private:
    size_t internalSize(uint32_t typeId, const RefType & iRef) const;
    size_t internalFrozenSize(uint32_t typeId, const RefType & iRef) const;
};

template <>
inline btree::BTreeNoLeafData
PostingStore<btree::BTreeNoLeafData>::bitVectorWeight()
{
    return btree::BTreeNoLeafData();
}

template <>
inline int32_t
PostingStore<int32_t>::bitVectorWeight()
{
    return 1;
}

template <typename DataT>
template <typename FunctionType>
void
PostingStore<DataT>::foreach_frozen_key(EntryRef ref, FunctionType func) const
{
    if (!ref.valid())
        return;
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            EntryRef ref2(bve->_tree);
            RefType iRef2(ref2);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                _allocator.getNodeStore().foreach_key(tree->getFrozenRoot(), func);
            } else {
                const BitVector *bv = bve->_bv.get();
                uint32_t docIdLimit = bv->size();
                uint32_t docId = bv->getFirstTrueBit(1);
                while (docId < docIdLimit) {
                    func(docId);
                    docId = bv->getNextTrueBit(docId + 1);
                }
            }
        } else {
            assert(isBTree(typeId));
            const BTreeType *tree = getTreeEntry(iRef);
            _allocator.getNodeStore().foreach_key(tree->getFrozenRoot(), func);
        }
    } else {
        const KeyDataType *p = getKeyDataEntry(iRef, clusterSize);
        const KeyDataType *pe = p + clusterSize;
        for (; p != pe; ++p) {
            func(p->_key);
        }
    }
}


template <typename DataT>
template <typename FunctionType>
void
PostingStore<DataT>::foreach_frozen(EntryRef ref, FunctionType func) const
{
    if (!ref.valid())
        return;
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            EntryRef ref2(bve->_tree);
            RefType iRef2(ref2);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                _allocator.getNodeStore().foreach(tree->getFrozenRoot(), func);
            } else {
                const BitVector *bv = bve->_bv.get();
                uint32_t docIdLimit = bv->size();
                uint32_t docId = bv->getFirstTrueBit(1);
                while (docId < docIdLimit) {
                    func(docId, bitVectorWeight());
                    docId = bv->getNextTrueBit(docId + 1);
                }
            }
        } else {
            const BTreeType *tree = getTreeEntry(iRef);
            _allocator.getNodeStore().foreach(tree->getFrozenRoot(), func);
        }
    } else {
        const KeyDataType *p = getKeyDataEntry(iRef, clusterSize);
        const KeyDataType *pe = p + clusterSize;
        for (; p != pe; ++p) {
            func(p->_key, p->getData());
        }
    }
}

} // namespace attribute

} // namespace search
