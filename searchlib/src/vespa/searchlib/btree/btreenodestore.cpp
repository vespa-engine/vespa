// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreenodestore.hpp"
#include "btreenode.h"
#include "btreerootbase.h"
#include "btreeroot.h"
#include "btreenodeallocator.h"
#include <vespa/searchlib/datastore/datastore.h>

namespace search::btree {

template class BTreeNodeStore<uint32_t, uint32_t,
                              NoAggregated,
                              BTreeDefaultTraits::INTERNAL_SLOTS,
                              BTreeDefaultTraits::LEAF_SLOTS>;
template class BTreeNodeStore<uint32_t, BTreeNoLeafData,
                              NoAggregated,
                              BTreeDefaultTraits::INTERNAL_SLOTS,
                              BTreeDefaultTraits::LEAF_SLOTS>;
template class BTreeNodeStore<uint32_t, int32_t,
                              MinMaxAggregated,
                              BTreeDefaultTraits::INTERNAL_SLOTS,
                              BTreeDefaultTraits::LEAF_SLOTS>;


typedef BTreeNodeStore<uint32_t, uint32_t, NoAggregated,
                       BTreeDefaultTraits::INTERNAL_SLOTS,
                       BTreeDefaultTraits::LEAF_SLOTS>        MyNodeStore1;
typedef BTreeNodeStore<uint32_t, BTreeNoLeafData, NoAggregated,
                       BTreeDefaultTraits::INTERNAL_SLOTS,
                       BTreeDefaultTraits::LEAF_SLOTS> MyNodeStore2;
typedef BTreeNodeStore<uint32_t, int32_t, MinMaxAggregated,
                       BTreeDefaultTraits::INTERNAL_SLOTS,
                       BTreeDefaultTraits::LEAF_SLOTS>        MyNodeStore3;

}
