// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreeaggregator.h"

namespace search
{

namespace btree
{

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
recalc(LeafNodeType &node, const AggrCalcT &aggrCalc)
{
    AggrT a;
    for (uint32_t i = 0, ie = node.validSlots(); i < ie; ++i) {
        aggrCalc.add(a, aggrCalc.getVal(node.getData(i)));
    }
    node.getAggregated() = a;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
recalc(InternalNodeType &node,
       const NodeAllocatorType &allocator,
       const AggrCalcT &aggrCalc)
{
    AggrT a;
    for (uint32_t i = 0, ie = node.validSlots(); i < ie; ++i) {
        const BTreeNode::Ref childRef = node.getChild(i);
        const AggrT &ca(allocator.getAggregated(childRef));
        aggrCalc.add(a, ca);
    }
    node.getAggregated() = a;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
typename BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS,
                         AggrCalcT>::AggregatedType
BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
recalc(LeafNodeType &node,
       LeafNodeType &splitNode,
       const AggrCalcT &aggrCalc)
{
    AggrT a;
    recalc(node, aggrCalc);
    recalc(splitNode, aggrCalc);
    a = node.getAggregated();
    aggrCalc.add(a, splitNode.getAggregated());
    return a;
}
 
          
template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
typename BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS,
                         AggrCalcT>::AggregatedType
BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
    recalc(InternalNodeType &node,
           InternalNodeType &splitNode,
           const NodeAllocatorType &allocator,
           const AggrCalcT &aggrCalc)
{
    AggrT a;
    recalc(node, allocator, aggrCalc);
    recalc(splitNode, allocator, aggrCalc);
    a = node.getAggregated();
    aggrCalc.add(a, splitNode.getAggregated());
    return a;
}


} // namespace search::btree
} // namespace search

