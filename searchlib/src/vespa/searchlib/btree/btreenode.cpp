// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btreenode.h"
#include "btreenode.hpp"

namespace search::btree {

BTreeNoLeafData BTreeNoLeafData::_instance;

NoAggregated BTreeNodeAggregatedWrap<NoAggregated>::_instance;
template <>
MinMaxAggregated BTreeNodeAggregatedWrap<MinMaxAggregated>::_instance =
    MinMaxAggregated();

template class BTreeNodeDataWrap<uint32_t, 16>;
template class BTreeNodeDataWrap<BTreeNoLeafData, 16>;
template class BTreeKeyData<uint32_t, uint32_t>;
template class BTreeKeyData<uint32_t, int32_t>;
template class BTreeNodeT<uint32_t, 16>;
template class BTreeNodeTT<uint32_t, uint32_t, NoAggregated, 16>;
template class BTreeNodeTT<uint32_t, BTreeNoLeafData, NoAggregated, 16>;
template class BTreeNodeTT<uint32_t, datastore::EntryRef, NoAggregated, 16>;
template class BTreeNodeTT<uint32_t, int32_t, MinMaxAggregated, 16>;
template class BTreeInternalNode<uint32_t, NoAggregated, 16>;
template class BTreeInternalNode<uint32_t, MinMaxAggregated, 16>;
template class BTreeLeafNode<uint32_t, uint32_t, NoAggregated, 16>;
template class BTreeLeafNode<uint32_t, BTreeNoLeafData, NoAggregated, 16>;
template class BTreeLeafNode<uint32_t, int32_t, MinMaxAggregated, 16>;
template class BTreeLeafNodeTemp<uint32_t, uint32_t, NoAggregated, 16>;
template class BTreeLeafNodeTemp<uint32_t, int32_t, MinMaxAggregated,
                                        16>;
template class BTreeLeafNodeTemp<uint32_t, BTreeNoLeafData, NoAggregated, 16>;

} // namespace search::btree
