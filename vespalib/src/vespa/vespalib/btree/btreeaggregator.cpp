// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreeaggregator.hpp"
#include "minmaxaggrcalc.h"

namespace vespalib::btree {

template class BTreeAggregator<uint32_t, uint32_t>;
template class BTreeAggregator<uint32_t, BTreeNoLeafData>;
template class BTreeAggregator<uint32_t, int32_t, MinMaxAggregated,
                               BTreeDefaultTraits::INTERNAL_SLOTS,
                               BTreeDefaultTraits::LEAF_SLOTS,
                               MinMaxAggrCalc>;

}
