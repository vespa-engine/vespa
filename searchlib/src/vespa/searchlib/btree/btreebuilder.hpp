// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreebuilder.h"

namespace search
{

namespace btree
{

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
BTreeBuilder(NodeAllocatorType &allocator)
    : _allocator(allocator),
      _numInternalNodes(0),
      _numLeafNodes(0),
      _numInserts(0),
      _inodes(),
      _leaf(),
      _defaultAggrCalc(),
      _aggrCalc(_defaultAggrCalc)
{
    _leaf = _allocator.allocLeafNode();
    ++_numLeafNodes;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
BTreeBuilder(NodeAllocatorType &allocator, const AggrCalcT &aggrCalc)
    : _allocator(allocator),
      _numInternalNodes(0),
      _numLeafNodes(0),
      _numInserts(0),
      _inodes(),
      _leaf(),
      _defaultAggrCalc(),
      _aggrCalc(aggrCalc)
{
    _leaf = _allocator.allocLeafNode();
    ++_numLeafNodes;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
~BTreeBuilder(void)
{
    clear();
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
recursiveDelete(NodeRef node)
{
    assert(_allocator.isValidRef(node));
    if (_allocator.isLeafRef(node)) {
        _allocator.holdNode(node, _allocator.mapLeafRef(node));
        _numLeafNodes--;
        return;
    }
    InternalNodeType *inode = _allocator.mapInternalRef(node);
    for (unsigned int i = 0; i < inode->validSlots(); ++i) {
        recursiveDelete(inode->getChild(i));
    }
    _allocator.holdNode(node, inode);
    _numInternalNodes--;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
normalize(void)
{
    std::vector<NodeRef> leftInodes;	// left to rightmost nodes in tree
    LeafNodeType *leftLeaf;
    NodeRef child;
    unsigned int level;
    LeafNodeType *leafNode = _leaf.second;

    if (_inodes.size() == 0) {
        if (leafNode->validSlots() == 0) {
            assert(_numLeafNodes == 1);
            assert(_numInserts == 0);
            _allocator.holdNode(_leaf.first, _leaf.second);
            _numLeafNodes--;
            _leaf = std::make_pair(NodeRef(),
                                   static_cast<LeafNodeType *>(NULL));
        }
        if (AggrCalcT::hasAggregated()) {
            Aggregator::recalc(*leafNode, _aggrCalc);
        }
        assert(_numInserts == leafNode->validSlots());
        return;
    }

    if (AggrCalcT::hasAggregated()) {
        Aggregator::recalc(*leafNode, _aggrCalc);
    }
    /* Adjust validLeaves for rightmost nodes */
    for (level = 0; level < _inodes.size(); level++) {
        InternalNodeType *inode = _inodes[level].second;
        NodeRef lcRef(inode->getLastChild());
        assert(NodeAllocatorType::isValidRef(lcRef));
        assert((level == 0) == _allocator.isLeafRef(lcRef));
        inode->incValidLeaves(_allocator.validLeaves(inode->getLastChild()));
        inode->update(inode->validSlots() - 1,
                      level == 0 ?
                      _allocator.mapLeafRef(lcRef)->getLastKey() :
                      _allocator.mapInternalRef(lcRef)->getLastKey(),
                      lcRef);
        if (AggrCalcT::hasAggregated()) {
            Aggregator::recalc(*inode, _allocator, _aggrCalc);
        }
    }
    for (level = 0; level + 1 < _inodes.size(); level++) {
        leftInodes.push_back(NodeRef());
    }
    /* Build vector of left to rightmost internal nodes (except root level) */
    level = _inodes.size() - 1;
    for (;;) {
        NodeRef iRef = _inodes[level].first;
        InternalNodeType *inode = _inodes[level].second;
        if (inode->validSlots() < 2) {
            /* Use last child of left to rightmost node on level */
            assert(level + 1 < _inodes.size());
            iRef = leftInodes[level];
            inode = _allocator.mapInternalRef(iRef);
            assert(inode != NULL);
            assert(inode->validSlots() >= 1);
            child = inode->getLastChild();
        } else {
            /* Use next to last child of rightmost node on level */
            child = inode->getChild(inode->validSlots() - 2);
        }
        if (level == 0)
            break;
        level--;
        assert(!_allocator.isLeafRef(child));
        leftInodes[level] = child;
    }
    /* Remember left to rightmost leaf node */
    assert(_allocator.isLeafRef(child));
    leftLeaf = _allocator.mapLeafRef(child);

    /* Check fanout on rightmost leaf node */
    if (leafNode->validSlots() < LeafNodeType::minSlots()) {
        InternalNodeType *pnode = _inodes[0].second;
        if (leftLeaf->validSlots() + leafNode->validSlots() <
            2 * LeafNodeType::minSlots()) {
            leftLeaf->stealAllFromRightNode(leafNode);
            if (pnode->validSlots() == 1) {
                InternalNodeType *lpnode =
                    _allocator.mapInternalRef(leftInodes[0]);
                lpnode->incValidLeaves(pnode->validLeaves());
                pnode->setValidLeaves(0);
            }
            /* Unlink from parent node */
            pnode->remove(pnode->validSlots() - 1);
            _allocator.holdNode(_leaf.first, leafNode);
            _numLeafNodes--;
            _leaf = std::make_pair(child, leftLeaf);
            if (AggrCalcT::hasAggregated()) {
                Aggregator::recalc(*leftLeaf, _aggrCalc);
            }
        } else {
            leafNode->stealSomeFromLeftNode(leftLeaf);
            if (AggrCalcT::hasAggregated()) {
                Aggregator::recalc(*leftLeaf, _aggrCalc);
                Aggregator::recalc(*leafNode, _aggrCalc);
            }
            if (pnode->validSlots() == 1) {
                InternalNodeType *lpnode =
                    _allocator.mapInternalRef(leftInodes[0]);
                uint32_t steal = leafNode->validLeaves() -
                                 pnode->validLeaves();
                pnode->incValidLeaves(steal);
                lpnode->decValidLeaves(steal);
                if (AggrCalcT::hasAggregated()) {
                    Aggregator::recalc(*lpnode, _allocator, _aggrCalc);
                    Aggregator::recalc(*pnode, _allocator, _aggrCalc);
                }
            }
        }
        if (pnode->validSlots() > 0) {
            uint32_t s = pnode->validSlots() - 1;
            LeafNodeType *l = _allocator.mapLeafRef(pnode->getChild(s));
            pnode->writeKey(s, l->getLastKey());
            if (s > 0) {
                --s;
                l = _allocator.mapLeafRef(pnode->getChild(s));
                pnode->writeKey(s, l->getLastKey());
            }
        }
        if (!leftInodes.empty() && _allocator.isValidRef(leftInodes[0])) {
            InternalNodeType *lpnode =
                _allocator.mapInternalRef(leftInodes[0]);
            uint32_t s = lpnode->validSlots() - 1;
            LeafNodeType *l = _allocator.mapLeafRef(lpnode->getChild(s));
            lpnode->writeKey(s, l->getLastKey());
        }
    }

    /* Check fanout on rightmost internal nodes except root node */
    for (level = 0; level + 1 < _inodes.size(); level++) {
        InternalNodeType *inode = _inodes[level].second;
        NodeRef leftInodeRef = leftInodes[level];
        assert(NodeAllocatorType::isValidRef(leftInodeRef));
        InternalNodeType *leftInode = _allocator.mapInternalRef(leftInodeRef);

        InternalNodeType *pnode = _inodes[level + 1].second;
        if (inode->validSlots() < InternalNodeType::minSlots()) {
            if (leftInode->validSlots() + inode->validSlots() <
                2 * InternalNodeType::minSlots()) {
                leftInode->stealAllFromRightNode(inode);
                if (pnode->validSlots() == 1) {
                    InternalNodeType *lpnode =
                        _allocator.mapInternalRef(leftInodes[level + 1]);
                    lpnode->incValidLeaves(pnode->validLeaves());
                    pnode->setValidLeaves(0);
                }
                /* Unlink from parent node */
                pnode->remove(pnode->validSlots() - 1);
                _allocator.holdNode(_inodes[level].first, inode);
                _numInternalNodes--;
                _inodes[level] = std::make_pair(leftInodeRef, leftInode);
                if (AggrCalcT::hasAggregated()) {
                    Aggregator::recalc(*leftInode, _allocator, _aggrCalc);
                }
            } else {
                inode->stealSomeFromLeftNode(leftInode, _allocator);
                if (AggrCalcT::hasAggregated()) {
                    Aggregator::recalc(*leftInode, _allocator, _aggrCalc);
                    Aggregator::recalc(*inode, _allocator, _aggrCalc);
                }
                if (pnode->validSlots() == 1) {
                    InternalNodeType *lpnode =
                        _allocator.mapInternalRef(leftInodes[level + 1]);
                    uint32_t steal = inode->validLeaves() -
                                     pnode->validLeaves();
                    pnode->incValidLeaves(steal);
                    lpnode->decValidLeaves(steal);
                    if (AggrCalcT::hasAggregated()) {
                        Aggregator::recalc(*lpnode, _allocator, _aggrCalc);
                        Aggregator::recalc(*pnode, _allocator, _aggrCalc);
                    }
                }
            }
        }
        if (pnode->validSlots() > 0) {
            uint32_t s = pnode->validSlots() - 1;
            InternalNodeType *n =
                _allocator.mapInternalRef(pnode->getChild(s));
            pnode->writeKey(s, n->getLastKey());
            if (s > 0) {
                --s;
                n = _allocator.mapInternalRef(pnode->getChild(s));
                pnode->writeKey(s, n->getLastKey());
            }
        }
        if (level + 1 < leftInodes.size() &&
            _allocator.isValidRef(leftInodes[level + 1])) {
            InternalNodeType *lpnode =
                _allocator.mapInternalRef(leftInodes[level + 1]);
            uint32_t s = lpnode->validSlots() - 1;
            InternalNodeType *n =
                _allocator.mapInternalRef(lpnode->getChild(s));
            lpnode->writeKey(s, n->getLastKey());
        }
    }
    /* Check fanout on root node */
    assert(level < _inodes.size());
    InternalNodeType *inode = _inodes[level].second;
    assert(inode != NULL);
    assert(inode->validSlots() >= 1);
    if (inode->validSlots() == 1) {
        /* Remove top level from proposed tree since fanout is 1 */
        NodeRef iRef = _inodes[level].first;
        _inodes.pop_back();
        _allocator.holdNode(iRef, inode);
        _numInternalNodes--;
    }
    if (!_inodes.empty()) {
        assert(_numInserts == _inodes.back().second->validLeaves());
    } else {
        assert(_numInserts == _leaf.second->validLeaves());
    }
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
allocNewLeafNode(void)
{
    InternalNodeType  *inode;
    NodeRef child;

    if (AggrCalcT::hasAggregated()) {
        Aggregator::recalc(*_leaf.second, _aggrCalc);
    }
    LeafNodeTypeRefPair lPair(_allocator.allocLeafNode());
    _numLeafNodes++;

    child = lPair.first;

    unsigned int level = 0;
    for (;;) {
        if (level >= _inodes.size()) {
            InternalNodeTypeRefPair iPair(
                    _allocator.allocInternalNode(level + 1));
            inode = iPair.second;
            _numInternalNodes++;
            if (level > 0) {
                InternalNodeType *cnode = _inodes[level - 1].second;
                inode->insert(0, cnode->getLastKey(),
                              _inodes[level - 1].first);
                inode->setValidLeaves(cnode->validLeaves());
            } else {
                inode->insert(0, _leaf.second->getLastKey(), _leaf.first);
                inode->setValidLeaves(_leaf.second->validLeaves());
            }
            inode->insert(1, KeyType(), child);
            _inodes.push_back(iPair);
            break;
        }
        inode = _inodes[level].second;
        assert(inode->validSlots() > 0);
        NodeRef lcRef(inode->getLastChild());
        inode->incValidLeaves(_allocator.validLeaves(lcRef));
        inode->update(inode->validSlots() - 1,
                      level == 0 ?
                      _allocator.mapLeafRef(lcRef)->getLastKey() :
                      _allocator.mapInternalRef(lcRef)->getLastKey(),
                      lcRef);
        if (inode->validSlots() >= InternalNodeType::maxSlots()) {
            if (AggrCalcT::hasAggregated()) {
                Aggregator::recalc(*inode, _allocator, _aggrCalc);
            }
            InternalNodeTypeRefPair iPair(
                    _allocator.allocInternalNode(level + 1));
            inode = iPair.second;
            _numInternalNodes++;
            inode->insert(0, KeyType(), child);
            child = iPair.first;
            level++;
            continue;
        }
        inode->insert(inode->validSlots(), KeyType(), child);
        break;
    }
    while (level > 0) {
        assert(inode->validSlots() > 0);
        child = inode->getLastChild();
        assert(!_allocator.isLeafRef(child));
        inode = _allocator.mapInternalRef(child);
        level--;
        _inodes[level] = std::make_pair(child, inode);
    }
    _leaf = lPair;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
insert(const KeyT &key,
       const DataT &data)
{
    if (_leaf.second->validSlots() >= LeafNodeType::maxSlots())
        allocNewLeafNode();
    LeafNodeType *leaf = _leaf.second;
    leaf->insert(leaf->validSlots(), key, data);
    ++_numInserts;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
typename BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS,
                      AggrCalcT>::NodeRef
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
handover(void)
{
    NodeRef ret;

    normalize();

    if (!_inodes.empty())
        ret = _inodes.back().first;
    else
        ret = _leaf.first;

    _leaf = std::make_pair(NodeRef(),
                           static_cast<LeafNodeType *>(NULL));

    _inodes.clear();
    _numInternalNodes = 0;
    _numLeafNodes = 0;
    return ret;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
reuse(void)
{
    clear();
    _leaf = _allocator.allocLeafNode();
    ++_numLeafNodes;
    _numInserts = 0u;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
clear(void)
{
    if (!_inodes.empty()) {
        recursiveDelete(_inodes.back().first);
        _leaf = std::make_pair(NodeRef(),
                               static_cast<LeafNodeType *>(NULL));
        _inodes.clear();
    }
    if (NodeAllocatorType::isValidRef(_leaf.first)) {
        assert(_leaf.second != NULL);
        assert(_numLeafNodes == 1);
        _allocator.holdNode(_leaf.first, _leaf.second);
        --_numLeafNodes;
        _leaf = std::make_pair(NodeRef(),
                               static_cast<LeafNodeType *>(NULL));
    } else {
        assert(_leaf.second == NULL);
    }
    assert(_numLeafNodes == 0);
    assert(_numInternalNodes == 0);
}


} // namespace btree

} // namespace search

