// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/btree/btreestore.h>

namespace search
{

namespace attribute
{

template <typename DataT> class PostingListTraits;
template <typename DataT> class PostingStore;

template <>
class PostingListTraits<btree::BTreeNoLeafData>
{
public:
    typedef btree::NoAggregated AggregatedType;
    typedef btree::NoAggrCalc AggrCalcType;
    typedef btree::BTreeStore<uint32_t, btree::BTreeNoLeafData,
                              AggregatedType,
                              std::less<uint32_t>,
                              btree::BTreeDefaultTraits,
                              AggrCalcType> PostingStoreBase;
    typedef PostingStore<btree::BTreeNoLeafData> PostingList;
    typedef PostingStoreBase::KeyDataType Posting;
};


template <>
class PostingListTraits<int32_t>
{
public:
    typedef btree::MinMaxAggregated AggregatedType;
    typedef btree::MinMaxAggrCalc AggrCalcType;
    typedef btree::BTreeStore<uint32_t, int32_t,
                              AggregatedType,
                              std::less<uint32_t>,
                              btree::BTreeDefaultTraits,
                              AggrCalcType> PostingStoreBase;
    typedef PostingStore<int32_t> PostingList;
    typedef PostingStoreBase::KeyDataType Posting;
};


} // namespace attribute

typedef btree::BTreeKeyData<uint32_t, btree::BTreeNoLeafData> AttributePosting;

typedef btree::BTreeKeyData<uint32_t, int32_t> AttributeWeightPosting;


} // namespace search

