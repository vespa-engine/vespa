// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postingstore.h"
#include <vespa/searchlib/datastore/datastore.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/common/growablebitvector.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/status.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.posting_store");

namespace search::attribute {

using btree::BTreeNoLeafData;

// #define FORCE_BITVECTORS


PostingStoreBase2::PostingStoreBase2(EnumPostingTree &dict, Status &status,
                                     const Config &config)
    :
#ifdef FORCE_BITVECTORS
      _enableBitVectors(true),
#else
      _enableBitVectors(config.getEnableBitVectors()),
#endif
      _enableOnlyBitVector(config.getEnableOnlyBitVector()),
      _isFilter(config.getIsFilter()),
      _bvSize(64u),
      _bvCapacity(128u),
      _minBvDocFreq(64),
      _maxBvDocFreq(std::numeric_limits<uint32_t>::max()),
      _bvs(),
      _dict(dict),
      _status(status),
      _bvExtraBytes(0)
{
}


PostingStoreBase2::~PostingStoreBase2()
{
}


bool
PostingStoreBase2::resizeBitVectors(uint32_t newSize, uint32_t newCapacity)
{
    assert(newCapacity >= newSize);
    newSize = (newSize + 63) & ~63;
    if (newSize >= newCapacity)
        newSize = newCapacity;
    if (newSize == _bvSize && newCapacity == _bvCapacity)
        return false;
    _minBvDocFreq = std::max(newSize >> 6, 64u);
    _maxBvDocFreq = std::max(newSize >> 5, 128u);
    if (_bvs.empty()) {
        _bvSize = newSize;
        _bvCapacity = newCapacity;
        return false;
    }
    _bvSize = newSize;
    _bvCapacity = newCapacity;
    return removeSparseBitVectors();
}


template <typename DataT>
PostingStore<DataT>::PostingStore(EnumPostingTree &dict, Status &status,
                                  const Config &config)
    : Parent(false),
      PostingStoreBase2(dict, status, config),
      _bvType(1, 1024u, RefType::offsetSize())
{
    // TODO: Add type for bitvector
    _store.addType(&_bvType);
    _store.initActiveBuffers();
    _store.enableFreeLists();
}


template <typename DataT>
PostingStore<DataT>::~PostingStore()
{
    _builder.clear();
    _store.dropBuffers();   // Drop buffers before type handlers are dropped
}


template <typename DataT>
bool
PostingStore<DataT>::removeSparseBitVectors()
{
    bool res = false;
    bool needscan = false;
    for (auto &i : _bvs) {
        RefType iRef(i);
        uint32_t typeId = getTypeId(iRef);
        (void) typeId;
        assert(isBitVector(typeId));
        BitVectorEntry *bve = getWBitVectorEntry(iRef);
        GrowableBitVector &bv = *bve->_bv.get();
        uint32_t docFreq = bv.countTrueBits();
        if (bve->_tree.valid()) {
            RefType iRef2(bve->_tree);
            assert(isBTree(iRef2));
            const BTreeType *tree = getTreeEntry(iRef2);
            assert(tree->size(_allocator) == docFreq);
            (void) tree;
        }
        if (docFreq < _minBvDocFreq)
            needscan = true;
        unsigned int oldExtraSize = bv.extraByteSize();
        if (bv.size() > _bvSize) {
            bv.shrink(_bvSize);
            res = true;
        }
        if (bv.capacity() < _bvCapacity) {
            bv.reserve(_bvCapacity);
            res = true;
        }
        if (bv.size() < _bvSize) {
            bv.extend(_bvSize);
        }
        unsigned int newExtraSize = bv.extraByteSize();
        if (oldExtraSize != newExtraSize) {
            _bvExtraBytes = _bvExtraBytes + newExtraSize - oldExtraSize;
        }
    }
    if (needscan) {
        typedef EnumPostingTree::Iterator EnumIterator;
        for (EnumIterator dictItr = _dict.begin(); dictItr.valid(); ++dictItr) {
            if (!isBitVector(getTypeId(dictItr.getData())))
                continue;
            EntryRef ref(dictItr.getData());
            RefType iRef(ref);
            uint32_t typeId = getTypeId(iRef);
            assert(isBitVector(typeId));
            assert(_bvs.find(ref.ref() )!= _bvs.end());
            BitVectorEntry *bve = getWBitVectorEntry(iRef);
            BitVector &bv = *bve->_bv.get();
            uint32_t docFreq = bv.countTrueBits();
            if (bve->_tree.valid()) {
                RefType iRef2(bve->_tree);
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                assert(tree->size(_allocator) == docFreq);
                (void) tree;
            }
            if (docFreq < _minBvDocFreq) {
                dropBitVector(ref);
                if (ref.valid()) {
                    iRef = ref;
                    typeId = getTypeId(iRef);
                    if (isBTree(typeId)) {
                        BTreeType *tree = getWTreeEntry(iRef);
                        normalizeTree(ref, tree, false);
                    }
                }
                _dict.thaw(dictItr);
                dictItr.writeData(ref);
                res = true;
            }
        }
    }
    return res;
}


template <typename DataT>
void
PostingStore<DataT>::applyNew(EntryRef &ref,
                              AddIter a,
                              AddIter ae)
{
    // No old data
    assert(!ref.valid());
    size_t additionSize(ae - a);
    uint32_t clusterSize = additionSize;
    if (clusterSize <= clusterLimit) {
        applyNewArray(ref, a, ae);
    } else if (_enableBitVectors && clusterSize >= _maxBvDocFreq) {
        applyNewBitVector(ref, a, ae);
    } else {
        applyNewTree(ref, a, ae, CompareT());
    }
}


template <typename DataT>
void
PostingStore<DataT>::makeDegradedTree(EntryRef &ref,
                                      const BitVector &bv)
{
    assert(!ref.valid());
    BTreeTypeRefPair tPair(allocBTree());
    BTreeType *tree = tPair.data;
    Builder &builder = _builder;
    builder.reuse();
    uint32_t docIdLimit = _bvSize;
    assert(_bvSize == bv.size());
    uint32_t docId = bv.getFirstTrueBit();
    while (docId < docIdLimit) {
        builder.insert(docId, bitVectorWeight());
        docId = bv.getNextTrueBit(docId + 1);
    }
    tree->assign(builder, _allocator);
    assert(tree->size(_allocator) == bv.countTrueBits());
    // barrier ?
    ref = tPair.ref;
}


template <typename DataT>
void
PostingStore<DataT>::dropBitVector(EntryRef &ref)
{
    assert(ref.valid());
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    assert(isBitVector(typeId));
    (void) typeId;
    BitVectorEntry *bve = getWBitVectorEntry(iRef);
    AllocatedBitVector *bv = bve->_bv.get();
    assert(bv);
    uint32_t docFreq = bv->countTrueBits();
    EntryRef ref2(bve->_tree);
    if (!ref2.valid()) {
        makeDegradedTree(ref2, *bv);
    }
    assert(ref2.valid());
    assert(isBTree(ref2));
    const BTreeType *tree = getTreeEntry(ref2);
    assert(tree->size(_allocator) == docFreq);
    (void) tree;
    (void) docFreq;
    _bvs.erase(ref.ref());
    _store.holdElem(iRef, 1);
    _status.decBitVectors();
    _bvExtraBytes -= bv->extraByteSize();
    ref = ref2;
}


template <typename DataT>
void
PostingStore<DataT>::makeBitVector(EntryRef &ref)
{
    assert(ref.valid());
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    assert(isBTree(typeId));
    (void) typeId;
    std::shared_ptr<GrowableBitVector> bvsp;
    vespalib::GenerationHolder &genHolder = _store.getGenerationHolder();
    bvsp.reset(new GrowableBitVector(_bvSize, _bvCapacity, genHolder));
    AllocatedBitVector &bv = *bvsp.get();
    uint32_t docIdLimit = _bvSize;
    (void) docIdLimit;
    Iterator it = begin(ref);
    uint32_t expDocFreq = it.size();
    (void) expDocFreq;
    for (; it.valid(); ++it) {
        uint32_t docId = it.getKey(); 
        assert(docId < docIdLimit);
        bv.setBit(docId);
    }
    bv.invalidateCachedCount();
    assert(bv.countTrueBits() == expDocFreq);
    BitVectorRefPair bPair(allocBitVector());
    BitVectorEntry *bve = bPair.data;
    if (_enableOnlyBitVector) {
        BTreeType *tree = getWTreeEntry(iRef);
        tree->clear(_allocator);
        _store.holdElem(ref, 1);
    } else {
        bve->_tree = ref;
    }
    bve->_bv = bvsp;
    _bvs.insert(bPair.ref.ref());
    _status.incBitVectors();
    _bvExtraBytes += bv.extraByteSize();
    // barrier ?
    ref = bPair.ref;
}

    
template <typename DataT>
void
PostingStore<DataT>::applyNewBitVector(EntryRef &ref,
                                       AddIter aOrg,
                                       AddIter ae)
{
    assert(!ref.valid());
    RefType iRef(ref);
    std::shared_ptr<GrowableBitVector> bvsp;
    vespalib::GenerationHolder &genHolder = _store.getGenerationHolder();
    bvsp.reset(new GrowableBitVector(_bvSize, _bvCapacity, genHolder));
    AllocatedBitVector &bv = *bvsp.get();
    uint32_t docIdLimit = _bvSize;
    (void) docIdLimit;
    uint32_t expDocFreq = ae - aOrg;
    (void) expDocFreq;
    for (AddIter a = aOrg; a != ae; ++a) {
        uint32_t docId = a->_key;
        assert(docId < docIdLimit);
        bv.setBit(docId);
    }
    bv.invalidateCachedCount();
    assert(bv.countTrueBits() == expDocFreq);
    BitVectorRefPair bPair(allocBitVector());
    BitVectorEntry *bve = bPair.data;
    if (!_enableOnlyBitVector) {
        applyNewTree(bve->_tree, aOrg, ae, CompareT());
    }
    bve->_bv = bvsp;
    _bvs.insert(bPair.ref.ref());
    _status.incBitVectors();
    _bvExtraBytes += bv.extraByteSize();
    // barrier ?
    ref = bPair.ref;
}

    
template <typename DataT>
void
PostingStore<DataT>::apply(BitVector &bv,
                           AddIter a,
                           AddIter ae,
                           RemoveIter r,
                           RemoveIter re)
{
    while (a != ae || r != re) {
        if (r != re && (a == ae || *r < a->_key)) {
            // remove
            assert(*r < bv.size());
            bv.slowClearBit(*r);
            ++r;
        } else {
            if (r != re && !(a->_key < *r)) {
                // update or add
                assert(a->_key < bv.size());
                bv.slowSetBit(a->_key);
                ++r;
            } else {
                assert(a->_key < bv.size());
                bv.slowSetBit(a->_key);
            }
            ++a;
        }
    }
}


template <typename DataT>
void
PostingStore<DataT>::apply(EntryRef &ref,
                           AddIter a,
                           AddIter ae,
                           RemoveIter r,
                           RemoveIter re)
{
    if (!ref.valid()) {
        // No old data
        applyNew(ref, a, ae);
        return;
    }
    RefType iRef(ref);
    bool wasArray = false;
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize != 0) {
        wasArray = true;
        if (applyCluster(ref, clusterSize, a, ae, r, re, CompareT()))
            return;
        iRef = ref;
        typeId = getTypeId(iRef);
    }
    // Old data was tree or has been converted to a tree
    // ... or old data was bitvector
    if (isBitVector(typeId)) {
        BitVectorEntry *bve = getWBitVectorEntry(iRef);
        EntryRef ref2(bve->_tree);
        RefType iRef2(ref2);
        if (iRef2.valid()) {
            assert(isBTree(iRef2));
            BTreeType *tree = getWTreeEntry(iRef2);
            applyTree(tree, a, ae, r, re, CompareT());
        }
        BitVector *bv = bve->_bv.get();
        assert(bv);
        apply(*bv, a, ae, r, re);
        uint32_t docFreq = bv->countTrueBits();
        if (docFreq < _minBvDocFreq) {
            dropBitVector(ref);
            if (ref.valid()) {
                iRef = ref;
                typeId = getTypeId(iRef);
                if (isBTree(typeId)) {
                    BTreeType *tree = getWTreeEntry(iRef);
                    assert(tree->size(_allocator) == docFreq);
                    normalizeTree(ref, tree, wasArray);
                }
            }
        }
    } else {
        BTreeType *tree = getWTreeEntry(iRef);
        applyTree(tree, a, ae, r, re, CompareT());
        if (_enableBitVectors) {
            uint32_t docFreq = tree->size(_allocator);
            if (docFreq >= _maxBvDocFreq) {
                makeBitVector(ref);
                return;
            }
        }
        normalizeTree(ref, tree, wasArray);
    }
}


