// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_allocator.h"

namespace vespalib::datastore {

/**
 * Allocator used to allocate raw buffers (EntryT *) in an underlying data store
 * with no construction or de-construction of elements in the buffer. Uses free lists if available.
 *
 * If free lists are enabled this allocator should only be used when
 * allocating the same number of elements each time (equal to cluster size).
 */
template <typename EntryT, typename RefT>
class FreeListRawAllocator : public RawAllocator<EntryT, RefT>
{
public:
    using ParentType = RawAllocator<EntryT, RefT>;
    using HandleType = typename ParentType::HandleType;

private:
    using ParentType::_store;
    using ParentType::_typeId;

public:
    FreeListRawAllocator(DataStoreBase &store, uint32_t typeId);

    HandleType alloc(size_t num_entries);
    template <typename BufferType>
    HandleType alloc_dynamic_array(size_t array_size);
};

}
