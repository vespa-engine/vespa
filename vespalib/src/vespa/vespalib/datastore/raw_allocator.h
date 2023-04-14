// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datastorebase.h"
#include "entryref.h"
#include "handle.h"

namespace vespalib::datastore {

/**
 * Allocator used to allocate raw buffers (EntryT *) in an underlying data store
 * with no construction or de-construction of elements in the buffer.
 */
template <typename EntryT, typename RefT>
class RawAllocator
{
public:
    using HandleType = Handle<EntryT>;

protected:
    DataStoreBase &_store;
    uint32_t _typeId;

public:
    RawAllocator(DataStoreBase &store, uint32_t typeId);

    HandleType alloc(size_t num_entries) {
        return alloc(num_entries, 0);
    }
    HandleType alloc(size_t num_entries, size_t extra_entries);
};

}