template <typename DataT>
size_t
PostingStore<DataT>::internalSize(uint32_t typeId, const RefType & iRef) const
{
    if (isBitVector(typeId)) {
        const BitVectorEntry *bve = getBitVectorEntry(iRef);
        RefType iRef2(bve->_tree);
        if (iRef2.valid()) {
            assert(isBTree(iRef2));
            const BTreeType *tree = getTreeEntry(iRef2);
            return tree->size(_allocator);
        } else {
            const BitVector *bv = bve->_bv.get();
            return bv->countTrueBits();
        }
    } else {
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->size(_allocator);
    }
}


template <typename DataT>
size_t
PostingStore<DataT>::internalFrozenSize(uint32_t typeId, const RefType & iRef) const
{
    if (isBitVector(typeId)) {
        const BitVectorEntry *bve = getBitVectorEntry(iRef);
        RefType iRef2(bve->_tree);
        if (iRef2.valid()) {
            assert(isBTree(iRef2));
            const BTreeType *tree = getTreeEntry(iRef2);
            return tree->frozenSize(_allocator);
        } else {
            // Some inaccuracy is expected, data changes underfeet
            return bve->_bv->countTrueBits();
        }
    } else {
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->frozenSize(_allocator);
    }
}


template <typename DataT>
typename PostingStore<DataT>::Iterator
PostingStore<DataT>::begin(const EntryRef ref) const
{
    if (!ref.valid())
        return Iterator();
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                return tree->begin(_allocator);
            }
            return Iterator();
        }
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->begin(_allocator);
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    return Iterator(shortArray, clusterSize, _allocator, _aggrCalc);
}


