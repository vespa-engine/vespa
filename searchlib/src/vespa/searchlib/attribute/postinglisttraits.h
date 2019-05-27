// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/btree/btreestore.h>

namespace search::attribute {

template <typename DataT> class PostingListTraits;
template <typename DataT> class PostingStore;

template <>
class PostingListTraits<btree::BTreeNoLeafData>
{
private:
    using BTreeTraits = btree::BTreeTraits<64, 16, 8, true>;
public:
    using AggregatedType = btree::NoAggregated;
    using AggrCalcType = btree::NoAggrCalc;
    using const_iterator = btree::BTreeConstIterator<uint32_t, btree::BTreeNoLeafData, AggregatedType, std::less<uint32_t>, BTreeTraits >;
    using PostingStoreBase = btree::BTreeStore<uint32_t, btree::BTreeNoLeafData, AggregatedType, std::less<uint32_t>, BTreeTraits, AggrCalcType> ;
    using PostingList = PostingStore<btree::BTreeNoLeafData>;
    using Posting = PostingStoreBase::KeyDataType;
};

template <>
class PostingListTraits<int32_t>
{
private:
    using BTreeTraits = btree::BTreeTraits<32, 16, 9, true>;
public:
    using AggregatedType = btree::MinMaxAggregated;
    using AggrCalcType = btree::MinMaxAggrCalc;
    using const_iterator = btree::BTreeConstIterator<uint32_t, int32_t, AggregatedType, std::less<uint32_t>, BTreeTraits >;
    using PostingStoreBase = btree::BTreeStore<uint32_t, int32_t, AggregatedType, std::less<uint32_t>, BTreeTraits, AggrCalcType>;
    using PostingList = PostingStore<int32_t>;
    using Posting = PostingStoreBase::KeyDataType;
};

}
