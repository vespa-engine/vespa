// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreeaggregator.h"

namespace vespalib::btree {

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
AggrT
BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::aggregate(const LeafNodeType &node, AggrCalcT aggrCalc)
{
    AggrT a;
    for (uint32_t i = 0, ie = node.validSlots(); i < ie; ++i) {
        if constexpr (AggrCalcT::aggregate_over_values()) {
            aggrCalc.add(a, aggrCalc.getVal(node.getData(i)));
        } else {
            aggrCalc.add(a, aggrCalc.getVal(node.getKey(i)));
        }
    }
    return a;
}

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
AggrT
BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::aggregate(const InternalNodeType &node, const NodeAllocatorType &allocator, AggrCalcT aggrCalc)
{
    AggrT a;
    for (uint32_t i = 0, ie = node.validSlots(); i < ie; ++i) {
        const BTreeNode::Ref childRef = node.getChild(i);
        const AggrT &ca(allocator.getAggregated(childRef));
        aggrCalc.add(a, ca);
    }
    return a;
}

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
recalc(LeafNodeType &node, const AggrCalcT &aggrCalc)
{
    node.getAggregated() = aggregate(node, aggrCalc);
}

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS, class AggrCalcT>
void
BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>::
recalc(InternalNodeType &node,
       const NodeAllocatorType &allocator,
       const AggrCalcT &aggrCalc)
{
    node.getAggregated() = aggregate(node, allocator, aggrCalc);
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

}
