// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenodestore.h"
#include <vespa/searchlib/datastore/datastore.hpp>

namespace search::btree {

template <typename EntryType>
void
BTreeNodeBufferType<EntryType>::initializeReservedElements(void *buffer, size_t reservedElements)
{
    ParentType::initializeReservedElements(buffer, reservedElements);
    EntryType *e = static_cast<EntryType *>(buffer);
    for (size_t j = reservedElements; j != 0; --j) {
        e->freeze();
        ++e;
    }
}


template <typename EntryType>
void
BTreeNodeBufferType<EntryType>::cleanHold(void *buffer, uint64_t offset, uint64_t len, CleanContext)
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
BTreeNodeStore()
    : _store(),
      _internalNodeType(MIN_BUFFER_ARRAYS, RefType::offsetSize()),
      _leafNodeType(MIN_BUFFER_ARRAYS, RefType::offsetSize())
{
    _store.addType(&_internalNodeType);
    _store.addType(&_leafNodeType);
    _store.initActiveBuffers();
    _store.enableFreeLists();
}

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
BTreeNodeStore<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
~BTreeNodeStore()
{
    _store.dropBuffers(); // Drop buffers before type handlers are dropped
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
std::vector<uint32_t>
BTreeNodeStore<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
startCompact()
{
    std::vector<uint32_t> iToHold = _store.startCompact(NODETYPE_INTERNAL);
    std::vector<uint32_t> lToHold = _store.startCompact(NODETYPE_LEAF);
    std::vector<uint32_t> ret = iToHold;
    ret.insert(ret.end(), lToHold.begin(), lToHold.end());
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

}
