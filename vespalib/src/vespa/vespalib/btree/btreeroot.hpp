// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreeroot.h"
#include "btreebuilder.h"
#include "btreerootbase.hpp"
#include "btreeinserter.hpp"
#include "btreeremover.hpp"
#include "btreeaggregator.hpp"
#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib::btree {

//----------------------- BTreeRoot ------------------------------------------//

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
vespalib::string
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
toString(BTreeNode::Ref node,
         const NodeAllocatorType &allocator) const
{
    if (allocator.isLeafRef(node)) {
        vespalib::asciistream ss;
        ss << "{" << allocator.toString(node) << "}";
        return ss.str();
    } else {
        const InternalNodeType * inode = allocator.mapInternalRef(node);
        vespalib::asciistream ss;
        ss << "{" << allocator.toString(inode) << ",children(" << inode->validSlots() << ")[";
        for (size_t i = 0; i < inode->validSlots(); ++i) {
            if (i > 0) ss << ",";
            ss << "c[" << i << "]" << toString(inode->getChild(i), allocator);
        }
        ss << "]}";
        return ss.str();
    }
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
bool
BTreeRoot<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
isValid(BTreeNode::Ref node,
        bool ignoreMinSlots, uint32_t level, const NodeAllocatorType &allocator,
        CompareT comp, AggrCalcT aggrCalc) const
{
    if (allocator.isLeafRef(node)) {
        if (level != 0) {
            return false;
        }
        const LeafNodeType * lnode = allocator.mapLeafRef(node);
        if (level != lnode->getLevel()) {
            return false;
        }
        if (lnode->validSlots() > LeafNodeType::maxSlots())
            return false;
        if (lnode->validSlots() < LeafNodeType::minSlots() && !ignoreMinSlots)
            return false;
        for (size_t i = 1; i < lnode->validSlots(); ++i) {
            if (!comp(lnode->getKey(i - 1), lnode->getKey(i))) {
                return false;
            }
        }
        if constexpr (AggrCalcT::hasAggregated()) {
            AggrT aggregated = Aggregator::aggregate(*lnode, aggrCalc);
            if (aggregated != lnode->getAggregated()) {
                return false;
            }
        }
    } else {
        if (level == 0) {
            return false;
        }
        const InternalNodeType * inode = allocator.mapInternalRef(node);
        if (level != inode->getLevel()) {
            return false;
        }
        if (inode->validSlots() > InternalNodeType::maxSlots())
            return false;
        if (inode->validSlots() < InternalNodeType::minSlots() &&
            !ignoreMinSlots)
            return false;
        size_t lChildren = 0;
        size_t iChildren = 0;
        uint32_t validLeaves = 0;
        for (size_t i = 0; i < inode->validSlots(); ++i) {
            if (i > 0 && !comp(inode->getKey(i - 1), inode->getKey(i))) {
                return false;
            }
            const BTreeNode::Ref childRef = inode->getChild(i);
            if (!allocator.isValidRef(childRef))
                return false;
            validLeaves += allocator.validLeaves(childRef);
            if (allocator.isLeafRef(childRef))
                lChildren++;
            else
                iChildren++;
            if (comp(inode->getKey(i), allocator.getLastKey(childRef))) {
                return false;
            }
            if (comp(allocator.getLastKey(childRef), inode->getKey(i))) {
                return false;
            }
            if (!isValid(childRef, false, level - 1, allocator, comp, aggrCalc)) {
                return false;
            }
        }
        if (validLeaves != inode->validLeaves()) {
            return false;
        }
        if (lChildren < inode->validSlots() && iChildren < inode->validSlots()) {
            return false;
        }
        if constexpr (AggrCalcT::hasAggregated()) {
            AggrT aggregated = Aggregator::aggregate(*inode, allocator, aggrCalc);
            if (aggregated != inode->getAggregated()) {
                return false;
            }
        }
    }
    return true;
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
typename BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::Iterator
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
findHelper(BTreeNode::Ref root, const KeyType & key,
           const NodeAllocatorType & allocator, CompareT comp)
{
    Iterator itr(BTreeNode::Ref(), allocator);
    itr.lower_bound(root, key, comp);
    if (itr.valid() && comp(key, itr.getKey())) {
        itr.setupEnd();
    }
    return itr;
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
typename BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::Iterator
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
lowerBoundHelper(BTreeNode::Ref root, const KeyType & key, const NodeAllocatorType & allocator, CompareT comp)
{
    Iterator itr(BTreeNode::Ref(), allocator);
    itr.lower_bound(root, key, comp);
    return itr;
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
typename BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::Iterator
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
upperBoundHelper(BTreeNode::Ref root, const KeyType & key,
                 const NodeAllocatorType & allocator, CompareT comp)
{
    Iterator itr(root, allocator);
    if (itr.valid() && !comp(key, itr.getKey())) {
        itr.seekPast(key, comp);
    }
    return itr;
}


//----------------------- BTreeRoot::FrozenView ----------------------------------//

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
typename BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::ConstIterator
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
FrozenView::find(const KeyType & key, CompareT comp) const
{
    ConstIterator itr(BTreeNode::Ref(), *_allocator);
    itr.lower_bound(_frozenRoot, key, comp);
    if (itr.valid() && comp(key, itr.getKey())) {
        itr.setupEnd();
    }
    return itr;
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
typename BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::ConstIterator
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
FrozenView::lowerBound(const KeyType & key, CompareT comp) const
{
    ConstIterator itr(BTreeNode::Ref(), *_allocator);
    itr.lower_bound(_frozenRoot, key, comp);
    return itr;
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
typename BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::ConstIterator
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
FrozenView::upperBound(const KeyType & key, CompareT comp) const
{
    ConstIterator itr(_frozenRoot, *_allocator);
    if (itr.valid() && !comp(key, itr.getKey())) {
        itr.seekPast(key, comp);
    }
    return itr;
}

//----------------------- BTreeRoot ----------------------------------------------//

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::BTreeRootT() = default;

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::~BTreeRootT() = default;

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
clear(NodeAllocatorType &allocator)
{
    if (NodeAllocatorType::isValidRef(_root)) {
        this->recursiveDelete(_root, allocator);
        _root = BTreeNode::Ref();
        if (NodeAllocatorType::isValidRef(getFrozenRootRelaxed()))
            allocator.needFreeze(this);
    }
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
typename BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::Iterator
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
find(const KeyType & key, const NodeAllocatorType & allocator, CompareT comp) const
{
    return findHelper(_root, key, allocator, comp);
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
typename BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::Iterator
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
lowerBound(const KeyType & key, const NodeAllocatorType & allocator, CompareT comp) const
{
    return lowerBoundHelper(_root, key, allocator, comp);
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
typename BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::Iterator
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
upperBound(const KeyType & key, const NodeAllocatorType & allocator, CompareT comp) const
{
    return upperBoundHelper(_root, key, allocator, comp);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
size_t
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
size(const NodeAllocatorType &allocator) const
{
    if (NodeAllocatorType::isValidRef(_root)) {
        return allocator.validLeaves(_root);
    }
    return 0u;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
size_t
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
frozenSize(const NodeAllocatorType &allocator) const
{
    BTreeNode::Ref frozenRoot = getFrozenRoot();
    if (NodeAllocatorType::isValidRef(frozenRoot)) {
        return allocator.validLeaves(frozenRoot);
    }
    return 0u;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
vespalib::string
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
toString(const NodeAllocatorType &allocator) const
{
    vespalib::asciistream ss;
    if (NodeAllocatorType::isValidRef(_root)) {
        ss << "root(" << toString(_root, allocator) << ")";
    }
    return ss.str();
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
bool
BTreeRoot<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
isValid(const NodeAllocatorType &allocator,
        CompareT comp) const
{
    if (NodeAllocatorType::isValidRef(_root)) {
        uint32_t level  = allocator.getLevel(_root);
        return isValid(_root, true, level, allocator, comp, AggrCalcT());
    }
    return true;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
bool
BTreeRoot<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
isValidFrozen(const NodeAllocatorType &allocator, CompareT comp) const
{
    BTreeNode::Ref frozenRoot = getFrozenRoot();
    if (NodeAllocatorType::isValidRef(frozenRoot)) {
        uint32_t level  = allocator.getLevel(frozenRoot);
        return isValid(frozenRoot, true, level, allocator, comp, AggrCalcT());
    }
    return true;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT>
size_t
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
bitSize(const NodeAllocatorType &allocator) const
{
    size_t ret = sizeof(BTreeRootT) * 8;
    if (NodeAllocatorType::isValidRef(_root))
        ret += bitSize(_root, allocator);
    return ret;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT>
size_t
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
bitSize(BTreeNode::Ref node, const NodeAllocatorType &allocator) const
{
    if (allocator.isLeafRef(node)) {
        return sizeof(LeafNodeType) * 8;
    } else {
        size_t ret = sizeof(InternalNodeType) * 8;
        const InternalNodeType * inode = allocator.mapInternalRef(node);
        size_t slots = inode->validSlots();
        for (size_t i = 0; i < slots; ++i) {
            ret += bitSize(inode->getChild(i), allocator);
        }
        return ret;
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>::
thaw(Iterator &itr)
{
    bool oldFrozen = isFrozen();
    _root = itr.thaw(_root);
    if (oldFrozen && !isFrozen())
        itr.getAllocator().needFreeze(this);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
void
BTreeRoot<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
assign(Builder &rhs,
       NodeAllocatorType &allocator)
{
    this->clear(allocator);

    bool oldFrozen = isFrozen();
    _root = rhs.handover();
    if (oldFrozen && !isFrozen())
        allocator.needFreeze(this);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
bool
BTreeRoot<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
insert(const KeyType & key, const DataType & data,
       NodeAllocatorType &allocator, CompareT comp,
       const AggrCalcT &aggrCalc)
{
    Iterator itr(BTreeNode::Ref(), allocator);
    itr.lower_bound(_root, key, comp);
    if (itr.valid() && !comp(key, itr.getKey()))
        return false; // Element already exists
    insert(itr, key, data, aggrCalc);
    return true;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
void
BTreeRoot<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
insert(Iterator &itr,
       const KeyType &key, const DataType &data,
       const AggrCalcT &aggrCalc)
{
    using Inserter = BTreeInserter<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>;
    bool oldFrozen = isFrozen();
    Inserter::insert(_root, itr, key, data,aggrCalc);
    if (oldFrozen && !isFrozen())
        itr.getAllocator().needFreeze(this);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
bool
BTreeRoot<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
remove(const KeyType & key,
       NodeAllocatorType &allocator, CompareT comp,
       const AggrCalcT &aggrCalc)
{
    Iterator itr(BTreeNode::Ref(), allocator);
    itr.lower_bound(_root, key, comp);
    if (!itr.valid() || comp(key, itr.getKey()))
        return false;
    remove(itr, aggrCalc);
    return true;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
void
BTreeRoot<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
remove(Iterator &itr, const AggrCalcT &aggrCalc)
{
    using Remover = BTreeRemover<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>;
    bool oldFrozen = isFrozen();
    Remover::remove(_root, itr, aggrCalc);
    if (oldFrozen && !isFrozen())
        itr.getAllocator().needFreeze(this);
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
void
BTreeRoot<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
move_nodes(NodeAllocatorType &allocator)
{
    Iterator itr = this->begin(allocator);
    this->setRoot(itr.moveFirstLeafNode(this->getRoot()), allocator);
    while (itr.valid()) {
        itr.moveNextLeafNode();
    }
}

}
