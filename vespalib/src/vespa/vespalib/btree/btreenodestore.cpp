// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreenodestore.hpp"
#include "btreerootbase.h"
#include "btreeroot.h"
#include "btreenodeallocator.h"
#include <vespa/vespalib/datastore/datastore.h>

namespace vespalib::btree {

template class BTreeNodeStore<uint32_t, uint32_t, NoAggregated,
                              BTreeDefaultTraits::INTERNAL_SLOTS,
                              BTreeDefaultTraits::LEAF_SLOTS>;
template class BTreeNodeStore<uint32_t, BTreeNoLeafData, NoAggregated,
                              BTreeDefaultTraits::INTERNAL_SLOTS,
                              BTreeDefaultTraits::LEAF_SLOTS>;
template class BTreeNodeStore<uint32_t, int32_t, MinMaxAggregated,
                              BTreeDefaultTraits::INTERNAL_SLOTS,
                              BTreeDefaultTraits::LEAF_SLOTS>;

}
