// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transient_bitvector_snapshot.h"

#include "allocatedbitvector.h"

#include <cassert>

/////////////////////////////////
namespace search {

TransientBitVectorSnapshot::TransientBitVectorSnapshot(Index size, const BitVector& org) : _bv(), _tracker() {
    auto lock = _tracker.acquire_lock();
    _bv = std::make_unique<AllocatedBitVector>(size, size, &org, nullptr, false);
    _tracker.set_transient_memory(std::move(lock), BitVector::legacy_num_bytes_with_single_guard_bit(_bv->size()));
}

TransientBitVectorSnapshot::TransientBitVectorSnapshot(TransientBitVectorSnapshot&&) noexcept = default;

TransientBitVectorSnapshot::~TransientBitVectorSnapshot() {
    if (_bv) {
        auto lock = _tracker.acquire_lock();
        _bv.reset();
        _tracker.set_transient_memory(std::move(lock), 0);
    }
}

TransientBitVectorSnapshot& TransientBitVectorSnapshot::operator=(TransientBitVectorSnapshot&&) noexcept = default;

} // namespace search
