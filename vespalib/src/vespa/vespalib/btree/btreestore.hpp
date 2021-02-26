// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreestore.h"
#include "btreebuilder.h"
#include "btreebuilder.hpp"
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/util/optimized.h>

namespace vespalib::btree {

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
BTreeStore()
    : BTreeStore(true)
{
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
BTreeStore(bool init)
    : _store(),
      _treeType(1, MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _small1Type(1, MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _small2Type(2, MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _small3Type(3, MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _small4Type(4, MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _small5Type(5, MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _small6Type(6, MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _small7Type(7, MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _small8Type(8, MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _allocator(),
      _aggrCalc(),
      _builder(_allocator, _aggrCalc)
{
    // XXX: order here makes typeId + 1 == clusterSize for small arrays,
    // code elsewhere depends on it.
    _store.addType(&_small1Type);
    _store.addType(&_small2Type);
    _store.addType(&_small3Type);
    _store.addType(&_small4Type);
    _store.addType(&_small5Type);
    _store.addType(&_small6Type);
    _store.addType(&_small7Type);
    _store.addType(&_small8Type);
    _store.addType(&_treeType);
    if (init) {
        _store.init_primary_buffers();
        _store.enableFreeLists();
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
BTreeStore<KeyT, DataT, AggrT, CompareT,TraitsT, AggrCalcT>::~BTreeStore()
{
    _builder.clear();
    _store.dropBuffers();   // Drop buffers before type handlers are dropped
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
typename BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
KeyDataTypeRefPair
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
allocNewKeyData(uint32_t clusterSize)
{
    assert(clusterSize >= 1 && clusterSize <= clusterLimit);
    uint32_t typeId = clusterSize - 1;
    return _store.allocator<KeyDataType>(typeId).allocArray(clusterSize);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
typename BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
KeyDataTypeRefPair
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
allocKeyData(uint32_t clusterSize)
{
    assert(clusterSize >= 1 && clusterSize <= clusterLimit);
    uint32_t typeId = clusterSize - 1;
    return _store.freeListAllocator<KeyDataType, datastore::DefaultReclaimer<KeyDataType>>(typeId).allocArray(clusterSize);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
typename BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
KeyDataTypeRefPair
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
allocNewKeyDataCopy(const KeyDataType *rhs, uint32_t clusterSize)
{
    assert(clusterSize >= 1 && clusterSize <= clusterLimit);
    uint32_t typeId = clusterSize - 1;
    return _store.allocator<KeyDataType>(typeId).allocArray(vespalib::ConstArrayRef<KeyDataType>(rhs, clusterSize));
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
typename BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
KeyDataTypeRefPair
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
allocKeyDataCopy(const KeyDataType *rhs, uint32_t clusterSize)
{
    assert(clusterSize >= 1 && clusterSize <= clusterLimit);
    uint32_t typeId = clusterSize - 1;
    return _store.freeListAllocator<KeyDataType, datastore::DefaultReclaimer<KeyDataType>>(typeId).
            allocArray(vespalib::ConstArrayRef<KeyDataType>(rhs, clusterSize));
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
std::vector<uint32_t>
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::startCompact()
{
    std::vector<uint32_t> ret = _store.startCompact(clusterLimit);
    for (uint32_t clusterSize = 1; clusterSize <= clusterLimit; ++clusterSize) {
        uint32_t typeId = clusterSize - 1;
        std::vector<uint32_t> toHold = _store.startCompact(typeId);
        for (auto i : toHold) {
            ret.push_back(i);
        }
    }
    return ret;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
finishCompact(const std::vector<uint32_t> &toHold)
{
    _store.finishCompact(toHold);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
const typename BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
KeyDataType *
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
lower_bound(const KeyDataType *b, const KeyDataType *e,
            const KeyType &key, CompareT comp)
{
    const KeyDataType *i = b;
    for (; i != e; ++i) {
        if (!comp(i->_key, key))
            break;
    }
    return i;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
makeTree(EntryRef &ref,
         const KeyDataType *array, uint32_t clusterSize)
{
    LeafNodeTypeRefPair lPair(_allocator.allocLeafNode());
    LeafNodeType *lNode = lPair.data;
    lNode->setValidSlots(clusterSize);
    const KeyDataType *o = array;
    for (uint32_t idx = 0; idx < clusterSize; ++idx, ++o) {
        lNode->update(idx, o->_key, o->getData());
    }
    typedef BTreeAggregator<KeyT, DataT, AggrT,
        TraitsT::INTERNAL_SLOTS, TraitsT::LEAF_SLOTS, AggrCalcT> Aggregator;
    if constexpr (AggrCalcT::hasAggregated()) {
        Aggregator::recalc(*lNode, _aggrCalc);
    }
    lNode->freeze();
    BTreeTypeRefPair tPair(allocBTree());
    tPair.data->setRoots(lPair.ref);
    _store.holdElem(ref, clusterSize);
    ref = tPair.ref;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
makeArray(EntryRef &ref, EntryRef root, LeafNodeType *leafNode)
{
    uint32_t clusterSize = leafNode->validSlots();
    KeyDataTypeRefPair kPair(allocKeyData(clusterSize));
    KeyDataType *kd = kPair.data;
    // Copy whole leaf node
    for (uint32_t idx = 0; idx < clusterSize; ++idx, ++kd) {
        kd->_key = leafNode->getKey(idx);
        kd->setData(leafNode->getData(idx));
    }
    assert(kd == kPair.data + clusterSize);
    _store.holdElem(ref, 1);
    if (!leafNode->getFrozen()) {
        leafNode->freeze();
    }
    _allocator.holdNode(root, leafNode);
    ref = kPair.ref;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
bool
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
insert(EntryRef &ref,
       const KeyType &key, const DataType &data,
       CompareT comp)
{
#ifdef FORCE_APPLY
    bool retVal = true;
    if (ref.valid()) {
        RefType iRef(ref);
        uint32_t clusterSize = getClusterSize(iRef);
        if (clusterSize == 0) {
            const BTreeType *tree = getTreeEntry(iRef);
            Iterator itr = tree->find(key, _allocator, comp);
            if (itr.valid())
                retVal = false;
        } else {
            const KeyDataType *old = getKeyDataEntry(iRef, clusterSize);
            const KeyDataType *olde = old + clusterSize;
            const KeyDataType *oldi = lower_bound(old, olde, key, comp);
            if (oldi < olde && !comp(key, oldi->_key))
                retVal = false; // key already present
        }
    }
    KeyDataType addition(key, data);
    if (retVal) {
        apply(ref, &addition, &addition+1, nullptr, nullptr, comp);
    }
    return retVal;
#else
    if (!ref.valid()) {
        KeyDataTypeRefPair kPair(allocKeyData(1));
        KeyDataType *kd = kPair.data;
        kd->_key = key;
        kd->setData(data);
        ref = kPair.ref;
        return true;
    }
    RefType iRef(ref);
    uint32_t clusterSize = getClusterSize(iRef);
    if (clusterSize == 0) {
        BTreeType *tree = getWTreeEntry(iRef);
        return tree->insert(key, data, _allocator, comp, _aggrCalc);
    }
    const KeyDataType *old = getKeyDataEntry(iRef, clusterSize);
    const KeyDataType *olde = old + clusterSize;
    const KeyDataType *oldi = lower_bound(old, olde, key, comp);
    if (oldi < olde && !comp(key, oldi->_key))
        return false;   // key already present
    if (clusterSize < clusterLimit) {
        // Grow array
        KeyDataTypeRefPair kPair(allocKeyData(clusterSize + 1));
        KeyDataType *kd = kPair.data;
        // Copy data before key
        for (const KeyDataType *i = old; i != oldi; ++i, ++kd) {
            kd->_key = i->_key;
            kd->setData(i->getData());
        }
        // Copy key
        kd->_key = key;
        kd->setData(data);
        ++kd;
        // Copy data after key
        for (const KeyDataType *i = oldi; i != olde; ++i, ++kd) {
            kd->_key = i->_key;
            kd->setData(i->getData());
        }
        assert(kd == kPair.data + clusterSize + 1);
        _store.holdElem(ref, clusterSize);
        ref = kPair.ref;
        return true;
    }
    // Convert from short array to tree
    LeafNodeTypeRefPair lPair(_allocator.allocLeafNode());
    LeafNodeType *lNode = lPair.data;
    uint32_t idx = 0;
    lNode->setValidSlots(clusterSize + 1);
    // Copy data before key
    for (const KeyDataType *i = old; i != oldi; ++i, ++idx) {
        lNode->update(idx, i->_key, i->getData());
    }
    // Copy key
    lNode->update(idx, key, data);
    ++idx;
    // Copy data after key
    for (const KeyDataType *i = oldi; i != olde; ++i, ++idx) {
        lNode->update(idx, i->_key, i->getData());
    }
    assert(idx == clusterSize + 1);
    typedef BTreeAggregator<KeyT, DataT, AggrT,
        TraitsT::INTERNAL_SLOTS, TraitsT::LEAF_SLOTS, AggrCalcT> Aggregator;
    if constexpr (AggrCalcT::hasAggregated()) {
        Aggregator::recalc(*lNode, _aggrCalc);
    }
    lNode->freeze();
    BTreeTypeRefPair tPair(allocBTree());
    tPair.data->setRoots(lPair.ref); // allow immediate access to readers
    _store.holdElem(ref, clusterSize);
    ref = tPair.ref;
    return true;
#endif
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
bool
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
remove(EntryRef &ref,
       const KeyType &key,
       CompareT comp)
{
#ifdef FORCE_APPLY
    bool retVal = true;
    if (!ref.valid())
        retVal = false; // not found
    else {
        RefType iRef(ref);
        uint32_t clusterSize = getClusterSize(iRef);
        if (clusterSize == 0) {
            const BTreeType *tree = getTreeEntry(iRef);
            Iterator itr = tree->find(key, _allocator, comp);
            if (!itr.valid())
                retVal = false;
        } else {
            const KeyDataType *old = getKeyDataEntry(iRef, clusterSize);
            const KeyDataType *olde = old + clusterSize;
            const KeyDataType *oldi = lower_bound(old, olde, key, comp);
            if (oldi == olde || comp(key, oldi->_key))
                retVal = false; // not found
        }
    }
    std::vector<KeyDataType> additions;
    std::vector<KeyType> removals;
    removals.push_back(key);
    apply(ref,
          &additions[0], &additions[additions.size()],
          &removals[0], &removals[removals.size()],
          comp);
    return retVal;
#else
    if (!ref.valid())
        return false;   // not found
    RefType iRef(ref);
    uint32_t clusterSize = getClusterSize(iRef);
    if (clusterSize != 0) {
        const KeyDataType *old = getKeyDataEntry(iRef, clusterSize);
        const KeyDataType *olde = old + clusterSize;
        const KeyDataType *oldi = lower_bound(old, olde, key, comp);
        if (oldi == olde || comp(key, oldi->_key))
            return false;   // not found
        if (clusterSize == 1) {
            _store.holdElem(ref, 1);
            ref = EntryRef();
            return true;
        }
        // Copy to smaller array
        KeyDataTypeRefPair kPair(allocKeyData(clusterSize - 1));
        KeyDataType *kd = kPair.data;
        // Copy data before key
        for (const KeyDataType *i = old; i != oldi; ++i, ++kd) {
            kd->_key = i->_key;
            kd->setData(i->getData());
        }
        // Copy data after key
        for (const KeyDataType *i = oldi + 1; i != olde; ++i, ++kd) {
            kd->_key = i->_key;
            kd->setData(i->getData());
        }
        assert(kd == kPair.data + clusterSize - 1);
        _store.holdElem(ref, clusterSize);
        ref = kPair.ref;
        return true;
    }
    BTreeType *tree = getWTreeEntry(iRef);
    if (!tree->remove(key, _allocator, comp, _aggrCalc))
        return false;   // not found
    EntryRef root = tree->getRoot();
    assert(NodeAllocatorType::isValidRef(root));
    if (!_allocator.isLeafRef(root))
        return true;
    LeafNodeType *lNode = _allocator.mapLeafRef(root);
    clusterSize = lNode->validSlots();
    assert(clusterSize > 0);
    if (clusterSize > clusterLimit)
        return true;
    // Convert from tree to short array
    makeArray(ref, root, lNode);
    return true;
#endif
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
uint32_t
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
getNewClusterSize(const KeyDataType *o,
                  const KeyDataType *oe,
                  AddIter a,
                  AddIter ae,
                  RemoveIter r,
                  RemoveIter re,
                  CompareT comp)
{
    uint32_t d = 0u;
    if (o == oe && a == ae)
        return 0u;
    while (a != ae || r != re) {
        if (r != re && (a == ae || comp(*r, a->_key))) {
            // remove
            while (o != oe && comp(o->_key, *r)) {
                ++d;
                ++o;
            }
            if (o != oe && !comp(*r, o->_key))
                ++o;
            ++r;
        } else {
            // add or update
            while (o != oe && comp(o->_key, a->_key)) {
                ++d;
                ++o;
            }
            if (o != oe && !comp(a->_key, o->_key))
                ++o;
            ++d;
            if (r != re && !comp(a->_key, *r))
                ++r;
            ++a;
        }
    }
    while (o != oe) {
        ++d;
        ++o;
    }
    return d;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
applyCluster(const KeyDataType *o,
             const KeyDataType *oe,
             KeyDataType *d,
             const KeyDataType *de,
             AddIter a,
             AddIter ae,
             RemoveIter r,
             RemoveIter re,
             CompareT comp)
{
    while (a != ae || r != re) {
        if (r != re && (a == ae || comp(*r, a->_key))) {
            // remove
            while (o != oe && comp(o->_key, *r)) {
                d->_key = o->_key;
                d->setData(o->getData());
                ++d;
                ++o;
            }
            if (o != oe && !comp(*r, o->_key))
                ++o;
            ++r;
        } else {
            // add or update
            while (o != oe && comp(o->_key, a->_key)) {
                d->_key = o->_key;
                d->setData(o->getData());
                ++d;
                ++o;
            }
            if (o != oe && !comp(a->_key, o->_key))
                ++o;
            d->_key = a->_key;
            d->setData(a->getData());
            ++d;
            if (r != re && !comp(a->_key, *r))
                ++r;
            ++a;
        }
    }
    while (o != oe) {
        d->_key = o->_key;
        d->setData(o->getData());
        ++d;
        ++o;
    }
    assert(d == de);
    (void) de;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
applyModifyTree(BTreeType *tree,
                AddIter a,
                AddIter ae,
                RemoveIter r,
                RemoveIter re,
                CompareT comp)
{
    if (a == ae && r == re)
        return;
    Iterator itr(BTreeNode::Ref(), _allocator);
    itr.lower_bound(tree->getRoot(),
                    (a != ae && r != re) ? (comp(a->_key, *r) ? a->_key : *r) :
                    ((a != ae) ? a->_key : *r),
                    comp);
    while (a != ae || r != re) {
        if (r != re && (a == ae || comp(*r, a->_key))) {
            // remove
            if (itr.valid() && comp(itr.getKey(), *r)) {
                itr.binarySeek(*r, comp);
            }
            if (itr.valid() && !comp(*r, itr.getKey())) {
                tree->remove(itr, _aggrCalc);
            }
            ++r;
        } else {
            // update or add
            if (itr.valid() && comp(itr.getKey(), a->_key)) {
                itr.binarySeek(a->_key, comp);
            }
            if (itr.valid() && !comp(a->_key, itr.getKey())) {
                tree->thaw(itr);
                itr.updateData(a->getData(), _aggrCalc);
            } else {
                tree->insert(itr, a->_key, a->getData(), _aggrCalc);
            }
            if (r != re && !comp(a->_key, *r)) {
                ++r;
            }
            ++a;
        }
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
applyBuildTree(BTreeType *tree,
               AddIter a,
               AddIter ae,
               RemoveIter r,
               RemoveIter re,
               CompareT comp)
{
    Iterator itr = tree->begin(_allocator);
    Builder &builder = _builder;
    builder.reuse();
    while (a != ae || r != re) {
        if (r != re && (a == ae || comp(*r, a->_key))) {
            // remove
            while (itr.valid() && comp(itr.getKey(), *r)) {
                builder.insert(itr.getKey(), itr.getData());
                ++itr;
            }
            if (itr.valid() && !comp(*r, itr.getKey()))
                ++itr;
            ++r;
        } else {
            // add or update
            while (itr.valid() && comp(itr.getKey(), a->_key)) {
                builder.insert(itr.getKey(), itr.getData());
                ++itr;
            }
            if (itr.valid() && !comp(a->_key, itr.getKey()))
                ++itr;
            builder.insert(a->_key, a->getData());
            if (r != re && !comp(a->_key, *r))
                ++r;
            ++a;
        }
    }
    while (itr.valid()) {
        builder.insert(itr.getKey(), itr.getData());
        ++itr;
    }
    tree->assign(builder, _allocator);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
applyNewArray(EntryRef &ref,
              AddIter aOrg,
              AddIter ae)
{
    assert(!ref.valid());
    if (aOrg == ae) {
        // No new data
        return;
    }
    size_t additionSize(ae - aOrg);
    uint32_t clusterSize = additionSize;
    assert(clusterSize <= clusterLimit);
    KeyDataTypeRefPair kPair(allocKeyData(clusterSize));
    KeyDataType *kd = kPair.data;
    AddIter a = aOrg;
    for (;a != ae; ++a, ++kd) {
        kd->_key = a->_key;
        kd->setData(a->getData());
    }
    assert(kd == kPair.data + clusterSize);
    assert(a == ae);
    ref = kPair.ref;
 }
    

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
applyNewTree(EntryRef &ref,
             AddIter a,
             AddIter ae,
             CompareT comp)
{
    assert(!ref.valid());
    size_t additionSize(ae - a);
    BTreeTypeRefPair tPair(allocBTree());
    BTreeType *tree = tPair.data;
    applyBuildTree(tree, a, ae, nullptr, nullptr, comp);
    assert(tree->size(_allocator) == additionSize);
    (void) additionSize;
    ref = tPair.ref;
}
 
   
template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
applyNew(EntryRef &ref,
         AddIter a,
         AddIter ae,
         CompareT comp)
{
    // No old data
    assert(!ref.valid());
    size_t additionSize(ae - a);
    uint32_t clusterSize = additionSize;
    if (clusterSize <= clusterLimit) {
        applyNewArray(ref, a, ae);
    } else {
        applyNewTree(ref, a, ae, comp);
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
bool
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
applyCluster(EntryRef &ref,
             uint32_t clusterSize,
             AddIter a,
             AddIter ae,
             RemoveIter r,
             RemoveIter re,
             CompareT comp)
{
    size_t additionSize(ae - a);
    size_t removeSize(re - r);
    uint32_t newSizeMin =
        std::max(clusterSize,
                 static_cast<uint32_t>(additionSize)) -
        std::min(clusterSize, static_cast<uint32_t>(removeSize));
    RefType iRef(ref);
    const KeyDataType *ob = getKeyDataEntry(iRef, clusterSize);
    const KeyDataType *oe = ob + clusterSize;
    if (newSizeMin <= clusterLimit) {
        uint32_t newSize = getNewClusterSize(ob, oe, a, ae, r, re, comp);
        if (newSize == 0) {
            _store.holdElem(ref, clusterSize);
            ref = EntryRef();
            return true;
        }
        if (newSize <= clusterLimit) {
            KeyDataTypeRefPair kPair(allocKeyData(newSize));
            applyCluster(ob, oe, kPair.data, kPair.data + newSize,
                         a, ae, r, re, comp);
            _store.holdElem(ref, clusterSize);
            ref = kPair.ref;
            return true;
        }
    }
    // Convert from short array to tree
    makeTree(ref, ob, clusterSize);
    return false;
}

namespace {

// Included here verbatim to avoid dependency on searchlib bitcompression
// sub-library just for this function.
// TODO should there be a special-casing for 0 here? Existing bitcompression code
// does not have this either, but msbIdx et al is not defined when no bits are set.
inline uint32_t asmlog2(uint64_t v) noexcept {
    return vespalib::Optimized::msbIdx(v);
}

}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
applyTree(BTreeType *tree,
          AddIter a,
          AddIter ae,
          RemoveIter r,
          RemoveIter re,
          CompareT comp)
{
    // Old data was tree or has been converted to a tree
    uint32_t treeSize = tree->size(_allocator);
    size_t additionSize(ae - a);
    size_t removeSize(re - r);
    uint64_t buildCost = treeSize * 2 + additionSize;
    uint64_t modifyCost = (asmlog2(treeSize + additionSize) + 1) *
                          (additionSize + removeSize);
    if (modifyCost < buildCost)
        applyModifyTree(tree, a, ae, r, re, comp);
    else
        applyBuildTree(tree, a, ae, r, re, comp);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
normalizeTree(EntryRef &ref,
              BTreeType *tree,
              bool wasArray)
{
    EntryRef root = tree->getRoot();
    if (!NodeAllocatorType::isValidRef(root)) {
        _store.holdElem(ref, 1);
        ref = EntryRef();
        return;
    }
    if (!_allocator.isLeafRef(root))
        return;
    LeafNodeType *lNode = _allocator.mapLeafRef(root);
    uint32_t treeSize = lNode->validSlots();
    assert(treeSize > 0);
    if (treeSize > clusterLimit)
        return;
    assert(!wasArray);  // Should never have used tree
    (void) wasArray;
    // Convert from tree to short array
    makeArray(ref, root, lNode);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
apply(EntryRef &ref,
      AddIter a,
      AddIter ae,
      RemoveIter r,
      RemoveIter re,
      CompareT comp)
{
    if (!ref.valid()) {
        // No old data
        applyNew(ref, a, ae, comp);
        return;
    }
    RefType iRef(ref);
    bool wasArray = false;
    uint32_t clusterSize = getClusterSize(iRef);
    if (clusterSize != 0) {
        wasArray = true;
        if (applyCluster(ref, clusterSize, a, ae, r, re, comp))
            return;
        iRef = ref;
    }
    // Old data was tree or has been converted to a tree
    BTreeType *tree = getWTreeEntry(iRef);
    applyTree(tree, a, ae, r, re, comp);
    normalizeTree(ref, tree, wasArray);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
clear(const EntryRef ref)
{
    if (!ref.valid())
        return;
    RefType iRef(ref);
    uint32_t clusterSize = getClusterSize(iRef);
    if (clusterSize == 0) {
        BTreeType *tree = getWTreeEntry(iRef);
        tree->clear(_allocator);
        _store.holdElem(ref, 1);
    } else {
        _store.holdElem(ref, clusterSize);
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
size_t
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
size(const EntryRef ref) const
{
    if (!ref.valid())
        return 0;
    RefType iRef(ref);
    uint32_t clusterSize = getClusterSize(iRef);
    if (clusterSize == 0) {
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->size(_allocator);
    }
    return clusterSize;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
size_t
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
frozenSize(const EntryRef ref) const
{
    if (!ref.valid())
        return 0;
    RefType iRef(ref);
    uint32_t clusterSize = getClusterSize(iRef);
    if (clusterSize == 0) {
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->frozenSize(_allocator);
    }
    return clusterSize;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
bool
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
isSmallArray(const EntryRef ref) const
{
    if (!ref.valid())
        return true;
    RefType iRef(ref);
    uint32_t typeId(_store.getBufferState(iRef.bufferId()).getTypeId());
    return typeId < clusterLimit;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
typename BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
Iterator
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
begin(const EntryRef ref) const
{
    if (!ref.valid())
        return Iterator();
    RefType iRef(ref);
    uint32_t clusterSize = getClusterSize(iRef);
    if (clusterSize == 0) {
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->begin(_allocator);
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    return Iterator(shortArray, clusterSize, _allocator, _aggrCalc);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
typename BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
ConstIterator
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
beginFrozen(const EntryRef ref) const
{
    if (!ref.valid())
        return ConstIterator();
    RefType iRef(ref);
    uint32_t clusterSize = getClusterSize(iRef);
    if (clusterSize == 0) {
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->getFrozenView(_allocator).begin();
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    return ConstIterator(shortArray, clusterSize, _allocator, _aggrCalc);
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
void
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
beginFrozen(const EntryRef ref, std::vector<ConstIterator> &where) const
{
    if (!ref.valid()) {
        where.emplace_back();
        return;
    }
    RefType iRef(ref);
    uint32_t clusterSize = getClusterSize(iRef);
    if (clusterSize == 0) {
        const BTreeType *tree = getTreeEntry(iRef);
        tree->getFrozenView(_allocator).begin(where);
        return;
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    where.emplace_back(shortArray, clusterSize, _allocator, _aggrCalc);
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, typename AggrCalcT>
typename BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
AggregatedType
BTreeStore<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
getAggregated(const EntryRef ref) const
{
    if (!ref.valid())
        return AggregatedType();
    RefType iRef(ref);
    uint32_t clusterSize = getClusterSize(iRef);
    if (clusterSize == 0) {
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->getAggregated(_allocator);
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    AggregatedType a;
    for (uint32_t i = 0; i < clusterSize; ++i) {
        if constexpr (AggrCalcT::aggregate_over_values()) {
            _aggrCalc.add(a, _aggrCalc.getVal(shortArray[i].getData()));
        } else {
            _aggrCalc.add(a, _aggrCalc.getVal(shortArray[i].getKey()));
        }
    }
    return a;
}

}
