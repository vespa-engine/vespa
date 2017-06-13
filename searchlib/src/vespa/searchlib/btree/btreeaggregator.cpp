// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreetraits.h"
#include "btreeaggregator.hpp"
#include "noaggrcalc.h"
#include "minmaxaggrcalc.h"

namespace search::btree {

template class BTreeAggregator<uint32_t, uint32_t>;
template class BTreeAggregator<uint32_t, BTreeNoLeafData>;
template class BTreeAggregator<uint32_t, int32_t, MinMaxAggregated,
                               BTreeDefaultTraits::INTERNAL_SLOTS,
                               BTreeDefaultTraits::LEAF_SLOTS,
                               MinMaxAggrCalc>;

}
