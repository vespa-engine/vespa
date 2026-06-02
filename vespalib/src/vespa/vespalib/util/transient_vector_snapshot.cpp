// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transient_vector_snapshot.hpp"

#include <vespa/vespalib/datastore/atomic_entry_ref.h>

using vespalib::datastore::AtomicEntryRef;
using vespalib::datastore::EntryRef;

namespace vespalib {

template <>
void TransientVectorSnapshot<EntryRef>::fill(std::span<const AtomicEntryRef> source) {
    auto lock = _tracker.acquire_lock();
    _data.reserve(source.size());
    for (const auto& entry : source) {
        _data.emplace_back(entry.load_relaxed());
    }
    _tracker.set_transient_memory(std::move(lock), sizeof(AtomicEntryRef) * source.size());
}

template class TransientVectorSnapshot<EntryRef>;
template class TransientVectorSnapshot<uint8_t>;
template class TransientVectorSnapshot<uint16_t>;

} // namespace vespalib
