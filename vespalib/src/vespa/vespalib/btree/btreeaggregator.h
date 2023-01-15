// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenode.h"
#include "btreenodeallocator.h"
#include "btreetraits.h"
#include "noaggrcalc.h"

namespace vespalib::btree {

template <typename KeyT,
          typename DataT,
          typename AggrT = NoAggregated,
          size_t INTERNAL_SLOTS = BTreeDefaultTraits::INTERNAL_SLOTS,
          size_t LEAF_SLOTS = BTreeDefaultTraits::LEAF_SLOTS,
          class AggrCalcT = NoAggrCalc>
class BTreeAggregator
{
public:
    using NodeAllocatorType = BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>;
    using InternalNodeType = BTreeInternalNode<KeyT, AggrT, INTERNAL_SLOTS>;
    using LeafNodeType = BTreeLeafNode<KeyT, DataT, AggrT, LEAF_SLOTS>;
    using AggregatedType = AggrT;

    static AggrT aggregate(const LeafNodeType &node, AggrCalcT aggrCalc);
    static AggrT aggregate(const InternalNodeType &node, const NodeAllocatorType &allocator, AggrCalcT aggrCalc);

    static void recalc(LeafNodeType &node, const AggrCalcT &aggrCalc);

    static void recalc(LeafNodeType &node, const NodeAllocatorType &, const AggrCalcT &aggrCalc) {
        recalc(node, aggrCalc);
    }

    static void recalc(InternalNodeType &node, const NodeAllocatorType &allocator, const AggrCalcT &aggrCalc);
    static AggregatedType recalc(LeafNodeType &node, LeafNodeType &splitNode, const AggrCalcT &aggrCalc);

    static AggregatedType recalc(InternalNodeType &node, InternalNodeType &splitNode,
                                 const NodeAllocatorType &allocator, const AggrCalcT &aggrCalc);
};

}