template <typename DataT>
typename PostingStore<DataT>::ConstIterator
PostingStore<DataT>::beginFrozen(const EntryRef ref) const
{
    if (!ref.valid())
        return ConstIterator();
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                return tree->getFrozenView(_allocator).begin();
            }
            return ConstIterator();
        }
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->getFrozenView(_allocator).begin();
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    return ConstIterator(shortArray, clusterSize, _allocator, _aggrCalc);
}


template <typename DataT>
void
PostingStore<DataT>::beginFrozen(const EntryRef ref,
                                 std::vector<ConstIterator> &where) const
{
    if (!ref.valid()) {
        where.emplace_back();
        return;
    }
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                tree->getFrozenView(_allocator).begin(where);
                return;
            }
            where.emplace_back();
            return;
        }
        const BTreeType *tree = getTreeEntry(iRef);
        tree->getFrozenView(_allocator).begin(where);
        return;
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    where.emplace_back(shortArray, clusterSize, _allocator, _aggrCalc);
}


template <typename DataT>
typename PostingStore<DataT>::AggregatedType
PostingStore<DataT>::getAggregated(const EntryRef ref) const
{
    if (!ref.valid())
        return AggregatedType();
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                return tree->getAggregated(_allocator);
            }
            return AggregatedType();
        }
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->getAggregated(_allocator);
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    AggregatedType a;
    for (uint32_t i = 0; i < clusterSize; ++i) {
        _aggrCalc.add(a, _aggrCalc.getVal(shortArray[i].getData()));
    }
    return a;
}


template <typename DataT>
void
PostingStore<DataT>::clear(const EntryRef ref)
{
    if (!ref.valid())
        return;
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                BTreeType *tree = getWTreeEntry(iRef2);
                tree->clear(_allocator);
                _store.holdElem(iRef2, 1);
            }
            _bvs.erase(ref.ref());
            _status.decBitVectors();
            _bvExtraBytes -= bve->_bv->extraByteSize();
            _store.holdElem(ref, 1);
        } else {
            BTreeType *tree = getWTreeEntry(iRef);
            tree->clear(_allocator);
            _store.holdElem(ref, 1);
        }
    } else {
        _store.holdElem(ref, clusterSize);
    }
}


template <typename DataT>
MemoryUsage
PostingStore<DataT>::getMemoryUsage() const
{
    MemoryUsage usage;
    usage.merge(_allocator.getMemoryUsage());
    usage.merge(_store.getMemoryUsage());
    uint64_t bvExtraBytes = _bvExtraBytes;
    usage.incUsedBytes(bvExtraBytes);
    usage.incAllocatedBytes(bvExtraBytes);
    return usage;
}


template class PostingStore<BTreeNoLeafData>;

template class PostingStore<int32_t>;

}
