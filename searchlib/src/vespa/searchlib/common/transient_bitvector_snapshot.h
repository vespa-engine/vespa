// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvector.h"

#include <vespa/vespalib/util/transient_memory_tracker.h>

namespace search {

/*
 * Transient bitvector snapshot used during save of a single value bool attribute vector.
 */
class TransientBitVectorSnapshot {
    using Index = BitWord::Index;
    std::unique_ptr<BitVector>       _bv;
    vespalib::TransientMemoryTracker _tracker;

public:
    TransientBitVectorSnapshot(Index size, const BitVector& org);
    TransientBitVectorSnapshot(const TransientBitVectorSnapshot&) = delete;
    TransientBitVectorSnapshot(TransientBitVectorSnapshot&&) noexcept;
    ~TransientBitVectorSnapshot();
    TransientBitVectorSnapshot& operator=(const TransientBitVectorSnapshot&) = delete;
    TransientBitVectorSnapshot& operator=(TransientBitVectorSnapshot&&) noexcept;
    const BitVector& bitvector() const noexcept { return *_bv; }
};

} // namespace search
