// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreestore.h"
#include "btreestore.hpp"
#include "btreeiterator.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.btree.breestore");

namespace search::btree {

template class BTreeStore<uint32_t, uint32_t,
                          NoAggregated,
                          std::less<uint32_t>,
                          BTreeDefaultTraits>;

template class BTreeStore<uint32_t, BTreeNoLeafData,
                          NoAggregated,
                          std::less<uint32_t>,
                          BTreeDefaultTraits>;

template class BTreeStore<uint32_t, int32_t,
                          MinMaxAggregated,
                          std::less<uint32_t>,
                          BTreeDefaultTraits,
                          MinMaxAggrCalc>;

}
