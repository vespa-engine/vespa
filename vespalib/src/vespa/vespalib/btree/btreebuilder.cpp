// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreebuilder.hpp"
#include "btreenode.hpp"

namespace vespalib::btree {

template class BTreeBuilder<uint32_t, uint32_t, NoAggregated,
                            BTreeDefaultTraits::INTERNAL_SLOTS,
                            BTreeDefaultTraits::LEAF_SLOTS>;
template class BTreeBuilder<uint32_t, BTreeNoLeafData, NoAggregated,
                            BTreeDefaultTraits::INTERNAL_SLOTS,
                            BTreeDefaultTraits::LEAF_SLOTS>;
template class BTreeBuilder<uint32_t, int32_t, MinMaxAggregated,
                            BTreeDefaultTraits::INTERNAL_SLOTS,
                            BTreeDefaultTraits::LEAF_SLOTS,
                            MinMaxAggrCalc>;

}
