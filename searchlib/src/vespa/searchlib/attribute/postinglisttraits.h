// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/btree/btreestore.h>

namespace search::attribute {

template <typename DataT> class PostingListTraits;
template <typename DataT> class PostingStore;

template <>
class PostingListTraits<vespalib::btree::BTreeNoLeafData>
{
private:
    using BTreeTraits = vespalib::btree::BTreeTraits<64, 16, 8, true>;
public:
    using AggregatedType = vespalib::btree::NoAggregated;
    using AggrCalcType = vespalib::btree::NoAggrCalc;
    using const_iterator = vespalib::btree::BTreeConstIterator<uint32_t, vespalib::btree::BTreeNoLeafData, AggregatedType, std::less<uint32_t>, BTreeTraits >;
    using PostingStoreBase = vespalib::btree::BTreeStore<uint32_t, vespalib::btree::BTreeNoLeafData, AggregatedType, std::less<uint32_t>, BTreeTraits, AggrCalcType> ;
    using PostingStoreType = PostingStore<vespalib::btree::BTreeNoLeafData>;
    using Posting = PostingStoreBase::KeyDataType;
};

template <>
class PostingListTraits<int32_t>
{
private:
    using BTreeTraits = vespalib::btree::BTreeTraits<32, 16, 9, true>;
public:
    using AggregatedType = vespalib::btree::MinMaxAggregated;
    using AggrCalcType = vespalib::btree::MinMaxAggrCalc;
    using const_iterator = vespalib::btree::BTreeConstIterator<uint32_t, int32_t, AggregatedType, std::less<uint32_t>, BTreeTraits >;
    using PostingStoreBase = vespalib::btree::BTreeStore<uint32_t, int32_t, AggregatedType, std::less<uint32_t>, BTreeTraits, AggrCalcType>;
    using PostingStoreType = PostingStore<int32_t>;
    using Posting = PostingStoreBase::KeyDataType;
};

}
