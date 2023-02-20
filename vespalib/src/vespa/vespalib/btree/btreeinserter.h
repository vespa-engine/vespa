// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenode.h"
#include "btreenodeallocator.h"
#include "btreerootbase.h"
#include "btreeaggregator.h"
#include "noaggrcalc.h"
#include "minmaxaggrcalc.h"
#include "btreeiterator.h"

namespace vespalib::btree {

template <typename KeyT,
          typename DataT,
          typename AggrT = NoAggregated,
          typename CompareT = std::less<KeyT>,
          typename TraitsT = BTreeDefaultTraits,
          class AggrCalcT = NoAggrCalc>
class BTreeInserter
{
public:
    using NodeAllocatorType = BTreeNodeAllocator<KeyT, DataT, AggrT,
                                                 TraitsT::INTERNAL_SLOTS,
                                                 TraitsT::LEAF_SLOTS>;
    using Aggregator = BTreeAggregator<KeyT, DataT, AggrT,
                                       TraitsT::INTERNAL_SLOTS,
                                       TraitsT::LEAF_SLOTS,
                                       AggrCalcT>;
    using Iterator = BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>;
    using InternalNodeType = BTreeInternalNode<KeyT, AggrT, TraitsT::INTERNAL_SLOTS>;
    using LeafNodeType = BTreeLeafNode<KeyT, DataT, AggrT, TraitsT::LEAF_SLOTS>;
    using KeyType = KeyT;
    using DataType = DataT;
    using InternalNodeTypeRefPair = typename InternalNodeType::RefPair;
    using LeafNodeTypeRefPair = typename LeafNodeType::RefPair;
    using Inserter = BTreeInserter<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>;

private:
    static void rebalanceLeafEntries(LeafNodeType *leafNode, Iterator &itr, AggrCalcT aggrCalc);

public:
    static void
    insert(BTreeNode::Ref &root, Iterator &itr, const KeyType &key, const DataType &data, const AggrCalcT &aggrCalc);
};

extern template class BTreeInserter<uint32_t, uint32_t, NoAggregated>;
extern template class BTreeInserter<uint32_t, BTreeNoLeafData, NoAggregated>;
extern template class BTreeInserter<uint32_t, int32_t, MinMaxAggregated,
                                    std::less<uint32_t>, BTreeDefaultTraits, MinMaxAggrCalc>;

}
