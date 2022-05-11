// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "allocatedbitvector.h"
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/generationholder.h>

namespace search {

class GrowableBitVector
{
public:
    using Alloc = vespalib::alloc::Alloc;
    using GenerationHolder = vespalib::GenerationHolder;
    using GenerationHeldBase = vespalib::GenerationHeldBase;
    GrowableBitVector(BitWord::Index newSize, BitWord::Index newCapacity,
                      GenerationHolder &generationHolder, const Alloc *init_alloc = nullptr);

    const BitVector &reader() const { return acquire_self(); }
    AllocatedBitVector &writer() { return *_stored; }

    BitWord::Index extraByteSize() const {
        return sizeof(AllocatedBitVector) + acquire_self().extraByteSize();
    }

    /** Will return true if a a buffer is held */
    bool reserve(BitWord::Index newCapacity);
    bool shrink(BitWord::Index newCapacity);
    bool extend(BitWord::Index newCapacity);
private:
    GenerationHeldBase::UP grow(BitWord::Index newLength, BitWord::Index newCapacity);

    AllocatedBitVector &acquire_self() const { return *(_self.load(std::memory_order_acquire)); }

    VESPA_DLL_LOCAL bool hold(GenerationHeldBase::UP v);
    std::unique_ptr<AllocatedBitVector> _stored;
    std::atomic<AllocatedBitVector *> _self;
    GenerationHolder &_generationHolder;
};

} // namespace search

