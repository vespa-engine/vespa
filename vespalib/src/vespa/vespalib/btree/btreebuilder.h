// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenode.h"
#include "btreerootbase.h"
#include "btreenodeallocator.h"
#include "noaggrcalc.h"
#include "minmaxaggrcalc.h"
#include "btreeaggregator.h"

namespace vespalib::btree {

template <typename KeyT,
          typename DataT,
          typename AggrT,
          size_t INTERNAL_SLOTS,
          size_t LEAF_SLOTS,
          class AggrCalcT = NoAggrCalc>
class BTreeBuilder
{
public:
    using NodeAllocatorType = BTreeNodeAllocator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>;
    using BTreeRootBaseType = typename NodeAllocatorType::BTreeRootBaseType;
    using InternalNodeType = typename NodeAllocatorType::InternalNodeType;
    using LeafNodeType = typename NodeAllocatorType::LeafNodeType;
    using Aggregator = BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>;
private:
    using KeyType = KeyT;
    using DataType = DataT;
    using InternalNodeTypeRefPair =  typename InternalNodeType::RefPair;
    using LeafNodeTypeRefPair = typename LeafNodeType::RefPair;
    using NodeRef = BTreeNode::Ref;

    NodeAllocatorType &_allocator;
    int _numInternalNodes;
    int _numLeafNodes;
    uint32_t _numInserts;
    std::vector<InternalNodeTypeRefPair> _inodes;
    LeafNodeTypeRefPair _leaf;
    AggrCalcT _defaultAggrCalc;
    const AggrCalcT &_aggrCalc;

    void normalize();
    void allocNewLeafNode();
    InternalNodeType *createInternalNode();
public:
    BTreeBuilder(NodeAllocatorType &allocator);
    BTreeBuilder(NodeAllocatorType &allocator, const AggrCalcT &aggrCalc);
    ~BTreeBuilder();

    void recursiveDelete(NodeRef node);
    void insert(const KeyT &key, const DataT &data);
    NodeRef handover();
    void reuse();
    void clear();
};

extern template class BTreeBuilder<uint32_t, uint32_t, NoAggregated,
                                   BTreeDefaultTraits::INTERNAL_SLOTS,
                                   BTreeDefaultTraits::LEAF_SLOTS>;
extern template class BTreeBuilder<uint32_t, BTreeNoLeafData, NoAggregated,
                                   BTreeDefaultTraits::INTERNAL_SLOTS,
                                   BTreeDefaultTraits::LEAF_SLOTS>;
extern template class BTreeBuilder<uint32_t, int32_t, MinMaxAggregated,
                                   BTreeDefaultTraits::INTERNAL_SLOTS,
                                   BTreeDefaultTraits::LEAF_SLOTS,
                                   MinMaxAggrCalc>;

}
