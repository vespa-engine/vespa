// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vector>

namespace search::enumstore {

using AtomicIndex = vespalib::datastore::AtomicEntryRef;
using EnumHandle = uint32_t;
using EnumVector = std::vector<uint32_t, vespalib::allocator_large<uint32_t>>;
using Index = vespalib::datastore::EntryRef;
using IndexVector = std::vector<Index, vespalib::allocator_large<Index>>;
using InternalIndex = vespalib::datastore::EntryRefT<22>;

}
