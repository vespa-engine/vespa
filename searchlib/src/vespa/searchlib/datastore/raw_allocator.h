// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datastorebase.h"
#include "entryref.h"
#include "handle.h"

namespace search {
namespace datastore {

/**
 * Allocator used to allocate raw buffers (char *) in an underlying data store.
 */
template <typename RefT>
class RawAllocator
{
public:
    using HandleType = Handle<char>;

private:
    DataStoreBase &_store;
    uint32_t _typeId;

public:
    RawAllocator(DataStoreBase &store, uint32_t typeId);

    HandleType alloc(size_t numBytes);
};

}
}
