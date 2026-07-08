// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "transient_memory_tracker.h"

#include <vespa/vespalib/stllike/allocator.h>

#include <cstdint>
#include <span>
#include <type_traits>
#include <vector>

namespace vespalib::datastore {

class EntryRef;
class AtomicEntryRef;

} // namespace vespalib::datastore

namespace vespalib {

/*
 * Class containing a transient snapshot of a vector.
 */
template <typename T>
class TransientVectorSnapshot {
public:
    using S = std::conditional_t<std::is_same_v<T, datastore::EntryRef>, datastore::AtomicEntryRef, T>;
    using Vector = std::vector<T, allocator_large<T>>;

private:
    Vector                 _data;
    TransientMemoryTracker _tracker;

public:
    explicit TransientVectorSnapshot(std::span<const S> source);
    TransientVectorSnapshot(const TransientVectorSnapshot<T>&) = delete;
    TransientVectorSnapshot(TransientVectorSnapshot<T>&&) noexcept = default;
    ~TransientVectorSnapshot();
    TransientVectorSnapshot& operator=(const TransientVectorSnapshot<T>&) = delete;
    TransientVectorSnapshot& operator=(TransientVectorSnapshot<T>&&) noexcept = default;
    void fill(std::span<const S> source);
    [[nodiscard]] std::span<const T> span() const noexcept { return _data; }
};

template <>
void TransientVectorSnapshot<datastore::EntryRef>::fill(std::span<const datastore::AtomicEntryRef> source);

extern template class TransientVectorSnapshot<vespalib::datastore::EntryRef>;
extern template class TransientVectorSnapshot<uint8_t>;
extern template class TransientVectorSnapshot<uint16_t>;

} // namespace vespalib
