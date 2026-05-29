// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "transient_vector_snapshot.h"

namespace vespalib {

template <typename T>
TransientVectorSnapshot<T>::TransientVectorSnapshot(std::span<const S> source)
    : TransientVectorSnapshotBase(), _data() {
    fill(source);
}

template <typename T>
TransientVectorSnapshot<T>::~TransientVectorSnapshot() = default;

template <typename T>
void TransientVectorSnapshot<T>::fill(std::span<const S> source) {
    _data = Vector(source.begin(), source.end());
}

} // namespace vespalib
