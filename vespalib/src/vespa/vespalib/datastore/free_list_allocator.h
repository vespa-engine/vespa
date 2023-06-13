// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "allocator.h"

namespace vespalib::datastore {

/**
 * Allocator used to allocate entries of a specific type in an underlying data store
 * and uses free lists if available.
 */
template <typename EntryT, typename RefT, typename ReclaimerT>
class FreeListAllocator : public Allocator<EntryT, RefT>
{
public:
    using ParentType = Allocator<EntryT, RefT>;
    using HandleType = typename ParentType::HandleType;
    using ConstArrayRef = typename ParentType::ConstArrayRef;

private:
    using ParentType::_store;
    using ParentType::_typeId;

public:
    FreeListAllocator(DataStoreBase &store, uint32_t typeId);

    template <typename ... Args>
    HandleType alloc(Args && ... args);

    HandleType allocArray(ConstArrayRef array);
    HandleType allocArray();
};

}
