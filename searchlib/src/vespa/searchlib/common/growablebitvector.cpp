// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "growablebitvector.h"
#include <cassert>

/////////////////////////////////
namespace search {

using vespalib::GenerationHeldBase;
using vespalib::GenerationHolder;

namespace {

struct GenerationHeldAllocatedBitVector : public vespalib::GenerationHeldBase {
    std::unique_ptr<AllocatedBitVector> vector;
    GenerationHeldAllocatedBitVector(std::unique_ptr<AllocatedBitVector> vector_in)
      : GenerationHeldBase(sizeof(AllocatedBitVector) + vector_in->extraByteSize()),
        vector(std::move(vector_in)) {}
};

}

GrowableBitVector::GrowableBitVector(BitWord::Index newSize, BitWord::Index newCapacity,
                                     GenerationHolder &generationHolder,
                                     const Alloc *init_alloc)
    : _stored(std::make_unique<AllocatedBitVector>(newSize, newCapacity, nullptr, init_alloc, true)),
      _self(_stored.get()),
      _generationHolder(generationHolder)
{
    assert(newSize <= newCapacity);
}

GrowableBitVector::~GrowableBitVector() = default;

std::unique_ptr<const BitVector>
GrowableBitVector::make_snapshot(BitWord::Index new_size)
{
    AllocatedBitVector& self = *_stored;
    assert(new_size <= self.size());
    return std::make_unique<AllocatedBitVector>(new_size, new_size, &self, nullptr, false);
}

void
GrowableBitVector::fixup_after_load()
{
    AllocatedBitVector& self = *_stored;
    self.set_dynamic_guard_bits(self.size());
    self.set_dynamic_guard_bits(self.capacity());
    self.updateCount();
}

GenerationHeldBase::UP
GrowableBitVector::grow(BitWord::Index newSize, BitWord::Index newCapacity)
{
    AllocatedBitVector &self = *_stored;
    assert(newCapacity >= newSize);
    if (newCapacity != self.capacity()) {
        auto tbv = std::make_unique<AllocatedBitVector>(newSize, newCapacity, &self, &self._alloc, true);
        auto to_hold = std::make_unique<GenerationHeldAllocatedBitVector>(std::move(_stored));
        _self.store(tbv.get(), std::memory_order_release);
        _stored = std::move(tbv);
        return to_hold;
    } else {
        if (newSize > self.size()) {
            BitVector::Range clearRange(self.size(), newSize);
            self.setSize(newSize);
            self.clearIntervalNoInvalidation(clearRange);
        } else {
            self.clearIntervalNoInvalidation(BitVector::Range(newSize, self.size()));
            self.setSize(newSize);
            self.updateCount();
        }
    }
    return {};
}

bool
GrowableBitVector::reserve(BitWord::Index newCapacity)
{
    BitWord::Index oldCapacity = _stored->capacity();
    assert(newCapacity >= oldCapacity);
    if (newCapacity == oldCapacity)
        return false;
    return hold(grow(_stored->size(), newCapacity));
}

bool
GrowableBitVector::hold(GenerationHeldBase::UP v)
{
    if (v) {
        _generationHolder.insert(std::move(v));
        return true;
    }
    return false;
}

bool
GrowableBitVector::shrink(BitWord::Index newCapacity)
{
    BitWord::Index oldCapacity = _stored->capacity();
    assert(newCapacity <= oldCapacity);
    (void) oldCapacity;
    return hold(grow(newCapacity, std::max(_stored->capacity(), newCapacity)));
}

bool
GrowableBitVector::extend(BitWord::Index newCapacity)
{
    return hold(grow(newCapacity, std::max(_stored->capacity(), newCapacity)));
}

} // namespace search
