// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "transient_vector_snapshot.h"

namespace vespalib {

template <typename T>
TransientVectorSnapshot<T>::TransientVectorSnapshot(std::span<const S> source) : _data(), _tracker() {
    fill(source);
}

template <typename T>
TransientVectorSnapshot<T>::~TransientVectorSnapshot() {
    auto lock = _tracker.acquire_lock();
    Vector().swap(_data);
    _tracker.set_transient_memory(std::move(lock), 0);
}

template <typename T>
void TransientVectorSnapshot<T>::fill(std::span<const S> source) {
    auto lock = _tracker.acquire_lock();
    _data = Vector(source.begin(), source.end());
    _tracker.set_transient_memory(std::move(lock), sizeof(T) * source.size());
}

} // namespace vespalib
