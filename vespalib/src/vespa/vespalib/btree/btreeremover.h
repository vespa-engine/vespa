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
          typename AggrT,
          size_t INTERNAL_SLOTS,
          size_t LEAF_SLOTS,
          class AggrCalcT>
class BTreeRemoverBase
{
public:
    using NodeAllocatorType = BTreeNodeAllocator<KeyT, DataT, AggrT,
                                                 INTERNAL_SLOTS,
                                                 LEAF_SLOTS>;
    using Aggregator = BTreeAggregator<KeyT, DataT, AggrT,
                                       INTERNAL_SLOTS,
                                       LEAF_SLOTS,
                                       AggrCalcT>;
    using InternalNodeType = BTreeInternalNode<KeyT, AggrT, INTERNAL_SLOTS>;
    using LeafNodeType = BTreeLeafNode<KeyT, DataT, AggrT, LEAF_SLOTS>;
    using InternalNodeTypeRefPair = typename InternalNodeType::RefPair;
    using LeafNodeTypeRefPair = typename LeafNodeType::RefPair;

    template <typename NodeType, typename NodeTypeRefPair,
              class Iterator>
    static void
    steal(InternalNodeType *pNode,
          BTreeNode::Ref sNodeRef,
          NodeType *sNode,
          uint32_t idx,
          NodeAllocatorType &allocator,
          const AggrCalcT &aggrCalc,
          Iterator &itr,
          uint32_t level);
};

template <typename KeyT,
          typename DataT,
          typename AggrT,
          typename CompareT = std::less<KeyT>,
          typename TraitsT = BTreeDefaultTraits,
          class AggrCalcT = NoAggrCalc>
class BTreeRemover : public BTreeRemoverBase<KeyT, DataT, AggrT,
                                             TraitsT::INTERNAL_SLOTS,
                                             TraitsT::LEAF_SLOTS,
                                             AggrCalcT>

{
public:
    using ParentType = BTreeRemoverBase<KeyT, DataT, AggrT,
                                        TraitsT::INTERNAL_SLOTS,
                                        TraitsT::LEAF_SLOTS,
                                        AggrCalcT>;
    using NodeAllocatorType = BTreeNodeAllocator<KeyT, DataT, AggrT,
                                                 TraitsT::INTERNAL_SLOTS,
                                                 TraitsT::LEAF_SLOTS>;
    using Aggregator = BTreeAggregator<KeyT, DataT, AggrT,
                                       TraitsT::INTERNAL_SLOTS,
                                       TraitsT::LEAF_SLOTS,
                                       AggrCalcT>;
    using InternalNodeType = BTreeInternalNode<KeyT, AggrT, TraitsT::INTERNAL_SLOTS>;
    using LeafNodeType = BTreeLeafNode<KeyT, DataT, AggrT, TraitsT::LEAF_SLOTS>;
    using KeyType = KeyT;
    using DataType = DataT;
    using InternalNodeTypeRefPair = typename InternalNodeType::RefPair;
    using LeafNodeTypeRefPair = typename LeafNodeType::RefPair;
    using Iterator = BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>;

    static void
    remove(BTreeNode::Ref &root, Iterator &itr, const AggrCalcT &aggrCalc);
};

extern template class BTreeRemover<uint32_t, uint32_t, NoAggregated>;
extern template class BTreeRemover<uint32_t, BTreeNoLeafData, NoAggregated>;
extern template class BTreeRemover<uint32_t, int32_t, MinMaxAggregated,
                                   std::less<uint32_t>, BTreeDefaultTraits, MinMaxAggrCalc>;

}
