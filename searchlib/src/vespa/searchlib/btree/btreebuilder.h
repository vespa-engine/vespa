// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenode.h"
#include "btreerootbase.h"
#include "btreenodeallocator.h"
#include "noaggrcalc.h"
#include "minmaxaggrcalc.h"
#include "btreeaggregator.h"

namespace search
{

namespace btree
{

template <typename KeyT,
          typename DataT,
          typename AggrT,
          size_t INTERNAL_SLOTS,
          size_t LEAF_SLOTS,
          class AggrCalcT = NoAggrCalc>
class BTreeBuilder
{
public:
    typedef BTreeNodeAllocator<KeyT, DataT, AggrT,
                               INTERNAL_SLOTS, LEAF_SLOTS> NodeAllocatorType;
    typedef typename NodeAllocatorType::BTreeRootBaseType BTreeRootBaseType;
    typedef typename NodeAllocatorType::InternalNodeType InternalNodeType;
    typedef typename NodeAllocatorType::LeafNodeType LeafNodeType;
    typedef BTreeAggregator<KeyT, DataT, AggrT,
                            INTERNAL_SLOTS,
                            LEAF_SLOTS,
                            AggrCalcT> Aggregator;
private:
    typedef KeyT KeyType;
    typedef DataT DataType;
    typedef typename InternalNodeType::RefPair InternalNodeTypeRefPair;
    typedef typename LeafNodeType::RefPair LeafNodeTypeRefPair;
    typedef BTreeNode::Ref NodeRef;

    NodeAllocatorType &_allocator;
    int _numInternalNodes;
    int _numLeafNodes;
    uint32_t _numInserts;
    std::vector<InternalNodeTypeRefPair> _inodes;
    LeafNodeTypeRefPair _leaf;
    AggrCalcT _defaultAggrCalc;
    const AggrCalcT &_aggrCalc;

    void
    normalize();

    void
    allocNewLeafNode();

    InternalNodeType *
    createInternalNode();
public:
    BTreeBuilder(NodeAllocatorType &allocator);

    BTreeBuilder(NodeAllocatorType &allocator, const AggrCalcT &aggrCalc);

    ~BTreeBuilder();

    void
    recursiveDelete(NodeRef node);

    void
    insert(const KeyT &key, const DataT &data);

    NodeRef
    handover();

    void
    reuse();

    void
    clear();
};

extern template class BTreeBuilder<uint32_t, uint32_t,
                                   NoAggregated,
                                   BTreeDefaultTraits::INTERNAL_SLOTS,
                                   BTreeDefaultTraits::LEAF_SLOTS>;
extern template class BTreeBuilder<uint32_t, BTreeNoLeafData,
                                   NoAggregated,
                                   BTreeDefaultTraits::INTERNAL_SLOTS,
                                   BTreeDefaultTraits::LEAF_SLOTS>;
extern template class BTreeBuilder<uint32_t, int32_t,
                                   MinMaxAggregated,
                                   BTreeDefaultTraits::INTERNAL_SLOTS,
                                   BTreeDefaultTraits::LEAF_SLOTS,
                                   MinMaxAggrCalc>;

} // namespace btree

} // namespace search

