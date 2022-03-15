// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vector>

namespace search::enumstore {

using Index = vespalib::datastore::EntryRef;
using InternalIndex = vespalib::datastore::EntryRefT<22>;
using IndexVector = std::vector<Index, vespalib::allocator_large<Index>>;
using EnumHandle = uint32_t;
using EnumVector = std::vector<uint32_t, vespalib::allocator_large<uint32_t>>;

}
