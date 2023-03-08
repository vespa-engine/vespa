// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "save_utils.h"
#include <cassert>

namespace search::attribute {

EntryRefVector
make_entry_ref_vector_snapshot(const vespalib::RcuVectorBase<vespalib::datastore::AtomicEntryRef>& ref_vector, uint32_t size)
{
    assert(size <= ref_vector.get_size());
    auto* source = &ref_vector.get_elem_ref(0);
    EntryRefVector result;
    result.reserve(size);
    for (uint32_t lid = 0; lid < size; ++lid) {
        result.emplace_back(source[lid].load_relaxed());
    }
    return result;
}

}
