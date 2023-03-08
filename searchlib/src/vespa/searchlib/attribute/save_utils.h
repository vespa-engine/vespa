// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <vector>

namespace search::attribute {

using EntryRefVector = std::vector<vespalib::datastore::EntryRef, vespalib::allocator_large<vespalib::datastore::EntryRef>>;

EntryRefVector make_entry_ref_vector_snapshot(const vespalib::RcuVectorBase<vespalib::datastore::AtomicEntryRef>& ref_vector, uint32_t size);

}
