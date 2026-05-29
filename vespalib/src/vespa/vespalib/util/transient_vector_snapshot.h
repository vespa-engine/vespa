// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "transient_vector_snapshot_base.h"

#include <vespa/vespalib/stllike/allocator.h>

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
class TransientVectorSnapshot : public TransientVectorSnapshotBase {
public:
    using S = std::conditional_t<std::is_same_v<T, datastore::EntryRef>, datastore::AtomicEntryRef, T>;
    using Vector = std::vector<T, allocator_large<T>>;

private:
    Vector _data;

public:
    TransientVectorSnapshot(std::span<const S> source);
    TransientVectorSnapshot(const TransientVectorSnapshot<T>&) = delete;
    TransientVectorSnapshot(TransientVectorSnapshot<T>&&) noexcept = default;
    ;
    ~TransientVectorSnapshot();
    TransientVectorSnapshot& operator=(const TransientVectorSnapshot<T>&) = delete;
    TransientVectorSnapshot& operator=(TransientVectorSnapshot<T>&&) noexcept = default;
    ;
    void fill(std::span<const S> source);
    [[nodiscard]] std::span<const T> span() const noexcept { return _data; }
};

template <>
void TransientVectorSnapshot<datastore::EntryRef>::fill(std::span<const datastore::AtomicEntryRef> source);

} // namespace vespalib
