// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreestore.hpp"
#include "btreeiterator.hpp"
#include <vespa/vespalib/datastore/buffer_type.hpp>

namespace vespalib::btree {

template class BTreeStore<uint32_t, uint32_t, NoAggregated, std::less<uint32_t>, BTreeDefaultTraits>;
template class BTreeStore<uint32_t, BTreeNoLeafData, NoAggregated, std::less<uint32_t>, BTreeDefaultTraits>;
template class BTreeStore<uint32_t, int32_t, MinMaxAggregated, std::less<uint32_t>, BTreeDefaultTraits, MinMaxAggrCalc>;

}

namespace vespalib::datastore {

using namespace btree;

template class BufferType<BTreeRoot<uint32_t, uint32_t, NoAggregated, std::less<uint32_t>, BTreeDefaultTraits>>;
template class BufferType<BTreeRoot<uint32_t, BTreeNoLeafData, NoAggregated, std::less<uint32_t>, BTreeDefaultTraits>>;
template class BufferType<BTreeRoot<uint32_t, int32_t, MinMaxAggregated, std::less<uint32_t>, BTreeDefaultTraits, MinMaxAggrCalc>>;

}
