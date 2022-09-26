// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreeremover.h"
#include "btreerootbase.hpp"

namespace vespalib::btree {

template <typename KeyT, typename DataT, typename AggrT, size_t INTERNAL_SLOTS,
          size_t LEAF_SLOTS, class AggrCalcT>
template <typename NodeType, typename NodeTypeRefPair, class Iterator>
void
BTreeRemoverBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
steal(InternalNodeType *pNode,
      BTreeNode::Ref sNodeRef,
      NodeType * sNode, uint32_t idx, NodeAllocatorType &allocator,
      [[maybe_unused]] const AggrCalcT &aggrCalc,
      Iterator &itr,
      uint32_t level)
{
    BTreeNode::Ref leftVictimRef = BTreeNode::Ref();
    NodeType * leftVictim = nullptr;
    BTreeNode::Ref rightVictimRef = BTreeNode::Ref();
    NodeType * rightVictim = nullptr;
    if (idx > 0) {
        leftVictimRef = pNode->get_child_relaxed(idx - 1);
        leftVictim = allocator.template mapRef<NodeType>(leftVictimRef);
    }
    if (idx + 1 < pNode->validSlots()) {
        rightVictimRef = pNode->get_child_relaxed(idx + 1);
        rightVictim = allocator.template mapRef<NodeType>(rightVictimRef);
    }
    if (leftVictim != nullptr &&
        leftVictim->validSlots() + sNode->validSlots() <=
        NodeType::maxSlots())
    {
        uint32_t stolen = leftVictim->validSlots();
        sNode->stealAllFromLeftNode(leftVictim);
        pNode->update(idx, sNode->getLastKey(), sNodeRef);
        pNode->remove(idx - 1);
        allocator.holdNode(leftVictimRef, leftVictim);
        itr.adjustSteal(level, true, stolen);
    } else if (rightVictim != nullptr &&
               rightVictim->validSlots() + sNode->validSlots() <=
               NodeType::maxSlots())
    {
        sNode->stealAllFromRightNode(rightVictim);
        pNode->update(idx, sNode->getLastKey(), sNodeRef);
        pNode->remove(idx + 1);
        allocator.holdNode(rightVictimRef, rightVictim);
    } else if (leftVictim != nullptr &&
               (rightVictim == nullptr ||
                leftVictim->validSlots() > rightVictim->validSlots()))
    {
        if (leftVictim->getFrozen()) {
            NodeTypeRefPair thawed =
                allocator.thawNode(leftVictimRef, leftVictim);
            leftVictimRef = thawed.ref;
            leftVictim = thawed.data;
        }
        uint32_t oldLeftValid = leftVictim->validSlots();
        sNode->stealSomeFromLeftNode(leftVictim, allocator);
        uint32_t stolen = oldLeftValid - leftVictim->validSlots();
        pNode->update(idx, sNode->getLastKey(), sNodeRef);
        pNode->update(idx - 1, leftVictim->getLastKey(), leftVictimRef);
        if constexpr (AggrCalcT::hasAggregated()) {
            Aggregator::recalc(*leftVictim, allocator, aggrCalc);
        }
        itr.adjustSteal(level, false, stolen);
    } else if (rightVictim != nullptr) {
        if (rightVictim->getFrozen()) {
           NodeTypeRefPair thawed =
               allocator.thawNode(rightVictimRef, rightVictim);
           rightVictimRef = thawed.ref;
           rightVictim = thawed.data;
        }
        sNode->stealSomeFromRightNode(rightVictim, allocator);
        pNode->update(idx, sNode->getLastKey(), sNodeRef);
        pNode->update(idx + 1, rightVictim->getLastKey(), rightVictimRef);
        if constexpr (AggrCalcT::hasAggregated()) {
            Aggregator::recalc(*rightVictim, allocator, aggrCalc);
        }
    }
    if constexpr (AggrCalcT::hasAggregated()) {
        Aggregator::recalc(*sNode, allocator, aggrCalc);
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
void
BTreeRemover<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
remove(BTreeNode::Ref &root,
       Iterator &itr,
       const AggrCalcT &aggrCalc)
{
    assert(itr.valid());
    root = itr.thaw(root);

    uint32_t idx = itr.getLeafNodeIdx();
    LeafNodeType * lnode = itr.getLeafNode();
    if (lnode->validSlots() == 1u) {
        itr.removeLast(root);
        root = BTreeNode::Ref();
        return;
    }
    NodeAllocatorType &allocator(itr.getAllocator());
    AggrT oldca(AggrCalcT::hasAggregated() ? lnode->getAggregated() : AggrT());
    AggrT ca;
    if constexpr (AggrCalcT::hasAggregated()) {
        bool need_aggregation_recalc;
        if constexpr (AggrCalcT::aggregate_over_values()) {
            need_aggregation_recalc = aggrCalc.remove(lnode->getAggregated(), aggrCalc.getVal(lnode->getData(idx)));
        } else {
            need_aggregation_recalc = aggrCalc.remove(lnode->getAggregated(), aggrCalc.getVal(lnode->getKey(idx)));
        }
        lnode->remove(idx);
        if (need_aggregation_recalc) {
            Aggregator::recalc(*lnode, aggrCalc);
        }
    } else {
        lnode->remove(idx);
    }
    if constexpr (AggrCalcT::hasAggregated()) {
        ca = lnode->getAggregated();
    }
    bool steppedBack = idx >= lnode->validSlots();
    if (steppedBack) {
        itr.setLeafNodeIdx(itr.getLeafNodeIdx() - 1);
        --idx;
    }
    uint32_t level = 0;
    uint32_t levels = itr.getPathSize();
    InternalNodeType *node = nullptr;
    for (; level < levels; ++level) {
        typename Iterator::PathElement &pe = itr.getPath(level);
        node = pe.getWNode();
        idx = pe.getIdx();
        AggrT olda(AggrCalcT::hasAggregated() ?
                   node->getAggregated() : AggrT());
        BTreeNode::Ref subNode = node->get_child_relaxed(idx);
        node->update(idx, allocator.getLastKey(subNode), subNode);
        node->decValidLeaves(1);
        if (level == 0) {
            LeafNodeType * sNode = allocator.mapLeafRef(subNode);
            assert(sNode == lnode);
            if (!sNode->isAtLeastHalfFull()) {
                // too few elements in sub node, steal from left or
                // right sibling
                ParentType::template steal<LeafNodeType,
                    LeafNodeTypeRefPair>
                    (node, subNode, sNode, idx, allocator, aggrCalc,
                     itr, level);
            }
        } else {
            InternalNodeType * sNode = allocator.mapInternalRef(subNode);
            if (!sNode->isAtLeastHalfFull()) {
                // too few elements in sub node, steal from left or
                // right sibling
                ParentType::template steal<InternalNodeType,
                    InternalNodeTypeRefPair>
                    (node, subNode, sNode, idx, allocator, aggrCalc,
                     itr, level);
            }
        }
        if constexpr (AggrCalcT::hasAggregated()) {
            if (aggrCalc.remove(node->getAggregated(), oldca, ca)) {
                Aggregator::recalc(*node, allocator, aggrCalc);
            }
            ca = node->getAggregated();
            oldca = olda;
        }
    }
    if (level > 0 && node->validSlots() == 1) {
        root = itr.removeLevel(root, node);
    }
    if (steppedBack)
        ++itr;
}

}
