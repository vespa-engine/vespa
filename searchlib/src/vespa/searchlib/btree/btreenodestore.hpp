// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenodestore.h"
#include <vespa/searchlib/datastore/datastore.hpp>

namespace search
{

namespace btree
{

template <typename EntryType>
void
BTreeNodeBufferType<EntryType>::cleanInitialElements(void *buffer)
{
    ParentType::cleanInitialElements(buffer);
    EntryType *e = static_cast<EntryType *>(buffer);
    for (size_t j = _clusterSize; j != 0; --j) {
        e->freeze();
        ++e;
    }
}


template <typename EntryType>
void
BTreeNodeBufferType<EntryType>::cleanHold(void *buffer,
                                          uint64_t offset,
                                          uint64_t len)
{
    EntryType *e = static_cast<EntryType *>(buffer) + offset;
    for (size_t j = len; j != 0; --j) {
        e->cleanFrozen();
        ++e;
    }
}




template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
BTreeNodeStore<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
BTreeNodeStore(void)
    : _store(),
      _internalNodeType(MIN_CLUSTERS, RefType::offsetSize()),
      _leafNodeType(MIN_CLUSTERS, RefType::offsetSize())
{
    _store.addType(&_internalNodeType);
    _store.addType(&_leafNodeType);
    _store.initActiveBuffers();
    _store.enableFreeLists();
}

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
BTreeNodeStore<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
~BTreeNodeStore(void)
{
    _store.dropBuffers(); // Drop buffers before type handlers are dropped
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
std::vector<uint32_t>
BTreeNodeStore<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
startCompact(void)
{
    std::vector<uint32_t> iToHold =
        _store.startCompact(NODETYPE_INTERNAL);
    std::vector<uint32_t> lToHold =
        _store.startCompact(NODETYPE_LEAF);
    std::vector<uint32_t> ret = iToHold;
    for (std::vector<uint32_t>::const_iterator
             i = lToHold.begin(), ie = lToHold.end(); i != ie; ++i)
        ret.push_back(*i);
    return ret;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
void
BTreeNodeStore<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
finishCompact(const std::vector<uint32_t> &toHold)
{
    _store.finishCompact(toHold);
}


} // namespace btree

} // namespace search


