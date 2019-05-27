// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglisttraits.h"
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>

namespace search {

using btree::BTreeNoLeafData;
using NoD = attribute::PostingListTraits<BTreeNoLeafData>;
using WD = attribute::PostingListTraits<int32_t>;

template class btree::BTreeStore<uint32_t, BTreeNoLeafData, NoD::AggregatedType, std::less<uint32_t>, NoD::BTreeTraits, NoD::AggrCalcType>;
template class btree::BTreeRoot<uint32_t, BTreeNoLeafData, NoD::AggregatedType, std::less<uint32_t>, NoD::BTreeTraits, NoD::AggrCalcType>;
template class btree::BTreeRootT<uint32_t, BTreeNoLeafData, NoD::AggregatedType, std::less<uint32_t>, NoD::BTreeTraits>;
template class btree::BTreeNodeAllocator<uint32_t, BTreeNoLeafData, NoD::AggregatedType, NoD::BTreeTraits::INTERNAL_SLOTS, NoD::BTreeTraits::LEAF_SLOTS>;
template class btree::BTreeBuilder<uint32_t, BTreeNoLeafData, NoD::AggregatedType, NoD::BTreeTraits::INTERNAL_SLOTS, NoD::BTreeTraits::LEAF_SLOTS, NoD::AggrCalcType>;
template class btree::BTreeIteratorBase<uint32_t, BTreeNoLeafData, NoD::AggregatedType, NoD::BTreeTraits::INTERNAL_SLOTS, NoD::BTreeTraits::LEAF_SLOTS, NoD::BTreeTraits::PATH_SIZE>;
template class btree::BTreeConstIterator<uint32_t, BTreeNoLeafData, NoD::AggregatedType, std::less<uint32_t>, NoD::BTreeTraits>;

template class btree::BTreeStore<uint32_t, int32_t, WD::AggregatedType, std::less<uint32_t>, WD::BTreeTraits, WD::AggrCalcType>;
template class btree::BTreeRoot<uint32_t, int32_t, WD::AggregatedType, std::less<uint32_t>, WD::BTreeTraits, WD::AggrCalcType>;
template class btree::BTreeRootT<uint32_t, int32_t, WD::AggregatedType, std::less<uint32_t>, WD::BTreeTraits>;
template class btree::BTreeNodeAllocator<uint32_t, int32_t, WD::AggregatedType, WD::BTreeTraits::INTERNAL_SLOTS, WD::BTreeTraits::LEAF_SLOTS>;
template class btree::BTreeBuilder<uint32_t, int32_t, WD::AggregatedType, WD::BTreeTraits::INTERNAL_SLOTS, WD::BTreeTraits::LEAF_SLOTS, WD::AggrCalcType>;
template class btree::BTreeIteratorBase<uint32_t, int32_t, WD::AggregatedType, WD::BTreeTraits::INTERNAL_SLOTS, WD::BTreeTraits::LEAF_SLOTS, WD::BTreeTraits::PATH_SIZE>;
template class btree::BTreeConstIterator<uint32_t, int32_t, WD::AggregatedType, std::less<uint32_t>, WD::BTreeTraits>;
template class btree::BTreeAggregator<uint32_t, int32_t, WD::AggregatedType, WD::BTreeTraits::INTERNAL_SLOTS, WD::BTreeTraits::LEAF_SLOTS, WD::AggrCalcType >;

} // namespace search

