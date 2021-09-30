// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglisttraits.h"
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>

namespace vespalib::btree {

using NoD = search::attribute::PostingListTraits<BTreeNoLeafData>;
using WD = search::attribute::PostingListTraits<int32_t>;

template class BTreeStore<uint32_t, BTreeNoLeafData, NoD::AggregatedType, std::less<uint32_t>, NoD::BTreeTraits, NoD::AggrCalcType>;
template class BTreeRoot<uint32_t, BTreeNoLeafData, NoD::AggregatedType, std::less<uint32_t>, NoD::BTreeTraits, NoD::AggrCalcType>;
template class BTreeRootT<uint32_t, BTreeNoLeafData, NoD::AggregatedType, std::less<uint32_t>, NoD::BTreeTraits>;
template class BTreeNodeAllocator<uint32_t, BTreeNoLeafData, NoD::AggregatedType, NoD::BTreeTraits::INTERNAL_SLOTS, NoD::BTreeTraits::LEAF_SLOTS>;
template class BTreeBuilder<uint32_t, BTreeNoLeafData, NoD::AggregatedType, NoD::BTreeTraits::INTERNAL_SLOTS, NoD::BTreeTraits::LEAF_SLOTS, NoD::AggrCalcType>;
template class BTreeIteratorBase<uint32_t, BTreeNoLeafData, NoD::AggregatedType, NoD::BTreeTraits::INTERNAL_SLOTS, NoD::BTreeTraits::LEAF_SLOTS, NoD::BTreeTraits::PATH_SIZE>;
template class BTreeConstIterator<uint32_t, BTreeNoLeafData, NoD::AggregatedType, std::less<uint32_t>, NoD::BTreeTraits>;

template class BTreeStore<uint32_t, int32_t, WD::AggregatedType, std::less<uint32_t>, WD::BTreeTraits, WD::AggrCalcType>;
template class BTreeRoot<uint32_t, int32_t, WD::AggregatedType, std::less<uint32_t>, WD::BTreeTraits, WD::AggrCalcType>;
template class BTreeRootT<uint32_t, int32_t, WD::AggregatedType, std::less<uint32_t>, WD::BTreeTraits>;
template class BTreeNodeAllocator<uint32_t, int32_t, WD::AggregatedType, WD::BTreeTraits::INTERNAL_SLOTS, WD::BTreeTraits::LEAF_SLOTS>;
template class BTreeBuilder<uint32_t, int32_t, WD::AggregatedType, WD::BTreeTraits::INTERNAL_SLOTS, WD::BTreeTraits::LEAF_SLOTS, WD::AggrCalcType>;
template class BTreeIteratorBase<uint32_t, int32_t, WD::AggregatedType, WD::BTreeTraits::INTERNAL_SLOTS, WD::BTreeTraits::LEAF_SLOTS, WD::BTreeTraits::PATH_SIZE>;
template class BTreeConstIterator<uint32_t, int32_t, WD::AggregatedType, std::less<uint32_t>, WD::BTreeTraits>;
template class BTreeAggregator<uint32_t, int32_t, WD::AggregatedType, WD::BTreeTraits::INTERNAL_SLOTS, WD::BTreeTraits::LEAF_SLOTS, WD::AggrCalcType >;

}
