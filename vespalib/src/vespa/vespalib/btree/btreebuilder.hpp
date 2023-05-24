// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreebuilder.h"
#include <cassert>

namespace vespalib::btree {

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
~BTreeBuilder()
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
normalize()
{
    std::vector<NodeRef> leftInodes;    // left to rightmost nodes in tree
    LeafNodeType *leftLeaf;
    NodeRef child;
    unsigned int level;
    LeafNodeType *leafNode = _leaf.data;

    if (_inodes.size() == 0) {
        if (leafNode->validSlots() == 0) {
            assert(_numLeafNodes == 1);
            assert(_numInserts == 0);
            _allocator.holdNode(_leaf.ref, _leaf.data);
            _numLeafNodes--;
            _leaf = LeafNodeTypeRefPair(NodeRef(), static_cast<LeafNodeType *>(nullptr));

        }
        if constexpr (AggrCalcT::hasAggregated()) {
            Aggregator::recalc(*leafNode, _aggrCalc);
        }
        assert(_numInserts == leafNode->validSlots());
        return;
    }

    if constexpr (AggrCalcT::hasAggregated()) {
        Aggregator::recalc(*leafNode, _aggrCalc);
    }
    /* Adjust validLeaves for rightmost nodes */
    for (level = 0; level < _inodes.size(); level++) {
        InternalNodeType *inode = _inodes[level].data;
        NodeRef lcRef(inode->get_last_child_relaxed());
        assert(NodeAllocatorType::isValidRef(lcRef));
        assert((level == 0) == _allocator.isLeafRef(lcRef));
        inode->incValidLeaves(_allocator.validLeaves(inode->get_last_child_relaxed()));
        inode->update(inode->validSlots() - 1,
                      level == 0 ?
                      _allocator.mapLeafRef(lcRef)->getLastKey() :
                      _allocator.mapInternalRef(lcRef)->getLastKey(),
                      lcRef);
        if constexpr (AggrCalcT::hasAggregated()) {
            Aggregator::recalc(*inode, _allocator, _aggrCalc);
        }
    }
    for (level = 0; level + 1 < _inodes.size(); level++) {
        leftInodes.push_back(NodeRef());
    }
    /* Build vector of left to rightmost internal nodes (except root level) */
    level = _inodes.size() - 1;
    for (;;) {
        NodeRef iRef = _inodes[level].ref;
        InternalNodeType *inode = _inodes[level].data;
        if (inode->validSlots() < 2) {
            /* Use last child of left to rightmost node on level */
            assert(level + 1 < _inodes.size());
            iRef = leftInodes[level];
            inode = _allocator.mapInternalRef(iRef);
            assert(inode != nullptr);
            assert(inode->validSlots() >= 1);
            child = inode->get_last_child_relaxed();
        } else {
            /* Use next to last child of rightmost node on level */
            child = inode->get_child_relaxed(inode->validSlots() - 2);
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
        InternalNodeType *pnode = _inodes[0].data;
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
            _allocator.holdNode(_leaf.ref, leafNode);
            _numLeafNodes--;
            _leaf = LeafNodeTypeRefPair(child, leftLeaf);
            if constexpr (AggrCalcT::hasAggregated()) {
                Aggregator::recalc(*leftLeaf, _aggrCalc);
            }
        } else {
            leafNode->stealSomeFromLeftNode(leftLeaf);
            if constexpr (AggrCalcT::hasAggregated()) {
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
                if constexpr (AggrCalcT::hasAggregated()) {
                    Aggregator::recalc(*lpnode, _allocator, _aggrCalc);
                    Aggregator::recalc(*pnode, _allocator, _aggrCalc);
                }
            }
        }
        if (pnode->validSlots() > 0) {
            uint32_t s = pnode->validSlots() - 1;
            LeafNodeType *l = _allocator.mapLeafRef(pnode->get_child_relaxed(s));
            pnode->write_key_relaxed(s, l->getLastKey());
            if (s > 0) {
                --s;
                l = _allocator.mapLeafRef(pnode->get_child_relaxed(s));
                pnode->write_key_relaxed(s, l->getLastKey());
            }
        }
        if (!leftInodes.empty() && _allocator.isValidRef(leftInodes[0])) {
            InternalNodeType *lpnode =
                _allocator.mapInternalRef(leftInodes[0]);
            uint32_t s = lpnode->validSlots() - 1;
            LeafNodeType *l = _allocator.mapLeafRef(lpnode->get_child_relaxed(s));
            lpnode->write_key_relaxed(s, l->getLastKey());
        }
    }

    /* Check fanout on rightmost internal nodes except root node */
    for (level = 0; level + 1 < _inodes.size(); level++) {
        InternalNodeType *inode = _inodes[level].data;
        NodeRef leftInodeRef = leftInodes[level];
        assert(NodeAllocatorType::isValidRef(leftInodeRef));
        InternalNodeType *leftInode = _allocator.mapInternalRef(leftInodeRef);

        InternalNodeType *pnode = _inodes[level + 1].data;
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
                _allocator.holdNode(_inodes[level].ref, inode);
                _numInternalNodes--;
                _inodes[level] = InternalNodeTypeRefPair(leftInodeRef, leftInode);
                if constexpr (AggrCalcT::hasAggregated()) {
                    Aggregator::recalc(*leftInode, _allocator, _aggrCalc);
                }
            } else {
                inode->stealSomeFromLeftNode(leftInode, _allocator);
                if constexpr (AggrCalcT::hasAggregated()) {
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
                    if constexpr (AggrCalcT::hasAggregated()) {
                        Aggregator::recalc(*lpnode, _allocator, _aggrCalc);
                        Aggregator::recalc(*pnode, _allocator, _aggrCalc);
                    }
                }
            }
        }
        if (pnode->validSlots() > 0) {
            uint32_t s = pnode->validSlots() - 1;
            InternalNodeType *n =
                _allocator.mapInternalRef(pnode->get_child_relaxed(s));
            pnode->write_key_relaxed(s, n->getLastKey());
            if (s > 0) {
                --s;
                n = _allocator.mapInternalRef(pnode->get_child_relaxed(s));
                pnode->write_key_relaxed(s, n->getLastKey());
            }
        }
        if (level + 1 < leftInodes.size() &&
            _allocator.isValidRef(leftInodes[level + 1])) {
            InternalNodeType *lpnode =
                _allocator.mapInternalRef(leftInodes[level + 1]);
            uint32_t s = lpnode->validSlots() - 1;
            InternalNodeType *n =
                _allocator.mapInternalRef(lpnode->get_child_relaxed(s));
            lpnode->write_key_relaxed(s, n->getLastKey());
        }
    }
    /* Check fanout on root node */
    assert(level < _inodes.size());
    InternalNodeType *inode = _inodes[level].data;
    assert(inode != nullptr);
    assert(inode->validSlots() >= 1);
    if (inode->validSlots() == 1) {
        /* Remove top level from proposed tree since fanout is 1 */
        NodeRef iRef = _inodes[level].ref;
        _inodes.pop_back();
        _allocator.holdNode(iRef, inode);
        _numInternalNodes--;
    }
    if (!_inodes.empty()) {
        assert(_numInserts == _inodes.back().data->validLeaves());
    } else {
        assert(_numInserts == _leaf.data->validLeaves());
    }
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
allocNewLeafNode()
{
    InternalNodeType  *inode;
    NodeRef child;

    if constexpr (AggrCalcT::hasAggregated()) {
        Aggregator::recalc(*_leaf.data, _aggrCalc);
    }
    LeafNodeTypeRefPair lPair(_allocator.allocLeafNode());
    _numLeafNodes++;

    child = lPair.ref;

    unsigned int level = 0;
    for (;;) {
        if (level >= _inodes.size()) {
            InternalNodeTypeRefPair iPair(
                    _allocator.allocInternalNode(level + 1));
            inode = iPair.data;
            _numInternalNodes++;
            if (level > 0) {
                InternalNodeType *cnode = _inodes[level - 1].data;
                inode->insert(0, cnode->getLastKey(),
                              _inodes[level - 1].ref);
                inode->setValidLeaves(cnode->validLeaves());
            } else {
                inode->insert(0, _leaf.data->getLastKey(), _leaf.ref);
                inode->setValidLeaves(_leaf.data->validLeaves());
            }
            inode->insert(1, KeyType(), child);
            _inodes.push_back(iPair);
            break;
        }
        inode = _inodes[level].data;
        assert(inode->validSlots() > 0);
        NodeRef lcRef(inode->get_last_child_relaxed());
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
            inode = iPair.data;
            _numInternalNodes++;
            inode->insert(0, KeyType(), child);
            child = iPair.ref;
            level++;
            continue;
        }
        inode->insert(inode->validSlots(), KeyType(), child);
        break;
    }
    while (level > 0) {
        assert(inode->validSlots() > 0);
        child = inode->get_last_child_relaxed();
        assert(!_allocator.isLeafRef(child));
        inode = _allocator.mapInternalRef(child);
        level--;
        _inodes[level] = InternalNodeTypeRefPair(child, inode);
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
    if (_leaf.data->validSlots() >= LeafNodeType::maxSlots())
        allocNewLeafNode();
    LeafNodeType *leaf = _leaf.data;
    leaf->insert(leaf->validSlots(), key, data);
    ++_numInserts;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
typename BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS,
                      AggrCalcT>::NodeRef
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
handover()
{
    NodeRef ret;

    normalize();

    if (!_inodes.empty()) {
        ret = _inodes.back().ref;
    } else {
        ret = _leaf.ref;
    }

    _leaf = LeafNodeTypeRefPair(NodeRef(), static_cast<LeafNodeType *>(nullptr));

    _inodes.clear();
    _numInternalNodes = 0;
    _numLeafNodes = 0;
    return ret;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeBuilder<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
reuse()
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
clear()
{
    if (!_inodes.empty()) {
        recursiveDelete(_inodes.back().ref);
        _leaf = LeafNodeTypeRefPair(NodeRef(), static_cast<LeafNodeType *>(nullptr));
        _inodes.clear();
    }
    if (NodeAllocatorType::isValidRef(_leaf.ref)) {
        assert(_leaf.data != nullptr);
        assert(_numLeafNodes == 1);
        _allocator.holdNode(_leaf.ref, _leaf.data);
        --_numLeafNodes;
        _leaf = LeafNodeTypeRefPair(NodeRef(), static_cast<LeafNodeType *>(nullptr));
    } else {
        assert(_leaf.data == nullptr);
    }
    assert(_numLeafNodes == 0);
    assert(_numInternalNodes == 0);
}

}
