// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreeinserter.h"
#include "btreerootbase.hpp"
#include "btreeiterator.hpp"
#include <vespa/vespalib/stllike/asciistream.h>

namespace search {
namespace btree {


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
    uint32_t idx = itr.getLeafNodeIdx() + (inRange ? 0 : 1);
    LeafNodeType * lnode = itr.getLeafNode();
    BTreeNode::Ref splitNodeRef;
    const KeyT *splitLastKey = nullptr;
    bool inRightSplit = false;
    AggrT oldca(AggrCalcT::hasAggregated() ? lnode->getAggregated() : AggrT());
    AggrT ca;
    if (lnode->isFull()) {
        LeafNodeTypeRefPair splitNode = allocator.allocLeafNode();
        lnode->splitInsert(splitNode.data, idx, key, data);
        if (AggrCalcT::hasAggregated()) {
            ca = Aggregator::recalc(*lnode, *splitNode.data, aggrCalc);
        }
        splitNodeRef = splitNode.ref; // to signal that a split occured
        splitLastKey = &splitNode.data->getLastKey();
        inRightSplit = itr.setLeafNodeIdx(idx, splitNode.data);
    } else {
        lnode->insert(idx, key, data);
        itr.setLeafNodeIdx(idx);
        if (AggrCalcT::hasAggregated()) {
            aggrCalc.add(lnode->getAggregated(), aggrCalc.getVal(data));
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
        BTreeNode::Ref subNode = node->getChild(idx);
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
                if (AggrCalcT::hasAggregated()) {
                    ca = Aggregator::recalc(*node, *splitNode.data,
                                            allocator, aggrCalc);
                }
                splitNodeRef = splitNode.ref;
                splitLastKey = &splitNode.data->getLastKey();
            } else {
                node->insert(idx, *splitLastKey, splitNodeRef);
                pe.adjustSplit(inRightSplit);
                inRightSplit = false;
                if (AggrCalcT::hasAggregated()) {
                    aggrCalc.add(node->getAggregated(), oldca, ca);
                    ca = node->getAggregated();
                }
                splitNodeRef = BTreeNode::Ref();
                splitLastKey = nullptr;
            }
        } else {
            if (AggrCalcT::hasAggregated()) {
                aggrCalc.add(node->getAggregated(), oldca, ca);
                ca = node->getAggregated();
            }
        }
        if (AggrCalcT::hasAggregated()) {
            oldca = olda;
        }
        lastKey = &node->getLastKey();
    }
    if (NodeAllocatorType::isValidRef(splitNodeRef)) {
        root = itr.addLevel(root, splitNodeRef, inRightSplit, aggrCalc);
    }
}


} // namespace search::btree
} // namespace search

