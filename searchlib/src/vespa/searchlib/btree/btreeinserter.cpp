// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreeinserter.h"
#include "btreenodeallocator.h"
#include "btreerootbase.hpp"
#include "btreeinserter.hpp"
#include "btreenode.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.btree.btreeinserter");

namespace search::btree {

template class BTreeInserter<uint32_t, uint32_t, NoAggregated>;
template class BTreeInserter<uint32_t, BTreeNoLeafData, NoAggregated>;
template class BTreeInserter<uint32_t, int32_t, MinMaxAggregated,
                             std::less<uint32_t>,
                             BTreeDefaultTraits,
                             MinMaxAggrCalc>;

}
