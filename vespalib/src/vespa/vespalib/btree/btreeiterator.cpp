// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreeiterator.hpp"
#include "btreenode.hpp"

namespace vespalib::btree {

template class BTreeIteratorBase<uint32_t, uint32_t, NoAggregated>;
template class BTreeIteratorBase<uint32_t, BTreeNoLeafData, NoAggregated>;
template class BTreeIteratorBase<uint32_t, int32_t, MinMaxAggregated>;
template class BTreeConstIterator<uint32_t, uint32_t, NoAggregated>;
template class BTreeConstIterator<uint32_t, BTreeNoLeafData, NoAggregated>;
template class BTreeConstIterator<uint32_t, int32_t, MinMaxAggregated>;
template class BTreeIterator<uint32_t, uint32_t, NoAggregated>;
template class BTreeIterator<uint32_t, BTreeNoLeafData, NoAggregated>;
template class BTreeIterator<uint32_t, int32_t, MinMaxAggregated>;

}
