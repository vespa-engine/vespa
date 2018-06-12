// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreeroot.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.btree.btreeroot");

#include "btreenodeallocator.h"
#include "btreeiterator.hpp"
#include "btreeroot.hpp"
#include "btreenode.hpp"

namespace search::btree {

template class BTreeRootT<uint32_t, uint32_t, NoAggregated>;
template class BTreeRootT<uint32_t, BTreeNoLeafData, NoAggregated>;
template class BTreeRootT<uint32_t, int32_t, MinMaxAggregated>;
template class BTreeRoot<uint32_t, uint32_t, NoAggregated>;
template class BTreeRoot<uint32_t, BTreeNoLeafData, NoAggregated>;
template class BTreeRoot<uint32_t, int32_t, MinMaxAggregated,
                         std::less<uint32_t>,
                         BTreeDefaultTraits, MinMaxAggrCalc>;

}
