// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transient_vector_snapshot.hpp"

#include <vespa/vespalib/datastore/atomic_entry_ref.h>

using vespalib::datastore::AtomicEntryRef;
using vespalib::datastore::EntryRef;

namespace vespalib {

template <>
void TransientVectorSnapshot<EntryRef>::fill(std::span<const AtomicEntryRef> src) {
    _data.reserve(src.size());
    for (const auto& entry : src) {
        _data.emplace_back(entry.load_relaxed());
    }
}

template class TransientVectorSnapshot<EntryRef>;
template class TransientVectorSnapshot<uint8_t>;
template class TransientVectorSnapshot<uint16_t>;

} // namespace vespalib
