// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenodeallocator.h"
#include "btreerootbase.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include "btreenodestore.hpp"

namespace search {
namespace btree {

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
BTreeNodeAllocator(void)
    : _nodeStore(),
      _internalToFreeze(),
      _leafToFreeze(),
      _treeToFreeze(),
      _internalHoldUntilFreeze(),
      _leafHoldUntilFreeze()
{
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
~BTreeNodeAllocator(void)
{
    assert(_internalToFreeze.empty());
    assert(_leafToFreeze.empty());
    assert(_treeToFreeze.empty());
    assert(_internalHoldUntilFreeze.empty());
    assert(_leafHoldUntilFreeze.empty());
    DataStoreBase::MemStats stats = _nodeStore.getMemStats();
    assert(stats._usedBytes == stats._deadBytes);
    assert(stats._holdBytes == 0);
    (void) stats;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
typename BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
InternalNodeTypeRefPair
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
allocInternalNode(uint8_t level)
{
    if (_internalHoldUntilFreeze.empty()) {
        InternalNodeTypeRefPair nodeRef = _nodeStore.allocInternalNode();
        assert(nodeRef.first.valid());
        _internalToFreeze.push_back(nodeRef.first);
        nodeRef.second->setLevel(level);
        return nodeRef;
    }
    BTreeNode::Ref nodeRef = _internalHoldUntilFreeze.back();
    _internalHoldUntilFreeze.pop_back();
    InternalNodeType *node = mapInternalRef(nodeRef);
    assert(!node->getFrozen());
    node->setLevel(level);
    return std::make_pair(nodeRef, node);
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
typename BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
LeafNodeTypeRefPair
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
allocLeafNode(void)
{
    if (_leafHoldUntilFreeze.empty()) {
        LeafNodeTypeRefPair nodeRef = _nodeStore.allocLeafNode();
        _leafToFreeze.push_back(nodeRef.first);
        return nodeRef;
    }
    BTreeNode::Ref nodeRef = _leafHoldUntilFreeze.back();
    _leafHoldUntilFreeze.pop_back();
    LeafNodeType *node = mapLeafRef(nodeRef);
    assert(!node->getFrozen());
    return std::make_pair(nodeRef, node);
}



template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
typename BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
InternalNodeTypeRefPair
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
thawNode(BTreeNode::Ref nodeRef, InternalNodeType *node)
{
    if (_internalHoldUntilFreeze.empty()) {
        InternalNodeTypeRefPair retNodeRef =
            _nodeStore.allocInternalNodeCopy(*node);
        assert(retNodeRef.second->getFrozen());
        retNodeRef.second->unFreeze();
        assert(retNodeRef.first.valid());
        _internalToFreeze.push_back(retNodeRef.first);
        holdNode(nodeRef, node);
        return retNodeRef;
    }
    BTreeNode::Ref retNodeRef = _internalHoldUntilFreeze.back();
    InternalNodeType *retNode = mapInternalRef(retNodeRef);
    _internalHoldUntilFreeze.pop_back();
    assert(!retNode->getFrozen());
    *retNode = static_cast<const InternalNodeType &>(*node);
    assert(retNode->getFrozen());
    retNode->unFreeze();
    holdNode(nodeRef, node);
    return std::make_pair(retNodeRef, retNode);
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
typename BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
LeafNodeTypeRefPair
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
thawNode(BTreeNode::Ref nodeRef, LeafNodeType *node)
{
    if (_leafHoldUntilFreeze.empty()) {
        LeafNodeTypeRefPair retNodeRef =
            _nodeStore.allocLeafNodeCopy(*node);
        assert(retNodeRef.second->getFrozen());
        retNodeRef.second->unFreeze();
        _leafToFreeze.push_back(retNodeRef.first);
        holdNode(nodeRef, node);
        return retNodeRef;
    }
    BTreeNode::Ref retNodeRef = _leafHoldUntilFreeze.back();
    LeafNodeType *retNode = mapLeafRef(retNodeRef);
    _leafHoldUntilFreeze.pop_back();
    assert(!retNode->getFrozen());
    *retNode = static_cast<const LeafNodeType &>(*node);
    assert(retNode->getFrozen());
    retNode->unFreeze();
    holdNode(nodeRef, node);
    return std::make_pair(retNodeRef, retNode);
}

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
BTreeNode::Ref
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
thawNode(BTreeNode::Ref node)
{
    if (isLeafRef(node))
        return thawNode(node, mapLeafRef(node)).first;
    else
        return thawNode(node, mapInternalRef(node)).first;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
void
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
holdNode(BTreeNode::Ref nodeRef,
         InternalNodeType *node)
{
    if (node->getFrozen()) {
        _nodeStore.holdElem(nodeRef);
    } else {
        node->clean();
        _internalHoldUntilFreeze.push_back(nodeRef);
    }
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
void
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
holdNode(BTreeNode::Ref nodeRef,
         LeafNodeType *node)
{
    if (node->getFrozen()) {
        _nodeStore.holdElem(nodeRef);
    } else {
        node->clean();
        _leafHoldUntilFreeze.push_back(nodeRef);
    }
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
void
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
freeze(void)
{
    // Freeze nodes.

    if (!_internalToFreeze.empty() || !_leafToFreeze.empty()) {
        {
            for (auto &i : _internalToFreeze) {
                assert(i.valid());
                mapInternalRef(i)->freeze();
            }
            _internalToFreeze.clear();
        }
        {
            for (auto &i : _leafToFreeze) {
                assert(i.valid());
                mapLeafRef(i)->freeze();
            }
            _leafToFreeze.clear();
        }

        // Tree node freezes must be visible before tree freezes to
        // ensure that readers see a frozen world
        std::atomic_thread_fence(std::memory_order_release);
    }

    // Freeze trees.

    if (!_treeToFreeze.empty()) {
        for (auto &i : _treeToFreeze) {
            i->freeze(*this);
        }
        _treeToFreeze.clear();
        // Tree freezes must be visible before held nodes are freed
        std::atomic_thread_fence(std::memory_order_release);
    }


    // Free nodes that were only held due to freezing.

    {
        for (auto &i : _internalHoldUntilFreeze) {
            assert(!isLeafRef(i));
            InternalNodeType *inode = mapInternalRef(i);
            (void) inode;
            assert(inode->getFrozen());
            _nodeStore.freeElem(i);
        }
        _internalHoldUntilFreeze.clear();
    }
    {
        for (auto &i : _leafHoldUntilFreeze) {
            assert(isLeafRef(i));
            LeafNodeType *lnode = mapLeafRef(i);
            (void) lnode;
            assert(lnode->getFrozen());
            _nodeStore.freeElem(i);
        }
        _leafHoldUntilFreeze.clear();
    }
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
void
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
needFreeze(BTreeRootBaseType *tree)
{
    _treeToFreeze.push_back(tree);
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
void
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
trimHoldLists(generation_t usedGen)
{
    _nodeStore.trimHoldLists(usedGen);
}

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
void
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
transferHoldLists(generation_t generation)
{
    _nodeStore.transferHoldLists(generation);
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
void
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
clearHoldLists(void)
{
    _nodeStore.clearHoldLists();
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
typename BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
InternalNodeTypeRefPair
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
moveInternalNode(const InternalNodeType *node)
{
    InternalNodeTypeRefPair iPair;
    iPair = _nodeStore.allocNewInternalNodeCopy(*node);
    assert(iPair.first.valid());
    _internalToFreeze.push_back(iPair.first);
    return iPair;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
typename BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
LeafNodeTypeRefPair
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
moveLeafNode(const LeafNodeType *node)
{
    LeafNodeTypeRefPair lPair;
    lPair = _nodeStore.allocNewLeafNodeCopy(*node);
    _leafToFreeze.push_back(lPair.first);
    return lPair;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
uint32_t
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
validLeaves(BTreeNode::Ref ref) const
{
    if (isLeafRef(ref))
        return mapLeafRef(ref)->validSlots();
    else
        return mapInternalRef(ref)->validLeaves();
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
uint32_t
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
getLevel(BTreeNode::Ref ref) const
{
    if (isLeafRef(ref))
        return BTreeNode::LEAF_LEVEL;
    else
        return mapInternalRef(ref)->getLevel();
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
const KeyT &
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
getLastKey(BTreeNode::Ref node) const
{
    if (isLeafRef(node))
        return mapLeafRef(node)->getLastKey();
    else
        return mapInternalRef(node)->getLastKey();
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
const AggrT &
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
getAggregated(BTreeNode::Ref node) const
{
    if (!node.valid())
        return LeafNodeType::getEmptyAggregated();
    if (isLeafRef(node))
        return mapLeafRef(node)->getAggregated();
    else
        return mapInternalRef(node)->getAggregated();
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
MemoryUsage
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
getMemoryUsage() const
{
    MemoryUsage usage = _nodeStore.getMemoryUsage();
    return usage;
}

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
vespalib::string
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
toString(BTreeNode::Ref ref) const
{
    if (!isValidRef(ref)) {
        return "NULL";
    }
    if (isLeafRef(ref))
        return toString(mapLeafRef(ref));
    else
        return toString(mapInternalRef(ref));
}

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
vespalib::string
BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
toString(const BTreeNode * node) const
{
    if (node == NULL) {
        return "NULL";
    }
    vespalib::asciistream ss;
    if (node->isLeaf()) {
        const LeafNodeType * lnode = static_cast<const LeafNodeType *>(node);
        ss << "L: keys(" << lnode->validSlots() << ")[";
        for (uint32_t i = 0; i < lnode->validSlots(); ++i) {
            if (i > 0) ss << ",";
            ss << lnode->getKey(i);
        }
        ss << "]";
    } else {
        const InternalNodeType * inode =
            static_cast<const InternalNodeType *>(node);
        ss << "I: validLeaves(" << inode->validLeaves() <<
            "), keys(" << inode->validSlots() << ")[";
        for (uint32_t i = 0; i < inode->validSlots(); ++i) {
            if (i > 0) ss << ",";
            ss << inode->getKey(i);
        }
        ss << "]";
    }
    return ss.str();
}


} // namespace btree
} // namespace search

