// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreeinserter.h"
#include "btreerootbase.hpp"
#include "btreeiterator.hpp"

namespace vespalib::btree {

namespace {

template <typename NodeType, typename NodeAllocatorType>
void
considerThawNode(NodeType *&node, BTreeNode::Ref &ref, NodeAllocatorType &allocator)
{
    if (node->getFrozen()) {
        auto thawed = allocator.thawNode(ref, node);
        ref = thawed.ref;
        node = thawed.data;
    }
}

}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
void
BTreeInserter<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::rebalanceLeafEntries(LeafNodeType *leafNode, Iterator &itr, AggrCalcT aggrCalc)
{
    (void)aggrCalc;
    NodeAllocatorType &allocator(itr.getAllocator());
    auto &pathElem = itr.getPath(0);
    InternalNodeType *parentNode = pathElem.getWNode();
    uint32_t parentIdx = pathElem.getIdx();
    BTreeNode::Ref leafRef = parentNode->get_child_relaxed(parentIdx);
    BTreeNode::Ref leftRef = BTreeNode::Ref();
    LeafNodeType *leftNode = nullptr;
    BTreeNode::Ref rightRef = BTreeNode::Ref();
    LeafNodeType *rightNode = nullptr;
    if (parentIdx > 0) {
        leftRef = parentNode->get_child_relaxed(parentIdx - 1);
        leftNode = allocator.mapLeafRef(leftRef);
    }
    if (parentIdx + 1 < parentNode->validSlots()) {
        rightRef = parentNode->get_child_relaxed(parentIdx + 1);
        rightNode = allocator.mapLeafRef(rightRef);
    }
    if (leftNode != nullptr && leftNode->validSlots() < LeafNodeType::maxSlots() &&
        (rightNode == nullptr || leftNode->validSlots() < rightNode->validSlots())) {
        considerThawNode(leftNode, leftRef, allocator);
        uint32_t oldLeftValid = leftNode->validSlots();
        if (itr.getLeafNodeIdx() == 0 && (oldLeftValid + 1 == LeafNodeType::maxSlots())) {
            parentNode->update(parentIdx - 1, leftNode->getLastKey(), leftRef);
            itr.adjustGivenNoEntriesToLeftLeafNode();
        } else {
            leftNode->stealSomeFromRightNode(leafNode, allocator);
            uint32_t given = leftNode->validSlots() - oldLeftValid;
            parentNode->update(parentIdx, leafNode->getLastKey(), leafRef);
            parentNode->update(parentIdx - 1, leftNode->getLastKey(), leftRef);
            if constexpr (AggrCalcT::hasAggregated()) {
                Aggregator::recalc(*leftNode, allocator, aggrCalc);
                Aggregator::recalc(*leafNode, allocator, aggrCalc);
            }
            itr.adjustGivenEntriesToLeftLeafNode(given);
        }
    } else if (rightNode != nullptr && rightNode->validSlots() < LeafNodeType::maxSlots()) {
        considerThawNode(rightNode, rightRef, allocator);
        rightNode->stealSomeFromLeftNode(leafNode, allocator);
        parentNode->update(parentIdx, leafNode->getLastKey(), leafRef);
        parentNode->update(parentIdx + 1, rightNode->getLastKey(), rightRef);
        if constexpr (AggrCalcT::hasAggregated()) {
            Aggregator::recalc(*rightNode, allocator, aggrCalc);
            Aggregator::recalc(*leafNode, allocator, aggrCalc);
        }
        itr.adjustGivenEntriesToRightLeafNode();
    }
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
void
BTreeInserter<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::
insert(BTreeNode::Ref &root,
       Iterator &itr,
       const KeyType &key, const DataType &data,
       const AggrCalcT &aggrCalc)
{
    if (!NodeAllocatorType::isValidRef(root)) {
        root = itr.insertFirst(key, data, aggrCalc);
        return;
    }
    NodeAllocatorType &allocator(itr.getAllocator());
    bool inRange = itr.valid();
    if (!inRange) {
        --itr;
    }
    root = itr.thaw(root);
    LeafNodeType *lnode = itr.getLeafNode();
    if (lnode->isFull() && itr.getPathSize() > 0) {
        rebalanceLeafEntries(lnode, itr, aggrCalc);
        lnode = itr.getLeafNode();
    }
    uint32_t idx = itr.getLeafNodeIdx() + (inRange ? 0 : 1);
    BTreeNode::Ref splitNodeRef;
    const KeyT *splitLastKey = nullptr;
    bool inRightSplit = false;
    AggrT oldca(AggrCalcT::hasAggregated() ? lnode->getAggregated() : AggrT());
    AggrT ca;
    if (lnode->isFull()) {
        LeafNodeTypeRefPair splitNode = allocator.allocLeafNode();
        lnode->splitInsert(splitNode.data, idx, key, data);
        if constexpr (AggrCalcT::hasAggregated()) {
            ca = Aggregator::recalc(*lnode, *splitNode.data, aggrCalc);
        }
        splitNodeRef = splitNode.ref; // to signal that a split occured
        splitLastKey = &splitNode.data->getLastKey();
        inRightSplit = itr.setLeafNodeIdx(idx, splitNode.data);
    } else {
        lnode->insert(idx, key, data);
        itr.setLeafNodeIdx(idx);
        if constexpr (AggrCalcT::hasAggregated()) {
            if constexpr (AggrCalcT::aggregate_over_values()) {
                aggrCalc.add(lnode->getAggregated(), aggrCalc.getVal(data));
            } else {
                aggrCalc.add(lnode->getAggregated(), aggrCalc.getVal(key));
            }
            ca = lnode->getAggregated();
        }
    }
    const KeyT *lastKey = &lnode->getLastKey();
    uint32_t level = 0;
    uint32_t levels = itr.getPathSize();
    for (; level < levels; ++level) {
        typename Iterator::PathElement &pe = itr.getPath(level);
        InternalNodeType *node(pe.getWNode());
        idx = pe.getIdx();
        AggrT olda(AggrCalcT::hasAggregated() ?
                   node->getAggregated() : AggrT());
        BTreeNode::Ref subNode = node->get_child_relaxed(idx);
        node->update(idx, *lastKey, subNode);
        node->incValidLeaves(1);
        if (NodeAllocatorType::isValidRef(splitNodeRef)) {
            idx++; // the extra node is inserted in the next slot
            if (node->isFull()) {
                InternalNodeTypeRefPair splitNode =
                    allocator.allocInternalNode(level + 1);
                node->splitInsert(splitNode.data, idx,
                                  *splitLastKey, splitNodeRef, allocator);
                inRightSplit = pe.adjustSplit(inRightSplit, splitNode.data);
                if constexpr (AggrCalcT::hasAggregated()) {
                    ca = Aggregator::recalc(*node, *splitNode.data,
                                            allocator, aggrCalc);
                }
                splitNodeRef = splitNode.ref;
                splitLastKey = &splitNode.data->getLastKey();
            } else {
                node->insert(idx, *splitLastKey, splitNodeRef);
                pe.adjustSplit(inRightSplit);
                inRightSplit = false;
                if constexpr (AggrCalcT::hasAggregated()) {
                    aggrCalc.add(node->getAggregated(), oldca, ca);
                    ca = node->getAggregated();
                }
                splitNodeRef = BTreeNode::Ref();
                splitLastKey = nullptr;
            }
        } else {
            if constexpr (AggrCalcT::hasAggregated()) {
                aggrCalc.add(node->getAggregated(), oldca, ca);
                ca = node->getAggregated();
            }
        }
        if constexpr (AggrCalcT::hasAggregated()) {
            oldca = olda;
        }
        lastKey = &node->getLastKey();
    }
    if (NodeAllocatorType::isValidRef(splitNodeRef)) {
        root = itr.addLevel(root, splitNodeRef, inRightSplit, aggrCalc);
    }
}

}
